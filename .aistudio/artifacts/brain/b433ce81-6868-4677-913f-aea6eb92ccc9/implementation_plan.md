# AndroidHarness - Mobile AI Coding Agent

An advanced, native Android mobile IDE and autonomous AI coding agent harness inspired by **Sanuu7/AndroidHarness**. The app empowers developers to run autonomous coding workflows directly on their mobile device, featuring streaming agent conversations, interactive tool execution cards (file read/write, patch application, shell execution, git diffs), a project file manager, and a hybrid AI engine (Firebase AI Logic with Gemini 3.8 Flash built-in, custom BYOK providers, and a **fully tool-calling capable Local LLM engine**).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions incorporate your revision:

- **Confirmed App Scope**: Full AI coding agent application inspired by Sanuu7/AndroidHarness, featuring a dedicated Agent Workspace, real-time Tool Execution Cards, Virtual Project Manager, and System Diagnostics.
- **Confirmed Workflow Focus**: Centralized **Agent Chat Workspace** with live expandable **Tool Execution Cards** displaying step-by-step reasoning, tool inputs/arguments, stdout/stderr, code diffs, and outcome badges.
- **Tool-Calling Capable Local LLM Engine**: **Yes!** The Local LLM engine is explicitly designed with full autonomous tool-calling capabilities:
  - **Structured Protocol Parser**: Supports `<tool_call>{"name": "...", "arguments": {...}}</tool_call>`, ReAct syntax (`Thought: ... Action: ... Action Input: ...`), and Markdown JSON blocks.
  - **Autonomous Agent Loop**: Chains reasoning steps, dispatches tool execution to the Harness Tool Registry, parses tool results back into context, and continues until completion.
  - **Local Model & Endpoint Connectivity**: Configurable for on-device/local network endpoints (Ollama, llama.cpp, Termux local server, LocalAI) with customizable prompt templates (Llama 3, Qwen, Mistral).
  - **Intelligent Offline Fallback Engine**: Built-in deterministic multi-step reasoning engine capable of executing genuine multi-tool coding sequences (read, search, edit, patch, shell, git) offline with no network or server needed.
- **Hybrid Multi-Provider Architecture**:
  1. **Firebase AI Logic (`gemini-3.8-flash`)**: High-speed, zero-config cloud agent.
  2. **Local Tool-Calling LLM Engine**: Offline or local endpoint (Ollama/llama.cpp/Termux) with native tool-calling protocol.
  3. **Custom Cloud Providers (BYOK)**: Anthropic Claude, OpenAI, and custom OpenAI-compatible endpoints with function calling.

---

## 1. Overview & Core Concept

### What It Does
**AndroidHarness** brings the developer experience of desktop AI coding agents directly to mobile devices:
1. Converse naturally with an autonomous coding agent to plan features, debug code, refactor components, and inspect git history.
2. Watch the agent execute multi-step tool calls: reading workspace files, searching directories, writing code, applying unified diff patches, running virtual shell commands, and checking git status.
3. Every tool invocation renders as a rich, interactive **Tool Card** with collapsible outputs, syntax-highlighted code diffs, and execution timing.
4. **Local LLM Tool Calling**: Even completely offline or connected to local models (e.g. via Ollama or Termux), the agent understands and invokes tools autonomously.
5. Browse and edit project files directly via an integrated mobile **File & Project Explorer**.

### Target Audience & Persona
Mobile developers, software engineers, devops teams, and tech leads who want to code, debug, review git diffs, and execute toolchain tasks directly from an Android phone without needing a laptop.

### Key Value Proposition
Bringing the full autonomous "Thought ➔ Tool Call ➔ Execution ➔ Result ➔ Next Action" developer loop to Android with an intuitive touch-first UI, visual transparency into every tool action, and full local/offline tool execution capability.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme
A developer-centric **Cyber-Engineering Dark Mode** aesthetic tailored for reading code and terminal logs:
- **Atmosphere & Surfaces**: Deep Charcoal & Slate (`#0D1117`, `#161B22`, `#21262D`) providing true OLED contrast.
- **Brand Accents**: Electric Cyber Emerald (`#00E599` / `#00B87A`) for primary actions and active agent pulses; Neon Sapphire (`#388BFD`) for file operations; Vivid Amethyst (`#A371F7`) for Git & Diff operations; Amber Glow (`#D29922`) for warnings and diagnostics.
- **Typography**: Crisp display headers paired with clear body text and Jetpack Monospace (`FontFamily.Monospace`) for tool parameters, bash outputs, file trees, and diff blocks.
- **Depth & Hierarchy**: Card surfaces use Material 3 tonal elevation with subtle 1dp borders (`#30363D`) and colored status glow pills (`RUNNING`, `SUCCESS`, `ERROR`).

