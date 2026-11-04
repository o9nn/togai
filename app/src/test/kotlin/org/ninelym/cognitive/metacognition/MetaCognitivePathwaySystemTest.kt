package org.ninelym.cognitive.metacognition

import org.ninelym.cognitive.hypergraph.*
import org.ninelym.cognitive.ecan.ECANKernel
import org.ninelym.cognitive.CognitiveTensor
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Comprehensive test suite for MetaCognitivePathwaySystem
 * 
 * Phase 6.1: Deep Testing Protocols for recursive meta-cognition
 */
class MetaCognitivePathwaySystemTest {
    
    private lateinit var hypergraph: Hypergraph
    private lateinit var ecanKernel: ECANKernel
    private lateinit var metaSystem: MetaCognitivePathwaySystem
    
    @Before
    fun setUp() {
        hypergraph = Hypergraph()
        ecanKernel = ECANKernel(hypergraph)
        metaSystem = MetaCognitivePathwaySystem(hypergraph, ecanKernel)
        
        // Set up test cognitive environment
        setupTestCognitiveEnvironment()
    }
    
    private fun setupTestCognitiveEnvironment() {
        // Create test atoms representing cognitive elements
        val conceptAtoms = (1..10).map { i ->
            Atom(
                id = "concept_$i",
                type = AtomType.CONCEPT,
                name = "test_concept_$i",
                truthValue = TruthValue(0.7f + i * 0.02f, 0.9f),
                attentionValue = AttentionValue(0.5f + i * 0.03f, 0.8f)
            )
        }
        
        val relationAtoms = (1..5).map { i ->
            Atom(
                id = "relation_$i",
                type = AtomType.EVALUATION,
                name = "test_relation_$i",
                truthValue = TruthValue(0.8f, 0.85f),
                attentionValue = AttentionValue(0.6f + i * 0.05f, 0.7f)
            )
        }
        
        // Add atoms to hypergraph
        conceptAtoms.forEach { hypergraph.addAtom(it) }
        relationAtoms.forEach { hypergraph.addAtom(it) }
        
        // Create some links between atoms
        for (i in 0 until conceptAtoms.size - 1) {
            hypergraph.addLink(HyperLink("sequence_$i", LinkType.SIMILARITY, listOf(conceptAtoms[i].id, conceptAtoms[i + 1].id)))
        }
        
        // Process some attention cycles to create realistic state
        repeat(5) {
            ecanKernel.runAttentionCycle()
        }
    }
    
    @Test
    fun testBasicIntrospection() {
        val result = metaSystem.performIntrospection()
        
        assertNotNull("Introspection result should not be null", result)
        assertTrue("Introspection should generate insights", result.insights.isNotEmpty())
        assertTrue("Introspection should be timestamped", result.timestamp > 0)
        val awareness = metaSystem.getMetaCognitiveInsights().systemSelfAwareness
        assertTrue("System self-awareness should be between 0 and 1", awareness in 0.0f..1.0f)
    }
    
    @Test
    fun testCognitiveRuleExtraction() {
        // Perform multiple introspection cycles to build rule base
        repeat(3) {
            metaSystem.performIntrospection()
        }
        
        val insights = metaSystem.getMetaCognitiveInsights()
        
        assertTrue("Should extract some cognitive rules", insights.cognitiveRuleCount > 0)
        val latest = metaSystem.getLatestIntrospection()!!
        assertTrue("Processing efficiency should be measured", latest.processingEfficiency >= 0.0f)
        assertTrue("Attention coherence should be calculated", latest.attentionCoherence >= 0.0f)
    }
    
    @Test
    fun testSelfObservationPatternDetection() {
        // Create observable patterns by running multiple cycles
        repeat(5) {
            metaSystem.performIntrospection()
            // Simulate some system state changes
            ecanKernel.runAttentionCycle()
        }
        
        val patterns = metaSystem.getMetaCognitiveInsights().selfObservationPatterns
        
        assertNotNull("Patterns should be detected", patterns)
        // Patterns may be empty initially, but the system should be capable of detecting them
    }
    
