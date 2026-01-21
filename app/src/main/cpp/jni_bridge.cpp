/**
 * JNI Bridge for Neural-Symbolic Kernel
 * 
 * Connects native C++ neural-symbolic kernel implementation to Kotlin/Java.
 * Implements all native methods declared in GgmlNeuralSymbolicKernel.kt
 * 
 * @file jni_bridge.cpp
 * @brief JNI bridge implementation
 */

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include "neural_symbolic_kernel.h"

#define LOG_TAG "NSK-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global kernel context
static nsk_context_t* g_nsk_context = NULL;

/**
 * Convert Java backend string to native backend enum
 */
static nsk_backend_t jstring_to_backend(JNIEnv* env, jstring backend_str) {
    const char* backend_cstr = env->GetStringUTFChars(backend_str, NULL);
    nsk_backend_t backend = NSK_BACKEND_CPU;
    
    if (strcmp(backend_cstr, "ggml-cpu") == 0) {
        backend = NSK_BACKEND_CPU;
    } else if (strcmp(backend_cstr, "ggml-opencl") == 0) {
        backend = NSK_BACKEND_OPENCL;
    } else if (strcmp(backend_cstr, "ggml-vulkan") == 0) {
        backend = NSK_BACKEND_VULKAN;
    } else if (strcmp(backend_cstr, "ggml-hexagon") == 0) {
        backend = NSK_BACKEND_HEXAGON;
    }
    
    env->ReleaseStringUTFChars(backend_str, backend_cstr);
    return backend;
}

/**
 * Convert Java float array to native tensor
 */
static nsk_tensor_t* jfloatarray_to_tensor(JNIEnv* env, jfloatArray array) {
    if (!array) return NULL;
    
    jsize len = env->GetArrayLength(array);
    nsk_tensor_t* tensor = nsk_tensor_alloc(len, NSK_MIN_TENSOR_DIMS);
    
    if (tensor) {
        jfloat* elements = env->GetFloatArrayElements(array, NULL);
        if (elements) {
            memcpy(tensor->data, elements, len * sizeof(float));
            env->ReleaseFloatArrayElements(array, elements, JNI_ABORT);
        }
    }
    
    return tensor;
}

/**
 * Convert native tensor to Java float array
 */
static jfloatArray tensor_to_jfloatarray(JNIEnv* env, const nsk_tensor_t* tensor) {
    if (!tensor) return NULL;
    
    jfloatArray result = env->NewFloatArray(tensor->size);
    if (result) {
        env->SetFloatArrayRegion(result, 0, tensor->size, tensor->data);
    }
    
    return result;
}

/**
 * Convert Java 2D array to native batch
 */
static nsk_batch_t* jobjectarray_to_batch(JNIEnv* env, jobjectArray batch_array) {
    if (!batch_array) return NULL;
    
    jsize count = env->GetArrayLength(batch_array);
    nsk_batch_t* batch = nsk_batch_alloc(count);
    
    if (batch) {
        for (jsize i = 0; i < count; i++) {
            jfloatArray item = (jfloatArray)env->GetObjectArrayElement(batch_array, i);
            batch->tensors[i] = jfloatarray_to_tensor(env, item);
            env->DeleteLocalRef(item);
        }
    }
    
    return batch;
}

/**
 * Convert native batch to Java 2D array
 */
static jobjectArray batch_to_jobjectarray(JNIEnv* env, const nsk_batch_t* batch) {
    if (!batch) return NULL;
    
    jclass floatArrayClass = env->FindClass("[F");
    jobjectArray result = env->NewObjectArray(batch->count, floatArrayClass, NULL);
    
    if (result) {
        for (size_t i = 0; i < batch->count; i++) {
            jfloatArray item = tensor_to_jfloatarray(env, batch->tensors[i]);
            env->SetObjectArrayElement(result, i, item);
            env->DeleteLocalRef(item);
        }
    }
    
    return result;
}

// JNI Method Implementations

