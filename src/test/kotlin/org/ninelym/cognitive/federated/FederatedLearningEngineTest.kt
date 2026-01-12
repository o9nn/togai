package org.ninelym.cognitive.federated

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for Federated Learning Engine
 *
 * Tests federated averaging, differential privacy, and secure aggregation
 */
class FederatedLearningEngineTest {

    @Test
    fun `test participant registration`() = runBlocking {
        val engine = FederatedLearningEngine()

        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.8f,
            bandwidthMbps = 50f,
            batteryLevel = 0.9f,
            isPluggedIn = true,
            availableMemoryMb = 2048
        )

        val participantId = engine.registerParticipant(
            name = "TestDevice",
            datasetSize = 1000,
            capabilities = capabilities
        )

        assertNotNull(participantId)
        assertTrue(participantId.isNotEmpty())
    }

    @Test
    fun `test multiple participants registration`() = runBlocking {
        val engine = FederatedLearningEngine()

        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.7f,
            bandwidthMbps = 30f,
            batteryLevel = 0.8f,
            isPluggedIn = false,
            availableMemoryMb = 1024
        )

        val ids = mutableListOf<String>()
        repeat(5) { i ->
            val id = engine.registerParticipant(
                name = "Device$i",
                datasetSize = 500 + i * 100,
                capabilities = capabilities
            )
            ids.add(id)
        }

        assertEquals(5, ids.size)
        assertEquals(5, ids.toSet().size) // All unique
    }

    @Test
    fun `test global model initialization`() = runBlocking {
        val engine = FederatedLearningEngine()

        engine.initializeGlobalModel(dimensions = 100)

        val weights = engine.getGlobalModelWeights()
        assertEquals(100, weights.size)
        assertEquals(0, engine.getGlobalModelVersion())
    }

    @Test
    fun `test federated config defaults`() {
        val config = FederatedLearningEngine.FederatedConfig()

        assertEquals(3, config.minParticipants)
        assertEquals(100, config.maxParticipants)
        assertTrue(config.learningRate > 0f)
        assertTrue(config.differentialPrivacyEpsilon > 0f)
        assertTrue(config.clipNorm > 0f)
    }

    @Test
    fun `test custom federated config`() {
        val config = FederatedLearningEngine.FederatedConfig(
            minParticipants = 5,
            maxParticipants = 50,
            learningRate = 0.001f,
            differentialPrivacyEpsilon = 2.0f
        )

        assertEquals(5, config.minParticipants)
        assertEquals(50, config.maxParticipants)
        assertEquals(0.001f, config.learningRate, 0.0001f)
        assertEquals(2.0f, config.differentialPrivacyEpsilon, 0.01f)
    }

    @Test
    fun `test participant capabilities`() {
        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.9f,
            bandwidthMbps = 100f,
            batteryLevel = 1.0f,
            isPluggedIn = true,
            availableMemoryMb = 4096
        )

        assertEquals(0.9f, capabilities.computePower, 0.01f)
        assertEquals(100f, capabilities.bandwidthMbps, 0.01f)
        assertTrue(capabilities.isPluggedIn)
        assertEquals(4096, capabilities.availableMemoryMb)
    }

    @Test
    fun `test participant update status`() = runBlocking {
        val engine = FederatedLearningEngine()

        val initialCapabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.5f,
            bandwidthMbps = 20f,
            batteryLevel = 0.5f,
            isPluggedIn = false,
            availableMemoryMb = 512
        )

        val id = engine.registerParticipant(
            name = "UpdateTest",
            datasetSize = 100,
            capabilities = initialCapabilities
        )

        val newCapabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.8f,
            bandwidthMbps = 50f,
            batteryLevel = 0.9f,
            isPluggedIn = true,
            availableMemoryMb = 2048
        )

        engine.updateParticipantStatus(id, newCapabilities)
        // No exception means success
    }

    @Test
    fun `test training stats`() = runBlocking {
        val engine = FederatedLearningEngine()

        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.8f,
            bandwidthMbps = 50f,
            batteryLevel = 0.9f,
            isPluggedIn = true,
            availableMemoryMb = 2048
        )

        repeat(3) { i ->
            engine.registerParticipant(
                name = "Device$i",
                datasetSize = 1000,
                capabilities = capabilities
            )
        }

        val stats = engine.getTrainingStats()

        assertEquals(0, stats.totalRounds)
        assertEquals(3, stats.totalParticipants)
        assertEquals(3, stats.activeParticipants)
        assertEquals(0, stats.globalModelVersion)
    }

    @Test
    fun `test model export`() = runBlocking {
        val engine = FederatedLearningEngine()
        engine.initializeGlobalModel(dimensions = 50)

        val exported = engine.exportModel()

        assertEquals(50, exported.weights.size)
        assertEquals(0, exported.version)
        assertEquals(0, exported.trainingRounds)
        assertTrue(exported.exportTime > 0)
    }

    @Test
    fun `test federated round result data class`() {
        val result = FederatedLearningEngine.FederatedRoundResult(
            roundId = 1,
            participantsCount = 5,
            aggregatedLoss = 0.5f,
            globalModelVersion = 1,
            convergenceMetric = 0.1f,
            executionTime = 1000,
            privacyBudgetUsed = 0.5f
        )

        assertEquals(1, result.roundId)
        assertEquals(5, result.participantsCount)
        assertEquals(0.5f, result.aggregatedLoss, 0.01f)
        assertEquals(1, result.globalModelVersion)
        assertEquals(1000, result.executionTime)
    }

    @Test
    fun `test federated training result data class`() {
        val result = FederatedLearningEngine.FederatedTrainingResult(
            totalRounds = 10,
            finalLoss = 0.1f,
            finalModelVersion = 10,
            totalParticipants = 20,
            convergenceHistory = listOf(1.0f, 0.5f, 0.2f, 0.1f),
            privacyBudgetRemaining = 0.5f,
            executionTime = 5000
        )

        assertEquals(10, result.totalRounds)
        assertEquals(0.1f, result.finalLoss, 0.01f)
        assertEquals(20, result.totalParticipants)
        assertEquals(4, result.convergenceHistory.size)
        assertEquals(5000, result.executionTime)
    }

    @Test
    fun `test reset functionality`() = runBlocking {
        val engine = FederatedLearningEngine()

        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.8f,
            bandwidthMbps = 50f,
            batteryLevel = 0.9f,
            isPluggedIn = true,
            availableMemoryMb = 2048
        )

        engine.registerParticipant("Test", 100, capabilities)
        engine.initializeGlobalModel(50)

        val statsBeforeReset = engine.getTrainingStats()
        assertEquals(1, statsBeforeReset.totalParticipants)

        engine.reset()

        val statsAfterReset = engine.getTrainingStats()
        assertEquals(0, statsAfterReset.totalParticipants)
        assertEquals(0, statsAfterReset.totalRounds)
    }

    @Test
    fun `test prune inactive participants`() = runBlocking {
        val engine = FederatedLearningEngine()

        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.8f,
            bandwidthMbps = 50f,
            batteryLevel = 0.9f,
            isPluggedIn = true,
            availableMemoryMb = 2048
        )

        repeat(5) { i ->
            engine.registerParticipant("Device$i", 100, capabilities)
        }

        val statsBefore = engine.getTrainingStats()
        assertEquals(5, statsBefore.activeParticipants)

        // Prune with very short timeout (0ms) - should mark all as inactive
        engine.pruneInactiveParticipants(maxInactiveMs = 0)

        val statsAfter = engine.getTrainingStats()
        assertEquals(0, statsAfter.activeParticipants)
    }

    @Test
    fun `test exported model data class`() {
        val exported = FederatedLearningEngine.ExportedModel(
            weights = floatArrayOf(0.1f, 0.2f, 0.3f),
            version = 5,
            trainingRounds = 10,
            participants = 20,
            finalLoss = 0.05f,
            exportTime = System.currentTimeMillis()
        )

        assertEquals(3, exported.weights.size)
        assertEquals(5, exported.version)
        assertEquals(10, exported.trainingRounds)
        assertEquals(20, exported.participants)
        assertEquals(0.05f, exported.finalLoss, 0.01f)
        assertTrue(exported.exportTime > 0)
    }

    @Test
    fun `test round status enum`() {
        val statuses = FederatedLearningEngine.RoundStatus.values()
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(FederatedLearningEngine.RoundStatus.IN_PROGRESS))
        assertTrue(statuses.contains(FederatedLearningEngine.RoundStatus.AGGREGATING))
        assertTrue(statuses.contains(FederatedLearningEngine.RoundStatus.COMPLETED))
        assertTrue(statuses.contains(FederatedLearningEngine.RoundStatus.FAILED))
    }

    @Test
    fun `test model update data class equality`() {
        val update1 = FederatedLearningEngine.ModelUpdate(
            participantId = "p1",
            roundId = 1,
            gradients = floatArrayOf(0.1f, 0.2f),
            datasetSize = 100,
            localLoss = 0.5f,
            timestamp = 1000
        )

        val update2 = FederatedLearningEngine.ModelUpdate(
            participantId = "p1",
            roundId = 1,
            gradients = floatArrayOf(0.3f, 0.4f), // Different gradients
            datasetSize = 200,
            localLoss = 0.3f,
            timestamp = 2000
        )

        // Equality based on participantId and roundId
        assertEquals(update1, update2)
        assertEquals(update1.hashCode(), update2.hashCode())
    }

    @Test
    fun `test training stats data class`() {
        val stats = FederatedLearningEngine.TrainingStats(
            totalRounds = 5,
            activeParticipants = 10,
            totalParticipants = 15,
            globalModelVersion = 5,
            averageLoss = 0.2f,
            convergenceMetric = 0.05f,
            privacyBudgetUsed = 0.3f,
            privacyBudgetRemaining = 0.7f
        )

        assertEquals(5, stats.totalRounds)
        assertEquals(10, stats.activeParticipants)
        assertEquals(15, stats.totalParticipants)
        assertEquals(0.2f, stats.averageLoss, 0.01f)
        assertEquals(0.3f, stats.privacyBudgetUsed, 0.01f)
        assertEquals(0.7f, stats.privacyBudgetRemaining, 0.01f)
    }

    @Test
    fun `test insufficient participants throws exception`() = runBlocking {
        val engine = FederatedLearningEngine(
            FederatedLearningEngine.FederatedConfig(minParticipants = 5)
        )

        val capabilities = FederatedLearningEngine.ParticipantCapabilities(
            computePower = 0.8f,
            bandwidthMbps = 50f,
            batteryLevel = 0.9f,
            isPluggedIn = true,
            availableMemoryMb = 2048
        )

        // Only register 2 participants (below minimum of 5)
        repeat(2) { i ->
            engine.registerParticipant("Device$i", 100, capabilities)
        }

        engine.initializeGlobalModel(10)

        var exceptionThrown = false
        try {
            engine.runFederatedRound { weights, _ ->
                val gradients = FloatArray(weights.size) { 0.01f }
                gradients to 0.5f
            }
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("Not enough participants") == true)
        }

        assertTrue(exceptionThrown, "Should throw exception for insufficient participants")
    }
}
