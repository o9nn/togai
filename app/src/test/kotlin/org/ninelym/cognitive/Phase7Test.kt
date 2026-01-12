package org.ninelym.cognitive

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.runBlocking
import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import org.ninelym.cognitive.ecan.ECANKernel
import org.ninelym.cognitive.selfhealing.*
import org.ninelym.cognitive.distributed.*
import org.ninelym.cognitive.neurosymbolic.*

/**
 * Phase 7 Unit Tests
 *
 * Comprehensive tests for:
 * 1. Self-Healing Cognitive System
 * 2. Distributed Cognitive Synchronization
 * 3. Hybrid Neural-Symbolic Bridge
 */
class Phase7Test {

    private lateinit var hypergraph: Hypergraph
    private lateinit var cognitiveEngine: CognitiveEngine
    private lateinit var ecanKernel: ECANKernel

    @Before
    fun setup() {
        hypergraph = Hypergraph()
        cognitiveEngine = CognitiveEngine(hypergraph)
        ecanKernel = ECANKernel(hypergraph)
    }

    // ==================== Self-Healing System Tests ====================

    @Test
    fun `test SelfHealingConfig defaults`() {
        val config = SelfHealingConfig()

        assertEquals(5000, config.attentionMonitorIntervalMs)
        assertEquals(10000, config.tensorMonitorIntervalMs)
        assertTrue(config.autoRecoveryEnabled)
        assertEquals(AnomalySeverity.MEDIUM, config.autoRecoveryThreshold)
    }

    @Test
    fun `test SelfHealingConfig custom values`() {
        val config = SelfHealingConfig(
            attentionMonitorIntervalMs = 1000,
            autoRecoveryEnabled = false,
            memoryPressureThreshold = 0.9f
        )

        assertEquals(1000, config.attentionMonitorIntervalMs)
        assertFalse(config.autoRecoveryEnabled)
        assertEquals(0.9f, config.memoryPressureThreshold, 0.001f)
    }

    @Test
    fun `test AnomalyEvent creation`() {
        val event = AnomalyEvent(
            id = "test-anomaly-1",
            type = AnomalyType.ATTENTION_DRIFT,
            severity = AnomalySeverity.MEDIUM,
            timestamp = System.currentTimeMillis(),
            description = "Test anomaly",
            affectedComponent = "attention",
            metrics = mapOf("testMetric" to 0.5f)
        )

        assertEquals("test-anomaly-1", event.id)
        assertEquals(AnomalyType.ATTENTION_DRIFT, event.type)
        assertEquals(AnomalySeverity.MEDIUM, event.severity)
        assertFalse(event.resolved)
    }

    @Test
    fun `test AnomalySeverity ordering`() {
        assertTrue(AnomalySeverity.CRITICAL > AnomalySeverity.HIGH)
        assertTrue(AnomalySeverity.HIGH > AnomalySeverity.MEDIUM)
        assertTrue(AnomalySeverity.MEDIUM > AnomalySeverity.LOW)
    }

