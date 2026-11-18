#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "AeginLlamaJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
std::once_flag g_backend_init_flag;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeInitModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring modelPath,
        jboolean useMmap) {
    if (modelPath == nullptr) {
        LOGE("nativeInitModel called with null modelPath");
        return 0L;
    }

    std::call_once(g_backend_init_flag, []() {
        llama_backend_init();
        LOGI("llama_backend_init completed");
    });

    const char *cPath = env->GetStringUTFChars(modelPath, nullptr);
    if (cPath == nullptr) {
        LOGE("Failed to extract UTF chars for modelPath");
        return 0L;
    }

    llama_model_params params = llama_model_default_params();
    params.load_mode = (useMmap == JNI_TRUE) ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

    llama_model *model = llama_model_load_from_file(cPath, params);
    env->ReleaseStringUTFChars(modelPath, cPath);

    if (model == nullptr) {
        LOGE("llama_model_load_from_file returned null");
        return 0L;
    }

    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeInitContext(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong modelPtr,
        jint nCtx,
        jint nThreads) {
    if (modelPtr == 0L) {
        LOGE("nativeInitContext called with null modelPtr");
        return 0L;
    }

    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(nCtx > 0 ? nCtx : 2048);
    ctxParams.n_threads = nThreads > 0 ? nThreads : 4;
    ctxParams.n_threads_batch = ctxParams.n_threads;

    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        LOGE("llama_init_from_model returned null");
        return 0L;
    }

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeGenerateTokens(
        JNIEnv *env,
        jobject /* thiz */,
        jlong contextPtr,
        jlong modelPtr,
        jstring prompt,
        jint maxTokens,
        jstring grammar,
        jobject callback) {
    if (contextPtr == 0L || modelPtr == 0L || prompt == nullptr || callback == nullptr) {
        LOGE("nativeGenerateTokens called with invalid/null arguments");
        return;
    }

    auto *ctx = reinterpret_cast<llama_context *>(contextPtr);
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (vocab == nullptr) {
        LOGE("llama_model_get_vocab returned null");
        return;
    }

    const char *cPrompt = env->GetStringUTFChars(prompt, nullptr);
    if (cPrompt == nullptr) {
        return;
    }
    std::string promptStr(cPrompt);
    env->ReleaseStringUTFChars(prompt, cPrompt);

    std::string grammarStr;
    if (grammar != nullptr) {
        const char *cGrammar = env->GetStringUTFChars(grammar, nullptr);
        if (cGrammar != nullptr) {
            grammarStr.assign(cGrammar);
            env->ReleaseStringUTFChars(grammar, cGrammar);
        }
    }

    // Clear KV/memory cache for a clean single-turn evaluation
    llama_memory_clear(llama_get_memory(ctx), true);

    // Two-pass tokenization
    const int32_t promptLen = static_cast<int32_t>(promptStr.size());
    int32_t nTokens = -llama_tokenize(vocab, promptStr.c_str(), promptLen, nullptr, 0, true, true);
    if (nTokens <= 0) {
        LOGE("llama_tokenize failed with count=%d", nTokens);
        return;
    }

    std::vector<llama_token> tokens(static_cast<size_t>(nTokens));
    if (llama_tokenize(vocab, promptStr.c_str(), promptLen, tokens.data(), nTokens, true, true) < 0) {
        LOGE("llama_tokenize second pass failed");
        return;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed on prompt evaluation");
        return;
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    if (sampler == nullptr) {
        LOGE("llama_sampler_chain_init failed");
        return;
    }
    if (!grammarStr.empty()) {
        llama_sampler *grammarSampler = llama_sampler_init_grammar(vocab, grammarStr.c_str(), "root");
        if (grammarSampler != nullptr) {
            llama_sampler_chain_add(sampler, grammarSampler);
            LOGI("Attached GBNF grammar sampler constraint (%zu bytes)", grammarStr.size());
        } else {
            LOGE("Failed to parse GBNF grammar; continuing with greedy sampler");
        }
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenBytesMethod = env->GetMethodID(cbClass, "onTokenBytes", "([B)Z");
    env->DeleteLocalRef(cbClass);

    if (onTokenBytesMethod == nullptr) {
        LOGE("Failed to resolve TokenCallback.onTokenBytes([B)Z");
        llama_sampler_free(sampler);
        return;
    }

    std::vector<char> pieceBuf(512);
    const int limit = maxTokens > 0 ? maxTokens : 256;

    for (int i = 0; i < limit; ++i) {
        llama_token newToken = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) {
            break;
        }

        int32_t pieceLen = llama_token_to_piece(
            vocab, newToken, pieceBuf.data(), static_cast<int32_t>(pieceBuf.size()), 0, true
        );
        if (pieceLen < 0) {
            pieceBuf.resize(static_cast<size_t>(-pieceLen));
            pieceLen = llama_token_to_piece(
                vocab, newToken, pieceBuf.data(), static_cast<int32_t>(pieceBuf.size()), 0, true
            );
        }

        if (pieceLen > 0) {
            jbyteArray tokenBytes = env->NewByteArray(pieceLen);
            if (tokenBytes == nullptr) {
                break;
            }
            env->SetByteArrayRegion(
                tokenBytes, 0, pieceLen, reinterpret_cast<const jbyte *>(pieceBuf.data())
            );

            jboolean shouldContinue = env->CallBooleanMethod(callback, onTokenBytesMethod, tokenBytes);
            env->DeleteLocalRef(tokenBytes);

            if (env->ExceptionCheck() || shouldContinue == JNI_FALSE) {
                break;
            }
        }

        llama_batch nextBatch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(ctx, nextBatch) != 0) {
            LOGE("llama_decode failed during token generation");
            break;
        }
    }

    llama_sampler_free(sampler);
}

JNIEXPORT jbyteArray JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeGetChatTemplate(
        JNIEnv *env,
        jobject /* thiz */,
        jlong modelPtr) {
    if (modelPtr == 0L) {
        return nullptr;
    }
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr || tmpl[0] == '\0') {
        return nullptr;
    }
    std::string tmplStr(tmpl);
    jbyteArray out = env->NewByteArray(static_cast<jsize>(tmplStr.size()));
    if (out == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(tmplStr.size()),
                            reinterpret_cast<const jbyte *>(tmplStr.data()));
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeFormatChatMessages(
        JNIEnv *env,
        jobject /* thiz */,
        jlong modelPtr,
        jobjectArray roles,
        jobjectArray contents) {
    if (modelPtr == 0L || roles == nullptr || contents == nullptr) {
        return nullptr;
    }
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr || tmpl[0] == '\0') {
        return nullptr;
    }

    jsize nRoles = env->GetArrayLength(roles);
    jsize nContents = env->GetArrayLength(contents);
    if (nRoles <= 0 || nRoles != nContents) {
        return nullptr;
    }

    std::vector<std::string> roleStrings;
    std::vector<std::string> contentStrings;
    roleStrings.reserve(static_cast<size_t>(nRoles));
    contentStrings.reserve(static_cast<size_t>(nRoles));

    size_t totalChars = 0;
    for (jsize i = 0; i < nRoles; ++i) {
        auto jRole = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto jContent = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        if (jRole == nullptr || jContent == nullptr) {
            if (jRole != nullptr) env->DeleteLocalRef(jRole);
            if (jContent != nullptr) env->DeleteLocalRef(jContent);
            return nullptr;
        }
        const char *cRole = env->GetStringUTFChars(jRole, nullptr);
        const char *cContent = env->GetStringUTFChars(jContent, nullptr);
        std::string rStr = cRole != nullptr ? cRole : "user";
        std::string cStr = cContent != nullptr ? cContent : "";
        if (cRole != nullptr) env->ReleaseStringUTFChars(jRole, cRole);
        if (cContent != nullptr) env->ReleaseStringUTFChars(jContent, cContent);
        env->DeleteLocalRef(jRole);
        env->DeleteLocalRef(jContent);

        totalChars += rStr.size() + cStr.size();
        roleStrings.push_back(std::move(rStr));
        contentStrings.push_back(std::move(cStr));
    }

    std::vector<llama_chat_message> chatMsgs;
    chatMsgs.reserve(roleStrings.size());
    for (size_t i = 0; i < roleStrings.size(); ++i) {
        chatMsgs.push_back(llama_chat_message{roleStrings[i].c_str(), contentStrings[i].c_str()});
    }

    std::vector<char> buf(totalChars * 2 + 256);
    int32_t res = llama_chat_apply_template(
            tmpl, chatMsgs.data(), chatMsgs.size(), true,
            buf.data(), static_cast<int32_t>(buf.size()));
    if (res < 0) {
        return nullptr;
    }
    if (res > static_cast<int32_t>(buf.size())) {
        buf.resize(static_cast<size_t>(res) + 1);
        res = llama_chat_apply_template(
                tmpl, chatMsgs.data(), chatMsgs.size(), true,
                buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (res <= 0) {
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(static_cast<jsize>(res));
    if (out == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(res),
                            reinterpret_cast<const jbyte *>(buf.data()));
    return out;
}

JNIEXPORT void JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeFreeContext(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong contextPtr) {
    if (contextPtr != 0L) {
        llama_free(reinterpret_cast<llama_context *>(contextPtr));
    }
}

JNIEXPORT void JNICALL
Java_com_reveila_aegin_bridge_LlamaJniBridge_nativeFreeModel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong modelPtr) {
    if (modelPtr != 0L) {
        llama_model_free(reinterpret_cast<llama_model *>(modelPtr));
    }
}

} // extern "C"