extern "C" {

/**
 * Initialize native kernel
 * 
 * Java signature: private external fun initializeNative(backend: String): Boolean
 */
JNIEXPORT jboolean JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_initializeNative(
    JNIEnv* env,
    jobject thiz,
    jstring backend
) {
    LOGI("Initializing neural-symbolic kernel");
    
    if (g_nsk_context) {
        LOGI("Kernel already initialized, reinitializing...");
        nsk_shutdown(g_nsk_context);
        g_nsk_context = NULL;
    }
    
    nsk_backend_t backend_type = jstring_to_backend(env, backend);
    g_nsk_context = nsk_init(backend_type);
    
    if (!g_nsk_context) {
        LOGE("Failed to initialize kernel");
        return JNI_FALSE;
    }
    
    LOGI("Kernel initialized successfully with backend %d", backend_type);
    return JNI_TRUE;
}

/**
 * Forward pass
 * 
 * Java signature: private external fun forwardPass(input: FloatArray, weights: FloatArray): FloatArray
 */
JNIEXPORT jfloatArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_forwardPass(
    JNIEnv* env,
    jobject thiz,
    jfloatArray input,
    jfloatArray weights
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_tensor_t* input_tensor = jfloatarray_to_tensor(env, input);
    nsk_tensor_t* weight_tensor = jfloatarray_to_tensor(env, weights);
    nsk_tensor_t* output_tensor = nsk_tensor_alloc(
        input_tensor ? input_tensor->size : NSK_MIN_TENSOR_DIMS,
        NSK_MIN_TENSOR_DIMS
    );
    
    jfloatArray result = NULL;
    
    if (input_tensor && weight_tensor && output_tensor) {
        int status = nsk_forward_pass(g_nsk_context, input_tensor, weight_tensor, output_tensor);
        
        if (status == NSK_SUCCESS) {
            result = tensor_to_jfloatarray(env, output_tensor);
        } else {
            LOGE("Forward pass failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_tensor_free(input_tensor);
    nsk_tensor_free(weight_tensor);
    nsk_tensor_free(output_tensor);
    
    return result;
}

/**
 * Backward pass
 * 
 * Java signature: private external fun backwardPass(input: FloatArray, weights: FloatArray): FloatArray
 */
JNIEXPORT jfloatArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_backwardPass(
    JNIEnv* env,
    jobject thiz,
    jfloatArray input,
    jfloatArray weights
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_tensor_t* input_tensor = jfloatarray_to_tensor(env, input);
    nsk_tensor_t* weight_tensor = jfloatarray_to_tensor(env, weights);
    nsk_tensor_t* output_tensor = nsk_tensor_alloc(
        input_tensor ? input_tensor->size : NSK_MIN_TENSOR_DIMS,
        NSK_MIN_TENSOR_DIMS
    );
    
    jfloatArray result = NULL;
    
    if (input_tensor && weight_tensor && output_tensor) {
        int status = nsk_backward_pass(g_nsk_context, input_tensor, weight_tensor, output_tensor);
        
        if (status == NSK_SUCCESS) {
            result = tensor_to_jfloatarray(env, output_tensor);
        } else {
            LOGE("Backward pass failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_tensor_free(input_tensor);
    nsk_tensor_free(weight_tensor);
    nsk_tensor_free(output_tensor);
    
    return result;
}

/**
 * Attention mechanism
 * 
 * Java signature: private external fun attentionMechanism(input: FloatArray, weights: FloatArray): FloatArray
 */
JNIEXPORT jfloatArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_attentionMechanism(
    JNIEnv* env,
    jobject thiz,
    jfloatArray input,
    jfloatArray weights
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_tensor_t* input_tensor = jfloatarray_to_tensor(env, input);
    nsk_tensor_t* weight_tensor = jfloatarray_to_tensor(env, weights);
    nsk_tensor_t* output_tensor = nsk_tensor_alloc(
        input_tensor ? input_tensor->size : NSK_MIN_TENSOR_DIMS,
        NSK_MIN_TENSOR_DIMS
    );
    
    jfloatArray result = NULL;
    
    if (input_tensor && weight_tensor && output_tensor) {
        int status = nsk_attention_mechanism(g_nsk_context, input_tensor, weight_tensor, output_tensor);
        
        if (status == NSK_SUCCESS) {
            result = tensor_to_jfloatarray(env, output_tensor);
        } else {
            LOGE("Attention mechanism failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_tensor_free(input_tensor);
    nsk_tensor_free(weight_tensor);
    nsk_tensor_free(output_tensor);
    
    return result;
}

/**
 * Symbolic reasoning
 * 
 * Java signature: private external fun symbolicReasoning(input: FloatArray, weights: FloatArray): FloatArray
 */
JNIEXPORT jfloatArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_symbolicReasoning(
    JNIEnv* env,
    jobject thiz,
    jfloatArray input,
    jfloatArray weights
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_tensor_t* input_tensor = jfloatarray_to_tensor(env, input);
    nsk_tensor_t* weight_tensor = jfloatarray_to_tensor(env, weights);
    nsk_tensor_t* output_tensor = nsk_tensor_alloc(
        input_tensor ? input_tensor->size : NSK_MIN_TENSOR_DIMS,
        NSK_MIN_TENSOR_DIMS
    );
    
    jfloatArray result = NULL;
    
    if (input_tensor && weight_tensor && output_tensor) {
        int status = nsk_symbolic_reasoning(g_nsk_context, input_tensor, weight_tensor, output_tensor);
        
        if (status == NSK_SUCCESS) {
            result = tensor_to_jfloatarray(env, output_tensor);
        } else {
            LOGE("Symbolic reasoning failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_tensor_free(input_tensor);
    nsk_tensor_free(weight_tensor);
    nsk_tensor_free(output_tensor);
    
    return result;
}

/**
 * Parallel inference
 * 
 * Java signature: private external fun parallelInference(batch: Array<FloatArray>): Array<FloatArray>
 */
JNIEXPORT jobjectArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_parallelInference(
    JNIEnv* env,
    jobject thiz,
    jobjectArray batch
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_batch_t* input_batch = jobjectarray_to_batch(env, batch);
    nsk_batch_t* output_batch = nsk_batch_alloc(input_batch ? input_batch->count : 0);
    
    jobjectArray result = NULL;
    
    if (input_batch && output_batch) {
        // Allocate output tensors
        for (size_t i = 0; i < output_batch->count; i++) {
            output_batch->tensors[i] = nsk_tensor_alloc(
                input_batch->tensors[i]->size,
                input_batch->tensors[i]->dims
            );
        }
        
        int status = nsk_parallel_inference(g_nsk_context, input_batch, output_batch);
        
        if (status == NSK_SUCCESS) {
            result = batch_to_jobjectarray(env, output_batch);
        } else {
            LOGE("Parallel inference failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_batch_free(input_batch);
    nsk_batch_free(output_batch);
    
    return result;
}

/**
 * Sequential processing
 * 
 * Java signature: private external fun sequentialProcessing(batch: Array<FloatArray>): Array<FloatArray>
 */
JNIEXPORT jobjectArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_sequentialProcessing(
    JNIEnv* env,
    jobject thiz,
    jobjectArray batch
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_batch_t* input_batch = jobjectarray_to_batch(env, batch);
    nsk_batch_t* output_batch = nsk_batch_alloc(input_batch ? input_batch->count : 0);
    
    jobjectArray result = NULL;
    
    if (input_batch && output_batch) {
        // Allocate output tensors
        for (size_t i = 0; i < output_batch->count; i++) {
            output_batch->tensors[i] = nsk_tensor_alloc(
                input_batch->tensors[i]->size,
                input_batch->tensors[i]->dims
            );
        }
        
        int status = nsk_sequential_processing(g_nsk_context, input_batch, output_batch);
        
        if (status == NSK_SUCCESS) {
            result = batch_to_jobjectarray(env, output_batch);
        } else {
            LOGE("Sequential processing failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_batch_free(input_batch);
    nsk_batch_free(output_batch);
    
    return result;
}

/**
 * Attention pooling
 * 
 * Java signature: private external fun attentionPooling(batch: Array<FloatArray>): Array<FloatArray>
 */
JNIEXPORT jobjectArray JNICALL
Java_org_ninelym_cognitive_neural_GgmlNeuralSymbolicKernel_attentionPooling(
    JNIEnv* env,
    jobject thiz,
    jobjectArray batch
) {
    if (!g_nsk_context) {
        LOGE("Kernel not initialized");
        return NULL;
    }
    
    nsk_batch_t* input_batch = jobjectarray_to_batch(env, batch);
    nsk_batch_t* output_batch = nsk_batch_alloc(input_batch ? input_batch->count : 0);
    
    jobjectArray result = NULL;
    
    if (input_batch && output_batch) {
        // Allocate output tensors
        for (size_t i = 0; i < output_batch->count; i++) {
            output_batch->tensors[i] = nsk_tensor_alloc(
                input_batch->tensors[i]->size,
                input_batch->tensors[i]->dims
            );
        }
        
        int status = nsk_attention_pooling(g_nsk_context, input_batch, output_batch);
        
        if (status == NSK_SUCCESS) {
            result = batch_to_jobjectarray(env, output_batch);
        } else {
            LOGE("Attention pooling failed: %s", nsk_get_error_string(status));
        }
    }
    
    nsk_batch_free(input_batch);
    nsk_batch_free(output_batch);
    
    return result;
}

/**
 * JNI_OnLoad - called when library is loaded
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("Neural-Symbolic Kernel JNI library loaded");
    return JNI_VERSION_1_6;
}

/**
 * JNI_OnUnload - called when library is unloaded
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    if (g_nsk_context) {
        nsk_shutdown(g_nsk_context);
        g_nsk_context = NULL;
    }
    LOGI("Neural-Symbolic Kernel JNI library unloaded");
}

} // extern "C"
