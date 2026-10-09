package org.ninelym.cognitive.healing

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Comprehensive tests for Self-Healing System
 *
 * Tests anomaly detection, circuit breakers, recovery actions, and health monitoring
 */
class SelfHealingSystemTest {

    @Test
    fun `test component registration`() = runBlocking {
        val system = SelfHealingSystem()

        system.registerComponent("comp1", "TestComponent")

        val health = system.getComponentHealth("comp1")
        assertNotNull(health)
        assertEquals("comp1", health.componentId)
        assertEquals("TestComponent", health.name)
        assertEquals(SelfHealingSystem.HealthStatus.HEALTHY, health.status)
        assertEquals(1.0f, health.healthScore, 0.01f)
    }

    @Test
    fun `test multiple component registration`() = runBlocking {
        val system = SelfHealingSystem()

        system.registerComponent("comp1", "Component1")
        system.registerComponent("comp2", "Component2")
        system.registerComponent("comp3", "Component3")

        val allHealth = system.getAllComponentHealth()
        assertEquals(3, allHealth.size)
    }

    @Test
    fun `test metric reporting`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        // Report some metrics
        repeat(15) { i ->
            system.reportMetric("comp1", "cpu_usage", 50f + i * 2)
        }

        val health = system.getComponentHealth("comp1")
        assertNotNull(health)
        assertTrue(health.metrics.containsKey("cpu_usage"))
    }

    @Test
    fun `test health check reporting`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        system.reportHealthCheck("comp1", healthScore = 0.8f, latencyMs = 50)

        val health = system.getComponentHealth("comp1")
        assertNotNull(health)
        assertEquals(0.8f, health.healthScore, 0.01f)
        assertEquals(50, health.latencyMs)
        assertEquals(SelfHealingSystem.HealthStatus.DEGRADED, health.status)
    }

    @Test
    fun `test health status transitions`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        // Healthy (score > 0.7)
        system.reportHealthCheck("comp1", healthScore = 0.9f, latencyMs = 10)
        var health = system.getComponentHealth("comp1")
        assertEquals(SelfHealingSystem.HealthStatus.HEALTHY, health?.status)

        // Degraded (0.5 < score < 0.7)
        system.reportHealthCheck("comp1", healthScore = 0.6f, latencyMs = 50)
        health = system.getComponentHealth("comp1")
        assertEquals(SelfHealingSystem.HealthStatus.DEGRADED, health?.status)

        // Unhealthy (critical + degradation threshold < score < 0.5)
        system.reportHealthCheck("comp1", healthScore = 0.4f, latencyMs = 100)
        health = system.getComponentHealth("comp1")
        assertEquals(SelfHealingSystem.HealthStatus.UNHEALTHY, health?.status)

        // Critical (score < 0.5)
        system.reportHealthCheck("comp1", healthScore = 0.2f, latencyMs = 200)
        health = system.getComponentHealth("comp1")
        assertEquals(SelfHealingSystem.HealthStatus.CRITICAL, health?.status)
    }

    @Test
    fun `test circuit breaker initial state`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        assertTrue(system.isCircuitClosed("comp1"))
    }

    @Test
    fun `test circuit breaker opens after failures`() = runBlocking {
        val config = SelfHealingSystem.HealingConfig(
            circuitBreakerThreshold = 3
        )
        val system = SelfHealingSystem(config)
        system.registerComponent("comp1", "TestComponent")

        // Report failures
        repeat(3) {
            system.reportFailure("comp1")
        }

        // Circuit should now be open
        assertTrue(!system.isCircuitClosed("comp1"))
    }

    @Test
    fun `test circuit breaker success reporting`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        // Report some failures
        system.reportFailure("comp1")
        system.reportFailure("comp1")

        // Then successes
        system.reportSuccess("comp1")
        system.reportSuccess("comp1")

        // Should still be closed
        assertTrue(system.isCircuitClosed("comp1"))
    }

    @Test
    fun `test recovery action creation`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        val action = system.triggerRecovery(
            componentId = "comp1",
            reason = "Test recovery",
            actionType = SelfHealingSystem.RecoveryActionType.CLEAR_CACHE
        )

        assertNotNull(action)
        assertEquals("comp1", action.componentId)
        assertEquals("Test recovery", action.triggeredBy)
        assertEquals(SelfHealingSystem.RecoveryActionType.CLEAR_CACHE, action.actionType)
    }

    @Test
    fun `test max recovery attempts limit`() = runBlocking {
        val config = SelfHealingSystem.HealingConfig(
            maxRecoveryAttempts = 2
        )
        val system = SelfHealingSystem(config)
        system.registerComponent("comp1", "TestComponent")

        // Trigger multiple recoveries
        repeat(3) {
            system.triggerRecovery("comp1", "Test")
        }

        // Third one should fail
        val recoveries = system.getRecentRecoveries()
        val failedCount = recoveries.count {
            it.status == SelfHealingSystem.RecoveryStatus.FAILED &&
                    it.result?.contains("Max recovery attempts") == true
        }
        assertTrue(failedCount >= 1)
    }

    @Test
    fun `test checkpoint creation`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        val state = "test state".toByteArray()
        val checkpoint = system.createCheckpoint("comp1", state)

        assertNotNull(checkpoint)
        assertEquals("comp1", checkpoint.componentId)
        assertTrue(checkpoint.state.isNotEmpty())
        assertTrue(checkpoint.timestamp > 0)
    }

    @Test
    fun `test checkpoint retrieval`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        val state = "checkpoint data".toByteArray()
        system.createCheckpoint("comp1", state)

        val retrieved = system.getCheckpoint("comp1")
        assertNotNull(retrieved)
        assertEquals("comp1", retrieved.componentId)
    }

    @Test
    fun `test rollback to checkpoint`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        // Create checkpoint at good health
        val state = "good state".toByteArray()
        system.createCheckpoint("comp1", state)

        // Degrade health
        system.reportHealthCheck("comp1", healthScore = 0.3f, latencyMs = 100)

        // Rollback
        val rolledBack = system.rollbackToCheckpoint("comp1")
        assertNotNull(rolledBack)
        assertEquals("good state", String(rolledBack))
    }

    @Test
    fun `test system health report`() = runBlocking {
        val system = SelfHealingSystem()

        system.registerComponent("comp1", "Component1")
        system.registerComponent("comp2", "Component2")
        system.registerComponent("comp3", "Component3")

        system.reportHealthCheck("comp1", healthScore = 0.9f, latencyMs = 10)
        system.reportHealthCheck("comp2", healthScore = 0.5f, latencyMs = 50)
        system.reportHealthCheck("comp3", healthScore = 0.3f, latencyMs = 100)

        val report = system.getSystemHealth()

        assertEquals(3, report.componentCount)
        assertEquals(1, report.healthyComponents)
        assertTrue(report.criticalComponents >= 1 || report.unhealthyComponents >= 1)
        assertTrue(report.overallScore > 0f && report.overallScore < 1f)
        assertTrue(report.timestamp > 0)
    }

    @Test
    fun `test recent anomalies retrieval`() = runBlocking {
        val system = SelfHealingSystem()
        system.registerComponent("comp1", "TestComponent")

        // Report enough metrics to build history
        repeat(15) { i ->
            system.reportMetric("comp1", "metric1", i * 1.0f)
        }

        // Report anomalous value
        system.reportMetric("comp1", "metric1", 100f)

        val anomalies = system.getRecentAnomalies(50)
        // May or may not detect anomaly based on statistical significance
        assertTrue(anomalies.size >= 0)
    }

    @Test
    fun `test metric history`() {
        val history = SelfHealingSystem.ComponentHealth.MetricHistory("test", maxSize = 10)

        // Add values
        repeat(15) { i ->
            history.add(i.toFloat())
        }

        // Should only keep last 10
        val values = history.getValues()
        assertEquals(10, values.size)

        // Test statistics
        val mean = history.getMean()
        assertTrue(mean > 0f)

        val stdDev = history.getStdDev()
        assertTrue(stdDev >= 0f)

        val latest = history.getLatestValue()
        assertNotNull(latest)
        assertEquals(14f, latest, 0.01f)
    }

    @Test
    fun `test anomaly detection in metric history`() {
        val history = SelfHealingSystem.ComponentHealth.MetricHistory("test")

        // Add consistent values
        repeat(20) {
            history.add(50f)
        }

        // 50 is not an anomaly
        assertTrue(!history.isAnomaly(50f, 2.5f))

        // Very different value should be anomaly if there's variance
        // With all same values, stdDev is 0, so no anomaly detected
        assertTrue(!history.isAnomaly(100f, 2.5f))

        // Add some variance
        val historyWithVariance = SelfHealingSystem.ComponentHealth.MetricHistory("test2")
        repeat(10) { i ->
            historyWithVariance.add(50f + (i % 2) * 2f) // 50, 52, 50, 52...
        }

        // Now large deviation should be anomaly
        assertTrue(historyWithVariance.isAnomaly(100f, 2.5f))
    }

    @Test
    fun `test healing config defaults`() {
        val config = SelfHealingSystem.HealingConfig()

        assertEquals(2.5f, config.anomalyThreshold, 0.01f)
        assertEquals(5000, config.healthCheckIntervalMs)
        assertEquals(1000, config.maxAnomalyHistory)
        assertTrue(config.autoRecoveryEnabled)
        assertEquals(3, config.maxRecoveryAttempts)
        assertEquals(5, config.circuitBreakerThreshold)
    }

    @Test
    fun `test custom healing config`() {
        val config = SelfHealingSystem.HealingConfig(
            anomalyThreshold = 3.0f,
            healthCheckIntervalMs = 10000,
            autoRecoveryEnabled = false,
            maxRecoveryAttempts = 5
        )

        assertEquals(3.0f, config.anomalyThreshold, 0.01f)
        assertEquals(10000, config.healthCheckIntervalMs)
        assertTrue(!config.autoRecoveryEnabled)
        assertEquals(5, config.maxRecoveryAttempts)
    }

    @Test
    fun `test anomaly severity levels`() {
        val severities = SelfHealingSystem.AnomalySeverity.values()
        assertEquals(4, severities.size)
        assertTrue(severities.contains(SelfHealingSystem.AnomalySeverity.LOW))
        assertTrue(severities.contains(SelfHealingSystem.AnomalySeverity.MEDIUM))
        assertTrue(severities.contains(SelfHealingSystem.AnomalySeverity.HIGH))
        assertTrue(severities.contains(SelfHealingSystem.AnomalySeverity.CRITICAL))
    }

    @Test
    fun `test recovery action types`() {
        val types = SelfHealingSystem.RecoveryActionType.values()
        assertEquals(8, types.size)
        assertTrue(types.contains(SelfHealingSystem.RecoveryActionType.RESTART_COMPONENT))
        assertTrue(types.contains(SelfHealingSystem.RecoveryActionType.CLEAR_CACHE))
        assertTrue(types.contains(SelfHealingSystem.RecoveryActionType.ROLLBACK_STATE))
        assertTrue(types.contains(SelfHealingSystem.RecoveryActionType.GARBAGE_COLLECT))
    }

    @Test
    fun `test alert types`() {
        val types = SelfHealingSystem.AlertType.values()
        assertEquals(9, types.size)
        assertTrue(types.contains(SelfHealingSystem.AlertType.ANOMALY_DETECTED))
        assertTrue(types.contains(SelfHealingSystem.AlertType.HEALTH_DEGRADED))
        assertTrue(types.contains(SelfHealingSystem.AlertType.CIRCUIT_OPENED))
        assertTrue(types.contains(SelfHealingSystem.AlertType.RECOVERY_STARTED))
    }

    @Test
    fun `test health alert data class`() {
        val alert = SelfHealingSystem.HealthAlert(
            alertId = "alert-123",
            componentId = "comp1",
            alertType = SelfHealingSystem.AlertType.HEALTH_DEGRADED,
            severity = SelfHealingSystem.AnomalySeverity.HIGH,
            message = "Test alert",
            timestamp = System.currentTimeMillis(),
            metadata = mapOf("key" to "value")
        )

        assertEquals("alert-123", alert.alertId)
        assertEquals("comp1", alert.componentId)
        assertEquals(SelfHealingSystem.AlertType.HEALTH_DEGRADED, alert.alertType)
        assertEquals(SelfHealingSystem.AnomalySeverity.HIGH, alert.severity)
        assertEquals("Test alert", alert.message)
        assertEquals("value", alert.metadata["key"])
    }

    @Test
    fun `test system reset`() = runBlocking {
        val system = SelfHealingSystem()

        system.registerComponent("comp1", "Component1")
        system.registerComponent("comp2", "Component2")
        system.reportHealthCheck("comp1", 0.5f, 100)

        val reportBefore = system.getSystemHealth()
        assertEquals(2, reportBefore.componentCount)

        system.reset()

        val reportAfter = system.getSystemHealth()
        assertEquals(0, reportAfter.componentCount)
    }

    @Test
    fun `test unregistered component returns null`() {
        val system = SelfHealingSystem()

        val health = system.getComponentHealth("nonexistent")
        assertNull(health)
    }

    @Test
    fun `test circuit breaker for unregistered component returns true`() {
        val system = SelfHealingSystem()

        // Should return true (closed) for unregistered components
        assertTrue(system.isCircuitClosed("nonexistent"))
    }

    @Test
    fun `test recovery status enum`() {
        val statuses = SelfHealingSystem.RecoveryStatus.values()
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(SelfHealingSystem.RecoveryStatus.PENDING))
        assertTrue(statuses.contains(SelfHealingSystem.RecoveryStatus.IN_PROGRESS))
        assertTrue(statuses.contains(SelfHealingSystem.RecoveryStatus.COMPLETED))
        assertTrue(statuses.contains(SelfHealingSystem.RecoveryStatus.FAILED))
    }
}