### Key User Flows & Navigation
Organized into three cohesive tabs with quick-action top bars:

```
┌──────────────────────────────────────────────────────────────────┐
│  [⚡ AndroidHarness]  Project: AndroidAppSample    [🩺 Doctor]  │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [💬 Agent Workspace]     [📁 Project Files]     [⚙️ Settings]   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

1. **Agent Workspace (Core Tab)**:
   - **Conversation Stream**: Displays user prompts, agent thoughts, assistant messages, and **Interactive Tool Cards**.
   - **Tool Execution Card Anatomy**:
     - *Header*: Tool type icon (Shell `$_`, File `📄`, Git `🔀`, Patch `🩹`), tool name (`write_file`, `shell_exec`, `apply_patch`), execution duration, and status badge (`Success`, `Running...`, `Failed`).
     - *Summary / Args*: Clear preview of arguments (e.g. `path: "app/src/main/MainActivity.kt"`).
     - *Collapsible Body*: Detailed inputs, stdout logs, and unified diff preview (green additions `+`, red deletions `-`).
   - **Interactive Input Bar**: Clean text prompt field with quick suggestion chips ("Scaffold Room DB", "Create M3 Settings Screen", "Run Project Doctor", "Refactor to MVVM").
   - **Agent Controls**: "Stop Agent", "Approve Step", or "Auto-Continue" buttons for autonomous multi-step tasks.

2. **Project Files Tab**:
   - Visual directory tree of the active workspace.
   - Tap to view files with line numbers and syntax styling.
   - Quick "Send to Agent" button to ask questions about specific files.

3. **Diagnostics & Doctor Panel**:
   - Environment status probe: Toolchain readiness, Agent state, Memory, and Active Provider status.
   - Self-test diagnostic runner checking tool capabilities (`read_file`, `write_file`, `patch`, `shell`, `tool_calling_loop`).

4. **Settings & Providers Tab**:
   - Provider selector:
     - **Firebase AI Logic (`gemini-3.8-flash`)** (Default)
     - **Local LLM with Tool Calling** (On-device / Local Ollama / llama.cpp / Offline Autonomous Engine)
     - **Custom Cloud API Keys** (Gemini, Claude, OpenAI compatible)
   - Local Tool Calling Protocol options: Llama-3/Hermes XML tags, ReAct format, or Markdown JSON format.
   - Local Endpoint URL (e.g. `http://localhost:11434/v1` for Termux or local Wi-Fi IP).

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Local LLM Tool-Calling Architecture
- **Chosen Approach**: Build a dedicated `LocalToolCallingEngine` that:
  1. Injects tool definitions (names, parameters, descriptions) into the system prompt.
  2. Parses model outputs using a robust multi-format extractor (supporting `<tool_call>`, ```` ```json ```` blocks, and ReAct `Action: [tool]` patterns).
  3. Executes the matched tool in the `HarnessToolRegistry`.
  4. Feeds the tool output (`<tool_response>` or `Observation: [result]`) back into the context for subsequent reasoning steps.
  5. Includes a deterministic offline reasoning engine so local workflows execute flawlessly even without an active server.
- **Why**: Allows true offline agent autonomy and compatibility with local LLMs (Ollama, Termux, GGUF runtimes).

### Decision 2: 8 Built-In Developer Tools in the Harness
- **Chosen Approach**: Real implementations for 8 core developer operations:
  - `read_file`: Reads workspace file content.
  - `write_file`: Writes or overwrites workspace files.
  - `apply_patch`: Applies unified diff patches with rollback safety.
  - `list_directory`: Lists directory contents recursively.
  - `search_code`: Greps pattern matches across workspace files.
  - `shell_exec`: Executes sandboxed developer commands (gradle build simulation, git log, lint checks, tests).
  - `git_status` & `git_diff`: Displays modified files and unified diffs.
  - `doctor_diagnostics`: Runs health tests on the virtual harness.
- **Why**: Complete toolset required for autonomous mobile coding workflows.

### Decision 3: Local Persistence via Room Database
- **Chosen Approach**: Room Database storing `ProjectEntity`, `SessionEntity`, `MessageEntity`, `ToolCallEntity`, and `WorkspaceFileEntity`.
- **Why**: Preserves developer workspaces, conversation history, and created source code across app restarts.

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│                                                                        │
│  ┌──────────────────────┐  ┌────────────────────┐  ┌────────────────┐  │
│  │   AgentWorkspaceUI   │  │   ProjectFilesUI   │  │  SettingsUI    │  │
│  │  - ToolExecutionCard │  │  - FileTreeViewer  │  │  - ModelSelect │  │
│  │  - DiffViewer        │  │  - CodeInspector   │  │  - Local LLM   │  │
│  └──────────┬───────────┘  └─────────┬──────────┘  └────────┬───────┘  │
└─────────────┼────────────────────────┼──────────────────────┼──────────┘
              │                        │                      │
┌─────────────▼────────────────────────▼──────────────────────▼──────────┐
│                   HarnessViewModel & AgentEngine                       │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  Agent Loop: Prompt ➔ Reasoning ➔ Tool Dispatch ➔ Result ➔ Loop │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │
            ┌─────────────────────────┴────────────────────────┐
            ▼                                                  ▼
┌───────────────────────────────┐              ┌────────────────────────┐
│     Harness Tool Registry     │              │    AI Provider Layer   │
│  - read_file    - write_file  │              ├────────────────────────┤
│  - apply_patch  - search_code │              │ 1. Firebase AI Logic   │
│  - shell_exec   - git_status  │              │    (gemini-3.8-flash)  │
│  - git_diff     - doctor      │              │ 2. Local Tool LLM      │
└───────────────┬───────────────┘              │    - ReAct / XML Call  │
                │                              │    - Ollama/llama.cpp  │
                │                              │    - Offline Engine    │
                │                              │ 3. BYOK Cloud (Claude/ │
                │                              │    OpenAI endpoints)   │
                │                              └────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────┐
│              Data Layer: Room Database & Virtual Filesystem           │
│  - ProjectsDao    - MessagesDao    - ToolCallsDao    - FilesDao       │
└───────────────────────────────────────────────────────────────────────┘
```

### Data Entities & Schema
1. **ProjectEntity**: `id`, `name`, `description`, `createdTimestamp`, `activeSessionId`.
2. **SessionEntity**: `id`, `projectId`, `title`, `providerType`, `createdTimestamp`.
3. **MessageEntity**: `id`, `sessionId`, `role` (user/assistant/system), `content`, `thought`, `timestamp`, `isStreaming`.
4. **ToolCallEntity**: `id`, `messageId`, `toolName`, `argumentsJson`, `resultJson`, `status` (RUNNING, SUCCESS, ERROR), `durationMs`, `diffContent`.
5. **WorkspaceFileEntity**: `id`, `projectId`, `filePath`, `content`, `lastModified`, `isVirtual`.

### Execution Phases for Implementation
- **Phase 1: App Check & Firebase Provisioning**:
  - Run `keytool` to extract debug keystore SHA-1/SHA-256.
  - Present `show_aistudio_ui` for Firebase setup (`["ai_logic"]`), verify terms with `GetFirebaseTos`, and invoke `ProvisionFirebase`.
  - Implement `configureAppCheck` in `MainActivity.kt`.
- **Phase 2: Core Data & Domain Models**:
  - Setup Room database (`HarnessDatabase`) with DAOs and Entities for projects, messages, tool calls, and virtual files.
  - Seed default Android workspace with sample files (`MainActivity.kt`, `build.gradle.kts`, `Theme.kt`, `README.md`).
- **Phase 3: Tool Execution Engine**:
  - Implement the 8 built-in developer tools (`read_file`, `write_file`, `apply_patch`, `search_code`, `shell_exec`, `git_status`, `git_diff`, `doctor_diagnostics`).
  - Implement diff engine computing added/removed lines for patches.
- **Phase 4: Hybrid AI & Local Tool-Calling LLM Engine**:
  - Implement `AiEngine` interface with:
    - `FirebaseAiLogicProvider` (using `gemini-3.8-flash`)
    - `LocalToolCallingEngine` (supports XML `<tool_call>`, JSON, ReAct, and local endpoint/offline reasoning)
    - `CustomEndpointProvider` (OpenAI/Claude compatible)
  - Autonomous multi-step tool execution loop with configurable iteration limit.
- **Phase 5: Jetpack Compose UI & Visual Polish**:
  - Cyber-Engineering Dark Theme with color palette and monospace styling.
  - `AgentWorkspaceScreen` with streaming chat bubbles, thoughts disclosure, and rich `ToolExecutionCard`.
  - `ProjectFilesScreen` with interactive file explorer and syntax viewer.
  - `DiagnosticsDoctorDialog` for health checks.
  - `SettingsScreen` for switching providers and configuring local tool-calling endpoint and templates.
- **Phase 6: Verification & Compilation**:
  - Verify complete build with `compile_applet`.
