// JNI bridge between Kotlin and llama.cpp.
//
// Deliberately small: load a GGUF model once, generate text per request, free it.
// A fresh llama_context is created for every request so no KV-cache state leaks
// between turns. The model itself stays resident.
//
// Built with CPU-only llama.cpp (see CMakeLists.txt). No GPU backend, no
// multimodal, no chat-template helpers — the prompt is formatted in Kotlin.

#include <jni.h>

#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "harness_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *model = nullptr;
    const llama_vocab *vocab = nullptr;
};

// Feeds `n` tokens at positions starting from `n_past`. Returns true on success.
bool decode_range(llama_context *ctx, const llama_token *tokens, int32_t n, int32_t n_past) {
    llama_batch batch = llama_batch_init(n, 0, 1);
    for (int32_t i = 0; i < n; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = n_past + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n - 1) ? 1 : 0;
    }
    batch.n_tokens = n;
    const int32_t rc = llama_decode(ctx, batch);
    llama_batch_free(batch);
    return rc == 0;
}

// Java strings are UTF-16; llama.cpp works in UTF-8. NewStringUTF/GetStringUTFChars use
// *modified* UTF-8, which corrupts astral-plane characters (emoji, CJK ext), so we convert
// through UTF-16 explicitly in both directions. Invalid input decodes to U+FFFD.
constexpr char16_t kReplacement = 0xFFFD;

std::u16string utf8_to_utf16(const std::string &s) {
    std::u16string out;
    out.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        const auto c = static_cast<unsigned char>(s[i]);
        uint32_t cp = 0;
        size_t extra = 0;
        if (c < 0x80) { cp = c; extra = 0; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; extra = 1; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; extra = 2; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; extra = 3; }
        else { out.push_back(kReplacement); ++i; continue; }

        bool ok = true;
        for (size_t k = 1; k <= extra; ++k) {
            if (i + k >= s.size()) { ok = false; break; }
            const auto cc = static_cast<unsigned char>(s[i + k]);
            if ((cc & 0xC0) != 0x80) { ok = false; break; }
            cp = (cp << 6) | (cc & 0x3F);
        }
        if (!ok) { out.push_back(kReplacement); ++i; continue; }
        i += extra + 1;

        // Reject overlongs, UTF-16 surrogates, and out-of-range code points.
        if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF) ||
            (extra == 1 && cp < 0x80) || (extra == 2 && cp < 0x800) || (extra == 3 && cp < 0x10000)) {
            out.push_back(kReplacement);
            continue;
        }
        if (cp <= 0xFFFF) {
            out.push_back(static_cast<char16_t>(cp));
        } else {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return out;
}

