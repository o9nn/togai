package org.ninelym.cognitive.neural

import org.ninelym.cognitive.CognitiveTensor
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import org.ninelym.cognitive.hypergraph.TruthValue
import org.ninelym.cognitive.hypergraph.AttentionValue

/**
 * Neural-Symbolic Synthesis Demo
 * 
 * Demonstrates Phase 3 implementation: custom ggml kernels for seamless
 * neural-symbolic computation with tensor signature [atoms, confidence, features]
 * mapped to [modality, depth, context, salience, autonomy_index].
 */
object NeuralSymbolicSynthesisDemo {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("🧠 Neural-Symbolic Synthesis Demo")
        println("=".repeat(40))
        println("Phase 3: Custom ggml Kernels for Neural-Symbolic Computation")
        println()
        
        runDemo()
    }
    
    /**
     * Run complete neural-symbolic synthesis demonstration
     */
    fun runDemo() {
        try {
            // Initialize systems
            val kernel = GgmlNeuralSymbolicKernel()
            val benchmarkFramework = TensorBenchmarkFramework()
            val verificationSystem = NeuralSymbolicVerificationSystem()
            
            println("🚀 Initializing Neural-Symbolic Kernel...")
            val initialized = kernel.initialize(GgmlBackend.CPU)
            if (!initialized) {
                println("❌ Failed to initialize kernel - native kernel unavailable")
                runSimulationMode()
                return
            }
            
            println("✅ Kernel initialized successfully")
            println()
            
            // 3.1 Demonstrate Kernel Customization
            println("📋 3.1 Kernel Customization")
            println("-".repeat(30))
            demonstrateKernelCustomization(kernel)
            println()
            
            // 3.2 Demonstrate Tensor Benchmarking
            println("📋 3.2 Tensor Benchmarking")
            println("-".repeat(30))
            demonstrateTensorBenchmarking(benchmarkFramework)
            println()
            
            // 3.3 Demonstrate End-to-End Verification
            println("📋 3.3 End-to-End Verification")
            println("-".repeat(30))
            demonstrateEndToEndVerification(verificationSystem)
            println()
            
            // Cleanup
            kernel.shutdown()
            benchmarkFramework.shutdown()
            verificationSystem.shutdown()
            
            println("🎉 Neural-Symbolic Synthesis Demo Completed Successfully!")
            
        } catch (e: Exception) {
            println("❌ Demo failed with exception: ${e.message}")
            println("Native kernel unavailable.")
            runSimulationMode()
        }
    }
    
    /**
     * Demonstrate kernel customization features
     */
    private fun demonstrateKernelCustomization(kernel: GgmlNeuralSymbolicKernel) {
        println("🔧 Custom ggml Kernel Operations:")
        
        // Create sample atoms representing symbolic knowledge
        val atoms = listOf(
            Atom("concept_1", AtomType.CONCEPT, "dog", 
                 TruthValue(0.9f, 0.8f), AttentionValue(0.7f, 0.6f)),
            Atom("concept_2", AtomType.CONCEPT, "animal",
                 TruthValue(0.8f, 0.9f), AttentionValue(0.8f, 0.7f)),
            Atom("inheritance_1", AtomType.INHERITANCE, "dog->animal",
                 TruthValue(0.95f, 0.9f), AttentionValue(0.9f, 0.8f))
        )
        
        // Neural embeddings (simulated from neural network)
        val neuralEmbeddings = FloatArray(256) { kotlin.random.Random.nextFloat() }
        
        println("  • Input: ${atoms.size} symbolic atoms")
        println("  • Neural embeddings: ${neuralEmbeddings.size} dimensions")
        
        // Neural-symbolic fusion
        val fusedTensor = kernel.neuralSymbolicFusion(atoms, neuralEmbeddings, 0.85f)
        
        println("  • Fused tensor signature:")
        println("    - atoms (modality): ${String.format("%.3f", fusedTensor.modality)}")
        println("    - confidence (depth): ${String.format("%.3f", fusedTensor.depth)}")
        println("    - features (context): ${String.format("%.3f", fusedTensor.context)}")
        println("    - salience: ${String.format("%.3f", fusedTensor.salience)}")
        println("    - autonomy_index: ${String.format("%.3f", fusedTensor.autonomyIndex)}")
        
        // Demonstrate different tensor operations
        val weights = FloatArray(25) { kotlin.random.Random.nextFloat() }
        
        println("  • Tensor operations:")
        TensorOperation.values().forEach { operation ->
            val result = kernel.tensorInference(fusedTensor, weights, operation)
            println("    - ${operation.name}: attention_weight = ${String.format("%.3f", result.computeAttentionWeight())}")
        }
        
        // Demonstrate batch processing
        val tensors = (1..4).map { 
            kernel.neuralSymbolicFusion(atoms, neuralEmbeddings, 0.8f) 
        }
        val batchResults = kernel.batchProcess(tensors, BatchOperation.ATTENTION_POOLING)
        
        println("  • Batch processing: ${tensors.size} tensors -> ${batchResults.size} results")
    }
    
    /**
     * Demonstrate tensor benchmarking
     */
    private fun demonstrateTensorBenchmarking(benchmarkFramework: TensorBenchmarkFramework) {
        println("⚡ Tensor Benchmarking Framework:")
        
        if (!benchmarkFramework.initialize()) {
            println("❌ Benchmark framework initialization failed")
            return
        }
        
        // Run quick benchmark suite
        println("  • Running lightweight benchmark...")
        val suite = benchmarkFramework.runBenchmarkSuite()
        
        println("  • Benchmark Results:")
        println("    - Total tests: ${suite.results.size}")
        println("    - Total duration: ${suite.totalDuration} ms")
        println("    - Success rate: ${String.format("%.1f", suite.summary.avgThroughputOps)} ops/sec avg")
        println("    - Peak memory: ${String.format("%.2f", suite.summary.peakMemoryUsageMb)} MB")
        
        if (suite.summary.recommendations.isNotEmpty()) {
            println("  • Recommendations:")
            suite.summary.recommendations.take(2).forEach { recommendation ->
                println("    - $recommendation")
            }
        }
    }
    
    /**
     * Demonstrate end-to-end verification
     */
    private fun demonstrateEndToEndVerification(verificationSystem: NeuralSymbolicVerificationSystem) {
        println("✅ End-to-End Verification System:")
        
        if (!verificationSystem.initialize()) {
            println("❌ Verification system initialization failed")
            return
        }
        
        println("  • Running comprehensive verification...")
        val report = verificationSystem.runEndToEndVerification()
        
        println("  • Verification Results:")
        println("    - Overall success: ${if (report.overallSuccess) "✅ PASS" else "❌ FAIL"}")
        println("    - Total tests: ${report.summary.totalTests}")
        println("    - Passed: ${report.summary.passedTests}")
        println("    - Failed: ${report.summary.failedTests}")
        println("    - Success rate: ${String.format("%.1f", report.summary.successRate)}%")
        
        // Show critical results
        val criticalResults = report.results.filter { it.category == "critical" }
        if (criticalResults.isNotEmpty()) {
            println("  • Critical Tests:")
            criticalResults.forEach { result ->
                val status = if (result.passed) "✅" else "❌"
                println("    - $status ${result.testName}: ${result.message}")
            }
        }
        
        if (report.recommendations.isNotEmpty()) {
            println("  • Recommendations:")
            report.recommendations.take(3).forEach { recommendation ->
                println("    - $recommendation")
            }
        }
    }
    
    /**
     * Run simulation mode when native libraries are not available
     */
    private fun runSimulationMode() {
        // No numbers are reported without the native kernel actually running
        println("⚠️  The native neural-symbolic kernel is not available; no kernel, benchmark or")
        println("   verification results can be produced. Package the ggml native library to run this demo.")
    }
}

