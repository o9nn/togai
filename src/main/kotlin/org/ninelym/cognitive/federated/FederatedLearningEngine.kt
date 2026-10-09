package org.ninelym.cognitive.federated

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Federated Learning Engine - Phase 4B: Distributed Cognitive Mesh
 *
 * Implements privacy-preserving collaborative learning across distributed
 * cognitive systems without sharing raw data:
 * - Federated Averaging (FedAvg) for model aggregation
 * - Differential Privacy for gradient protection
 * - Secure Aggregation for encrypted updates
 * - Client Selection for efficient training
 *
 * Enables multiple Togai instances to collaboratively improve while
 * keeping all raw data on-device.
 */
class FederatedLearningEngine(
    private val config: FederatedConfig = FederatedConfig()
) {

    private val participants = ConcurrentHashMap<String, FederatedParticipant>()
    private val roundHistory = mutableListOf<FederatedRound>()
    private val globalModel = GlobalModelState()
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()

    /**
     * Configuration for federated learning
     */
    data class FederatedConfig(
        val minParticipants: Int = 3,
        val maxParticipants: Int = 100,
        val roundsPerEpoch: Int = 10,
        val localEpochs: Int = 5,
        val learningRate: Float = 0.01f,
        val batchSize: Int = 32,
        val participantSelectionFraction: Float = 0.3f,
        val differentialPrivacyEpsilon: Float = 1.0f,
        val differentialPrivacyDelta: Float = 1e-5f,
        val noiseMultiplier: Float = 1.1f,
        val clipNorm: Float = 1.0f,
        val secureAggregationEnabled: Boolean = true
    )

    /**
     * Represents a participant in federated learning
     */
    data class FederatedParticipant(
        val id: String,
        val name: String,
        val datasetSize: Int,
        var lastSeen: Long,
        var contributionScore: Float = 0f,
        var roundsParticipated: Int = 0,
        var localModelVersion: Int = 0,
        val capabilities: ParticipantCapabilities,
        var isActive: Boolean = true
    )

    /**
     * Participant capabilities for selection
     */
    data class ParticipantCapabilities(
        val computePower: Float,      // 0-1 scale
        val bandwidthMbps: Float,
        val batteryLevel: Float,      // 0-1 scale
        val isPluggedIn: Boolean,
        val availableMemoryMb: Int
    )

    /**
     * Model update from participant
     */
    data class ModelUpdate(
        val participantId: String,
        val roundId: Int,
        val gradients: FloatArray,
        val datasetSize: Int,
        val localLoss: Float,
        val timestamp: Long,
        val encryptedPayload: ByteArray? = null // For secure aggregation
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ModelUpdate) return false
            return participantId == other.participantId && roundId == other.roundId
        }

        override fun hashCode(): Int = participantId.hashCode() + roundId
    }

    /**
     * Federated learning round
     */
    data class FederatedRound(
        val roundId: Int,
        val startTime: Long,
        var endTime: Long? = null,
        val selectedParticipants: List<String>,
        val updates: MutableList<ModelUpdate> = mutableListOf(),
        var aggregatedGradients: FloatArray? = null,
        var globalLoss: Float = 0f,
        var status: RoundStatus = RoundStatus.IN_PROGRESS
    )

    enum class RoundStatus {
        IN_PROGRESS,
        AGGREGATING,
        COMPLETED,
        FAILED
    }

    /**
     * Global model state
     */
    inner class GlobalModelState {
        var weights: FloatArray = FloatArray(0)
        var version: Int = 0
        var lastUpdated: Long = System.currentTimeMillis()
        val updateHistory = mutableListOf<GlobalModelUpdate>()

        data class GlobalModelUpdate(
            val version: Int,
            val timestamp: Long,
            val participants: Int,
            val aggregatedLoss: Float
        )

        fun initialize(dimensions: Int) {
            weights = FloatArray(dimensions) { (secureRandom.nextGaussian() * 0.01).toFloat() }
            version = 0
            lastUpdated = System.currentTimeMillis()
        }

        fun update(newWeights: FloatArray, participants: Int, loss: Float) {
            weights = newWeights.copyOf()
            version++
            lastUpdated = System.currentTimeMillis()
            updateHistory.add(GlobalModelUpdate(version, lastUpdated, participants, loss))
        }
    }

    /**
     * Result of federated training round
     */
    data class FederatedRoundResult(
        val roundId: Int,
        val participantsCount: Int,
        val aggregatedLoss: Float,
        val globalModelVersion: Int,
        val convergenceMetric: Float,
        val executionTime: Long,
        val privacyBudgetUsed: Float
    )

    /**
     * Overall federated learning result
     */
    data class FederatedTrainingResult(
        val totalRounds: Int,
        val finalLoss: Float,
        val finalModelVersion: Int,
        val totalParticipants: Int,
        val convergenceHistory: List<Float>,
        val privacyBudgetRemaining: Float,
        val executionTime: Long
    )

    // ============================================
    // PARTICIPANT MANAGEMENT
    // ============================================

    /**
     * Register a new participant
     */
    suspend fun registerParticipant(
        name: String,
        datasetSize: Int,
        capabilities: ParticipantCapabilities
    ): String = mutex.withLock {
        val participantId = generateParticipantId(name)

        val participant = FederatedParticipant(
            id = participantId,
            name = name,
            datasetSize = datasetSize,
            lastSeen = System.currentTimeMillis(),
            capabilities = capabilities
        )

        participants[participantId] = participant
        participantId
    }

    /**
     * Update participant status
     */
    suspend fun updateParticipantStatus(
        participantId: String,
        capabilities: ParticipantCapabilities
    ) = mutex.withLock {
        participants[participantId]?.let {
            participants[participantId] = it.copy(
                lastSeen = System.currentTimeMillis(),
                capabilities = capabilities
            )
        }
    }

    /**
     * Remove inactive participants
     */
    suspend fun pruneInactiveParticipants(maxInactiveMs: Long = 3600000) = mutex.withLock {
        val cutoff = System.currentTimeMillis() - maxInactiveMs
        participants.values.filter { it.lastSeen < cutoff }.forEach {
            it.isActive = false
        }
    }

    /**
     * Select participants for a round
     */
    private fun selectParticipants(): List<String> {
        val activeParticipants = participants.values.filter {
            it.isActive && isParticipantEligible(it)
        }

        if (activeParticipants.size < config.minParticipants) {
            return emptyList()
        }

        // Score-based selection considering capabilities and contribution
        val scored = activeParticipants.map { p ->
            val capabilityScore = (p.capabilities.computePower +
                    p.capabilities.bandwidthMbps / 100f +
                    p.capabilities.batteryLevel +
                    (if (p.capabilities.isPluggedIn) 0.2f else 0f)) / 4f

            val contributionScore = p.contributionScore
            val dataScore = p.datasetSize.toFloat() / activeParticipants.maxOf { it.datasetSize }

            p.id to (capabilityScore * 0.4f + contributionScore * 0.3f + dataScore * 0.3f)
        }.sortedByDescending { it.second }

        val numToSelect = (activeParticipants.size * config.participantSelectionFraction)
            .toInt()
            .coerceIn(config.minParticipants, config.maxParticipants)

        return scored.take(numToSelect).map { it.first }
    }

    /**
     * Check if participant is eligible for selection
     */
    private fun isParticipantEligible(participant: FederatedParticipant): Boolean {
        return participant.capabilities.batteryLevel > 0.2f &&
                participant.capabilities.availableMemoryMb > 100 &&
                (System.currentTimeMillis() - participant.lastSeen) < 60000
    }

    // ============================================
    // FEDERATED AVERAGING (FedAvg)
    // ============================================

    /**
     * Initialize global model
     */
    suspend fun initializeGlobalModel(dimensions: Int) = mutex.withLock {
        globalModel.initialize(dimensions)
    }

    /**
     * Get current global model weights
     */
    fun getGlobalModelWeights(): FloatArray = globalModel.weights.copyOf()

    /**
     * Get global model version
     */
    fun getGlobalModelVersion(): Int = globalModel.version

    /**
     * Run a single federated round
     */
    suspend fun runFederatedRound(
        localTrainingFn: suspend (FloatArray, Int) -> Pair<FloatArray, Float>
    ): FederatedRoundResult {
        val startTime = System.currentTimeMillis()
        val roundId = roundHistory.size

        // Select participants
        val selectedParticipants = selectParticipants()
        if (selectedParticipants.size < config.minParticipants) {
            throw IllegalStateException("Not enough participants: ${selectedParticipants.size} < ${config.minParticipants}")
        }

        val round = FederatedRound(
            roundId = roundId,
            startTime = startTime,
            selectedParticipants = selectedParticipants
        )
        roundHistory.add(round)

        // Collect updates from participants (simulated local training)
        val updates = collectLocalUpdates(selectedParticipants, localTrainingFn)
        round.updates.addAll(updates)

        // Aggregate updates
        round.status = RoundStatus.AGGREGATING
        val aggregatedResult = aggregateUpdates(updates)

        // Apply aggregated gradients to global model
        mutex.withLock {
            val newWeights = applyGradients(globalModel.weights, aggregatedResult.gradients)
            globalModel.update(newWeights, updates.size, aggregatedResult.averageLoss)
        }

        round.aggregatedGradients = aggregatedResult.gradients
        round.globalLoss = aggregatedResult.averageLoss
        round.endTime = System.currentTimeMillis()
        round.status = RoundStatus.COMPLETED

        // Update participant scores
        updateParticipantScores(updates)

        val convergenceMetric = calculateConvergence()

        return FederatedRoundResult(
            roundId = roundId,
            participantsCount = updates.size,
            aggregatedLoss = aggregatedResult.averageLoss,
            globalModelVersion = globalModel.version,
            convergenceMetric = convergenceMetric,
            executionTime = System.currentTimeMillis() - startTime,
            privacyBudgetUsed = calculatePrivacyBudgetUsed()
        )
    }

    /**
     * Collect local updates from selected participants
     */
    private suspend fun collectLocalUpdates(
        selectedParticipants: List<String>,
        localTrainingFn: suspend (FloatArray, Int) -> Pair<FloatArray, Float>
    ): List<ModelUpdate> = coroutineScope {
        selectedParticipants.mapNotNull { participantId ->
            val participant = participants[participantId] ?: return@mapNotNull null

            async {
                try {
                    // Simulate local training
                    val (gradients, localLoss) = localTrainingFn(
                        globalModel.weights,
                        participant.datasetSize
                    )

                    // Apply differential privacy
                    val noisyGradients = addDifferentialPrivacyNoise(gradients)

                    // Optionally encrypt for secure aggregation
                    val encryptedPayload = if (config.secureAggregationEnabled) {
                        encryptGradients(noisyGradients)
                    } else null

                    ModelUpdate(
                        participantId = participantId,
                        roundId = roundHistory.size - 1,
                        gradients = noisyGradients,
                        datasetSize = participant.datasetSize,
                        localLoss = localLoss,
                        timestamp = System.currentTimeMillis(),
                        encryptedPayload = encryptedPayload
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Aggregate updates using weighted average
     */
    private fun aggregateUpdates(updates: List<ModelUpdate>): AggregatedResult {
        if (updates.isEmpty()) {
            return AggregatedResult(FloatArray(0), 0f)
        }

        val totalSamples = updates.sumOf { it.datasetSize }
        val dimensions = updates.first().gradients.size

        // Weighted averaging of gradients
        val aggregatedGradients = FloatArray(dimensions)
        var totalLoss = 0f

        updates.forEach { update ->
            val weight = update.datasetSize.toFloat() / totalSamples
            for (i in aggregatedGradients.indices) {
                aggregatedGradients[i] += update.gradients[i] * weight
            }
            totalLoss += update.localLoss * weight
        }

        return AggregatedResult(aggregatedGradients, totalLoss)
    }

    data class AggregatedResult(
        val gradients: FloatArray,
        val averageLoss: Float
    )

    /**
     * Apply gradients to weights
     */
    private fun applyGradients(weights: FloatArray, gradients: FloatArray): FloatArray {
        return FloatArray(weights.size) { i ->
            weights[i] - config.learningRate * gradients[i]
        }
    }

    // ============================================
    // DIFFERENTIAL PRIVACY
    // ============================================

    /**
     * Add differential privacy noise to gradients
     *
     * Implements the Gaussian mechanism with gradient clipping
     */
    private fun addDifferentialPrivacyNoise(gradients: FloatArray): FloatArray {
        // Clip gradients to bound sensitivity
        val clippedGradients = clipGradients(gradients, config.clipNorm)

        // Calculate noise scale based on privacy parameters
        val sensitivity = config.clipNorm
        val noiseScale = sensitivity * config.noiseMultiplier

        // Add Gaussian noise
        return FloatArray(clippedGradients.size) { i ->
            clippedGradients[i] + (secureRandom.nextGaussian() * noiseScale).toFloat()
        }
    }

    /**
     * Clip gradients to maximum L2 norm
     */
    private fun clipGradients(gradients: FloatArray, maxNorm: Float): FloatArray {
        val norm = sqrt(gradients.map { it * it }.sum())

        return if (norm > maxNorm) {
            val scale = maxNorm / norm
            FloatArray(gradients.size) { i -> gradients[i] * scale }
        } else {
            gradients.copyOf()
        }
    }

    /**
     * Calculate privacy budget used
     */
    private fun calculatePrivacyBudgetUsed(): Float {
        // Simplified privacy accounting using composition theorem
        val rounds = roundHistory.size
        return (config.differentialPrivacyEpsilon * sqrt(2f * rounds *
            kotlin.math.ln(1f / config.differentialPrivacyDelta)))
    }

    // ============================================
    // SECURE AGGREGATION
    // ============================================

    /**
     * Encrypt gradients for secure aggregation
     *
     * Uses a simplified approach; production would use proper MPC protocols
     */
    private fun encryptGradients(gradients: FloatArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")

        // Convert gradients to bytes
        val buffer = java.nio.ByteBuffer.allocate(gradients.size * 4)
        gradients.forEach { buffer.putFloat(it) }

        // Create encrypted payload (simplified - real impl would use secret sharing)
        val mask = ByteArray(buffer.array().size)
        secureRandom.nextBytes(mask)

        return xorBytes(buffer.array(), mask) + digest.digest(mask)
    }

    /**
     * XOR byte arrays
     */
    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i % b.size].toInt()).toByte() }
    }

    // ============================================
    // CONVERGENCE AND METRICS
    // ============================================

    /**
     * Calculate convergence metric
     */
    private fun calculateConvergence(): Float {
        if (roundHistory.size < 2) return 1.0f

        val recentLosses = roundHistory.takeLast(5).map { it.globalLoss }
        if (recentLosses.size < 2) return 1.0f

        val mean = recentLosses.average().toFloat()
        val variance = recentLosses.map { (it - mean) * (it - mean) }.average().toFloat()

        return sqrt(variance)
    }

    /**
     * Update participant contribution scores
     */
    private suspend fun updateParticipantScores(updates: List<ModelUpdate>) = mutex.withLock {
        updates.forEach { update ->
            participants[update.participantId]?.let { participant ->
                // Score based on data size, consistency, and participation
                val dataSizeScore = update.datasetSize.toFloat() /
                    updates.maxOf { it.datasetSize }
                val consistencyScore = 1f - (update.localLoss /
                    (updates.maxOf { it.localLoss } + 0.001f))

                participant.contributionScore = (participant.contributionScore * 0.9f +
                    (dataSizeScore + consistencyScore) / 2f * 0.1f)
                participant.roundsParticipated++
                participant.localModelVersion = globalModel.version
            }
        }
    }

    // ============================================
    // FULL TRAINING LOOP
    // ============================================

    /**
     * Run complete federated training
     */
    suspend fun runFederatedTraining(
        rounds: Int,
        localTrainingFn: suspend (FloatArray, Int) -> Pair<FloatArray, Float>,
        onRoundComplete: ((FederatedRoundResult) -> Unit)? = null
    ): FederatedTrainingResult {
        val startTime = System.currentTimeMillis()
        val convergenceHistory = mutableListOf<Float>()
        var completedRounds = 0

        repeat(rounds) { round ->
            try {
                val result = runFederatedRound(localTrainingFn)
                convergenceHistory.add(result.aggregatedLoss)
                onRoundComplete?.invoke(result)
                completedRounds++

                // Check for convergence
                if (result.convergenceMetric < 0.001f) {
                    return@repeat
                }
            } catch (e: Exception) {
                // Log error and continue
            }
        }

        return FederatedTrainingResult(
            totalRounds = completedRounds,
            finalLoss = convergenceHistory.lastOrNull() ?: Float.MAX_VALUE,
            finalModelVersion = globalModel.version,
            totalParticipants = participants.values.count { it.roundsParticipated > 0 },
            convergenceHistory = convergenceHistory,
            privacyBudgetRemaining = config.differentialPrivacyEpsilon - calculatePrivacyBudgetUsed(),
            executionTime = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Stream training progress
     */
    fun streamFederatedTraining(
        rounds: Int,
        localTrainingFn: suspend (FloatArray, Int) -> Pair<FloatArray, Float>
    ): Flow<FederatedRoundResult> = flow {
        repeat(rounds) {
            try {
                val result = runFederatedRound(localTrainingFn)
                emit(result)

                if (result.convergenceMetric < 0.001f) {
                    return@flow
                }
            } catch (e: Exception) {
                // Skip failed rounds
            }
        }
    }

    // ============================================
    // UTILITY FUNCTIONS
    // ============================================

    /**
     * Generate unique participant ID
     */
    private fun generateParticipantId(name: String): String {
        val timestamp = System.currentTimeMillis()
        val random = secureRandom.nextInt(10000)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("$name-$timestamp-$random".toByteArray())
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Get training statistics
     */
    fun getTrainingStats(): TrainingStats {
        return TrainingStats(
            totalRounds = roundHistory.size,
            activeParticipants = participants.values.count { it.isActive },
            totalParticipants = participants.size,
            globalModelVersion = globalModel.version,
            averageLoss = roundHistory.takeLast(10).map { it.globalLoss }.average().toFloat(),
            convergenceMetric = calculateConvergence(),
            privacyBudgetUsed = calculatePrivacyBudgetUsed(),
            privacyBudgetRemaining = config.differentialPrivacyEpsilon - calculatePrivacyBudgetUsed()
        )
    }

    data class TrainingStats(
        val totalRounds: Int,
        val activeParticipants: Int,
        val totalParticipants: Int,
        val globalModelVersion: Int,
        val averageLoss: Float,
        val convergenceMetric: Float,
        val privacyBudgetUsed: Float,
        val privacyBudgetRemaining: Float
    )

    /**
     * Export model for deployment
     */
    fun exportModel(): ExportedModel {
        return ExportedModel(
            weights = globalModel.weights.copyOf(),
            version = globalModel.version,
            trainingRounds = roundHistory.size,
            participants = participants.values.count { it.roundsParticipated > 0 },
            finalLoss = roundHistory.lastOrNull()?.globalLoss ?: Float.MAX_VALUE,
            exportTime = System.currentTimeMillis()
        )
    }

    data class ExportedModel(
        val weights: FloatArray,
        val version: Int,
        val trainingRounds: Int,
        val participants: Int,
        val finalLoss: Float,
        val exportTime: Long
    )

    /**
     * Reset training state
     */
    suspend fun reset() = mutex.withLock {
        participants.clear()
        roundHistory.clear()
        globalModel.weights = FloatArray(0)
        globalModel.version = 0
        globalModel.updateHistory.clear()
    }
}
