package org.ninelym.cognitive.metacognition

import org.ninelym.cognitive.hypergraph.*
import org.ninelym.cognitive.ecan.ECANKernel
import org.ninelym.cognitive.CognitiveTensor
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Comprehensive test suite for RecursiveVerificationSystem
 * 
 * Phase 6.1: Deep Testing Protocols for recursive verification
 */
class RecursiveVerificationSystemTest {
    
    private lateinit var hypergraph: Hypergraph
    private lateinit var ecanKernel: ECANKernel
    private lateinit var metaSystem: MetaCognitivePathwaySystem
    private lateinit var optimizer: EvolutionaryOptimizer
    private lateinit var verificationSystem: RecursiveVerificationSystem
    
    @Before
    fun setUp() {
        hypergraph = Hypergraph()
        ecanKernel = ECANKernel(hypergraph)
        metaSystem = MetaCognitivePathwaySystem(hypergraph, ecanKernel)
        optimizer = EvolutionaryOptimizer(hypergraph, ecanKernel, metaSystem)
        verificationSystem = RecursiveVerificationSystem(
            hypergraph, ecanKernel, org.ninelym.cognitive.verification.CognitiveVerificationSystem(), metaSystem, optimizer
        )
        
        // Set up test verification environment
        setupTestVerificationEnvironment()
    }
    
    private fun setupTestVerificationEnvironment() {
        // Create a cognitive system with verifiable properties
        val verifiableAtoms = (1..25).map { i ->
            Atom(
                id = "verify_atom_$i",
                type = AtomType.values()[i % AtomType.values().size],
                name = "verifiable_element_$i",
                truthValue = TruthValue(0.6f + (i % 5) * 0.08f, 0.9f),
                attentionValue = AttentionValue(0.5f + (i % 4) * 0.1f, 0.8f)
            )
        }
        
        verifiableAtoms.forEach { hypergraph.addAtom(it) }
        
        // Create predictable link patterns for verification
        for (i in 0 until verifiableAtoms.size - 1) {
            if (i % 3 == 0) {
                hypergraph.addLink(HyperLink("verification_link_$i", LinkType.SIMILARITY, listOf(verifiableAtoms[i].id, verifiableAtoms[i + 1].id)))
            }
        }
        
        // Establish baseline cognitive state
        repeat(3) {
            ecanKernel.runAttentionCycle()
            metaSystem.performIntrospection()
        }
    }
    
    /** All levels of a recursive verification, top level first. */
    private fun levels(result: RecursiveVerificationResult) = generateSequence(result) { it.nextLevel }.toList()

    @Test
    fun testBasicRecursiveVerification() {
        val result = verificationSystem.performRecursiveVerification()
        
        assertTrue("Should verify multiple layers", result.subVerifications.isNotEmpty())
        assertTrue("Execution time should be non-negative", result.executionTime >= 0)
        assertTrue("Overall system health should be measurable", 
                   result.systemHealth >= 0.0f && result.systemHealth <= 1.0f)
    }
    
    @Test
    fun testVerificationLayers() {
        val result = verificationSystem.performRecursiveVerification()
        
        assertEquals(
            listOf("Basic System Verification", "Meta-Cognitive Verification", "Evolutionary Verification", "Meta-Meta Verification"),
            result.subVerifications.map { it.layerName }
        )
        for (layer in result.subVerifications) {
            assertTrue("Layer should have valid health", layer.health >= 0.0f && layer.health <= 1.0f)
            assertTrue("Layer should have valid confidence", layer.confidence >= 0.0f && layer.confidence <= 1.0f)
        }
    }
    
    @Test
    fun testVerificationStatus() {
        val result = verificationSystem.performRecursiveVerification()
        
        assertTrue("Verification status should be valid", result.verificationStatus in VerificationStatus.values())
        // A completed run reports the status of its deepest level
        assertEquals(levels(result).last().verificationStatus, result.verificationStatus)
    }
    
    @Test
    fun testFeedbackActions() {
        val result = verificationSystem.performRecursiveVerification()
        
        for (action in levels(result).flatMap { it.feedbackActions }) {
            assertTrue("Action should be named", action.action.isNotEmpty())
            assertTrue("Action should have description", action.description.isNotEmpty())
            assertTrue("Impact should be bounded", action.impact in -1.0f..1.0f)
        }
    }
    
    @Test
    fun testRecursiveDepth() {
        val result = verificationSystem.performRecursiveVerification()
        val all = levels(result)
        
        // Depths increase by one per recursion
        all.forEachIndexed { i, level -> assertEquals(i, level.depth) }
        if (result.verificationStatus == VerificationStatus.NEEDS_RECURSION) {
            assertTrue("Needing recursion should have produced a deeper level", all.size >= 2)
        }
    }
    
    @Test
    fun testMetaVerification() {
        val result = verificationSystem.performRecursiveVerification()
        
        // The verification of the verification process itself is always present
        assertTrue(result.subVerifications.any { it.layerName == "Meta-Meta Verification" })
    }
    
    @Test
    fun testSelfImprovementLoop() {
        val initialHealth = verificationSystem.performRecursiveVerification().systemHealth
        val improvedResult = verificationSystem.performRecursiveVerification()
        
        assertTrue("Self-improvement should not degrade system",
                   improvedResult.systemHealth >= initialHealth - 0.1f)
    }
    
    @Test
    fun testVerificationConsistency() {
        val results = (1..3).map { verificationSystem.performRecursiveVerification() }
        
        val healthValues = results.map { it.systemHealth }
        val healthRange = healthValues.maxOrNull()!! - healthValues.minOrNull()!!
        assertTrue("Verification results should be consistent", healthRange < 0.3f)
        
        val layerCounts = results.map { it.subVerifications.size }
        assertTrue("Layer counts should be consistent", layerCounts.distinct().size == 1)
    }
    
    @Test
    fun testVerificationUnderStress() {
        val stressAtoms = (1..150).map { i ->
            Atom(
                id = "stress_verify_$i",
                type = AtomType.values()[i % AtomType.values().size],
                name = "stress_element_$i",
                truthValue = TruthValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat()),
                attentionValue = AttentionValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat())
            )
        }
        stressAtoms.forEach { hypergraph.addAtom(it) }
        
        val startTime = System.currentTimeMillis()
        val result = verificationSystem.performRecursiveVerification()
        val endTime = System.currentTimeMillis()
        
        assertTrue("Should complete verification in reasonable time", endTime - startTime < 15000)
        assertTrue("Should maintain verification quality under stress", result.systemHealth >= 0.0f)
    }
    
    @Test
    fun testVerificationStatistics() {
        verificationSystem.performRecursiveVerification()
        val stats = verificationSystem.getRecursiveVerificationStats()
        
        assertTrue("Should record verifications", stats.totalVerifications >= 1)
        assertTrue("Should track verification depth", stats.averageRecursionDepth >= 0.0f)
        assertTrue("Should track verification health", stats.averageSystemHealth >= 0.0f)
        assertTrue("Should track feedback actions", stats.totalFeedbackActions >= 0)
    }
    
    @Test
    fun testVerificationIntegration() {
        metaSystem.performIntrospection()
        optimizer.evolveSystem()
        
        val result = verificationSystem.performRecursiveVerification()
        
        assertTrue("Should verify introspection results", 
                   result.subVerifications.any { it.layerName == "Meta-Cognitive Verification" })
        assertTrue("Should verify evolution results",
                   result.subVerifications.any { it.layerName == "Evolutionary Verification" })
    }
}