    @Test
    fun `test HealthState enum values`() {
        val states = HealthState.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(HealthState.HEALTHY))
        assertTrue(states.contains(HealthState.DEGRADED))
        assertTrue(states.contains(HealthState.WARNING))
        assertTrue(states.contains(HealthState.CRITICAL))
    }

    @Test
    fun `test RecoveryResult creation`() {
        val result = RecoveryResult(
            success = true,
            message = "Recovery successful",
            actionTaken = "attention-drift"
        )

        assertTrue(result.success)
        assertEquals("Recovery successful", result.message)
        assertEquals("attention-drift", result.actionTaken)
    }

    @Test
    fun `test HealthAlert creation`() {
        val alert = HealthAlert(
            severity = AlertSeverity.WARNING,
            message = "Test warning",
            timestamp = 1234567890L,
            recommendedAction = "Monitor closely"
        )

        assertEquals(AlertSeverity.WARNING, alert.severity)
        assertEquals("Test warning", alert.message)
        assertEquals(1234567890L, alert.timestamp)
    }

    @Test
    fun `test StatisticalBaseline creation`() {
        val baseline = StatisticalBaseline(
            mean = 0.5f,
            stdDev = 0.1f,
            min = 0.0f,
            max = 1.0f,
            sampleSize = 100
        )

        assertEquals(0.5f, baseline.mean, 0.001f)
        assertEquals(0.1f, baseline.stdDev, 0.001f)
        assertEquals(100, baseline.sampleSize)
    }

    // ==================== Distributed Sync Tests ====================

    @Test
    fun `test SyncConfig defaults`() {
        val config = SyncConfig()

        assertEquals(5000, config.syncIntervalMs)
        assertEquals(100, config.maxBatchSize)
        assertTrue(config.compressionEnabled)
        assertTrue(config.encryptionEnabled)
    }

    @Test
    fun `test VectorClock initialization`() {
        val clock = VectorClock("node-1")

        assertEquals(0L, clock.getTime("node-1"))
        assertEquals(0L, clock.getTime("unknown-node"))
    }

    @Test
    fun `test VectorClock increment`() {
        val clock = VectorClock("node-1")

        clock.increment()
        assertEquals(1L, clock.getTime("node-1"))

        clock.increment()
        assertEquals(2L, clock.getTime("node-1"))
    }

    @Test
    fun `test VectorClock merge`() {
        val clock1 = VectorClock("node-1")
        val clock2 = VectorClock("node-2")

        clock1.increment()
        clock1.increment()
        clock2.increment()

        clock1.merge(clock2)

        assertEquals(2L, clock1.getTime("node-1"))
        assertEquals(1L, clock1.getTime("node-2"))
    }

    @Test
    fun `test VectorClock happensBefore`() {
        val clock1 = VectorClock("node-1")
        val clock2 = VectorClock("node-1")

        clock1.increment()
        clock2.increment()
        clock2.increment()

        assertTrue(clock1.happensBefore(clock2))
        assertFalse(clock2.happensBefore(clock1))
    }

    @Test
    fun `test VectorClock copy`() {
        val clock1 = VectorClock("node-1")
        clock1.increment()

        val clock2 = clock1.copy()
        clock1.increment()

        assertEquals(2L, clock1.getTime("node-1"))
        assertEquals(1L, clock2.getTime("node-1"))
    }

    @Test
    fun `test VectorClock hash consistency`() {
        val clock1 = VectorClock("node-1")
        val clock2 = VectorClock("node-1")

        clock1.increment()
        clock2.increment()

        assertEquals(clock1.hash(), clock2.hash())
    }

    @Test
    fun `test LWWElementSet add`() {
        val set = LWWElementSet<TestCRDTEntry>()

        val entry = TestCRDTEntry("item-1", 1000L, "node-1")
        assertTrue(set.add(entry, 1000L))
        assertNotNull(set.lookup("item-1"))
    }

    @Test
    fun `test LWWElementSet last writer wins`() {
        val set = LWWElementSet<TestCRDTEntry>()

        val entry1 = TestCRDTEntry("item-1", 1000L, "node-1")
        val entry2 = TestCRDTEntry("item-1", 2000L, "node-2")

        set.add(entry1, 1000L)
        set.add(entry2, 2000L)

        val result = set.lookup("item-1")
        assertNotNull(result)
        assertEquals("node-2", result?.nodeId)
    }

    @Test
    fun `test LWWElementSet remove after add`() {
        val set = LWWElementSet<TestCRDTEntry>()

        val entry = TestCRDTEntry("item-1", 1000L, "node-1")
        set.add(entry, 1000L)

        val removeEntry = TestCRDTEntry("item-1", 2000L, "node-1")
        assertTrue(set.remove(removeEntry, 2000L))
        assertNull(set.lookup("item-1"))
    }

    @Test
    fun `test GCounterMap increment`() {
        val counter = GCounterMap<String>()

        counter.increment("key-1", "node-1", 5.0f)
        assertEquals(5.0f, counter.value("key-1"), 0.001f)

        counter.increment("key-1", "node-1", 3.0f)
        assertEquals(8.0f, counter.value("key-1"), 0.001f)
    }

    @Test
    fun `test GCounterMap multiple nodes`() {
        val counter = GCounterMap<String>()

        counter.increment("key-1", "node-1", 5.0f)
        counter.increment("key-1", "node-2", 3.0f)

        assertEquals(8.0f, counter.value("key-1"), 0.001f)
    }

    @Test
    fun `test GCounterMap merge`() {
        val counter = GCounterMap<String>()

        counter.increment("key-1", "node-1", 5.0f)
        counter.merge("key-1", "node-1", 10.0f)

        // Merge should take maximum
        assertEquals(10.0f, counter.value("key-1"), 0.001f)
    }

    @Test
    fun `test SyncData creation`() {
        val clock = VectorClock("node-1")
        val data = SyncData(
            sourceNodeId = "node-1",
            atomOperations = emptyList(),
            attentionUpdates = emptyList(),
            vectorClock = clock,
            timestamp = System.currentTimeMillis(),
            isDelta = true
        )

        assertEquals("node-1", data.sourceNodeId)
        assertTrue(data.isDelta)
        assertTrue(data.atomOperations.isEmpty())
    }

    @Test
    fun `test SyncStatistics snapshot`() {
        val stats = SyncStatistics()

        stats.localOperations.incrementAndGet()
        stats.syncCycles.addAndGet(5)

        val snapshot = stats.snapshot()

        assertEquals(1L, snapshot.localOperations)
        assertEquals(5L, snapshot.syncCycles)
    }

    // ==================== Neural-Symbolic Bridge Tests ====================

    @Test
    fun `test BridgeConfig defaults`() {
        val config = BridgeConfig()

        assertEquals(64, config.embeddingDimension)
        assertEquals(100, config.embeddingIterations)
        assertEquals(0.01f, config.learningRate, 0.001f)
        assertEquals(0.6f, config.neuralWeight, 0.001f)
        assertEquals(0.4f, config.symbolicWeight, 0.001f)
    }

    @Test
    fun `test LogicRule creation`() {
        val rule = LogicRule(
            id = "rule-1",
            premises = listOf("A", "B"),
            conclusion = "C",
            initialWeight = 0.5f
        )

        assertEquals("rule-1", rule.id)
        assertEquals(2, rule.premises.size)
        assertEquals("C", rule.conclusion)
        assertEquals(0.5f, rule.initialWeight, 0.001f)
    }

    @Test
    fun `test InferenceQuery creation`() {
        val query = InferenceQuery(
            knownFacts = listOf("fact-1", "fact-2"),
            goal = "goal-1"
        )

        assertEquals(2, query.knownFacts.size)
        assertEquals("goal-1", query.goal)
    }

    @Test
    fun `test InferenceResult creation`() {
        val result = InferenceResult(
            goal = "goal-1",
            achieved = true,
            confidence = 0.85f,
            path = emptyList()
        )

        assertEquals("goal-1", result.goal)
        assertTrue(result.achieved)
        assertEquals(0.85f, result.confidence, 0.001f)
    }

    @Test
    fun `test ConceptScore ordering`() {
        val scores = listOf(
            ConceptScore("concept-1", 0.7f, 0.6f, 0.65f),
            ConceptScore("concept-2", 0.9f, 0.8f, 0.85f),
            ConceptScore("concept-3", 0.5f, 0.4f, 0.45f)
        )

        val sorted = scores.sortedByDescending { it.combinedScore }

        assertEquals("concept-2", sorted[0].conceptName)
        assertEquals("concept-1", sorted[1].conceptName)
        assertEquals("concept-3", sorted[2].conceptName)
    }

    @Test
    fun `test ClassificationResult creation`() {
        val result = ClassificationResult(
            example = listOf("a", "b"),
            topConcepts = emptyList(),
            primaryConcept = "concept-1",
            confidence = 0.9f
        )

        assertEquals(2, result.example.size)
        assertEquals("concept-1", result.primaryConcept)
        assertEquals(0.9f, result.confidence, 0.001f)
    }

    @Test
    fun `test Explanation creation`() {
        val explanation = Explanation(
            goal = "goal-1",
            achieved = true,
            steps = listOf(
                ExplanationStep(1, "Rule 1", 0.9f, "Applied rule 1")
            ),
            overallConfidence = 0.9f,
            summary = "Test summary"
        )

        assertEquals("goal-1", explanation.goal)
        assertTrue(explanation.achieved)
        assertEquals(1, explanation.steps.size)
    }

    @Test
    fun `test AggregationType enum`() {
        val types = AggregationType.values()

        assertEquals(3, types.size)
        assertTrue(types.contains(AggregationType.MEAN))
        assertTrue(types.contains(AggregationType.MAX))
        assertTrue(types.contains(AggregationType.ATTENTION_WEIGHTED))
    }

    @Test
    fun `test SymbolMatch creation`() {
        val atom = Atom("test-atom", AtomType.CONCEPT, "test", 0.8f)
        val match = SymbolMatch(
            symbolId = "test-atom",
            atom = atom,
            similarity = 0.95f,
            confidence = 0.9f
        )

        assertEquals("test-atom", match.symbolId)
        assertEquals(0.95f, match.similarity, 0.001f)
        assertEquals(0.9f, match.confidence, 0.001f)
    }

    @Test
    fun `test InducedRule creation`() {
        val rule = InducedRule(
            id = "induced-1",
            premises = listOf("A", "B"),
            conclusion = "C",
            confidence = 0.8f,
            support = 5
        )

        assertEquals("induced-1", rule.id)
        assertEquals(2, rule.premises.size)
        assertEquals(5, rule.support)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `test CRDTOperation enum`() {
        val ops = CRDTOperation.values()

        assertEquals(3, ops.size)
        assertTrue(ops.contains(CRDTOperation.ADD))
        assertTrue(ops.contains(CRDTOperation.REMOVE))
        assertTrue(ops.contains(CRDTOperation.UPDATE))
    }

    @Test
    fun `test ConnectionStatus enum`() {
        val statuses = ConnectionStatus.values()

        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(ConnectionStatus.CONNECTING))
        assertTrue(statuses.contains(ConnectionStatus.CONNECTED))
        assertTrue(statuses.contains(ConnectionStatus.DISCONNECTED))
        assertTrue(statuses.contains(ConnectionStatus.ERROR))
    }

    @Test
    fun `test RecoveryType enum`() {
        val types = RecoveryType.values()

        assertEquals(5, types.size)
        assertTrue(types.contains(RecoveryType.ATTENTION_RESET))
        assertTrue(types.contains(RecoveryType.TENSOR_REPAIR))
        assertTrue(types.contains(RecoveryType.HYPERGRAPH_CLEANUP))
    }

    @Test
    fun `test AttentionUpdate creation`() {
        val update = AttentionUpdate(
            atomId = "atom-1",
            nodeId = "node-1",
            value = 0.75f
        )

        assertEquals("atom-1", update.atomId)
        assertEquals("node-1", update.nodeId)
        assertEquals(0.75f, update.value, 0.001f)
    }

    @Test
    fun `test PeerConnection creation`() {
        val connection = PeerConnection(
            peerId = "peer-1",
            endpoint = "ws://localhost:8080",
            connectedAt = System.currentTimeMillis(),
            lastSyncAt = 0L,
            status = ConnectionStatus.CONNECTING
        )

        assertEquals("peer-1", connection.peerId)
        assertEquals(ConnectionStatus.CONNECTING, connection.status)
        assertNull(connection.vectorClockSnapshot)
    }

    @Test
    fun `test MergeResult creation`() {
        val result = MergeResult(
            success = true,
            mergedCount = 5,
            message = "Merged 5 operations"
        )

        assertTrue(result.success)
        assertEquals(5, result.mergedCount)
    }

    @Test
    fun `test ConvergenceStatus creation`() {
        val clock1 = VectorClock("node-1")
        val clock2 = VectorClock("node-2")

        val status = ConvergenceStatus(
            peerId = "peer-1",
            isConverged = true,
            localClock = clock1,
            peerClock = clock2,
            pendingDeltaCount = 0,
            lastSyncAt = System.currentTimeMillis()
        )

        assertEquals("peer-1", status.peerId)
        assertTrue(status.isConverged)
        assertEquals(0, status.pendingDeltaCount)
    }

    // ==================== Negative Tests ====================

    @Test
    fun `test GCounterMap requires positive delta`() {
        val counter = GCounterMap<String>()

        try {
            counter.increment("key-1", "node-1", -5.0f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("positive") == true)
        }
    }

    @Test
    fun `test LWWElementSet remove before add fails`() {
        val set = LWWElementSet<TestCRDTEntry>()

        val entry = TestCRDTEntry("item-1", 1000L, "node-1")
        assertFalse(set.remove(entry, 1000L))
    }
}

// Helper test class for CRDT tests
data class TestCRDTEntry(
    override val id: String,
    override val timestamp: Long,
    override val nodeId: String
) : CRDTEntry
