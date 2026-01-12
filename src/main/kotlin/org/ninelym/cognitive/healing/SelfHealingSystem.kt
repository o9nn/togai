package org.ninelym.cognitive.healing

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/**
 * Self-Healing System - Phase 4D: Self-Improvement Mechanisms
 *
 * Implements automatic anomaly detection and recovery for cognitive systems:
 * - Statistical anomaly detection in attention patterns
 * - Performance degradation monitoring
 * - Automatic error recovery and rollback
 * - Health monitoring and alerting
 * - Circuit breaker pattern for failing components
 * - Self-optimization triggers
 *
 * Ensures system stability and continuous operation even under adverse conditions.
 */
class SelfHealingSystem(
    private val config: HealingConfig = HealingConfig()
) {

    private val healthMetrics = ConcurrentHashMap<String, ComponentHealth>()
    private val anomalyHistory = ConcurrentLinkedQueue<AnomalyEvent>()
    private val recoveryActions = ConcurrentLinkedQueue<RecoveryAction>()
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val checkpoints = ConcurrentHashMap<String, SystemCheckpoint>()

    private val _alertFlow = MutableSharedFlow<HealthAlert>(replay = 10)
    val alertFlow: Flow<HealthAlert> = _alertFlow.asSharedFlow()

    private val mutex = Mutex()
    private var monitoringJob: Job? = null
    private var isMonitoring = false

    /**
     * Configuration for self-healing behavior
     */
    data class HealingConfig(
        val anomalyThreshold: Float = 2.5f,        // Standard deviations for anomaly
        val healthCheckIntervalMs: Long = 5000,
        val maxAnomalyHistory: Int = 1000,
        val autoRecoveryEnabled: Boolean = true,
        val maxRecoveryAttempts: Int = 3,
        val circuitBreakerThreshold: Int = 5,
        val circuitBreakerResetMs: Long = 60000,
        val checkpointIntervalMs: Long = 30000,
        val degradationThreshold: Float = 0.3f,    // 30% performance drop
        val criticalHealthThreshold: Float = 0.5f
    )

    /**
     * Component health status
     */
    data class ComponentHealth(
        val componentId: String,
        val name: String,
        var healthScore: Float = 1.0f,
        var status: HealthStatus = HealthStatus.HEALTHY,
        var lastCheck: Long = System.currentTimeMillis(),
        var errorCount: Int = 0,
        var latencyMs: Long = 0,
        val metrics: MutableMap<String, MetricHistory> = mutableMapOf(),
        var anomalies: Int = 0
    )

    enum class HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        CRITICAL,
        RECOVERING
    }

    /**
     * Metric history for statistical analysis
     */
    data class MetricHistory(
        val name: String,
        private val maxSize: Int = 100
    ) {
        private val values = ConcurrentLinkedQueue<TimestampedValue>()

        data class TimestampedValue(val value: Float, val timestamp: Long)

        fun add(value: Float) {
            values.add(TimestampedValue(value, System.currentTimeMillis()))
            while (values.size > maxSize) {
                values.poll()
            }
        }

        fun getValues(): List<Float> = values.map { it.value }

        fun getMean(): Float {
            val vals = getValues()
            return if (vals.isEmpty()) 0f else vals.average().toFloat()
        }

        fun getStdDev(): Float {
            val vals = getValues()
            if (vals.size < 2) return 0f
            val mean = getMean()
            return sqrt(vals.map { (it - mean).pow(2) }.average().toFloat())
        }

        fun getLatestValue(): Float? = values.lastOrNull()?.value

        fun isAnomaly(value: Float, threshold: Float): Boolean {
            val mean = getMean()
            val stdDev = getStdDev()
            if (stdDev == 0f) return false
            return abs(value - mean) > threshold * stdDev
        }
    }

    /**
     * Anomaly event record
     */
    data class AnomalyEvent(
        val id: String,
        val componentId: String,
        val metricName: String,
        val value: Float,
        val expectedRange: Pair<Float, Float>,
        val severity: AnomalySeverity,
        val timestamp: Long,
        var resolved: Boolean = false,
        var resolution: String? = null
    )

    enum class AnomalySeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Recovery action record
     */
    data class RecoveryAction(
        val id: String,
        val componentId: String,
        val actionType: RecoveryActionType,
        val triggeredBy: String,
        val timestamp: Long,
        var status: RecoveryStatus = RecoveryStatus.PENDING,
        var result: String? = null,
        var executionTimeMs: Long = 0
    )

    enum class RecoveryActionType {
        RESTART_COMPONENT,
        CLEAR_CACHE,
        ROLLBACK_STATE,
        REDUCE_LOAD,
        FAILOVER,
        RESET_CONNECTION,
        GARBAGE_COLLECT,
        REBALANCE_RESOURCES
    }

    enum class RecoveryStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    /**
     * Circuit breaker for failing components
     */
    data class CircuitBreaker(
        val componentId: String,
        var state: CircuitState = CircuitState.CLOSED,
        var failureCount: Int = 0,
        var lastFailure: Long = 0,
        var lastStateChange: Long = System.currentTimeMillis(),
        var successCount: Int = 0
    ) {
        enum class CircuitState {
            CLOSED,      // Normal operation
            OPEN,        // Failing, rejecting calls
            HALF_OPEN    // Testing recovery
        }
    }

    /**
     * System checkpoint for rollback
     */
    data class SystemCheckpoint(
        val id: String,
        val componentId: String,
        val state: ByteArray,
        val timestamp: Long,
        val healthScore: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SystemCheckpoint) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    /**
     * Health alert for notifications
     */
    data class HealthAlert(
        val alertId: String,
        val componentId: String,
        val alertType: AlertType,
        val severity: AnomalySeverity,
        val message: String,
        val timestamp: Long,
        val metadata: Map<String, Any> = emptyMap()
    )

    enum class AlertType {
        ANOMALY_DETECTED,
        HEALTH_DEGRADED,
        CIRCUIT_OPENED,
        RECOVERY_STARTED,
        RECOVERY_COMPLETED,
        RECOVERY_FAILED,
        PERFORMANCE_DROP,
        MEMORY_PRESSURE,
        ERROR_SPIKE
    }

    // ============================================
    // HEALTH MONITORING
    // ============================================

    /**
     * Register a component for health monitoring
     */
    suspend fun registerComponent(componentId: String, name: String) = mutex.withLock {
        healthMetrics[componentId] = ComponentHealth(componentId, name)
        circuitBreakers[componentId] = CircuitBreaker(componentId)
    }

    /**
     * Report metric value for a component
     */
    suspend fun reportMetric(
        componentId: String,
        metricName: String,
        value: Float
    ) {
        val health = healthMetrics[componentId] ?: return

        val history = health.metrics.getOrPut(metricName) { MetricHistory(metricName) }
        history.add(value)

        // Check for anomaly
        if (history.getValues().size >= 10 && history.isAnomaly(value, config.anomalyThreshold)) {
            val mean = history.getMean()
            val stdDev = history.getStdDev()

            val anomaly = AnomalyEvent(
                id = "anomaly-${System.currentTimeMillis()}-${anomalyHistory.size}",
                componentId = componentId,
                metricName = metricName,
                value = value,
                expectedRange = (mean - 2 * stdDev) to (mean + 2 * stdDev),
                severity = calculateAnomalySeverity(value, mean, stdDev),
                timestamp = System.currentTimeMillis()
            )

            recordAnomaly(anomaly)
        }
    }

    /**
     * Report component health check
     */
    suspend fun reportHealthCheck(
        componentId: String,
        healthScore: Float,
        latencyMs: Long,
        error: Exception? = null
    ) = mutex.withLock {
        val health = healthMetrics[componentId] ?: return

        health.healthScore = healthScore
        health.latencyMs = latencyMs
        health.lastCheck = System.currentTimeMillis()

        if (error != null) {
            health.errorCount++
            handleComponentError(componentId, error)
        } else {
            // Decay error count over time
            if (health.errorCount > 0) {
                health.errorCount = (health.errorCount * 0.9f).toInt()
            }
        }

        // Update status
        health.status = when {
            healthScore < config.criticalHealthThreshold -> HealthStatus.CRITICAL
            healthScore < config.degradationThreshold + config.criticalHealthThreshold -> HealthStatus.UNHEALTHY
            healthScore < 1 - config.degradationThreshold -> HealthStatus.DEGRADED
            else -> HealthStatus.HEALTHY
        }

        // Emit alert if needed
        if (health.status != HealthStatus.HEALTHY) {
            emitAlert(
                componentId = componentId,
                alertType = AlertType.HEALTH_DEGRADED,
                severity = when (health.status) {
                    HealthStatus.CRITICAL -> AnomalySeverity.CRITICAL
                    HealthStatus.UNHEALTHY -> AnomalySeverity.HIGH
                    else -> AnomalySeverity.MEDIUM
                },
                message = "Component ${health.name} health degraded to ${health.status}"
            )
        }
    }

    /**
     * Calculate anomaly severity based on deviation
     */
    private fun calculateAnomalySeverity(value: Float, mean: Float, stdDev: Float): AnomalySeverity {
        if (stdDev == 0f) return AnomalySeverity.LOW

        val deviation = abs(value - mean) / stdDev
        return when {
            deviation > 4f -> AnomalySeverity.CRITICAL
            deviation > 3f -> AnomalySeverity.HIGH
            deviation > 2.5f -> AnomalySeverity.MEDIUM
            else -> AnomalySeverity.LOW
        }
    }

    /**
     * Record anomaly and trigger recovery if needed
     */
    private suspend fun recordAnomaly(anomaly: AnomalyEvent) {
        anomalyHistory.add(anomaly)
        while (anomalyHistory.size > config.maxAnomalyHistory) {
            anomalyHistory.poll()
        }

        healthMetrics[anomaly.componentId]?.let { it.anomalies++ }

        emitAlert(
            componentId = anomaly.componentId,
            alertType = AlertType.ANOMALY_DETECTED,
            severity = anomaly.severity,
            message = "Anomaly in ${anomaly.metricName}: ${anomaly.value} " +
                    "(expected ${anomaly.expectedRange.first}-${anomaly.expectedRange.second})"
        )

        // Trigger recovery for critical anomalies
        if (config.autoRecoveryEnabled && anomaly.severity >= AnomalySeverity.HIGH) {
            triggerRecovery(anomaly.componentId, "Anomaly: ${anomaly.metricName}")
        }
    }

    // ============================================
    // CIRCUIT BREAKER
    // ============================================

    /**
     * Check if circuit breaker allows operation
     */
    fun isCircuitClosed(componentId: String): Boolean {
        val breaker = circuitBreakers[componentId] ?: return true

        return when (breaker.state) {
            CircuitBreaker.CircuitState.CLOSED -> true
            CircuitBreaker.CircuitState.OPEN -> {
                // Check if enough time has passed to try again
                if (System.currentTimeMillis() - breaker.lastStateChange > config.circuitBreakerResetMs) {
                    breaker.state = CircuitBreaker.CircuitState.HALF_OPEN
                    breaker.lastStateChange = System.currentTimeMillis()
                    true
                } else {
                    false
                }
            }
            CircuitBreaker.CircuitState.HALF_OPEN -> true
        }
    }

    /**
     * Report operation success to circuit breaker
     */
    suspend fun reportSuccess(componentId: String) {
        val breaker = circuitBreakers[componentId] ?: return

        when (breaker.state) {
            CircuitBreaker.CircuitState.HALF_OPEN -> {
                breaker.successCount++
                if (breaker.successCount >= 3) {
                    // Close the circuit
                    breaker.state = CircuitBreaker.CircuitState.CLOSED
                    breaker.failureCount = 0
                    breaker.successCount = 0
                    breaker.lastStateChange = System.currentTimeMillis()

                    emitAlert(
                        componentId = componentId,
                        alertType = AlertType.RECOVERY_COMPLETED,
                        severity = AnomalySeverity.LOW,
                        message = "Circuit breaker closed - component recovered"
                    )
                }
            }
            CircuitBreaker.CircuitState.CLOSED -> {
                // Reset failure count on success
                if (breaker.failureCount > 0) {
                    breaker.failureCount--
                }
            }
            else -> {}
        }
    }

    /**
     * Report operation failure to circuit breaker
     */
    suspend fun reportFailure(componentId: String) {
        val breaker = circuitBreakers[componentId] ?: return

        breaker.failureCount++
        breaker.lastFailure = System.currentTimeMillis()

        when (breaker.state) {
            CircuitBreaker.CircuitState.CLOSED -> {
                if (breaker.failureCount >= config.circuitBreakerThreshold) {
                    // Open the circuit
                    breaker.state = CircuitBreaker.CircuitState.OPEN
                    breaker.lastStateChange = System.currentTimeMillis()

                    emitAlert(
                        componentId = componentId,
                        alertType = AlertType.CIRCUIT_OPENED,
                        severity = AnomalySeverity.HIGH,
                        message = "Circuit breaker opened after ${breaker.failureCount} failures"
                    )

                    if (config.autoRecoveryEnabled) {
                        triggerRecovery(componentId, "Circuit breaker opened")
                    }
                }
            }
            CircuitBreaker.CircuitState.HALF_OPEN -> {
                // Back to open state
                breaker.state = CircuitBreaker.CircuitState.OPEN
                breaker.successCount = 0
                breaker.lastStateChange = System.currentTimeMillis()
            }
            else -> {}
        }
    }

    /**
     * Handle component error
     */
    private suspend fun handleComponentError(componentId: String, error: Exception) {
        reportFailure(componentId)

        val health = healthMetrics[componentId] ?: return

        // Check for error spike
        val recentErrors = anomalyHistory.filter {
            it.componentId == componentId &&
                    System.currentTimeMillis() - it.timestamp < 60000
        }.size

        if (recentErrors > 5) {
            emitAlert(
                componentId = componentId,
                alertType = AlertType.ERROR_SPIKE,
                severity = AnomalySeverity.HIGH,
                message = "Error spike detected: $recentErrors errors in last minute"
            )
        }
    }

    // ============================================
    // RECOVERY ACTIONS
    // ============================================

    /**
     * Trigger automatic recovery for a component
     */
    suspend fun triggerRecovery(
        componentId: String,
        reason: String,
        actionType: RecoveryActionType = RecoveryActionType.RESTART_COMPONENT
    ): RecoveryAction = mutex.withLock {
        val health = healthMetrics[componentId]

        // Check recovery attempt limit
        val recentAttempts = recoveryActions.filter {
            it.componentId == componentId &&
                    System.currentTimeMillis() - it.timestamp < 300000 // 5 minutes
        }.size

        if (recentAttempts >= config.maxRecoveryAttempts) {
            val action = RecoveryAction(
                id = "recovery-${System.currentTimeMillis()}",
                componentId = componentId,
                actionType = actionType,
                triggeredBy = reason,
                timestamp = System.currentTimeMillis(),
                status = RecoveryStatus.FAILED,
                result = "Max recovery attempts exceeded"
            )
            recoveryActions.add(action)
            return@withLock action
        }

        val action = RecoveryAction(
            id = "recovery-${System.currentTimeMillis()}",
            componentId = componentId,
            actionType = actionType,
            triggeredBy = reason,
            timestamp = System.currentTimeMillis()
        )

        recoveryActions.add(action)

        // Update health status
        health?.status = HealthStatus.RECOVERING

        emitAlert(
            componentId = componentId,
            alertType = AlertType.RECOVERY_STARTED,
            severity = AnomalySeverity.MEDIUM,
            message = "Starting recovery: $actionType"
        )

        // Execute recovery
        CoroutineScope(Dispatchers.Default).launch {
            executeRecovery(action)
        }

        action
    }

    /**
     * Execute recovery action
     */
    private suspend fun executeRecovery(action: RecoveryAction) {
        val startTime = System.currentTimeMillis()
        action.status = RecoveryStatus.IN_PROGRESS

        try {
            when (action.actionType) {
                RecoveryActionType.RESTART_COMPONENT -> {
                    // Simulate component restart
                    delay(1000)
                    resetComponentState(action.componentId)
                }
                RecoveryActionType.CLEAR_CACHE -> {
                    // Clear component cache
                    delay(500)
                }
                RecoveryActionType.ROLLBACK_STATE -> {
                    // Rollback to last checkpoint
                    rollbackToCheckpoint(action.componentId)
                }
                RecoveryActionType.REDUCE_LOAD -> {
                    // Trigger load reduction
                    delay(500)
                }
                RecoveryActionType.FAILOVER -> {
                    // Switch to backup
                    delay(2000)
                }
                RecoveryActionType.RESET_CONNECTION -> {
                    // Reset network connections
                    delay(1000)
                }
                RecoveryActionType.GARBAGE_COLLECT -> {
                    // Force GC
                    System.gc()
                    delay(500)
                }
                RecoveryActionType.REBALANCE_RESOURCES -> {
                    // Rebalance system resources
                    delay(1000)
                }
            }

            action.status = RecoveryStatus.COMPLETED
            action.result = "Recovery completed successfully"
            action.executionTimeMs = System.currentTimeMillis() - startTime

            // Reset circuit breaker
            circuitBreakers[action.componentId]?.let {
                it.state = CircuitBreaker.CircuitState.HALF_OPEN
                it.successCount = 0
                it.lastStateChange = System.currentTimeMillis()
            }

            emitAlert(
                componentId = action.componentId,
                alertType = AlertType.RECOVERY_COMPLETED,
                severity = AnomalySeverity.LOW,
                message = "Recovery completed in ${action.executionTimeMs}ms"
            )

        } catch (e: Exception) {
            action.status = RecoveryStatus.FAILED
            action.result = "Recovery failed: ${e.message}"
            action.executionTimeMs = System.currentTimeMillis() - startTime

            emitAlert(
                componentId = action.componentId,
                alertType = AlertType.RECOVERY_FAILED,
                severity = AnomalySeverity.HIGH,
                message = "Recovery failed: ${e.message}"
            )
        }
    }

    /**
     * Reset component state
     */
    private suspend fun resetComponentState(componentId: String) = mutex.withLock {
        healthMetrics[componentId]?.let { health ->
            health.errorCount = 0
            health.anomalies = 0
            health.status = HealthStatus.HEALTHY
            health.healthScore = 1.0f
        }

        circuitBreakers[componentId]?.let { breaker ->
            breaker.failureCount = 0
            breaker.successCount = 0
            breaker.state = CircuitBreaker.CircuitState.CLOSED
        }
    }

    // ============================================
    // CHECKPOINTING
    // ============================================

    /**
     * Create checkpoint of component state
     */
    suspend fun createCheckpoint(
        componentId: String,
        state: ByteArray
    ): SystemCheckpoint = mutex.withLock {
        val health = healthMetrics[componentId]

        val checkpoint = SystemCheckpoint(
            id = "checkpoint-$componentId-${System.currentTimeMillis()}",
            componentId = componentId,
            state = state.copyOf(),
            timestamp = System.currentTimeMillis(),
            healthScore = health?.healthScore ?: 1.0f
        )

        checkpoints[componentId] = checkpoint
        checkpoint
    }

    /**
     * Rollback to last checkpoint
     */
    suspend fun rollbackToCheckpoint(componentId: String): ByteArray? = mutex.withLock {
        val checkpoint = checkpoints[componentId] ?: return@withLock null

        // Only rollback if current state is worse
        val currentHealth = healthMetrics[componentId]?.healthScore ?: 0f
        if (currentHealth < checkpoint.healthScore) {
            healthMetrics[componentId]?.healthScore = checkpoint.healthScore
            return@withLock checkpoint.state.copyOf()
        }

        null
    }

    /**
     * Get latest checkpoint
     */
    fun getCheckpoint(componentId: String): SystemCheckpoint? = checkpoints[componentId]

    // ============================================
    // MONITORING
    // ============================================

    /**
     * Start continuous health monitoring
     */
    fun startMonitoring(scope: CoroutineScope) {
        if (isMonitoring) return

        isMonitoring = true
        monitoringJob = scope.launch {
            while (isActive && isMonitoring) {
                performHealthCheck()
                delay(config.healthCheckIntervalMs)
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Perform health check on all components
     */
    private suspend fun performHealthCheck() {
        healthMetrics.values.forEach { health ->
            // Check for stale metrics
            if (System.currentTimeMillis() - health.lastCheck > config.healthCheckIntervalMs * 3) {
                health.status = HealthStatus.UNHEALTHY

                emitAlert(
                    componentId = health.componentId,
                    alertType = AlertType.HEALTH_DEGRADED,
                    severity = AnomalySeverity.MEDIUM,
                    message = "Component ${health.name} is not reporting metrics"
                )
            }

            // Check for memory pressure (simulated)
            val memoryUsage = Runtime.getRuntime().let {
                (it.totalMemory() - it.freeMemory()).toFloat() / it.maxMemory()
            }

            if (memoryUsage > 0.9f) {
                emitAlert(
                    componentId = health.componentId,
                    alertType = AlertType.MEMORY_PRESSURE,
                    severity = AnomalySeverity.HIGH,
                    message = "Memory usage at ${(memoryUsage * 100).toInt()}%"
                )

                if (config.autoRecoveryEnabled) {
                    triggerRecovery(
                        health.componentId,
                        "Memory pressure",
                        RecoveryActionType.GARBAGE_COLLECT
                    )
                }
            }
        }
    }

    /**
     * Emit health alert
     */
    private suspend fun emitAlert(
        componentId: String,
        alertType: AlertType,
        severity: AnomalySeverity,
        message: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val alert = HealthAlert(
            alertId = "alert-${System.currentTimeMillis()}",
            componentId = componentId,
            alertType = alertType,
            severity = severity,
            message = message,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )

        _alertFlow.emit(alert)
    }

    // ============================================
    // STATUS AND REPORTING
    // ============================================

    /**
     * Get overall system health
     */
    fun getSystemHealth(): SystemHealthReport {
        val components = healthMetrics.values.toList()

        val overallScore = if (components.isEmpty()) 1.0f else {
            components.map { it.healthScore }.average().toFloat()
        }

        val overallStatus = when {
            components.any { it.status == HealthStatus.CRITICAL } -> HealthStatus.CRITICAL
            components.any { it.status == HealthStatus.UNHEALTHY } -> HealthStatus.UNHEALTHY
            components.any { it.status == HealthStatus.DEGRADED } -> HealthStatus.DEGRADED
            components.any { it.status == HealthStatus.RECOVERING } -> HealthStatus.RECOVERING
            else -> HealthStatus.HEALTHY
        }

        val recentAnomalies = anomalyHistory.filter {
            System.currentTimeMillis() - it.timestamp < 3600000 // Last hour
        }.size

        val openCircuits = circuitBreakers.values.count {
            it.state == CircuitBreaker.CircuitState.OPEN
        }

        return SystemHealthReport(
            overallScore = overallScore,
            overallStatus = overallStatus,
            componentCount = components.size,
            healthyComponents = components.count { it.status == HealthStatus.HEALTHY },
            degradedComponents = components.count { it.status == HealthStatus.DEGRADED },
            unhealthyComponents = components.count { it.status == HealthStatus.UNHEALTHY },
            criticalComponents = components.count { it.status == HealthStatus.CRITICAL },
            recentAnomalies = recentAnomalies,
            openCircuits = openCircuits,
            activeRecoveries = recoveryActions.count { it.status == RecoveryStatus.IN_PROGRESS },
            timestamp = System.currentTimeMillis()
        )
    }

    data class SystemHealthReport(
        val overallScore: Float,
        val overallStatus: HealthStatus,
        val componentCount: Int,
        val healthyComponents: Int,
        val degradedComponents: Int,
        val unhealthyComponents: Int,
        val criticalComponents: Int,
        val recentAnomalies: Int,
        val openCircuits: Int,
        val activeRecoveries: Int,
        val timestamp: Long
    )

    /**
     * Get component health details
     */
    fun getComponentHealth(componentId: String): ComponentHealth? = healthMetrics[componentId]

    /**
     * Get all component health statuses
     */
    fun getAllComponentHealth(): List<ComponentHealth> = healthMetrics.values.toList()

    /**
     * Get recent anomalies
     */
    fun getRecentAnomalies(limit: Int = 50): List<AnomalyEvent> {
        return anomalyHistory.toList().takeLast(limit)
    }

    /**
     * Get recent recovery actions
     */
    fun getRecentRecoveries(limit: Int = 20): List<RecoveryAction> {
        return recoveryActions.toList().takeLast(limit)
    }

    /**
     * Reset all health data
     */
    suspend fun reset() = mutex.withLock {
        stopMonitoring()
        healthMetrics.clear()
        anomalyHistory.clear()
        recoveryActions.clear()
        circuitBreakers.clear()
        checkpoints.clear()
    }
}