std::string jstring_to_utf8(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const jsize len = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out;
    out.reserve(static_cast<size_t>(len) * 3);
    for (jsize i = 0; i < len; ++i) {
        uint32_t cp;
        const uint32_t c = chars[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < len) {
            const uint32_t c2 = chars[i + 1];
            if (c2 >= 0xDC00 && c2 <= 0xDFFF) {
                cp = 0x10000 + (((c - 0xD800) << 10) | (c2 - 0xDC00));
                ++i;
            } else {
                cp = kReplacement;
            }
        } else if (c >= 0xDC00 && c <= 0xDFFF) {
            cp = kReplacement;
        } else {
            cp = c;
        }
        if (cp < 0x80) {
            out.push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

jstring to_jstring(JNIEnv *env, const std::string &utf8) {
    if (utf8.empty()) return env->NewString(reinterpret_cast<const jchar *>(u""), 0);
    const std::u16string u16 = utf8_to_utf16(utf8);
    return env->NewString(reinterpret_cast<const jchar *>(u16.data()), static_cast<jsize>(u16.size()));
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_llm_LlamaBridge_nativeLoadModel(
        JNIEnv *env, jobject /*thiz*/, jstring jPath, jint nThreads, jint nCtx) {
    const std::string path = jstring_to_utf8(env, jPath);
    if (path.empty()) {
        LOGE("nativeLoadModel: empty path");
        return 0;
    }

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;   // CPU only
    // v0.9.0 replaced use_mmap/use_mlock with a load_mode enum.
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;

    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("nativeLoadModel: failed to load %s", path.c_str());
        llama_backend_free();
        return 0;
    }

    auto *session = new Session();
    session->model = model;
    session->vocab = llama_model_get_vocab(model);

    LOGI("loaded %s (threads=%d, ctx=%d)", path.c_str(), nThreads, nCtx);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_example_llm_LlamaBridge_nativeGenerate(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jstring jPrompt, jint maxTokens,
        jint nThreads, jint nCtx) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->model == nullptr) {
        return to_jstring(env, "");
    }

    const std::string prompt = jstring_to_utf8(env, jPrompt);
    if (prompt.empty()) return to_jstring(env, "");

    // --- tokenize the whole prompt -------------------------------------------
    const int32_t needed = -llama_tokenize(
            session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            nullptr, 0, /*add_special=*/true, /*parse_special=*/true);
    if (needed <= 0) {
        LOGE("nativeGenerate: tokenize probe failed");
        return to_jstring(env, "");
    }

    std::vector<llama_token> tokens(static_cast<size_t>(needed));
    const int32_t n_prompt = llama_tokenize(
            session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), needed, /*add_special=*/true, /*parse_special=*/true);
    if (n_prompt <= 0) {
        LOGE("nativeGenerate: tokenize failed");
        return to_jstring(env, "");
    }

    if (n_prompt >= nCtx) {
        LOGE("nativeGenerate: prompt (%d tokens) exceeds context (%d)", n_prompt, nCtx);
        return to_jstring(env, "");
    }

    // --- fresh context per request ------------------------------------------
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx);
    cparams.n_batch = 512;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;

    llama_context *ctx = llama_init_from_model(session->model, cparams);
    if (ctx == nullptr) {
        LOGE("nativeGenerate: llama_init_from_model failed");
        return to_jstring(env, "");
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string out;
    int32_t n_past = 0;
    const llama_token eos = llama_vocab_eos(session->vocab);

    // Prefill in slices no larger than n_batch: one oversized batch fails to decode on long
    // prompts. Only the final slice needs logits; sampling starts after the whole prompt.
    bool ok = true;
    const int32_t kPrefillBatch = 512;
    for (int32_t start = 0; ok && start < n_prompt; start += kPrefillBatch) {
        const int32_t n = std::min(kPrefillBatch, n_prompt - start);
        ok = decode_range(ctx, tokens.data() + start, n, n_past);
        n_past += n;
    }

    const int32_t limit = maxTokens > 0 ? maxTokens : 512;
    for (int32_t produced = 0; ok && produced < limit; ++produced) {
        const llama_token id = llama_sampler_sample(smpl, ctx, -1);
        // is_eog covers end-of-generation tokens (e.g. ChatML im_end) beyond plain EOS.
        if (id < 0 || id == eos || llama_vocab_is_eog(session->vocab, id)) break;

        char stack_buf[128];
        int32_t len = llama_token_to_piece(
                session->vocab, id, stack_buf, sizeof(stack_buf), /*lstrip=*/0, /*special=*/false);
        if (len < 0) {
            std::string big(static_cast<size_t>(-len), '\0');
            len = llama_token_to_piece(
                    session->vocab, id, big.data(), static_cast<int32_t>(big.size()), 0, false);
            if (len > 0) out.append(big.data(), static_cast<size_t>(len));
        } else if (len > 0) {
            out.append(stack_buf, static_cast<size_t>(len));
        }

        if (n_past + 1 >= nCtx) break;
        ok = decode_range(ctx, &id, 1, n_past);
        n_past += 1;
    }

    llama_sampler_free(smpl);
    llama_free(ctx);

    if (!ok) LOGE("nativeGenerate: decode failed mid-generation");

    return to_jstring(env, out);
}

JNIEXPORT void JNICALL
Java_com_example_llm_LlamaBridge_nativeFreeModel(JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) return;
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
    llama_backend_free();
}

}  // extern "C"

