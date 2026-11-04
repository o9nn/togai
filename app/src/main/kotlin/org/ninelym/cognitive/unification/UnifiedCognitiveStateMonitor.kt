package org.ninelym.cognitive.unification

import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.HypergraphStats
import org.ninelym.cognitive.ecan.ECANKernel
import org.ninelym.cognitive.metacognition.*
import org.ninelym.cognitive.CognitiveTensor

/**
 * Unified Cognitive State Monitor for Phase 6
 * 
 * Provides holistic system state observation and tracking across all cognitive phases,
 * enabling comprehensive understanding of system behavior and health.
 * 
 * Phase 6.3: Cognitive Unification component
 */
class UnifiedCognitiveStateMonitor(
    private val hypergraph: Hypergraph,
    private val ecanKernel: ECANKernel,
    private val metaSystem: MetaCognitivePathwaySystem,
    private val optimizer: EvolutionaryOptimizer,
    private val verificationSystem: RecursiveVerificationSystem,
    /** Live status of the Phase 4 cognitive mesh, when one is connected. */
    private val meshStatus: (() -> org.ninelym.cognitive.api.MeshStatusResponse)? = null
) {
    
    private val stateHistory = mutableListOf<UnifiedCognitiveState>()
    private val healthMetrics = SystemHealthMetrics()
    
    /**
     * Capture current unified cognitive state across all system phases
     */
    fun captureUnifiedState(): UnifiedCognitiveState {
        val timestamp = System.currentTimeMillis()
        
        // Phase 1: Cognitive Primitives State
        val primitives = capturePrimitivesState()
        
        // Phase 2: ECAN Attention State
        val attention = captureAttentionState()
        
        // Phase 4: Cognitive Mesh State (if available)
        val mesh = captureMeshState()
        
        // Phase 5: Meta-Cognitive State
        val metaCognition = captureMetaCognitiveState()
        
        // Phase 6: Testing and Validation State
        val testing = captureTestingState()
        
        // Calculate unified health metrics
        val overallHealth = calculateOverallHealth(primitives, attention, mesh, metaCognition, testing)
        val unificationIndex = calculateUnificationIndex(primitives, attention, mesh, metaCognition, testing)
        val consistencyScore = calculateConsistencyScore(primitives, attention, mesh, metaCognition, testing)
        
        val unifiedState = UnifiedCognitiveState(
            timestamp = timestamp,
            phase1Primitives = primitives,
            phase2Attention = attention,
            phase4Mesh = mesh,
            phase5MetaCognition = metaCognition,
            phase6Testing = testing,
            overallHealth = overallHealth,
            unificationIndex = unificationIndex,
            consistencyScore = consistencyScore,
            tensorSignatures = captureTensorSignatures()
        )
        
        // Add to history
        stateHistory.add(unifiedState)
        if (stateHistory.size > 100) {
            stateHistory.removeFirst()
        }
        
        // Update health metrics
        healthMetrics.updateMetrics(unifiedState)
        
        return unifiedState
    }
    
    private fun capturePrimitivesState(): CognitivePrimitivesState {
        val stats = hypergraph.getStats()
        val activeTensors = hypergraph.getActiveTensors(0.1f)
        
        return CognitivePrimitivesState(
            atomCount = stats.atomCount,
            linkCount = stats.linkCount,
            activeTensorCount = activeTensors.size,
            averageTruthValue = calculateAverageTruthValue(),
            averageAttentionValue = calculateAverageAttentionValue(),
            hypergraphComplexity = calculateHypergraphComplexity(stats),
            primitiveHealth = calculatePrimitiveHealth(stats, activeTensors)
        )
    }
    
    private fun captureAttentionState(): ECANAttentionState {
        val ecanStats = computeECANStats(hypergraph, ecanKernel)
        
        return ECANAttentionState(
            totalSTI = ecanStats.totalSTI,
            totalLTI = ecanStats.totalLTI,
            averageSTI = ecanStats.averageSTI,
            averageLTI = ecanStats.averageLTI,
            attentionFocus = ecanStats.attentionFocus,
            cyclesRun = ecanStats.cyclesRun,
            attentionEfficiency = calculateAttentionEfficiency(ecanStats),
            attentionHealth = calculateAttentionHealth(ecanStats)
        )
    }
    
    private fun captureMeshState(): CognitiveMeshState {
        // With no mesh connected there is nothing to report; it is excluded from overall health
        val status = meshStatus?.invoke()
            ?: return CognitiveMeshState(0, 0, 0, 0.0f, 0.0f, connected = false)
        return CognitiveMeshState(
            agentCount = status.agents.toInt(),
            sensorCount = status.sensors.toInt(),
            effectorCount = status.effectors.toInt(),
            meshConnectivity = status.state.coerceIn(0f, 1f),
            distributedHealth = status.state.coerceIn(0f, 1f),
            connected = true
        )
    }
    
    private fun captureMetaCognitiveState(): MetaCognitiveState {
        val insights = metaSystem.getMetaCognitiveInsights()
        val evolutionStats = optimizer.getEvolutionStats()
        val verificationStats = verificationSystem.getRecursiveVerificationStats()
        val introspection = metaSystem.getLatestIntrospection()

        return MetaCognitiveState(
            selfAwareness = insights.systemSelfAwareness,
            cognitiveRuleCount = insights.cognitiveRuleCount,
            processingEfficiency = introspection?.processingEfficiency ?: 0f,
            attentionCoherence = introspection?.attentionCoherence ?: 0f,
            evolutionFitness = evolutionStats.bestOverallFitness,
            convergenceRate = evolutionStats.convergenceRate,
            verificationDepth = verificationStats.averageRecursionDepth,
            systemHealth = verificationStats.averageSystemHealth,
            metaCognitiveHealth = calculateMetaCognitiveHealth(insights, evolutionStats, verificationStats)
        )
    }
    
    /**
     * Runtime validation measured now: coverage is the share of cognitive functions that have
     * actually run, and pass rate the share of active tensors passing validation.
     */
    private fun captureTestingState(): TestingValidationState {
        val tensors = hypergraph.getActiveTensors(0.0f)
        val passed = if (tensors.isEmpty()) 0f else tensors.count { it.isValid() }.toFloat() / tensors.size
        val coverage = calculateFunctionCompleteness()
        return TestingValidationState(
            testCoverage = coverage,
            testsPassed = passed,
            performanceBenchmarks = capturePerformanceBenchmarks(),
            validationHealth = (coverage + passed) / 2f
        )
    }
    
    private fun captureTensorSignatures(): Map<String, CognitiveTensor> {
        val primitives = capturePrimitivesState()
        val attention = captureAttentionState()
        return mapOf(
            // [complexity, truth, health, tensor share, link density] from the live hypergraph
            "phase1_primitives" to CognitiveTensor(
                primitives.hypergraphComplexity.coerceIn(0f, 1f),
                primitives.averageTruthValue.coerceIn(0f, 1f),
                primitives.primitiveHealth.coerceIn(0f, 1f),
                if (primitives.atomCount == 0) 0f else (primitives.activeTensorCount.toFloat() / primitives.atomCount).coerceIn(0f, 1f),
                if (primitives.atomCount == 0) 0f else (primitives.linkCount.toFloat() / primitives.atomCount).coerceIn(0f, 1f)
            ),
            // [efficiency, health, focus share, cycles, LTI share] from the live ECAN state
            "phase2_attention" to CognitiveTensor(
                attention.attentionEfficiency.coerceIn(0f, 1f),
                attention.attentionHealth.coerceIn(0f, 1f),
                if (primitives.atomCount == 0) 0f else (attention.attentionFocus / primitives.atomCount).coerceIn(0f, 1f),
                (attention.cyclesRun / 10f).coerceIn(0f, 1f),
                if (attention.totalSTI + attention.totalLTI == 0f) 0f else (attention.totalLTI / (attention.totalSTI + attention.totalLTI)).coerceIn(0f, 1f)
            ),
            "phase5_metacognition" to calculatePhase5TensorSignature(),
            "phase6_unification" to calculatePhase6TensorSignature()
        )
    }
    
    private fun calculateOverallHealth(
        primitives: CognitivePrimitivesState,
        attention: ECANAttentionState,
        mesh: CognitiveMeshState,
        metaCognition: MetaCognitiveState,
        testing: TestingValidationState
    ): Float {
        val weights = mapOf(
            "primitives" to 0.25f,
            "attention" to 0.20f,
            "mesh" to 0.15f,
            "metaCognition" to 0.25f,
            "testing" to 0.15f
        )
        
        // An unconnected mesh has no health to report; its weight is redistributed
        val meshWeight = if (mesh.connected) weights["mesh"]!! else 0f
        val total = weights.values.sum() - weights["mesh"]!! + meshWeight
        return (primitives.primitiveHealth * weights["primitives"]!! +
                attention.attentionHealth * weights["attention"]!! +
                mesh.distributedHealth * meshWeight +
                metaCognition.metaCognitiveHealth * weights["metaCognition"]!! +
                testing.validationHealth * weights["testing"]!!) / total
    }
    
    private fun calculateUnificationIndex(
        primitives: CognitivePrimitivesState,
        attention: ECANAttentionState,
        mesh: CognitiveMeshState,
        metaCognition: MetaCognitiveState,
        testing: TestingValidationState
    ): Float {
        // Measure how well different phases are integrated
        val integrationScores = listOf(
            calculatePrimitivesAttentionIntegration(primitives, attention),
            calculateAttentionMetaCognitionIntegration(attention, metaCognition),
            calculateMetaCognitionTestingIntegration(metaCognition, testing)
        )
        
        return integrationScores.average().toFloat()
    }
    
    private fun calculateConsistencyScore(
        primitives: CognitivePrimitivesState,
        attention: ECANAttentionState,
        mesh: CognitiveMeshState,
        metaCognition: MetaCognitiveState,
        testing: TestingValidationState
    ): Float {
        // Measure consistency across different cognitive phases
        val consistencyChecks = listOf(
            checkTensorConsistency(),
            checkHealthConsistency(primitives, attention, metaCognition),
            checkPerformanceConsistency(attention, metaCognition, testing)
        )
        
        return consistencyChecks.average().toFloat()
    }
    
    // Same [rules, mutability, fitness, introspect] mapping as CognitiveEngine.getPhase5TensorSignature()
    private fun calculatePhase5TensorSignature(): CognitiveTensor {
        val insights = metaSystem.getMetaCognitiveInsights()
        val evolutionStats = optimizer.getEvolutionStats()
        val rules = Math.min(insights.cognitiveRuleCount.toFloat() / 100.0f, 1.0f)
        val mutability = Math.min(evolutionStats.convergenceRate * 10.0f, 1.0f)
        return CognitiveTensor(rules, mutability, evolutionStats.bestOverallFitness.coerceIn(0f, 1f), insights.systemSelfAwareness, 0.8f)
    }

    private fun calculatePhase6TensorSignature(): CognitiveTensor {
        // Phase 6 tensor signature: [functions, validation, verification success, synthesis]
        val functions = calculateFunctionCompleteness()
        val testing = captureTestingState()
        val verified = verificationSystem.getRecursiveVerificationStats().verificationSuccessRate
        val synthesis = calculateCognitiveSynthesis().coerceIn(0f, 1f)
        
        return CognitiveTensor(functions, testing.testsPassed, verified.coerceIn(0f, 1f), synthesis, testing.validationHealth)
    }
    
    // Helper calculation methods
    private fun calculateAverageTruthValue(): Float {
        val atoms = hypergraph.getAllAtoms()
        return if (atoms.isNotEmpty()) {
            atoms.map { it.truthValue.strength }.average().toFloat()
        } else 0.5f
    }
    
    private fun calculateAverageAttentionValue(): Float {
        val atoms = hypergraph.getAllAtoms()
        return if (atoms.isNotEmpty()) {
            atoms.map { it.attentionValue.sti }.average().toFloat()
        } else 0.5f
    }
    
    private fun calculateHypergraphComplexity(stats: HypergraphStats): Float {
        // Calculate complexity based on atom/link ratios and interconnectedness
        return if (stats.atomCount > 0) {
            Math.min(stats.linkCount.toFloat() / stats.atomCount.toFloat(), 1.0f)
        } else 0.0f
    }
    
    private fun calculatePrimitiveHealth(stats: HypergraphStats, tensors: List<CognitiveTensor>): Float {
        val atomHealth = if (stats.atomCount > 0) 0.8f else 0.0f
        val tensorHealth = if (tensors.isNotEmpty()) {
            tensors.map { if (it.isValid()) 1.0f else 0.0f }.average().toFloat()
        } else 0.5f
        
        return (atomHealth + tensorHealth) / 2.0f
    }
    
    private fun calculateAttentionEfficiency(stats: ECANStats): Float {
        // Measure how efficiently attention is being allocated
        return Math.min(stats.attentionFocus / 100.0f, 1.0f)
    }
    
    private fun calculateAttentionHealth(stats: ECANStats): Float {
        // Measure overall health of attention system
        val stiHealth = if (stats.totalSTI > 0) Math.min(stats.averageSTI / stats.totalSTI, 1.0f) else 0.5f
        val ltiHealth = if (stats.totalLTI > 0) Math.min(stats.averageLTI / stats.totalLTI, 1.0f) else 0.5f
        
        return (stiHealth + ltiHealth) / 2.0f
    }
    
    private fun calculateMetaCognitiveHealth(
        insights: MetaCognitiveInsights,
        evolutionStats: EvolutionStats,
        verificationStats: RecursiveVerificationStats
    ): Float {
        val insightHealth = insights.systemSelfAwareness
        val evolutionHealth = evolutionStats.bestOverallFitness.coerceIn(0f, 1f)
        val verificationHealth = verificationStats.averageSystemHealth
        
        return (insightHealth + evolutionHealth + verificationHealth) / 3.0f
    }
    
    /** Measured now / taken from the live statistics (no assumed numbers). */
    private fun capturePerformanceBenchmarks(): Map<String, Float> {
        val atoms = hypergraph.getAllAtoms()
        val start = System.nanoTime()
        atoms.forEach { it.toCognitiveTensor() }
        val seconds = (System.nanoTime() - start) / 1e9f
        return mapOf(
            "evolution_convergence" to optimizer.getEvolutionStats().convergenceRate,
            "verification_depth" to verificationSystem.getRecursiveVerificationStats().averageRecursionDepth,
            "tensor_processing_rate" to if (seconds > 0f) atoms.size / seconds else 0f // tensors/second
        )
    }
    
    private fun calculateFunctionCompleteness(): Float {
        // Assess completeness of cognitive functions
        val requiredFunctions = listOf(
            "introspection", "evolution", "verification", 
            "attention", "tensor_processing", "hypergraph_management"
        )
        
        // Share of the functions that have actually been exercised in this system
        val ran = mapOf(
            "introspection" to (metaSystem.getMetaCognitiveInsights().totalIntrospections > 0),
            "evolution" to (optimizer.getEvolutionStats().totalGenerations > 0),
            "verification" to (verificationSystem.getRecursiveVerificationStats().totalVerifications > 0),
            "attention" to (computeECANStats(hypergraph, ecanKernel).cyclesRun > 0),
            "tensor_processing" to hypergraph.getActiveTensors(0.0f).isNotEmpty(),
            "hypergraph_management" to (hypergraph.getStats().atomCount > 0)
        )
        return requiredFunctions.count { ran[it] == true }.toFloat() / requiredFunctions.size
    }
    
    private fun calculateCognitiveSynthesis(): Float {
        // Measure level of cognitive system unification and integration
        val currentState = stateHistory.lastOrNull()
        return currentState?.unificationIndex ?: 0.8f
    }
    
    // Integration calculation methods
    private fun calculatePrimitivesAttentionIntegration(
        primitives: CognitivePrimitivesState,
        attention: ECANAttentionState
    ): Float {
        // Measure how well primitives and attention systems are integrated
        val tensorAttentionAlignment = if (primitives.activeTensorCount > 0) {
            Math.min(attention.attentionFocus / primitives.activeTensorCount.toFloat(), 1.0f)
        } else 0.5f
        
        return tensorAttentionAlignment
    }
    
    private fun calculateAttentionMetaCognitionIntegration(
        attention: ECANAttentionState,
        metaCognition: MetaCognitiveState
    ): Float {
        // Measure integration between attention and meta-cognitive systems
        val coherenceIntegration = attention.attentionHealth * metaCognition.attentionCoherence
        return Math.min(coherenceIntegration, 1.0f)
    }
    
    private fun calculateMetaCognitionTestingIntegration(
        metaCognition: MetaCognitiveState,
        testing: TestingValidationState
    ): Float {
        // Measure integration between meta-cognition and testing systems
        val validationIntegration = metaCognition.systemHealth * testing.validationHealth
        return Math.min(validationIntegration, 1.0f)
    }
    
    // Consistency check methods
    private fun checkTensorConsistency(): Float {
        val tensorSignatures = captureTensorSignatures()
        val validTensors = tensorSignatures.values.count { it.isValid() }
        return validTensors.toFloat() / tensorSignatures.size.toFloat()
    }
    
    private fun checkHealthConsistency(
        primitives: CognitivePrimitivesState,
        attention: ECANAttentionState,
        metaCognition: MetaCognitiveState
    ): Float {
        val healthValues = listOf(
            primitives.primitiveHealth,
            attention.attentionHealth,
            metaCognition.metaCognitiveHealth
        )
        
        val avgHealth = healthValues.average().toFloat()
        val variance = healthValues.map { (it - avgHealth) * (it - avgHealth) }.average().toFloat()
        
        // Lower variance indicates better consistency
        return Math.max(0.0f, 1.0f - variance)
    }
    
    private fun checkPerformanceConsistency(
        attention: ECANAttentionState,
        metaCognition: MetaCognitiveState,
        testing: TestingValidationState
    ): Float {
        val performanceIndicators = listOf(
            attention.attentionEfficiency,
            metaCognition.processingEfficiency,
            testing.validationHealth
        )
        
        val avgPerformance = performanceIndicators.average().toFloat()
        val consistency = performanceIndicators.map { Math.abs(it - avgPerformance) }.average().toFloat()
        
        // Lower deviation indicates better consistency
        return Math.max(0.0f, 1.0f - consistency)
    }
    
    /**
     * Get unified cognitive state history
     */
    fun getStateHistory(): List<UnifiedCognitiveState> = stateHistory.toList()
    
    /**
     * Get current system health metrics
     */
    fun getHealthMetrics(): SystemHealthMetrics = healthMetrics
    
    /**
     * Generate comprehensive system health report
     */
    fun generateHealthReport(): CognitiveHealthReport {
        val currentState = captureUnifiedState()
        
        return CognitiveHealthReport(
            timestamp = System.currentTimeMillis(),
            overallHealth = currentState.overallHealth,
            unificationIndex = currentState.unificationIndex,
            consistencyScore = currentState.consistencyScore,
            phaseHealthBreakdown = mapOf(
                "primitives" to currentState.phase1Primitives.primitiveHealth,
                "attention" to currentState.phase2Attention.attentionHealth,
                "mesh" to currentState.phase4Mesh.distributedHealth,
                "metaCognition" to currentState.phase5MetaCognition.metaCognitiveHealth,
                "testing" to currentState.phase6Testing.validationHealth
            ),
            recommendations = generateHealthRecommendations(currentState),
            tensorSignatureHealth = currentState.tensorSignatures.mapValues { 
                if (it.value.isValid()) 1.0f else 0.0f 
            }
        )
    }
    
    private fun generateHealthRecommendations(state: UnifiedCognitiveState): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (state.overallHealth < 0.7f) {
            recommendations.add("Overall system health is below optimal. Consider system-wide optimization.")
        }
        
        if (state.unificationIndex < 0.6f) {
            recommendations.add("System unification needs improvement. Focus on inter-phase integration.")
        }
        
        if (state.consistencyScore < 0.8f) {
            recommendations.add("System consistency issues detected. Verify tensor signature alignment.")
        }
        
        if (state.phase5MetaCognition.selfAwareness < 0.6f) {
            recommendations.add("Meta-cognitive self-awareness is low. Increase introspection frequency.")
        }
        
        if (state.phase5MetaCognition.evolutionFitness < 0.5f) {
            recommendations.add("Evolutionary fitness is suboptimal. Run additional optimization cycles.")
        }
        
        return recommendations
    }
}