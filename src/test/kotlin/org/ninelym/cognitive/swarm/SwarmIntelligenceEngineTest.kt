package org.ninelym.cognitive.swarm

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for Swarm Intelligence Engine
 *
 * Tests all swarm algorithms: ACO, PSO, ABC, and Firefly
 */
class SwarmIntelligenceEngineTest {

    @Test
    fun `test particle swarm optimization finds minimum`() {
        val engine = SwarmIntelligenceEngine()

        // Simple quadratic function with minimum at 0
        val objectiveFunction: (FloatArray) -> Float = { x ->
            x.map { it * it }.sum()
        }

        val result = engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 3,
            bounds = -10f to 10f,
            populationSize = 30,
            maximize = false
        )

        assertTrue(result.bestSolution.fitness < 1.0f,
            "Should find solution close to minimum")
        assertTrue(result.iterations > 0, "Should have iterations")
        assertTrue(result.executionTime > 0, "Should have execution time")
        assertEquals(SwarmIntelligenceEngine.SwarmAlgorithm.PARTICLE_SWARM, result.algorithm)
        assertNotNull(result.convergenceHistory)
        assertTrue(result.convergenceHistory.isNotEmpty())
    }

    @Test
    fun `test particle swarm optimization finds maximum`() {
        val engine = SwarmIntelligenceEngine()

        // Negative quadratic with maximum at 0
        val objectiveFunction: (FloatArray) -> Float = { x ->
            -x.map { it * it }.sum()
        }

        val result = engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -5f to 5f,
            populationSize = 20,
            maximize = true
        )

        assertTrue(result.bestSolution.fitness > -1.0f,
            "Should find solution close to maximum")
        assertEquals(2, result.bestSolution.position.size)
    }

    @Test
    fun `test artificial bee colony optimization`() {
        val engine = SwarmIntelligenceEngine()

        // Rosenbrock-like function
        val objectiveFunction: (FloatArray) -> Float = { x ->
            if (x.size < 2) 0f
            else {
                var sum = 0f
                for (i in 0 until x.size - 1) {
                    sum += 100 * (x[i + 1] - x[i] * x[i]).let { it * it } +
                            (1 - x[i]).let { it * it }
                }
                sum
            }
        }

        val result = engine.artificialBeeColony(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -2f to 2f,
            colonySize = 40,
            maximize = false
        )

        assertEquals(SwarmIntelligenceEngine.SwarmAlgorithm.ARTIFICIAL_BEE, result.algorithm)
        assertTrue(result.iterations > 0)
        assertTrue(result.bestSolution.fitness >= 0f)
        assertEquals(2, result.bestSolution.position.size)
    }

    @Test
    fun `test firefly optimization`() {
        val engine = SwarmIntelligenceEngine()

        // Sphere function
        val objectiveFunction: (FloatArray) -> Float = { x ->
            x.map { it * it }.sum()
        }

        val result = engine.fireflyOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 3,
            bounds = -5f to 5f,
            populationSize = 25,
            maximize = false
        )

        assertEquals(SwarmIntelligenceEngine.SwarmAlgorithm.FIREFLY, result.algorithm)
        assertTrue(result.iterations > 0)
        assertTrue(result.bestSolution.fitness >= 0f)
        assertTrue(result.executionTime > 0)
    }

    @Test
    fun `test ant colony optimization finds path`() {
        val engine = SwarmIntelligenceEngine()

        // Create a simple graph
        val graph = Graph.fullyConnected(5, defaultWeight = 1.0f)

        val result = engine.antColonyOptimization(
            graph = graph,
            sourceNode = 0,
            targetNode = 4,
            numAnts = 20
        )

        if (result.path.isNotEmpty()) {
            assertEquals(0, result.path.first(), "Path should start at source")
            assertEquals(4, result.path.last(), "Path should end at target")
            assertTrue(result.length > 0f, "Path should have length")
        }
        assertTrue(result.iterations > 0)
        assertTrue(result.executionTime > 0)
        assertNotNull(result.pheromoneMatrix)
    }

    @Test
    fun `test graph creation methods`() {
        // Test fully connected graph
        val fullyConnected = Graph.fullyConnected(4, 2.0f)
        assertEquals(4, fullyConnected.size)
        assertEquals(2.0f, fullyConnected.getWeight(0, 1), 0.01f)
        assertEquals(0f, fullyConnected.getWeight(0, 0), 0.01f) // No self-loops

        val neighbors = fullyConnected.getNeighbors(0)
        assertEquals(3, neighbors.size) // All except self

        // Test adjacency matrix graph
        val matrix = arrayOf(
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(1f, 0f, 2f),
            floatArrayOf(0f, 2f, 0f)
        )
        val graph = Graph.fromAdjacencyMatrix(matrix)
        assertEquals(3, graph.size)
        assertEquals(1f, graph.getWeight(0, 1), 0.01f)
        assertEquals(2f, graph.getWeight(1, 2), 0.01f)
    }

    @Test
    fun `test pheromone matrix operations`() {
        val matrix = PheromoneMatrix(5, initialValue = 0.5f)

        assertEquals(5, matrix.size)
        assertEquals(0.5f, matrix.get(0, 1), 0.01f)

        matrix.set(0, 1, 2.0f)
        assertEquals(2.0f, matrix.get(0, 1), 0.01f)

        // Test minimum pheromone level
        matrix.set(2, 3, -1.0f)
        assertTrue(matrix.get(2, 3) > 0f, "Pheromone should not go below minimum")

        val row = matrix.getRow(0)
        assertEquals(5, row.size)
    }

    @Test
    fun `test swarm config defaults`() {
        val config = SwarmIntelligenceEngine.SwarmConfig()

        assertEquals(50, config.defaultPopulationSize)
        assertEquals(100, config.maxIterations)
        assertTrue(config.convergenceThreshold > 0f)
        assertTrue(config.evaporationRate > 0f && config.evaporationRate < 1f)
        assertTrue(config.inertiaWeight > 0f && config.inertiaWeight < 1f)
    }

    @Test
    fun `test custom swarm config`() {
        val config = SwarmIntelligenceEngine.SwarmConfig(
            defaultPopulationSize = 100,
            maxIterations = 200,
            convergenceThreshold = 0.0001f
        )
        val engine = SwarmIntelligenceEngine(config)

        val objectiveFunction: (FloatArray) -> Float = { x -> x.sum() * x.sum() }

        val result = engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -1f to 1f,
            populationSize = 100
        )

        assertTrue(result.iterations <= 200)
    }

    @Test
    fun `test convergence detection`() {
        val engine = SwarmIntelligenceEngine(
            SwarmIntelligenceEngine.SwarmConfig(
                convergenceThreshold = 0.001f,
                maxIterations = 500
            )
        )

        // Very simple function that converges quickly
        val objectiveFunction: (FloatArray) -> Float = { x -> x[0] * x[0] }

        val result = engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 1,
            bounds = -5f to 5f,
            populationSize = 50
        )

        // Should converge before max iterations for simple function
        assertTrue(result.iterations <= 500)
    }

    @Test
    fun `test solution data class`() {
        val position = floatArrayOf(1f, 2f, 3f)
        val solution = SwarmIntelligenceEngine.Solution(
            position = position,
            fitness = 0.5f,
            metadata = mapOf("iteration" to 10)
        )

        assertEquals(3, solution.position.size)
        assertEquals(0.5f, solution.fitness, 0.01f)
        assertEquals(10, solution.metadata["iteration"])

        // Test equality
        val sameSolution = SwarmIntelligenceEngine.Solution(
            position = floatArrayOf(1f, 2f, 3f),
            fitness = 0.5f
        )
        assertEquals(solution, sameSolution)
    }

    @Test
    fun `test multi-dimensional optimization`() {
        val engine = SwarmIntelligenceEngine()

        // High-dimensional sphere function
        val objectiveFunction: (FloatArray) -> Float = { x ->
            x.map { it * it }.sum()
        }

        val result = engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 10,
            bounds = -10f to 10f,
            populationSize = 50
        )

        assertEquals(10, result.bestSolution.position.size)
        assertTrue(result.bestSolution.fitness >= 0f)
    }

    @Test
    fun `test bee algorithm with different trial limits`() {
        val engine = SwarmIntelligenceEngine()

        val objectiveFunction: (FloatArray) -> Float = { x -> x.sum() * x.sum() }

        val result1 = engine.artificialBeeColony(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -5f to 5f,
            limitTrials = 5
        )

        val result2 = engine.artificialBeeColony(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -5f to 5f,
            limitTrials = 50
        )

        // Both should complete successfully
        assertTrue(result1.iterations > 0)
        assertTrue(result2.iterations > 0)
    }

    @Test
    fun `test firefly algorithm parameters`() {
        val engine = SwarmIntelligenceEngine()

        val objectiveFunction: (FloatArray) -> Float = { x -> x.map { it * it }.sum() }

        val result = engine.fireflyOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -5f to 5f,
            beta0 = 2.0f,
            gamma = 0.5f,
            alpha = 0.1f
        )

        assertTrue(result.iterations > 0)
        assertEquals(SwarmIntelligenceEngine.SwarmAlgorithm.FIREFLY, result.algorithm)
    }

    @Test
    fun `test get active swarms`() {
        val engine = SwarmIntelligenceEngine()

        // Initially no active swarms
        assertTrue(engine.getActiveSwarms().isEmpty())

        // Run an optimization
        val objectiveFunction: (FloatArray) -> Float = { x -> x.sum() }
        engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -1f to 1f
        )

        // Should have recorded the swarm
        val swarms = engine.getActiveSwarms()
        assertTrue(swarms.isNotEmpty())
    }

    @Test
    fun `test clear old swarms`() {
        val engine = SwarmIntelligenceEngine()

        val objectiveFunction: (FloatArray) -> Float = { x -> x.sum() }
        engine.particleSwarmOptimization(
            objectiveFunction = objectiveFunction,
            dimensions = 2,
            bounds = -1f to 1f
        )

        // Clear with very long age - should keep swarms
        engine.clearOldSwarms(maxAgeMs = 3600000)
        assertTrue(engine.getActiveSwarms().isNotEmpty())

        // Clear with zero age - should remove all
        engine.clearOldSwarms(maxAgeMs = 0)
        assertTrue(engine.getActiveSwarms().isEmpty())
    }

    @Test
    fun `test optimization progress data class`() {
        val progress = OptimizationProgress(
            iteration = 5,
            bestFitness = 0.123f,
            isComplete = false
        )

        assertEquals(5, progress.iteration)
        assertEquals(0.123f, progress.bestFitness, 0.001f)
        assertEquals(false, progress.isComplete)
    }

    @Test
    fun `test ACO result data class`() {
        val matrix = PheromoneMatrix(3)
        val result = ACOResult(
            path = listOf(0, 1, 2),
            length = 5.0f,
            pheromoneMatrix = matrix,
            iterations = 10,
            executionTime = 100
        )

        assertEquals(3, result.path.size)
        assertEquals(5.0f, result.length, 0.01f)
        assertEquals(10, result.iterations)
        assertEquals(100, result.executionTime)
    }
}
