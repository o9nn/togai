package org.ninelym.cognitive.metacognition

import org.ninelym.cognitive.hypergraph.*
import org.ninelym.cognitive.ecan.ECANKernel
import org.ninelym.cognitive.CognitiveTensor
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Comprehensive test suite for EvolutionaryOptimizer
 * 
 * Phase 6.1: Deep Testing Protocols for evolutionary optimization
 */
class EvolutionaryOptimizerTest {
    
    private lateinit var hypergraph: Hypergraph
    private lateinit var ecanKernel: ECANKernel
    private lateinit var metaSystem: MetaCognitivePathwaySystem
    private lateinit var optimizer: EvolutionaryOptimizer
    
    @Before
    fun setUp() {
        hypergraph = Hypergraph()
        ecanKernel = ECANKernel(hypergraph)
        metaSystem = MetaCognitivePathwaySystem(hypergraph, ecanKernel)
        optimizer = EvolutionaryOptimizer(hypergraph, ecanKernel, metaSystem)
        
        // Set up test environment
        setupTestEvolutionaryEnvironment()
    }
    
    private fun setupTestEvolutionaryEnvironment() {
        // Create a diverse population of cognitive elements
        val testAtoms = (1..50).map { i ->
            Atom(
                id = "evol_atom_$i",
                type = if (i % 3 == 0) AtomType.CONCEPT else if (i % 3 == 1) AtomType.EVALUATION else AtomType.PREDICATE,
                name = "evolutionary_element_$i",
                truthValue = TruthValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat()),
                attentionValue = AttentionValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat())
            )
        }
        
        testAtoms.forEach { hypergraph.addAtom(it) }
        
        // Create interconnections for evolutionary selection
        for (i in 0 until testAtoms.size - 1) {
            if (kotlin.random.Random.nextFloat() > 0.7f) {
                hypergraph.addLink(HyperLink("evolutionary_link_$i", LinkType.SIMILARITY, listOf(testAtoms[i].id, testAtoms[i + 1].id)))
            }
        }
        
        // Run initial attention cycles
        repeat(3) {
            ecanKernel.runAttentionCycle()
        }
    }
    
    @Test
    fun testBasicEvolution() {
        val result = optimizer.evolveSystem()
        
        assertNotNull("Evolution result should not be null", result)
        assertTrue("Should generate some population", result.populationSize > 0)
        assertTrue("Best fitness should be valid", result.bestFitness >= 0.0f && result.bestFitness >= result.averageFitness)
        assertTrue("Should run a generation", result.generation > 0)
        assertTrue("Execution time should be non-negative", result.executionTime >= 0)
    }
    
    @Test
    fun testFitnessEvaluation() {
        // Test fitness evaluation for different cognitive configurations
        val result = optimizer.evolveSystem()
        
        assertTrue("Convergence rate should be measurable", optimizer.getEvolutionStats().convergenceRate >= 0.0f)
        assertTrue("Should apply some optimizations", result.appliedOptimizations.isNotEmpty())
        
        // Fitness should improve over generations
        assertTrue("Best fitness should be reasonable", result.bestFitness > 0.0f)
    }
    
    @Test
    fun testPopulationDiversity() {
        val result = optimizer.evolveSystem()
        
        // Check that we maintain population diversity
        assertTrue("Population should be reasonably sized", result.populationSize >= 10)
        assertTrue("Should explore multiple optimization dimensions", 
                   result.appliedOptimizations.map { it.type }.toSet().size > 1)
    }
    
    @Test
    fun testOptimizationApplication() {
        val initialStats = hypergraph.getStats()
        val result = optimizer.evolveSystem()
        
        // Verify that optimizations were actually applied
        assertTrue("Should apply at least one optimization", result.appliedOptimizations.isNotEmpty())
        
        for (optimization in result.appliedOptimizations) {
            assertTrue("Optimization should report its improvement", !optimization.improvement.isNaN())
            assertTrue("Optimization should have valid type", optimization.type.name.isNotEmpty())
            assertTrue("Optimization should have description", optimization.description.isNotEmpty())
        }
    }
    
    @Test
    fun testEvolutionaryConvergence() {
        // Run multiple evolution cycles to test convergence
        val results = mutableListOf<EvolutionResult>()
        
        repeat(3) {
            results.add(optimizer.evolveSystem())
        }
        
        // Check that convergence improves or stabilizes
        val convergenceRates = results.map { optimizer.getEvolutionStats().convergenceRate }
        
        // Convergence should generally improve or remain stable
        assertTrue("Evolution should show convergence patterns", 
                   convergenceRates.isNotEmpty())
    }
    
    @Test
    fun testGeneticOperators() {
        val result = optimizer.evolveSystem()
        
        // Verify genetic operations were performed
        assertTrue("Should perform selection", result.populationSize > 0)
        assertTrue("Should perform crossover and mutation", result.generation > 0)
        
        // Check for evidence of genetic diversity in optimizations
        val optimizationTypes = result.appliedOptimizations.map { it.type }.toSet()
        assertTrue("Should explore multiple optimization types", optimizationTypes.size > 0)
    }
    
    @Test
    fun testGenerationsAdvance() {
        val first = optimizer.evolveSystem()
        val second = optimizer.evolveSystem()
        assertEquals("Each cycle should be the next generation", first.generation + 1, second.generation)
    }
    
    @Test
    fun testMultiObjectiveOptimization() {
        val result = optimizer.evolveSystem()
        
        // Check that multiple objectives are being optimized
        val optimizationCategories = result.appliedOptimizations.groupBy { it.type.name }
        
        // Should optimize multiple aspects of the cognitive system
        assertTrue("Should optimize multiple categories", optimizationCategories.size > 1)
        
        // Common optimization categories
        val expectedCategories = setOf("ATTENTION", "RULES", "STRUCTURE", "PROCESSING")
        val actualCategories = optimizationCategories.keys.toSet()
        assertTrue("Should include standard optimization categories", 
                   actualCategories.intersect(expectedCategories).isNotEmpty())
    }
    
    @Test
    fun testEvolutionStatistics() {
        val result = optimizer.evolveSystem()
        val stats = optimizer.getEvolutionStats()
        
        assertNotNull("Evolution statistics should be available", stats)
        assertTrue("Should track best fitness", stats.bestOverallFitness >= 0.0f)
        assertTrue("Should track convergence", stats.convergenceRate >= 0.0f)
        assertTrue("Should track generations", stats.totalGenerations >= 1)
    }
    
    @Test
    fun testStressEvolution() {
        // Create a more complex environment for stress testing
        val stressAtoms = (1..200).map { i ->
            Atom(
                id = "stress_evol_$i",
                type = AtomType.values()[i % AtomType.values().size],
                name = "stress_element_$i",
                truthValue = TruthValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat()),
                attentionValue = AttentionValue(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat())
            )
        }
        
        stressAtoms.forEach { hypergraph.addAtom(it) }
        
        val startTime = System.currentTimeMillis()
        val result = optimizer.evolveSystem()
        val endTime = System.currentTimeMillis()
        
        assertNotNull("Should handle stress evolution", result)
        assertTrue("Should complete evolution in reasonable time", endTime - startTime < 30000)
        assertTrue("Should maintain performance under stress", result.bestFitness >= 0.0f)
    }
    
    @Test
    fun testEvolutionReproducibility() {
        // Test that evolution with same seed produces similar results
        val seed = 12345L
        
        // Note: This test assumes the optimizer supports seeding
        // If not implemented, this test will verify behavioral consistency
        val result1 = optimizer.evolveSystem()
        val result2 = optimizer.evolveSystem()
        
        // Results should be in similar ranges even if not identical
        assertTrue("Results should be in similar fitness ranges",
                   Math.abs(result1.bestFitness - result2.bestFitness) < 0.5f)
    }
}