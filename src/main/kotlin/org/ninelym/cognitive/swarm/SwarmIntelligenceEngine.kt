package org.ninelym.cognitive.swarm

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

/**
 * Swarm Intelligence Engine - Phase 4B: Distributed Cognitive Mesh
 *
 * Implements collective problem-solving algorithms inspired by swarm behavior:
 * - Ant Colony Optimization (ACO) for pathfinding and routing
 * - Particle Swarm Optimization (PSO) for parameter tuning
 * - Artificial Bee Colony (ABC) for resource allocation
 * - Firefly Algorithm (FA) for synchronization and clustering
 *
 * These algorithms enable decentralized, emergent intelligence that can
 * solve complex optimization problems without centralized control.
 */
class SwarmIntelligenceEngine(
    private val config: SwarmConfig = SwarmConfig()
) {

    private val activeSwarms = ConcurrentHashMap<String, SwarmState>()
    private val pheromoneTrails = ConcurrentHashMap<String, PheromoneMatrix>()

    /**
     * Configuration for swarm behavior
     */
    data class SwarmConfig(
        val defaultPopulationSize: Int = 50,
        val maxIterations: Int = 100,
        val convergenceThreshold: Float = 0.001f,
        val evaporationRate: Float = 0.1f,
        val explorationRate: Float = 0.3f,
        val inertiaWeight: Float = 0.7f,
        val cognitiveCoefficient: Float = 1.5f,
        val socialCoefficient: Float = 1.5f
    )

    /**
     * Swarm state tracking
     */
    data class SwarmState(
        val id: String,
        val algorithm: SwarmAlgorithm,
        val iteration: Int,
        val bestSolution: Solution,
        val convergenceHistory: List<Float>,
        val isConverged: Boolean,
        val startTime: Long,
        val agents: List<SwarmAgent>
    )

    /**
     * Generic solution representation
     */
    data class Solution(
        val position: FloatArray,
        val fitness: Float,
        val metadata: Map<String, Any> = emptyMap()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Solution) return false
            return position.contentEquals(other.position) && fitness == other.fitness
        }

        override fun hashCode(): Int = position.contentHashCode()
    }

    /**
     * Swarm agent representation
     */
    data class SwarmAgent(
        val id: Int,
        val position: FloatArray,
        val velocity: FloatArray,
        val personalBest: FloatArray,
        val personalBestFitness: Float,
        val fitness: Float
    )

    /**
     * Supported swarm algorithms
     */
    enum class SwarmAlgorithm {
        ANT_COLONY,
        PARTICLE_SWARM,
        ARTIFICIAL_BEE,
        FIREFLY
    }

    /**
     * Optimization result
     */
    data class OptimizationResult(
        val bestSolution: Solution,
        val iterations: Int,
        val convergenceHistory: List<Float>,
        val executionTime: Long,
        val algorithm: SwarmAlgorithm,
        val converged: Boolean
    )

    // ============================================
    // ANT COLONY OPTIMIZATION
    // ============================================

    /**
     * Ant Colony Optimization for discrete optimization problems
     *
     * Uses pheromone trails to guide ants towards optimal solutions.
     * Best suited for pathfinding, routing, and scheduling problems.
     */
    fun antColonyOptimization(
        graph: Graph,
        sourceNode: Int,
        targetNode: Int,
        numAnts: Int = config.defaultPopulationSize
    ): ACOResult {
        val startTime = System.currentTimeMillis()
        val swarmId = "aco-${System.currentTimeMillis()}"

        // Initialize pheromone matrix
        val pheromones = PheromoneMatrix(graph.size, initialValue = 1.0f)
        pheromoneTrails[swarmId] = pheromones

        var bestPath: List<Int> = emptyList()
        var bestLength = Float.MAX_VALUE
        val convergenceHistory = mutableListOf<Float>()

        repeat(config.maxIterations) { iteration ->
            val antPaths = mutableListOf<Pair<List<Int>, Float>>()

            // Each ant constructs a solution
            repeat(numAnts) { antId ->
                val path = constructAntPath(graph, pheromones, sourceNode, targetNode)
                if (path.isNotEmpty()) {
                    val length = calculatePathLength(graph, path)
                    antPaths.add(path to length)

                    if (length < bestLength) {
                        bestLength = length
                        bestPath = path
                    }
                }
            }

            // Update pheromones
            evaporatePheromones(pheromones)
            antPaths.forEach { (path, length) ->
                depositPheromones(pheromones, path, 1.0f / length)
            }

            convergenceHistory.add(bestLength)

            // Check convergence
            if (iteration > 10 && hasConverged(convergenceHistory, config.convergenceThreshold)) {
                break
            }
        }

        return ACOResult(
            path = bestPath,
            length = bestLength,
            pheromoneMatrix = pheromones,
            iterations = convergenceHistory.size,
            executionTime = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Construct path for single ant using probabilistic selection
     */
    private fun constructAntPath(
        graph: Graph,
        pheromones: PheromoneMatrix,
        source: Int,
        target: Int
    ): List<Int> {
        val path = mutableListOf(source)
        val visited = mutableSetOf(source)
        var current = source

        while (current != target && path.size < graph.size) {
            val neighbors = graph.getNeighbors(current).filter { it !in visited }
            if (neighbors.isEmpty()) break

            // Calculate probabilities based on pheromone and heuristic
            val probabilities = neighbors.map { neighbor ->
                val pheromone = pheromones.get(current, neighbor)
                val heuristic = 1.0f / (graph.getWeight(current, neighbor) + 0.001f)
                pheromone.pow(2) * heuristic.pow(3)
            }

            val sum = probabilities.sum()
            val normalized = probabilities.map { it / sum }

            // Roulette wheel selection
            val r = Random.nextFloat()
            var cumulative = 0f
            var selectedIndex = 0

            for (i in normalized.indices) {
                cumulative += normalized[i]
                if (r <= cumulative) {
                    selectedIndex = i
                    break
                }
            }

            current = neighbors[selectedIndex]
            path.add(current)
            visited.add(current)
        }

        return if (current == target) path else emptyList()
    }

    /**
     * Evaporate pheromones (decay over time)
     */
    private fun evaporatePheromones(pheromones: PheromoneMatrix) {
        for (i in 0 until pheromones.size) {
            for (j in 0 until pheromones.size) {
                val current = pheromones.get(i, j)
                pheromones.set(i, j, current * (1 - config.evaporationRate))
            }
        }
    }

    /**
     * Deposit pheromones along path
     */
    private fun depositPheromones(pheromones: PheromoneMatrix, path: List<Int>, amount: Float) {
        for (i in 0 until path.size - 1) {
            val current = pheromones.get(path[i], path[i + 1])
            pheromones.set(path[i], path[i + 1], current + amount)
        }
    }

    // ============================================
    // PARTICLE SWARM OPTIMIZATION
    // ============================================

    /**
     * Particle Swarm Optimization for continuous optimization
     *
     * Particles explore the search space, sharing information about
     * good solutions to converge on optimal values.
     */
    fun particleSwarmOptimization(
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>,
        populationSize: Int = config.defaultPopulationSize,
        maximize: Boolean = false
    ): OptimizationResult {
        val startTime = System.currentTimeMillis()
        val swarmId = "pso-${System.currentTimeMillis()}"

        // Initialize particles
        val particles = (0 until populationSize).map { id ->
            val position = FloatArray(dimensions) {
                Random.nextFloat() * (bounds.second - bounds.first) + bounds.first
            }
            val velocity = FloatArray(dimensions) {
                (Random.nextFloat() - 0.5f) * (bounds.second - bounds.first) * 0.1f
            }
            val fitness = objectiveFunction(position)

            SwarmAgent(
                id = id,
                position = position,
                velocity = velocity,
                personalBest = position.copyOf(),
                personalBestFitness = fitness,
                fitness = fitness
            )
        }.toMutableList()

        // Initialize global best
        var globalBest = if (maximize) {
            particles.maxByOrNull { it.fitness }!!
        } else {
            particles.minByOrNull { it.fitness }!!
        }

        val convergenceHistory = mutableListOf<Float>()
        var iteration = 0

        while (iteration < config.maxIterations) {
            // Update each particle
            for (i in particles.indices) {
                val particle = particles[i]

                // Update velocity
                val newVelocity = FloatArray(dimensions) { d ->
                    val inertia = config.inertiaWeight * particle.velocity[d]
                    val cognitive = config.cognitiveCoefficient * Random.nextFloat() *
                            (particle.personalBest[d] - particle.position[d])
                    val social = config.socialCoefficient * Random.nextFloat() *
                            (globalBest.position[d] - particle.position[d])

                    (inertia + cognitive + social).coerceIn(
                        -abs(bounds.second - bounds.first),
                        abs(bounds.second - bounds.first)
                    )
                }

                // Update position
                val newPosition = FloatArray(dimensions) { d ->
                    (particle.position[d] + newVelocity[d]).coerceIn(bounds.first, bounds.second)
                }

                val newFitness = objectiveFunction(newPosition)

                // Update personal best
                val betterPersonal = if (maximize) {
                    newFitness > particle.personalBestFitness
                } else {
                    newFitness < particle.personalBestFitness
                }

                val newPersonalBest = if (betterPersonal) newPosition.copyOf() else particle.personalBest
                val newPersonalBestFitness = if (betterPersonal) newFitness else particle.personalBestFitness

                particles[i] = SwarmAgent(
                    id = particle.id,
                    position = newPosition,
                    velocity = newVelocity,
                    personalBest = newPersonalBest,
                    personalBestFitness = newPersonalBestFitness,
                    fitness = newFitness
                )

                // Update global best
                val betterGlobal = if (maximize) {
                    newFitness > globalBest.fitness
                } else {
                    newFitness < globalBest.fitness
                }

                if (betterGlobal) {
                    globalBest = particles[i]
                }
            }

            convergenceHistory.add(globalBest.fitness)
            iteration++

            // Check convergence
            if (iteration > 10 && hasConverged(convergenceHistory, config.convergenceThreshold)) {
                break
            }
        }

        // Store final state
        activeSwarms[swarmId] = SwarmState(
            id = swarmId,
            algorithm = SwarmAlgorithm.PARTICLE_SWARM,
            iteration = iteration,
            bestSolution = Solution(globalBest.position, globalBest.fitness),
            convergenceHistory = convergenceHistory,
            isConverged = hasConverged(convergenceHistory, config.convergenceThreshold),
            startTime = startTime,
            agents = particles
        )

        return OptimizationResult(
            bestSolution = Solution(globalBest.position.copyOf(), globalBest.fitness),
            iterations = iteration,
            convergenceHistory = convergenceHistory,
            executionTime = System.currentTimeMillis() - startTime,
            algorithm = SwarmAlgorithm.PARTICLE_SWARM,
            converged = hasConverged(convergenceHistory, config.convergenceThreshold)
        )
    }

    // ============================================
    // ARTIFICIAL BEE COLONY
    // ============================================

    /**
     * Artificial Bee Colony for resource allocation and optimization
     *
     * Simulates foraging behavior of honeybees with three types of bees:
     * - Employed bees: Exploit known food sources
     * - Onlooker bees: Select sources based on quality
     * - Scout bees: Explore new sources randomly
     */
    fun artificialBeeColony(
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>,
        colonySize: Int = config.defaultPopulationSize,
        limitTrials: Int = 20,
        maximize: Boolean = false
    ): OptimizationResult {
        val startTime = System.currentTimeMillis()
        val swarmId = "abc-${System.currentTimeMillis()}"

        val numEmployed = colonySize / 2

        // Initialize food sources (employed bees)
        data class FoodSource(
            val position: FloatArray,
            var fitness: Float,
            var trials: Int = 0
        )

        val foodSources = (0 until numEmployed).map {
            val position = FloatArray(dimensions) {
                Random.nextFloat() * (bounds.second - bounds.first) + bounds.first
            }
            FoodSource(position, objectiveFunction(position))
        }.toMutableList()

        var globalBest = if (maximize) {
            foodSources.maxByOrNull { it.fitness }!!
        } else {
            foodSources.minByOrNull { it.fitness }!!
        }

        val convergenceHistory = mutableListOf<Float>()
        var iteration = 0

        while (iteration < config.maxIterations) {
            // Employed bee phase
            for (i in foodSources.indices) {
                val source = foodSources[i]
                val neighbor = (foodSources.indices - i).random()

                // Generate new candidate solution
                val newPosition = FloatArray(dimensions) { d ->
                    val phi = Random.nextFloat() * 2 - 1
                    (source.position[d] + phi * (source.position[d] - foodSources[neighbor].position[d]))
                        .coerceIn(bounds.first, bounds.second)
                }

                val newFitness = objectiveFunction(newPosition)
                val better = if (maximize) newFitness > source.fitness else newFitness < source.fitness

                if (better) {
                    foodSources[i] = FoodSource(newPosition, newFitness, 0)
                } else {
                    source.trials++
                }
            }

            // Calculate probabilities for onlooker bees
            val fitnesses = foodSources.map { it.fitness }
            val maxFitness = fitnesses.maxOrNull() ?: 1f
            val minFitness = fitnesses.minOrNull() ?: 0f
            val range = maxFitness - minFitness + 0.001f

            val probabilities = foodSources.map { source ->
                if (maximize) {
                    (source.fitness - minFitness) / range
                } else {
                    (maxFitness - source.fitness) / range
                }
            }
            val sumProb = probabilities.sum()
            val normalizedProb = probabilities.map { it / sumProb }

            // Onlooker bee phase
            repeat(numEmployed) {
                // Select food source based on probability
                val r = Random.nextFloat()
                var cumulative = 0f
                var selectedIndex = 0

                for (i in normalizedProb.indices) {
                    cumulative += normalizedProb[i]
                    if (r <= cumulative) {
                        selectedIndex = i
                        break
                    }
                }

                val source = foodSources[selectedIndex]
                val neighbor = (foodSources.indices - selectedIndex).random()

                val newPosition = FloatArray(dimensions) { d ->
                    val phi = Random.nextFloat() * 2 - 1
                    (source.position[d] + phi * (source.position[d] - foodSources[neighbor].position[d]))
                        .coerceIn(bounds.first, bounds.second)
                }

                val newFitness = objectiveFunction(newPosition)
                val better = if (maximize) newFitness > source.fitness else newFitness < source.fitness

                if (better) {
                    foodSources[selectedIndex] = FoodSource(newPosition, newFitness, 0)
                } else {
                    source.trials++
                }
            }

            // Scout bee phase - abandon exhausted sources
            for (i in foodSources.indices) {
                if (foodSources[i].trials > limitTrials) {
                    val position = FloatArray(dimensions) {
                        Random.nextFloat() * (bounds.second - bounds.first) + bounds.first
                    }
                    foodSources[i] = FoodSource(position, objectiveFunction(position), 0)
                }
            }

            // Update global best
            val currentBest = if (maximize) {
                foodSources.maxByOrNull { it.fitness }!!
            } else {
                foodSources.minByOrNull { it.fitness }!!
            }

            val betterGlobal = if (maximize) {
                currentBest.fitness > globalBest.fitness
            } else {
                currentBest.fitness < globalBest.fitness
            }

            if (betterGlobal) {
                globalBest = currentBest
            }

            convergenceHistory.add(globalBest.fitness)
            iteration++

            if (iteration > 10 && hasConverged(convergenceHistory, config.convergenceThreshold)) {
                break
            }
        }

        return OptimizationResult(
            bestSolution = Solution(globalBest.position.copyOf(), globalBest.fitness),
            iterations = iteration,
            convergenceHistory = convergenceHistory,
            executionTime = System.currentTimeMillis() - startTime,
            algorithm = SwarmAlgorithm.ARTIFICIAL_BEE,
            converged = hasConverged(convergenceHistory, config.convergenceThreshold)
        )
    }

    // ============================================
    // FIREFLY ALGORITHM
    // ============================================

    /**
     * Firefly Algorithm for multimodal optimization and clustering
     *
     * Fireflies are attracted to brighter (more fit) fireflies,
     * enabling simultaneous exploration of multiple optimal regions.
     */
    fun fireflyOptimization(
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>,
        populationSize: Int = config.defaultPopulationSize,
        beta0: Float = 1.0f,
        gamma: Float = 1.0f,
        alpha: Float = 0.2f,
        maximize: Boolean = false
    ): OptimizationResult {
        val startTime = System.currentTimeMillis()

        data class Firefly(
            val position: FloatArray,
            var brightness: Float
        )

        // Initialize fireflies
        val fireflies = (0 until populationSize).map {
            val position = FloatArray(dimensions) {
                Random.nextFloat() * (bounds.second - bounds.first) + bounds.first
            }
            Firefly(position, objectiveFunction(position))
        }.toMutableList()

        var globalBest = if (maximize) {
            fireflies.maxByOrNull { it.brightness }!!
        } else {
            fireflies.minByOrNull { it.brightness }!!
        }

        val convergenceHistory = mutableListOf<Float>()
        var iteration = 0
        var currentAlpha = alpha

        while (iteration < config.maxIterations) {
            // Move fireflies
            for (i in fireflies.indices) {
                for (j in fireflies.indices) {
                    if (i == j) continue

                    val isBrighter = if (maximize) {
                        fireflies[j].brightness > fireflies[i].brightness
                    } else {
                        fireflies[j].brightness < fireflies[i].brightness
                    }

                    if (isBrighter) {
                        // Calculate distance
                        val distance = sqrt(
                            (0 until dimensions).sumOf { d ->
                                (fireflies[i].position[d] - fireflies[j].position[d]).toDouble().pow(2)
                            }
                        ).toFloat()

                        // Calculate attractiveness
                        val attractiveness = beta0 * exp(-gamma * distance * distance)

                        // Move towards brighter firefly
                        for (d in 0 until dimensions) {
                            val randomness = currentAlpha * (Random.nextFloat() - 0.5f) *
                                    (bounds.second - bounds.first)
                            fireflies[i].position[d] = (
                                fireflies[i].position[d] +
                                attractiveness * (fireflies[j].position[d] - fireflies[i].position[d]) +
                                randomness
                            ).coerceIn(bounds.first, bounds.second)
                        }

                        fireflies[i].brightness = objectiveFunction(fireflies[i].position)
                    }
                }
            }

            // Update global best
            val currentBest = if (maximize) {
                fireflies.maxByOrNull { it.brightness }!!
            } else {
                fireflies.minByOrNull { it.brightness }!!
            }

            val betterGlobal = if (maximize) {
                currentBest.brightness > globalBest.brightness
            } else {
                currentBest.brightness < globalBest.brightness
            }

            if (betterGlobal) {
                globalBest = Firefly(currentBest.position.copyOf(), currentBest.brightness)
            }

            convergenceHistory.add(globalBest.brightness)
            iteration++

            // Decrease alpha for convergence
            currentAlpha *= 0.97f

            if (iteration > 10 && hasConverged(convergenceHistory, config.convergenceThreshold)) {
                break
            }
        }

        return OptimizationResult(
            bestSolution = Solution(globalBest.position.copyOf(), globalBest.brightness),
            iterations = iteration,
            convergenceHistory = convergenceHistory,
            executionTime = System.currentTimeMillis() - startTime,
            algorithm = SwarmAlgorithm.FIREFLY,
            converged = hasConverged(convergenceHistory, config.convergenceThreshold)
        )
    }

    // ============================================
    // UTILITY FUNCTIONS
    // ============================================

    /**
     * Check if optimization has converged
     */
    private fun hasConverged(history: List<Float>, threshold: Float): Boolean {
        if (history.size < 10) return false

        val recent = history.takeLast(10)
        val variance = recent.map { (it - recent.average()).toFloat().pow(2) }.average().toFloat()

        return sqrt(variance) < threshold
    }

    /**
     * Calculate path length in graph
     */
    private fun calculatePathLength(graph: Graph, path: List<Int>): Float {
        var length = 0f
        for (i in 0 until path.size - 1) {
            length += graph.getWeight(path[i], path[i + 1])
        }
        return length
    }

    /**
     * Get active swarm state
     */
    fun getSwarmState(swarmId: String): SwarmState? = activeSwarms[swarmId]

    /**
     * Get all active swarms
     */
    fun getActiveSwarms(): List<SwarmState> = activeSwarms.values.toList()

    /**
     * Clear inactive swarms
     */
    fun clearOldSwarms(maxAgeMs: Long = 3600000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        activeSwarms.entries.removeIf { it.value.startTime < cutoff }
    }

    /**
     * Stream optimization progress
     */
    fun streamOptimization(
        algorithm: SwarmAlgorithm,
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>
    ): Flow<OptimizationProgress> = flow {
        when (algorithm) {
            SwarmAlgorithm.PARTICLE_SWARM -> {
                streamPSO(objectiveFunction, dimensions, bounds).collect { emit(it) }
            }
            SwarmAlgorithm.ARTIFICIAL_BEE -> {
                streamABC(objectiveFunction, dimensions, bounds).collect { emit(it) }
            }
            SwarmAlgorithm.FIREFLY -> {
                streamFirefly(objectiveFunction, dimensions, bounds).collect { emit(it) }
            }
            else -> {
                // ACO requires graph, handle separately
                emit(OptimizationProgress(0, 0f, false))
            }
        }
    }

    private fun streamPSO(
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>
    ): Flow<OptimizationProgress> = flow {
        // Simplified streaming version - emits progress updates
        val result = particleSwarmOptimization(objectiveFunction, dimensions, bounds)
        result.convergenceHistory.forEachIndexed { index, fitness ->
            emit(OptimizationProgress(index, fitness, index == result.iterations - 1))
            delay(10) // Small delay for streaming effect
        }
    }

    private fun streamABC(
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>
    ): Flow<OptimizationProgress> = flow {
        val result = artificialBeeColony(objectiveFunction, dimensions, bounds)
        result.convergenceHistory.forEachIndexed { index, fitness ->
            emit(OptimizationProgress(index, fitness, index == result.iterations - 1))
            delay(10)
        }
    }

    private fun streamFirefly(
        objectiveFunction: (FloatArray) -> Float,
        dimensions: Int,
        bounds: Pair<Float, Float>
    ): Flow<OptimizationProgress> = flow {
        val result = fireflyOptimization(objectiveFunction, dimensions, bounds)
        result.convergenceHistory.forEachIndexed { index, fitness ->
            emit(OptimizationProgress(index, fitness, index == result.iterations - 1))
            delay(10)
        }
    }
}

/**
 * Optimization progress update
 */
data class OptimizationProgress(
    val iteration: Int,
    val bestFitness: Float,
    val isComplete: Boolean
)

/**
 * ACO-specific result
 */
data class ACOResult(
    val path: List<Int>,
    val length: Float,
    val pheromoneMatrix: PheromoneMatrix,
    val iterations: Int,
    val executionTime: Long
)

/**
 * Graph representation for ACO
 */
class Graph(
    val size: Int,
    private val adjacency: Array<FloatArray>
) {
    fun getWeight(from: Int, to: Int): Float = adjacency[from][to]

    fun getNeighbors(node: Int): List<Int> {
        return adjacency[node].indices.filter { adjacency[node][it] > 0 && it != node }
    }

    companion object {
        fun fromAdjacencyMatrix(matrix: Array<FloatArray>): Graph {
            return Graph(matrix.size, matrix)
        }

        fun fullyConnected(size: Int, defaultWeight: Float = 1.0f): Graph {
            val adjacency = Array(size) { i ->
                FloatArray(size) { j -> if (i != j) defaultWeight else 0f }
            }
            return Graph(size, adjacency)
        }
    }
}

/**
 * Pheromone matrix for ACO
 */
class PheromoneMatrix(
    val size: Int,
    initialValue: Float = 1.0f
) {
    private val matrix = Array(size) { FloatArray(size) { initialValue } }

    fun get(i: Int, j: Int): Float = matrix[i][j]

    fun set(i: Int, j: Int, value: Float) {
        matrix[i][j] = value.coerceAtLeast(0.001f) // Minimum pheromone level
    }

    fun getRow(i: Int): FloatArray = matrix[i].copyOf()
}
