# AndroidHarness

**AndroidHarness** is a coding-agent runtime that lives entirely on your Android phone. It reads and
writes real files in a sandboxed on-device project directory, runs real allowlisted processes, and
talks to a real language model — either an OpenAI-compatible HTTP endpoint (bring your own key) or a
`.gguf` model loaded with llama.cpp, fully offline.

Nothing about the agent loop is simulated: tool results shown in the chat are the actual stdout/exit
codes of the processes that ran, and file edits are written to the real filesystem with atomic renames.

[![Build AndroidHarness (on-device llama.cpp)](https://github.com/Dilipkumarkv/Harness-/actions/workflows/build.yml/badge.svg)](https://github.com/Dilipkumarkv/Harness-/actions/workflows/build.yml)

---

## Features

- **Real tool execution on device** — 9 tools (`read_file`, `write_file`, `apply_patch`, `list_directory`,
  `search_code`, `shell_exec`, `git_status`, `git_diff`, `doctor_diagnostics`) that operate on a per-app
  workspace directory instead of a fake FS.
- **Three model providers**
  - **Local Keyword Router** — no model needed; matches keywords to run one real tool, for testing the
    pipeline and for devices with no network/model.
  - **Custom Endpoint (BYOK)** — any OpenAI-compatible `/chat/completions` endpoint (OpenAI, Together,
    Groq, Ollama, vLLM, LM Studio…). The model drives the tool loop directly.
  - **On-Device (GGUF)** — an imported `.gguf` model runs fully offline via a bundled CPU-only
    [llama.cpp](https://github.com/ggml-org/llama.cpp) build (`libharness_llama.so`).
- **Unified-diff patch engine** — computes real `@@` hunks, and applies external patches tolerating
  line drift and whitespace fuzz instead of failing or guessing.
- **Encrypted API key storage** — the BYOK key is stored in Android Keystore-backed AES/GCM, never in
  plaintext; excluded from backups.
- **Adaptive Files browser** — two-pane on tablets/foldables, single-pane with back navigation and a
  breadcrumb path navigator on phones; virtualized code rows and light syntax highlighting.
- **File-by-file diffs** — the Git Diffs tab groups `git diff` output per file with +/- counts and
  expandable hunk viewers instead of one giant text blob.
- **Approval gate** — mutating tools (`write_file`, `apply_patch`, `shell_exec`) always ask before
  running; read-only tools can be auto-approved in Settings.
- **Live conversation UI** — streaming agent cards, markdown-rendered assistant replies, reasoning-plan
  toggle, tap-to-expand tool outputs, approve/deny prompts with a human-readable summary of what will
  happen, copy-to-clipboard on messages, and a jump-to-latest chip when you scroll back in history.
- **Doctor self-test** — in-app diagnostics (filesystem probe, index consistency, diff round-trip,
  shell runner, git availability).
- **Room persistence** — chat history, tool calls, and a file index survive restarts.

## Requirements

| Component | Version |
|---|---|
| Android | minSdk 26, targetSdk 36, `arm64-v8a` |
| JDK | 21 (Temurin recommended) |
| Android SDK | platforms;android-36, build-tools;36.0.0 |
| Android NDK | 27.2.12479018 (matches `ndkVersion` in `app/build.gradle.kts`) |
| CMake | 3.22.1 (installed via Android SDK manager) |
| Gradle | 9.3.1 (via wrapper) |

## Build

```bash
# 1. Install the SDK/NDK/CMake pieces (example with sdkmanager)
sdkmanager --install "platforms;android-36" "build-tools;36.0.0" "cmake;3.22.1" "ndk;27.2.12479018"

# 2. Point Gradle at your SDK (local.properties is gitignored)
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 3. Debug builds are signed with a debug keystore (generated, gitignored)
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

# 4. Build and test
./gradlew :app:testDebugUnitTest   # unit tests (host, no device needed)
./gradlew :app:assembleDebug       # installable APK
```

Release builds need a keystore supplied via the `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`
environment variables (see `signingConfigs.release` in `app/build.gradle.kts`); without them the release
task fails by design. The release build is intentionally **not** wired to a checked-in key.

> Note: `app-debug.apk` in the repository root is a fresh debug build of the current `main` branch,
> committed so the raw GitHub link serves the latest APK. The debug signing key is the standard
> public debug key (`android`/`androiddebugkey`) — not a secret. CI also uploads the same APK as a
> run artifact.

### Consuming CI artifacts

GitHub Actions builds every push/PR to `main`: it installs the pinned NDK, compiles `harness_llama`
and llama.cpp, runs the unit-test suite, and uploads `app-debug.apk` as the `androidharness-debug`
artifact (Actions → select a run → Artifacts).

## Architecture

```
┌────────────────────────────────────────────────────────┐
│ UI (Jetpack Compose, Material 3, Oceanic Dark theme)   │
│   AgentWorkspace · Files · Git Diffs · Settings · …    │
├────────────────────────────────────────────────────────┤
│ HarnessViewModel (AndroidViewModel)                    │
│   chat loop orchestration · tool-call persistence ·    │
│   settings · refresh orchestration                      │
├────────────────────────────────────────────────────────┤
│ Agent layer (domain/agent)                             │
│   LocalToolCallingEngine ─ routes prompts to tools     │
│     · keyword router (no model)                        │
│     · model-driven step (BYOK HTTP / on-device GGUF)   │
│   ApprovalGate · ToolCallParser · AgentPrompt · LlmClient │
├────────────────────────────────────────────────────────┤
│ Tools (domain/tools)                                   │
│   HarnessToolRegistry ─ 9 tools, allowlist + sandbox   │
│   WorkspaceFs  ─ canonical-path sandbox, atomic writes │
│   ProcessRunner─ direct exec, output cap, timeout,     │
│                  cancellation                          │
│   PatchEngine  ─ LCS diff + fuzzy patch application    │
├────────────────────────────────────────────────────────┤
│ Data (Room)                                            │
│   projects/sessions/messages/tool_calls/workspace_files│
│   v2 schema: indexed lookups                           │
├────────────────────────────────────────────────────────┤
│ settings: SharedPreferences + Android Keystore AES/GCM │
│ llm: LlamaBridge (JNI) → libharness_llama.so (llama.cpp)│
└────────────────────────────────────────────────────────┘
```

### The agent loop

1. `HarnessViewModel.sendPrompt()` persists the user message plus a placeholder assistant message,
   then starts `runAgentLoop()`.
2. Each step calls `LocalToolCallingEngine.executeNextStep()`:
   - `BYOK_CUSTOM` / `ON_DEVICE_LLAMA` → the configured model reads the system prompt
     (`AgentPrompt`), the transcript, and one tool per turn emitted in the selected protocol
     (XML tags / ReAct text / JSON block — see `ToolCallParser`).
   - `LOCAL_TOOL_LLM` → a deterministic keyword router picks a tool (never pretends to be a model).
3. Every step runs the tool through `HarnessToolRegistry`, appends a `ToolCallItem`, and streams the
   accumulated explanation into the assistant message.
4. The loop ends when the model replies with prose, hits `maxAutonomousIterations` (Settings), or the
   user stops the agent (`stopAgent()`), which cancels the coroutine and releases the approval gate.

### Workspace sandbox

All file tools resolve paths through `WorkspaceFs.resolve()`, which refuses:

- absolute paths and drive-qualified paths (`C:`),
- any `..` segment (even ones that would land back inside),
- backslashes (workspace-relative paths are defined with `/`),
- symlink escapes (canonical-path comparison against the workspace root).

Writes go to a uniquely-named temp file in the same directory followed by an atomic rename, so a crash
can never leave a half-written source file. Symlinks are never followed during walks/search.

`shell_exec` runs one allowlisted command (`git`, `gradle`, `ls`, `find`, `grep`, `cat`, `head`,
`tail`, `wc`, `pwd`, `tree`, `du`, `stat`, `file`, `echo`, `uname`, `date`, `which`) directly with
`ProcessBuilder` — no shell, no pipes/redirection/metacharacters, arguments must stay workspace-relative,
options that escape the workspace or execute arbitrary code (`-exec`, `--output`, `git -c`,
`gradle --init-script`, …) are rejected. Output is capped and the child is killed on timeout or when
the agent turn is cancelled.

### Security notes

- The BYOK API key is encrypted with an Android Keystore AES/GCM key before it reaches disk and the
  plaintext slot is removed; if the Keystore is unavailable it falls back to app-private plaintext.
- Keystore credentials cannot be backed up; the model directory and settings are excluded from Auto
  Backup / device transfer (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
- Mutating tools always require explicit approval unless `autoApproveTools` is on, and even then the
  mutating tools (`write_file`, `apply_patch`, `shell_exec`) still ask — see
  `HarnessToolRegistry.requiresApproval()`.
- The workspace lives in app-private storage (`filesDir/workspace/project_main_harness`), not shared
  external storage.

## Tool reference

| Tool | Mutating | Description |
|---|---|---|
| `read_file` | no | Read a UTF-8 text file (≤ 512 KB) |
| `write_file` | yes (approval) | Atomic full-file write, parents created |
| `apply_patch` | yes (approval) | Apply a unified diff; replaces the whole file if the patch has no `@@` hunks, fails rather than guesses otherwise |
| `list_directory` | no | List entries (dirs first), depth ≤ 6, ≤ 300 entries |
| `search_code` | no | Grep-like content search with optional `*.kt`-style glob |
| `shell_exec` | yes (approval) | One allowlisted command, no shell, timeout ≤ 600 s |
| `git_status` | no | `git status --porcelain=v1 --branch` |
| `git_diff` | no | Working-tree diff; untracked files listed separately |
| `doctor_diagnostics` | no | Filesystem/index/diff/shell/git health probes |

## Project layout

```
app/src/main/java/com/example/
├── MainActivity.kt
├── data/            Room entities, DAOs, database
├── domain/
│   ├── agent/       LocalToolCallingEngine, LlmClient, TextGenerator, ToolCallParser,
│   │                AgentPrompt, ApprovalGate
│   ├── model/       Models.kt (settings, tool calls, diffs, enums)
│   ├── settings/    SettingsStore, SecretCipher
│   └── tools/       HarnessToolRegistry, WorkspaceFs, ProcessRunner, PatchEngine
├── llm/             LlamaBridge (JNI), ModelManager (.gguf import/lifecycle)
└── ui/              Compose screens, tool execution cards, theme, ViewModel

app/src/main/cpp/    harness_llama.cpp (JNI bridge), CMakeLists.txt (pinned llama.cpp)
app/src/test/        Host unit tests (JUnit4 + Robolectric)
.github/workflows/   build.yml — NDK install, test, APK artifact upload
```

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

The suite covers the patch/diff round-trip, fuzzy whitespace matching, hunk drift tolerance, workspace
path confinement (traversal, absolute paths, symlink escapes, Windows-style separators), atomic writes,
approval-gate semantics, settings encryption round-trip, and model-import hardening.

## On-device models

Models are copied into app-private storage (`filesDir/models`) so llama.cpp can mmap them; the `.gguf`
magic is verified before anything is renamed into place, existing models are never overwritten, and
deletes are refused outside the models directory. On-device inference is CPU-only (no Vulkan/OpenMP),
prompts are rendered as ChatML, and each generation runs in a fresh `llama_context` so no KV-cache
state leaks between turns. Recommended starting points: Qwen2.5-Coder-1.5B/3B-Instruct at Q4_K_M.

## Limitations & non-goals

- No GPU acceleration, no multimodal input, no per-model chat templates (ChatML only).
- The keyword router cannot author code; it can only run one tool per turn and will say so.
- Diff generation falls back to a coarse replacement block for very large mid-file changes
  (> 2 M LCS table cells) rather than failing or exhausting memory.
- `apply_patch` cannot represent a pure trailing-newline toggle; line changes are required.
- `shell_exec` cannot pipe, redirect or chain commands by design.
- Git needs a real binary (e.g. Termux `pkg install git`); set its path in Settings.
- `minSdk = 26`; the on-device path needs enough RAM for the chosen `.gguf` model.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `doctor_diagnostics` reports "Git toolchain: FAIL" | Install git (Termux works) and set the binary path in Settings → Git Binary Path |
| Doctor reports "Workspace filesystem: FAIL" | The app-private dir is unwritable; check device storage or reinstall |
| Model import rejected | File is not a GGUF (missing magic) or is truncated; re-download |
| "llama.cpp could not load …" | Model needs more RAM than available; try a smaller quantization |
| On-device responses stop early | Hit the `maxAutonomousIterations` or the 1500-token reply budget; raise context size in code if needed |
| BYOK requests fail with 401/404 | Check endpoint URL ends with `/v1` (or `/chat/completions` — both accepted) and the model name |

## License

Not specified — add a `LICENSE` file if you intend to distribute. Not legal advice.