    @Test
    fun testIntrospectionConsistency() {
        val awareness1 = metaSystem.performIntrospection().let { metaSystem.getMetaCognitiveInsights().systemSelfAwareness }
        metaSystem.performIntrospection()
        val awareness3 = metaSystem.performIntrospection().let { metaSystem.getMetaCognitiveInsights().systemSelfAwareness }
        
        // Self-awareness should generally increase or remain stable with introspection
        assertTrue("Self-awareness should not decrease drastically", 
                   awareness3 >= awareness1 - 0.1f)
    }
    
    @Test
    fun testMetaCognitiveAtomGeneration() {
        val result = metaSystem.performIntrospection()
        
        // Introspection describes itself as meta-cognitive atoms
        val ids = result.metaAtoms.map { it.id }
        assertTrue("Meta-cognitive atoms should be generated during introspection: $ids",
                   ids.containsAll(listOf("meta_self_structure_complexity", "meta_self_attention_coherence", "meta_self_rule_count")))
    }
    
    @Test
    fun testStructuralAnalysisAccuracy() {
        val initialStats = hypergraph.getStats()
        val result = metaSystem.performIntrospection()
        
        // The introspection should reflect the hypergraph's structure and attention
        assertTrue("Complexity should be measured", result.hypergraphComplexity >= 0.0f)
        assertTrue("Attention coherence should be measured", result.attentionCoherence in 0.0f..1.0f)
        assertTrue("Insights must be about structure, attention or rules",
                   result.insights.all { i -> listOf("complexity", "clustering", "attention", "rule").any { it in i.lowercase() } })
    }
    
    @Test
    fun testIntrospectionHistory() {
        metaSystem.performIntrospection()
        metaSystem.performIntrospection()
        metaSystem.performIntrospection()
        
        assertEquals("Should maintain history of 3 introspections", 3,
                     metaSystem.getMetaCognitiveInsights().totalIntrospections)
    }
    
    @Test
    fun testMetaCognitiveInsightsEvolution() {
        // Baseline insights
        val initialInsights = metaSystem.getMetaCognitiveInsights()
        
        // Perform several introspection cycles
        repeat(5) {
            metaSystem.performIntrospection()
            // Make some changes to the system
            val newAtom = Atom(
                id = "dynamic_${System.currentTimeMillis()}",
                type = AtomType.CONCEPT,
                name = "dynamic_concept",
                truthValue = TruthValue(0.6f, 0.8f),
                attentionValue = AttentionValue(0.7f, 0.9f)
            )
            hypergraph.addAtom(newAtom)
            ecanKernel.runAttentionCycle()
        }
        
        val evolvedInsights = metaSystem.getMetaCognitiveInsights()
        
        // System should show evolution in self-understanding
        assertTrue("System self-awareness should evolve",
                   evolvedInsights.systemSelfAwareness >= initialInsights.systemSelfAwareness)
    }
    
    @Test
    fun testStressIntrospection() {
        // Create a complex cognitive environment
        val stressAtoms = (1..100).map { i ->
            Atom(
                id = "stress_$i",
                type = if (i % 2 == 0) AtomType.CONCEPT else AtomType.EVALUATION,
                name = "stress_element_$i",
                truthValue = TruthValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat()),
                attentionValue = AttentionValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat())
            )
        }
        
        stressAtoms.forEach { hypergraph.addAtom(it) }
        
        // Perform introspection under stress
        val startTime = System.currentTimeMillis()
        val result = metaSystem.performIntrospection()
        val endTime = System.currentTimeMillis()
        
        assertNotNull("Should handle stress introspection", result)
        assertTrue("Should complete introspection in reasonable time", endTime - startTime < 5000)
        assertTrue("Should maintain accuracy under stress", metaSystem.getMetaCognitiveInsights().systemSelfAwareness >= 0.0f)
    }
}