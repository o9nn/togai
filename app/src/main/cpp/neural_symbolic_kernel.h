/**
 * Neural-Symbolic Kernel for ggml-based Cognitive Computation
 * 
 * Implements custom ggml kernels for seamless neural-symbolic synthesis
 * using tensor signature: [atoms, confidence, features] mapped to
 * [modality, depth, context, salience, autonomy_index]
 * 
 * @file neural_symbolic_kernel.h
 * @brief Header for neural-symbolic kernel operations
 * @author OpenCog Kernel Team
 * @date 2024
 */

#ifndef NEURAL_SYMBOLIC_KERNEL_H
#define NEURAL_SYMBOLIC_KERNEL_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Version information
#define NSK_VERSION_MAJOR 1
#define NSK_VERSION_MINOR 0
#define NSK_VERSION_PATCH 0

// Tensor dimensions
#define NSK_TENSOR_DIMS 9
#define NSK_MIN_TENSOR_DIMS 5

// Backend types
typedef enum {
    NSK_BACKEND_CPU = 0,
    NSK_BACKEND_OPENCL = 1,
    NSK_BACKEND_VULKAN = 2,
    NSK_BACKEND_HEXAGON = 3
} nsk_backend_t;

// Tensor operation types
typedef enum {
    NSK_OP_FORWARD = 0,
    NSK_OP_BACKWARD = 1,
    NSK_OP_ATTENTION = 2,
    NSK_OP_SYMBOLIC_REASONING = 3
} nsk_operation_t;

// Batch operation types
typedef enum {
    NSK_BATCH_PARALLEL = 0,
    NSK_BATCH_SEQUENTIAL = 1,
    NSK_BATCH_ATTENTION_POOLING = 2
} nsk_batch_operation_t;

// Kernel context structure
typedef struct nsk_context {
    nsk_backend_t backend;
    void* ggml_ctx;
    void* backend_ctx;
    bool initialized;
    size_t max_tensor_size;
    size_t memory_pool_size;
} nsk_context_t;

// Tensor structure
typedef struct nsk_tensor {
    float* data;
    size_t size;
    size_t dims;
} nsk_tensor_t;

// Batch tensor structure
typedef struct nsk_batch {
    nsk_tensor_t** tensors;
    size_t count;
} nsk_batch_t;

/**
 * Initialize the neural-symbolic kernel
 * 
 * @param backend Backend type to use (CPU, OpenCL, Vulkan, Hexagon)
 * @return Context pointer on success, NULL on failure
 */
nsk_context_t* nsk_init(nsk_backend_t backend);

/**
 * Shutdown and cleanup kernel context
 * 
 * @param ctx Kernel context to cleanup
 */
void nsk_shutdown(nsk_context_t* ctx);

/**
 * Check if kernel is initialized
 * 
 * @param ctx Kernel context
 * @return true if initialized, false otherwise
 */
bool nsk_is_initialized(const nsk_context_t* ctx);

/**
 * Neural-Symbolic Forward Pass
 * 
 * Implements forward propagation through neural-symbolic fusion layer.
 * Combines symbolic atom representations with neural embeddings.
 * 
 * @param ctx Kernel context
 * @param input Input tensor [atoms, confidence, features]
 * @param weights Weight tensor for transformation
 * @param output Output tensor (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_forward_pass(
    nsk_context_t* ctx,
    const nsk_tensor_t* input,
    const nsk_tensor_t* weights,
    nsk_tensor_t* output
);

/**
 * Neural-Symbolic Backward Pass
 * 
 * Computes gradients for backpropagation through neural-symbolic layer.
 * 
 * @param ctx Kernel context
 * @param input Input tensor
 * @param weights Weight tensor
 * @param output Gradient tensor (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_backward_pass(
    nsk_context_t* ctx,
    const nsk_tensor_t* input,
    const nsk_tensor_t* weights,
    nsk_tensor_t* output
);

/**
 * Attention Mechanism
 * 
 * Applies attention-weighted processing to cognitive tensors.
 * Maps salience dimension to attention weights.
 * 
 * @param ctx Kernel context
 * @param input Input tensor
 * @param weights Attention weight tensor
 * @param output Output tensor (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_attention_mechanism(
    nsk_context_t* ctx,
    const nsk_tensor_t* input,
    const nsk_tensor_t* weights,
    nsk_tensor_t* output
);

/**
 * Symbolic Reasoning
 * 
 * Performs symbolic logic operations on tensors.
 * Preserves symbolic structure while enabling differentiable reasoning.
 * 
 * @param ctx Kernel context
 * @param input Input tensor
 * @param weights Logic rule weights
 * @param output Output tensor (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_symbolic_reasoning(
    nsk_context_t* ctx,
    const nsk_tensor_t* input,
    const nsk_tensor_t* weights,
    nsk_tensor_t* output
);

/**
 * Parallel Batch Inference
 * 
 * Processes multiple tensors in parallel using GGML compute graphs.
 * 
 * @param ctx Kernel context
 * @param batch Input batch of tensors
 * @param output Output batch (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_parallel_inference(
    nsk_context_t* ctx,
    const nsk_batch_t* batch,
    nsk_batch_t* output
);

/**
 * Sequential Batch Processing
 * 
 * Processes tensors sequentially with state propagation.
 * 
 * @param ctx Kernel context
 * @param batch Input batch of tensors
 * @param output Output batch (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_sequential_processing(
    nsk_context_t* ctx,
    const nsk_batch_t* batch,
    nsk_batch_t* output
);

/**
 * Attention Pooling
 * 
 * Pools batch tensors using attention-weighted aggregation.
 * 
 * @param ctx Kernel context
 * @param batch Input batch of tensors
 * @param output Output batch (pre-allocated)
 * @return 0 on success, negative error code on failure
 */
int nsk_attention_pooling(
    nsk_context_t* ctx,
    const nsk_batch_t* batch,
    nsk_batch_t* output
);

// Tensor utility functions

/**
 * Allocate tensor
 * 
 * @param size Number of elements
 * @param dims Number of dimensions
 * @return Allocated tensor, NULL on failure
 */
nsk_tensor_t* nsk_tensor_alloc(size_t size, size_t dims);

/**
 * Free tensor
 * 
 * @param tensor Tensor to free
 */
void nsk_tensor_free(nsk_tensor_t* tensor);

/**
 * Allocate batch
 * 
 * @param count Number of tensors in batch
 * @return Allocated batch, NULL on failure
 */
nsk_batch_t* nsk_batch_alloc(size_t count);

/**
 * Free batch
 * 
 * @param batch Batch to free
 */
void nsk_batch_free(nsk_batch_t* batch);

// Error codes
#define NSK_SUCCESS           0
#define NSK_ERROR_INIT       -1
#define NSK_ERROR_INVALID    -2
#define NSK_ERROR_MEMORY     -3
#define NSK_ERROR_BACKEND    -4
#define NSK_ERROR_COMPUTE    -5

/**
 * Get error message for error code
 * 
 * @param error_code Error code
 * @return Error message string
 */
const char* nsk_get_error_string(int error_code);

#ifdef __cplusplus
}
#endif

#endif // NEURAL_SYMBOLIC_KERNEL_H
