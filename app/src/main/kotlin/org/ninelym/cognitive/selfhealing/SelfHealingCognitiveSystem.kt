package org.ninelym.cognitive.selfhealing

import org.ninelym.cognitive.CognitiveEngine
import org.ninelym.cognitive.CognitiveTensor
import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.ecan.ECANKernel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Self-Healing Cognitive System - Phase 4D Advanced Feature
 *
 * Provides autonomous detection and recovery from cognitive anomalies:
 * - Anomaly detection in attention patterns
 * - Automatic error recovery
 * - Degradation prevention
 * - Health monitoring and alerting
 * - Self-repair mechanisms
 *
 * The system continuously monitors cognitive state and automatically
 * intervenes when anomalies are detected, ensuring stable operation.
 */
class SelfHealingCognitiveSystem(
    private val cognitiveEngine: CognitiveEngine,
    private val hypergraph: Hypergraph,
    private val ecanKernel: ECANKernel,
    private val config: SelfHealingConfig = SelfHealingConfig()
) {

    // Monitoring state
    private val anomalyHistory = ConcurrentHashMap<String, MutableList<AnomalyEvent>>()
    private val healthMetrics = ConcurrentHashMap<String, HealthMetric>()
    private val recoveryActions = ConcurrentHashMap<String, RecoveryAction>()
    private val alertSubscribers = mutableListOf<(HealthAlert) -> Unit>()

    // Baseline statistics for anomaly detection
    private var attentionBaseline: StatisticalBaseline? = null
    private var tensorBaseline: StatisticalBaseline? = null
    private var performanceBaseline: PerformanceBaseline? = null

    // Monitoring coroutine scope
    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    // Counters for tracking
    private val totalAnomaliesDetected = AtomicLong(0)
    private val totalRecoveriesPerformed = AtomicLong(0)
    private val lastHealthCheck = AtomicLong(System.currentTimeMillis())

    /**
     * Start the self-healing monitoring system
     */
    fun startMonitoring(): Boolean {
        if (isMonitoring) return false

        isMonitoring = true

        // Establish baselines from current state
        establishBaselines()

        // Start continuous monitoring coroutines
        monitoringScope.launch { runAttentionMonitor() }
        monitoringScope.launch { runTensorMonitor() }
        monitoringScope.launch { runPerformanceMonitor() }
        monitoringScope.launch { runHealthAggregator() }

        return true
    }

    /**
     * Stop the self-healing monitoring system
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringScope.coroutineContext.cancelChildren()
    }

    /**
     * Subscribe to health alerts
     */
    fun subscribeToAlerts(callback: (HealthAlert) -> Unit) {
        alertSubscribers.add(callback)
    }

    /**
     * Get current health status
     */
    fun getHealthStatus(): SystemHealthStatus {
        val metrics = healthMetrics.values.toList()
        val overallHealth = if (metrics.isEmpty()) 1.0f
            else metrics.map { it.score }.average().toFloat()

        val anomalies = anomalyHistory.values.flatten()
            .filter { System.currentTimeMillis() - it.timestamp < config.anomalyRetentionMs }

        return SystemHealthStatus(
            overallHealth = overallHealth,
            componentHealth = healthMetrics.toMap(),
            activeAnomalies = anomalies.filter { !it.resolved },
            recentRecoveries = recoveryActions.values
                .filter { it.status == RecoveryStatus.COMPLETED }
                .sortedByDescending { it.completedAt }
                .take(10),
            totalAnomaliesDetected = totalAnomaliesDetected.get(),
            totalRecoveriesPerformed = totalRecoveriesPerformed.get(),
            lastHealthCheck = lastHealthCheck.get(),
            status = when {
                overallHealth >= 0.9f -> HealthState.HEALTHY
                overallHealth >= 0.7f -> HealthState.DEGRADED
                overallHealth >= 0.5f -> HealthState.WARNING
                else -> HealthState.CRITICAL
            }
        )
    }

    /**
     * Manually trigger a health check
     */
    suspend fun performHealthCheck(): HealthCheckResult {
        val startTime = System.currentTimeMillis()
        val anomalies = mutableListOf<AnomalyEvent>()
        val repairs = mutableListOf<RepairAction>()

        // Check attention patterns
        val attentionAnomalies = checkAttentionHealth()
        anomalies.addAll(attentionAnomalies)

        // Check tensor coherence
        val tensorAnomalies = checkTensorCoherence()
        anomalies.addAll(tensorAnomalies)

        // Check hypergraph integrity
        val hypergraphAnomalies = checkHypergraphIntegrity()
        anomalies.addAll(hypergraphAnomalies)

        // Check ECAN scheduler health
        val ecanAnomalies = checkECANHealth()
        anomalies.addAll(ecanAnomalies)

        // Attempt automatic repairs for detected anomalies
        for (anomaly in anomalies) {
            val repair = attemptAutomaticRepair(anomaly)
            if (repair != null) {
                repairs.add(repair)
            }
        }

        lastHealthCheck.set(System.currentTimeMillis())

        return HealthCheckResult(
            timestamp = startTime,
            duration = System.currentTimeMillis() - startTime,
            anomaliesDetected = anomalies.size,
            repairsAttempted = repairs.size,
            repairsSuccessful = repairs.count { it.success },
            anomalies = anomalies,
            repairs = repairs,
            overallHealth = calculateOverallHealth(anomalies)
        )
    }

    /**
     * Register a custom recovery action
     */
    fun registerRecoveryAction(
        anomalyType: AnomalyType,
        action: suspend (AnomalyEvent) -> RecoveryResult
    ) {
        customRecoveryActions[anomalyType] = action
    }

    // ==================== Private Implementation ====================

    private val customRecoveryActions = ConcurrentHashMap<AnomalyType, suspend (AnomalyEvent) -> RecoveryResult>()

    /**
     * Establish statistical baselines from current cognitive state
     */
    private fun establishBaselines() {
        val cognitiveState = cognitiveEngine.getCognitiveState()

        // Attention baseline
        val attentionValues = cognitiveState.tensors.map { it.salience }
        if (attentionValues.isNotEmpty()) {
            attentionBaseline = StatisticalBaseline(
                mean = attentionValues.average().toFloat(),
                stdDev = calculateStdDev(attentionValues),
                min = attentionValues.minOrNull() ?: 0f,
                max = attentionValues.maxOrNull() ?: 1f,
                sampleSize = attentionValues.size
            )
        }

        // Tensor baseline
        val tensorMagnitudes = cognitiveState.tensors.map { t ->
            sqrt(t.modality * t.modality + t.depth * t.depth +
                 t.context * t.context + t.salience * t.salience)
        }
        if (tensorMagnitudes.isNotEmpty()) {
            tensorBaseline = StatisticalBaseline(
                mean = tensorMagnitudes.average().toFloat(),
                stdDev = calculateStdDev(tensorMagnitudes),
                min = tensorMagnitudes.minOrNull() ?: 0f,
                max = tensorMagnitudes.maxOrNull() ?: 2f,
                sampleSize = tensorMagnitudes.size
            )
        }

        // Performance baseline
        performanceBaseline = PerformanceBaseline(
            avgCycleTime = 50f, // ms
            avgMemoryUsage = 0.5f, // fraction
            avgCpuUsage = 0.15f, // fraction
            throughput = 100f // ops/sec
        )
    }

    /**
     * Continuous attention pattern monitoring
     */
    private suspend fun runAttentionMonitor() {
        while (isMonitoring) {
            try {
                val anomalies = checkAttentionHealth()
                processAnomalies(anomalies, "attention")

                // Update health metric
                val score = if (anomalies.isEmpty()) 1.0f
                    else (1.0f - anomalies.size * 0.1f).coerceIn(0f, 1f)
                healthMetrics["attention"] = HealthMetric(
                    component = "attention",
                    score = score,
                    lastUpdate = System.currentTimeMillis(),
                    details = "Active anomalies: ${anomalies.size}"
                )

            } catch (e: Exception) {
                handleMonitoringError("attention", e)
            }

            delay(config.attentionMonitorIntervalMs)
        }
    }

    /**
     * Continuous tensor coherence monitoring
     */
    private suspend fun runTensorMonitor() {
        while (isMonitoring) {
            try {
                val anomalies = checkTensorCoherence()
                processAnomalies(anomalies, "tensor")

                val score = if (anomalies.isEmpty()) 1.0f
                    else (1.0f - anomalies.size * 0.15f).coerceIn(0f, 1f)
                healthMetrics["tensor"] = HealthMetric(
                    component = "tensor",
                    score = score,
                    lastUpdate = System.currentTimeMillis(),
                    details = "Coherence check: ${if (anomalies.isEmpty()) "passed" else "issues found"}"
                )

            } catch (e: Exception) {
                handleMonitoringError("tensor", e)
            }

            delay(config.tensorMonitorIntervalMs)
        }
    }

    /**
     * Continuous performance monitoring
     */
    private suspend fun runPerformanceMonitor() {
        while (isMonitoring) {
            try {
                val anomalies = checkPerformanceHealth()
                processAnomalies(anomalies, "performance")

                val score = if (anomalies.isEmpty()) 1.0f
                    else (1.0f - anomalies.size * 0.2f).coerceIn(0f, 1f)
                healthMetrics["performance"] = HealthMetric(
                    component = "performance",
                    score = score,
                    lastUpdate = System.currentTimeMillis(),
                    details = "Performance within bounds: ${anomalies.isEmpty()}"
                )

            } catch (e: Exception) {
                handleMonitoringError("performance", e)
            }

            delay(config.performanceMonitorIntervalMs)
        }
    }

    /**
     * Aggregate health metrics and emit alerts
     */
    private suspend fun runHealthAggregator() {
        while (isMonitoring) {
            try {
                val status = getHealthStatus()

                // Emit alerts based on health state
                when (status.status) {
                    HealthState.CRITICAL -> emitAlert(
                        HealthAlert(
                            severity = AlertSeverity.CRITICAL,
                            message = "System health critical: ${status.overallHealth}",
                            timestamp = System.currentTimeMillis(),
                            recommendedAction = "Immediate intervention required"
                        )
                    )
                    HealthState.WARNING -> emitAlert(
                        HealthAlert(
                            severity = AlertSeverity.WARNING,
                            message = "System health degraded: ${status.overallHealth}",
                            timestamp = System.currentTimeMillis(),
                            recommendedAction = "Monitor closely, consider intervention"
                        )
                    )
                    else -> { /* No alert needed */ }
                }

            } catch (e: Exception) {
                handleMonitoringError("aggregator", e)
            }

            delay(config.healthAggregationIntervalMs)
        }
    }

    /**
     * Check attention patterns for anomalies
     */
    private fun checkAttentionHealth(): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()
        val baseline = attentionBaseline ?: return anomalies

        val cognitiveState = cognitiveEngine.getCognitiveState()
        val currentAttention = cognitiveState.tensors.map { it.salience }

        if (currentAttention.isEmpty()) return anomalies

        val currentMean = currentAttention.average().toFloat()
        val currentStdDev = calculateStdDev(currentAttention)

        // Check for mean shift (significant change in average attention)
        val meanShift = abs(currentMean - baseline.mean)
        if (meanShift > config.attentionMeanShiftThreshold * baseline.stdDev) {
            anomalies.add(AnomalyEvent(
                id = "attention-mean-shift-${System.currentTimeMillis()}",
                type = AnomalyType.ATTENTION_DRIFT,
                severity = calculateSeverity(meanShift, baseline.stdDev),
                timestamp = System.currentTimeMillis(),
                description = "Attention mean shifted by ${meanShift} (threshold: ${config.attentionMeanShiftThreshold * baseline.stdDev})",
                affectedComponent = "attention",
                metrics = mapOf("meanShift" to meanShift, "currentMean" to currentMean, "baselineMean" to baseline.mean)
            ))
        }

        // Check for variance explosion (instability)
        if (currentStdDev > baseline.stdDev * config.varianceExplosionThreshold) {
            anomalies.add(AnomalyEvent(
                id = "attention-variance-${System.currentTimeMillis()}",
                type = AnomalyType.ATTENTION_INSTABILITY,
                severity = AnomalySeverity.HIGH,
                timestamp = System.currentTimeMillis(),
                description = "Attention variance explosion: $currentStdDev vs baseline ${baseline.stdDev}",
                affectedComponent = "attention",
                metrics = mapOf("currentStdDev" to currentStdDev, "baselineStdDev" to baseline.stdDev)
            ))
        }

        // Check for attention saturation (too many high values)
        val saturationRatio = currentAttention.count { it > config.attentionSaturationThreshold } /
            currentAttention.size.toFloat()
        if (saturationRatio > config.saturationRatioThreshold) {
            anomalies.add(AnomalyEvent(
                id = "attention-saturation-${System.currentTimeMillis()}",
                type = AnomalyType.ATTENTION_SATURATION,
                severity = AnomalySeverity.MEDIUM,
                timestamp = System.currentTimeMillis(),
                description = "Attention saturation: ${saturationRatio * 100}% above threshold",
                affectedComponent = "attention",
                metrics = mapOf("saturationRatio" to saturationRatio)
            ))
        }

        return anomalies
    }

    /**
     * Check tensor coherence for anomalies
     */
    private fun checkTensorCoherence(): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()
        val cognitiveState = cognitiveEngine.getCognitiveState()

        cognitiveState.tensors.forEachIndexed { index, tensor ->
            // Check for NaN or Inf values
            if (tensor.hasInvalidValues()) {
                anomalies.add(AnomalyEvent(
                    id = "tensor-invalid-${index}-${System.currentTimeMillis()}",
                    type = AnomalyType.TENSOR_CORRUPTION,
                    severity = AnomalySeverity.CRITICAL,
                    timestamp = System.currentTimeMillis(),
                    description = "Tensor $index contains invalid values (NaN/Inf)",
                    affectedComponent = "tensor",
                    metrics = mapOf("tensorIndex" to index.toFloat())
                ))
            }

            // Check for out-of-bounds values
            if (!tensor.isNormalized()) {
                anomalies.add(AnomalyEvent(
                    id = "tensor-bounds-${index}-${System.currentTimeMillis()}",
                    type = AnomalyType.TENSOR_BOUNDS_VIOLATION,
                    severity = AnomalySeverity.MEDIUM,
                    timestamp = System.currentTimeMillis(),
                    description = "Tensor $index has out-of-bounds values",
                    affectedComponent = "tensor",
                    metrics = mapOf("tensorIndex" to index.toFloat())
                ))
            }
        }

        return anomalies
    }

    /**
     * Check hypergraph integrity
     */
    private fun checkHypergraphIntegrity(): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()

        // Check for orphaned atoms (atoms with invalid references)
        val orphanedCount = hypergraph.findOrphanedAtoms().size
        if (orphanedCount > config.maxOrphanedAtoms) {
            anomalies.add(AnomalyEvent(
                id = "hypergraph-orphans-${System.currentTimeMillis()}",
                type = AnomalyType.HYPERGRAPH_INCONSISTENCY,
                severity = AnomalySeverity.MEDIUM,
                timestamp = System.currentTimeMillis(),
                description = "Found $orphanedCount orphaned atoms (threshold: ${config.maxOrphanedAtoms})",
                affectedComponent = "hypergraph",
                metrics = mapOf("orphanedCount" to orphanedCount.toFloat())
            ))
        }

        // Check for circular references
        val circularRefs = hypergraph.detectCircularReferences()
        if (circularRefs.isNotEmpty()) {
            anomalies.add(AnomalyEvent(
                id = "hypergraph-circular-${System.currentTimeMillis()}",
                type = AnomalyType.HYPERGRAPH_CIRCULAR_REF,
                severity = AnomalySeverity.LOW,
                timestamp = System.currentTimeMillis(),
                description = "Detected ${circularRefs.size} circular reference chains",
                affectedComponent = "hypergraph",
                metrics = mapOf("circularCount" to circularRefs.size.toFloat())
            ))
        }

        return anomalies
    }

    /**
     * Check ECAN scheduler health
     */
    private fun checkECANHealth(): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()

        // Check task queue health
        val queueStatus = ecanKernel.getQueueStatus()
        if (queueStatus.queueDepth > config.maxQueueDepth) {
            anomalies.add(AnomalyEvent(
                id = "ecan-queue-${System.currentTimeMillis()}",
                type = AnomalyType.ECAN_QUEUE_OVERFLOW,
                severity = AnomalySeverity.HIGH,
                timestamp = System.currentTimeMillis(),
                description = "ECAN queue depth ${queueStatus.queueDepth} exceeds threshold ${config.maxQueueDepth}",
                affectedComponent = "ecan",
                metrics = mapOf("queueDepth" to queueStatus.queueDepth.toFloat())
            ))
        }

        // Check for stalled tasks
        if (queueStatus.stalledTasks > config.maxStalledTasks) {
            anomalies.add(AnomalyEvent(
                id = "ecan-stalled-${System.currentTimeMillis()}",
                type = AnomalyType.ECAN_TASK_STALL,
                severity = AnomalySeverity.HIGH,
                timestamp = System.currentTimeMillis(),
                description = "ECAN has ${queueStatus.stalledTasks} stalled tasks",
                affectedComponent = "ecan",
                metrics = mapOf("stalledTasks" to queueStatus.stalledTasks.toFloat())
            ))
        }

        return anomalies
    }

    /**
     * Check performance metrics for anomalies
     */
    private fun checkPerformanceHealth(): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()
        val baseline = performanceBaseline ?: return anomalies

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory()

        // Memory pressure check
        if (usedMemory > config.memoryPressureThreshold) {
            anomalies.add(AnomalyEvent(
                id = "perf-memory-${System.currentTimeMillis()}",
                type = AnomalyType.MEMORY_PRESSURE,
                severity = if (usedMemory > 0.9f) AnomalySeverity.CRITICAL else AnomalySeverity.HIGH,
                timestamp = System.currentTimeMillis(),
                description = "Memory usage at ${(usedMemory * 100).toInt()}%",
                affectedComponent = "performance",
                metrics = mapOf("memoryUsage" to usedMemory)
            ))
        }

        return anomalies
    }

    /**
     * Process detected anomalies
     */
    private suspend fun processAnomalies(anomalies: List<AnomalyEvent>, source: String) {
        for (anomaly in anomalies) {
            // Record anomaly
            anomalyHistory.getOrPut(source) { mutableListOf() }.add(anomaly)
            totalAnomaliesDetected.incrementAndGet()

            // Attempt automatic recovery if enabled
            if (config.autoRecoveryEnabled && anomaly.severity >= config.autoRecoveryThreshold) {
                val recovery = initiateRecovery(anomaly)
                if (recovery.success) {
                    anomaly.resolved = true
                    totalRecoveriesPerformed.incrementAndGet()
                }
            }

            // Emit alert for severe anomalies
            if (anomaly.severity >= AnomalySeverity.HIGH) {
                emitAlert(HealthAlert(
                    severity = when (anomaly.severity) {
                        AnomalySeverity.CRITICAL -> AlertSeverity.CRITICAL
                        AnomalySeverity.HIGH -> AlertSeverity.ERROR
                        else -> AlertSeverity.WARNING
                    },
                    message = anomaly.description,
                    timestamp = anomaly.timestamp,
                    recommendedAction = getRecommendedAction(anomaly)
                ))
            }
        }

        // Cleanup old anomalies
        anomalyHistory[source]?.removeIf {
            System.currentTimeMillis() - it.timestamp > config.anomalyRetentionMs
        }
    }

    /**
     * Attempt automatic repair for an anomaly
     */
    private suspend fun attemptAutomaticRepair(anomaly: AnomalyEvent): RepairAction? {
        val recovery = initiateRecovery(anomaly)
        return RepairAction(
            anomalyId = anomaly.id,
            action = recovery.actionTaken,
            success = recovery.success,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Initiate recovery for an anomaly
     */
    private suspend fun initiateRecovery(anomaly: AnomalyEvent): RecoveryResult {
        val recoveryId = "recovery-${anomaly.id}"
        val action = RecoveryAction(
            id = recoveryId,
            anomalyId = anomaly.id,
            type = getRecoveryType(anomaly.type),
            status = RecoveryStatus.IN_PROGRESS,
            startedAt = System.currentTimeMillis()
        )
        recoveryActions[recoveryId] = action

        // Check for custom recovery action
        val customAction = customRecoveryActions[anomaly.type]
        if (customAction != null) {
            return try {
                val result = customAction(anomaly)
                action.status = if (result.success) RecoveryStatus.COMPLETED else RecoveryStatus.FAILED
                action.completedAt = System.currentTimeMillis()
                result
            } catch (e: Exception) {
                action.status = RecoveryStatus.FAILED
                action.completedAt = System.currentTimeMillis()
                RecoveryResult(false, "Custom recovery failed: ${e.message}", "custom")
            }
        }

        // Default recovery strategies
        return try {
            val result = when (anomaly.type) {
                AnomalyType.ATTENTION_DRIFT -> recoverFromAttentionDrift(anomaly)
                AnomalyType.ATTENTION_INSTABILITY -> recoverFromAttentionInstability(anomaly)
                AnomalyType.ATTENTION_SATURATION -> recoverFromAttentionSaturation(anomaly)
                AnomalyType.TENSOR_CORRUPTION -> recoverFromTensorCorruption(anomaly)
                AnomalyType.TENSOR_BOUNDS_VIOLATION -> recoverFromTensorBounds(anomaly)
                AnomalyType.HYPERGRAPH_INCONSISTENCY -> recoverFromHypergraphInconsistency(anomaly)
                AnomalyType.HYPERGRAPH_CIRCULAR_REF -> recoverFromCircularRefs(anomaly)
                AnomalyType.ECAN_QUEUE_OVERFLOW -> recoverFromQueueOverflow(anomaly)
                AnomalyType.ECAN_TASK_STALL -> recoverFromTaskStall(anomaly)
                AnomalyType.MEMORY_PRESSURE -> recoverFromMemoryPressure(anomaly)
            }
            action.status = if (result.success) RecoveryStatus.COMPLETED else RecoveryStatus.FAILED
            action.completedAt = System.currentTimeMillis()
            result
        } catch (e: Exception) {
            action.status = RecoveryStatus.FAILED
            action.completedAt = System.currentTimeMillis()
            RecoveryResult(false, "Recovery failed: ${e.message}", "default")
        }
    }

    // ==================== Recovery Strategies ====================

    private fun recoverFromAttentionDrift(anomaly: AnomalyEvent): RecoveryResult {
        // Re-normalize attention values toward baseline
        val baseline = attentionBaseline ?: return RecoveryResult(false, "No baseline available", "attention-drift")
        cognitiveEngine.normalizeAttention(baseline.mean, baseline.stdDev)
        return RecoveryResult(true, "Attention normalized to baseline", "attention-drift")
    }

    private fun recoverFromAttentionInstability(anomaly: AnomalyEvent): RecoveryResult {
        // Apply smoothing to reduce variance
        cognitiveEngine.applyAttentionSmoothing(config.smoothingFactor)
        return RecoveryResult(true, "Applied attention smoothing", "attention-instability")
    }

    private fun recoverFromAttentionSaturation(anomaly: AnomalyEvent): RecoveryResult {
        // Decay high attention values
        cognitiveEngine.decayHighAttention(config.attentionDecayRate)
        return RecoveryResult(true, "Applied attention decay", "attention-saturation")
    }

    private fun recoverFromTensorCorruption(anomaly: AnomalyEvent): RecoveryResult {
        // Reset corrupted tensors to safe defaults
        val tensorIndex = anomaly.metrics["tensorIndex"]?.toInt() ?: return RecoveryResult(false, "Unknown tensor", "tensor-corruption")
        cognitiveEngine.resetTensor(tensorIndex)
        return RecoveryResult(true, "Reset corrupted tensor $tensorIndex", "tensor-corruption")
    }

    private fun recoverFromTensorBounds(anomaly: AnomalyEvent): RecoveryResult {
        // Clamp tensor values to valid range
        cognitiveEngine.clampAllTensors()
        return RecoveryResult(true, "Clamped tensor values to valid range", "tensor-bounds")
    }

    private fun recoverFromHypergraphInconsistency(anomaly: AnomalyEvent): RecoveryResult {
        // Remove orphaned atoms
        val removed = hypergraph.removeOrphanedAtoms()
        return RecoveryResult(true, "Removed $removed orphaned atoms", "hypergraph-inconsistency")
    }

    private fun recoverFromCircularRefs(anomaly: AnomalyEvent): RecoveryResult {
        // Break circular reference chains
        val broken = hypergraph.breakCircularReferences()
        return RecoveryResult(true, "Broke $broken circular reference chains", "circular-refs")
    }

    private fun recoverFromQueueOverflow(anomaly: AnomalyEvent): RecoveryResult {
        // Drain low-priority tasks from queue
        val drained = ecanKernel.drainLowPriorityTasks(config.drainRatio)
        return RecoveryResult(true, "Drained $drained low-priority tasks", "queue-overflow")
    }

    private fun recoverFromTaskStall(anomaly: AnomalyEvent): RecoveryResult {
        // Cancel stalled tasks
        val cancelled = ecanKernel.cancelStalledTasks(config.stallTimeoutMs)
        return RecoveryResult(true, "Cancelled $cancelled stalled tasks", "task-stall")
    }

    private fun recoverFromMemoryPressure(anomaly: AnomalyEvent): RecoveryResult {
        // Request garbage collection and clear caches
        System.gc()
        cognitiveEngine.clearCaches()
        hypergraph.compactStorage()
        return RecoveryResult(true, "Cleared caches and requested GC", "memory-pressure")
    }

    // ==================== Utility Functions ====================

    private fun calculateStdDev(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    private fun calculateSeverity(deviation: Float, baseline: Float): AnomalySeverity {
        val ratio = deviation / (baseline + 0.001f)
        return when {
            ratio > 4.0f -> AnomalySeverity.CRITICAL
            ratio > 3.0f -> AnomalySeverity.HIGH
            ratio > 2.0f -> AnomalySeverity.MEDIUM
            else -> AnomalySeverity.LOW
        }
    }

    private fun calculateOverallHealth(anomalies: List<AnomalyEvent>): Float {
        if (anomalies.isEmpty()) return 1.0f
        val severityPenalties = anomalies.map {
            when (it.severity) {
                AnomalySeverity.CRITICAL -> 0.3f
                AnomalySeverity.HIGH -> 0.2f
                AnomalySeverity.MEDIUM -> 0.1f
                AnomalySeverity.LOW -> 0.05f
            }
        }
        return (1.0f - severityPenalties.sum()).coerceIn(0f, 1f)
    }

    private fun getRecoveryType(anomalyType: AnomalyType): RecoveryType = when (anomalyType) {
        AnomalyType.ATTENTION_DRIFT,
        AnomalyType.ATTENTION_INSTABILITY,
        AnomalyType.ATTENTION_SATURATION -> RecoveryType.ATTENTION_RESET
        AnomalyType.TENSOR_CORRUPTION,
        AnomalyType.TENSOR_BOUNDS_VIOLATION -> RecoveryType.TENSOR_REPAIR
        AnomalyType.HYPERGRAPH_INCONSISTENCY,
        AnomalyType.HYPERGRAPH_CIRCULAR_REF -> RecoveryType.HYPERGRAPH_CLEANUP
        AnomalyType.ECAN_QUEUE_OVERFLOW,
        AnomalyType.ECAN_TASK_STALL -> RecoveryType.SCHEDULER_RESET
        AnomalyType.MEMORY_PRESSURE -> RecoveryType.MEMORY_CLEANUP
    }

    private fun getRecommendedAction(anomaly: AnomalyEvent): String = when (anomaly.type) {
        AnomalyType.ATTENTION_DRIFT -> "Consider re-establishing baselines"
        AnomalyType.ATTENTION_INSTABILITY -> "Check for rapid state changes"
        AnomalyType.ATTENTION_SATURATION -> "Review attention allocation strategy"
        AnomalyType.TENSOR_CORRUPTION -> "Investigate source of invalid values"
        AnomalyType.TENSOR_BOUNDS_VIOLATION -> "Review tensor normalization"
        AnomalyType.HYPERGRAPH_INCONSISTENCY -> "Run full consistency check"
        AnomalyType.HYPERGRAPH_CIRCULAR_REF -> "Review relationship creation logic"
        AnomalyType.ECAN_QUEUE_OVERFLOW -> "Increase processing capacity or reduce load"
        AnomalyType.ECAN_TASK_STALL -> "Check for deadlocks or resource contention"
        AnomalyType.MEMORY_PRESSURE -> "Increase heap size or reduce memory usage"
    }

    private fun emitAlert(alert: HealthAlert) {
        alertSubscribers.forEach { callback ->
            try {
                callback(alert)
            } catch (e: Exception) {
                // Log but don't fail on subscriber errors
            }
        }
    }

    private fun handleMonitoringError(component: String, error: Exception) {
        healthMetrics[component] = HealthMetric(
            component = component,
            score = 0.5f,
            lastUpdate = System.currentTimeMillis(),
            details = "Monitoring error: ${error.message}"
        )
    }
}

// ==================== Data Classes ====================

/**
 * Configuration for self-healing system
 */
data class SelfHealingConfig(
    val attentionMonitorIntervalMs: Long = 5000,
    val tensorMonitorIntervalMs: Long = 10000,
    val performanceMonitorIntervalMs: Long = 15000,
    val healthAggregationIntervalMs: Long = 30000,
    val anomalyRetentionMs: Long = 3600000, // 1 hour
    val autoRecoveryEnabled: Boolean = true,
    val autoRecoveryThreshold: AnomalySeverity = AnomalySeverity.MEDIUM,
    val attentionMeanShiftThreshold: Float = 2.0f,
    val varianceExplosionThreshold: Float = 3.0f,
    val attentionSaturationThreshold: Float = 0.9f,
    val saturationRatioThreshold: Float = 0.5f,
    val maxOrphanedAtoms: Int = 100,
    val maxQueueDepth: Int = 1000,
    val maxStalledTasks: Int = 10,
    val memoryPressureThreshold: Float = 0.85f,
    val smoothingFactor: Float = 0.3f,
    val attentionDecayRate: Float = 0.1f,
    val drainRatio: Float = 0.25f,
    val stallTimeoutMs: Long = 60000
)

/**
 * Statistical baseline for anomaly detection
 */
data class StatisticalBaseline(
    val mean: Float,
    val stdDev: Float,
    val min: Float,
    val max: Float,
    val sampleSize: Int
)

/**
 * Performance baseline
 */
data class PerformanceBaseline(
    val avgCycleTime: Float,
    val avgMemoryUsage: Float,
    val avgCpuUsage: Float,
    val throughput: Float
)

/**
 * Anomaly event
 */
data class AnomalyEvent(
    val id: String,
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val timestamp: Long,
    val description: String,
    val affectedComponent: String,
    val metrics: Map<String, Float>,
    var resolved: Boolean = false
)

/**
 * Types of anomalies that can be detected
 */
enum class AnomalyType {
    ATTENTION_DRIFT,
    ATTENTION_INSTABILITY,
    ATTENTION_SATURATION,
    TENSOR_CORRUPTION,
    TENSOR_BOUNDS_VIOLATION,
    HYPERGRAPH_INCONSISTENCY,
    HYPERGRAPH_CIRCULAR_REF,
    ECAN_QUEUE_OVERFLOW,
    ECAN_TASK_STALL,
    MEMORY_PRESSURE
}

/**
 * Severity levels for anomalies
 */
enum class AnomalySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Health metric for a component
 */
data class HealthMetric(
    val component: String,
    val score: Float,
    val lastUpdate: Long,
    val details: String
)

/**
 * Overall system health status
 */
data class SystemHealthStatus(
    val overallHealth: Float,
    val componentHealth: Map<String, HealthMetric>,
    val activeAnomalies: List<AnomalyEvent>,
    val recentRecoveries: List<RecoveryAction>,
    val totalAnomaliesDetected: Long,
    val totalRecoveriesPerformed: Long,
    val lastHealthCheck: Long,
    val status: HealthState
)

/**
 * Health states
 */
enum class HealthState {
    HEALTHY,
    DEGRADED,
    WARNING,
    CRITICAL
}

/**
 * Recovery action tracking
 */
data class RecoveryAction(
    val id: String,
    val anomalyId: String,
    val type: RecoveryType,
    var status: RecoveryStatus,
    val startedAt: Long,
    var completedAt: Long? = null
)

/**
 * Types of recovery actions
 */
enum class RecoveryType {
    ATTENTION_RESET,
    TENSOR_REPAIR,
    HYPERGRAPH_CLEANUP,
    SCHEDULER_RESET,
    MEMORY_CLEANUP
}

/**
 * Status of recovery action
 */
enum class RecoveryStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Result of a recovery attempt
 */
data class RecoveryResult(
    val success: Boolean,
    val message: String,
    val actionTaken: String
)

/**
 * Health check result
 */
data class HealthCheckResult(
    val timestamp: Long,
    val duration: Long,
    val anomaliesDetected: Int,
    val repairsAttempted: Int,
    val repairsSuccessful: Int,
    val anomalies: List<AnomalyEvent>,
    val repairs: List<RepairAction>,
    val overallHealth: Float
)

/**
 * Repair action record
 */
data class RepairAction(
    val anomalyId: String,
    val action: String,
    val success: Boolean,
    val timestamp: Long
)

/**
 * Health alert
 */
data class HealthAlert(
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long,
    val recommendedAction: String
)

/**
 * Alert severity levels
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

// ==================== Extension Functions ====================

/**
 * Check if tensor has invalid values
 */
private fun CognitiveTensor.hasInvalidValues(): Boolean {
    return listOf(modality, depth, context, salience, autonomyIndex).any {
        it.isNaN() || it.isInfinite()
    }
}

/**
 * Check if tensor values are normalized (0-1 range)
 */
private fun CognitiveTensor.isNormalized(): Boolean {
    return listOf(modality, depth, context, salience, autonomyIndex).all {
        it in 0f..1f
    }
}
