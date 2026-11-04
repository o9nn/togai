package org.ninelym.cognitive

import org.ninelym.cognitive.hypergraph.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Comprehensive tensor validation framework for Phase 6
 * 
 * Phase 6.1: Deep Testing Protocols for cognitive tensor validation
 */
class TensorValidationFrameworkTest {
    
    private lateinit var cognitiveEngine: CognitiveEngine
    
    @Before
    fun setUp() {
        cognitiveEngine = CognitiveEngine()
        setupTensorValidationEnvironment()
    }
    
    private fun setupTensorValidationEnvironment() {
        // (id, type, name, truth strength, truth confidence, STI, LTI)
        val testAtoms = listOf(
            listOf("concept_high_truth", AtomType.CONCEPT, "high_truth_concept", 0.9f, 0.95f, 0.8f, 0.9f),
            listOf("concept_medium_truth", AtomType.CONCEPT, "medium_truth_concept", 0.5f, 0.7f, 0.6f, 0.7f),
            listOf("concept_low_truth", AtomType.CONCEPT, "low_truth_concept", 0.1f, 0.3f, 0.2f, 0.4f),
            listOf("relation_strong", AtomType.EVALUATION, "strong_relation", 0.8f, 0.9f, 0.7f, 0.8f),
            listOf("relation_weak", AtomType.EVALUATION, "weak_relation", 0.3f, 0.5f, 0.3f, 0.5f),
            listOf("predicate_active", AtomType.PREDICATE, "active_predicate", 0.7f, 0.85f, 0.9f, 0.95f),
            listOf("predicate_passive", AtomType.PREDICATE, "passive_predicate", 0.4f, 0.6f, 0.2f, 0.4f)
        )
        testAtoms.forEach { a ->
            val result = cognitiveEngine.addAtom(
                a[0] as String, a[1] as AtomType, a[2] as String,
                a[3] as Float, a[4] as Float, a[5] as Float, a[6] as Float
            )
            assertTrue("$result", result is ProcessingResult.Success)
        }
        
        assertTrue(cognitiveEngine.addLink("concept_high_truth", "relation_strong", "strong_link") is ProcessingResult.Success)
        assertTrue(cognitiveEngine.addLink("concept_medium_truth", "relation_weak", "weak_link") is ProcessingResult.Success)
        assertTrue(cognitiveEngine.addLink("relation_strong", "predicate_active", "active_link") is ProcessingResult.Success)
        
        repeat(3) { cognitiveEngine.runAttentionCycle() }
    }
    
    @Test
    fun testBasicTensorValidation() {
        val tensors = cognitiveEngine.getCognitiveState().tensors
        
        assertTrue("Should have active tensors", tensors.isNotEmpty())
        for (tensor in tensors) {
            assertTrue("Tensor should be valid: $tensor", tensor.isValid())
        }
    }
    
    @Test
    fun testTensorSignatureConsistency() {
        assertTrue(CognitiveTensor.create5D(0.5f, 0.6f, 0.7f, 0.8f, 0.9f).isValid())
        val phase5Tensor = cognitiveEngine.getPhase5TensorSignature()
        assertTrue("Phase 5 tensor should be valid", phase5Tensor.isValid())
        
        val values = phase5Tensor.to5DArray()
        assertEquals("Phase 5 signature should have 5 dimensions", 5, values.size)
        values.forEachIndexed { i, v -> assertTrue("Dimension $i in range", v in 0.0f..1.0f) }
        assertTrue(phase5Tensor.computeAttentionWeight() in 0.0f..1.0f)
    }
    
    @Test
    fun testArrayRoundTrip() {
        val original = CognitiveTensor.create5D(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
        val restored = CognitiveTensor.fromArray(original.toArray())
        assertEquals(original, restored)
        assertArrayEquals(floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f), original.to5DArray(), 0.0001f)
    }
    
    @Test
    fun testInvalidTensorsAreDetected() {
        assertFalse(CognitiveTensor.create5D(1.5f, 0.5f, 0.5f, 0.5f, 0.5f).isValid())
        assertFalse(CognitiveTensor.create5D(0.5f, 0.5f, -0.1f, 0.5f, 0.5f).isValid())
        assertTrue("Depth may exceed 1", CognitiveTensor.create5D(0.5f, 2.0f, 0.5f, 0.5f, 0.5f).isValid())
    }
    
    @Test
    fun testAttentionWeight() {
        val tensor = CognitiveTensor.create5D(0.5f, 0.6f, 0.7f, 0.8f, 0.5f)
        assertEquals(0.4f, tensor.computeAttentionWeight(), 0.0001f)
    }
    
    @Test
    fun testClampingRestoresValidity() {
        cognitiveEngine.runAttentionCycle()
        cognitiveEngine.clampAllTensors()
        assertTrue(cognitiveEngine.getCognitiveState().tensors.all { it.isValid() })
    }
    
    @Test
    fun testTensorEvolutionTracking() {
        repeat(5) { cognitiveEngine.runAttentionCycle() }
        val evolved = cognitiveEngine.getCognitiveState().tensors
        assertTrue("Should maintain some active tensors", evolved.isNotEmpty())
        assertTrue(evolved.map { it.salience }.average() >= 0.0)
    }
}
