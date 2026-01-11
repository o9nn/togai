package org.ninelym.cognitive.distributed

import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import org.ninelym.cognitive.CognitiveTensor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.security.MessageDigest
import kotlin.math.max

/**
 * Distributed Cognitive Synchronization System - Phase 4B
 *
 * Enables peer-to-peer synchronization of cognitive states across multiple devices
 * using Conflict-free Replicated Data Types (CRDTs) for eventual consistency.
 *
 * Features:
 * - Distributed hypergraph synchronization
 * - CRDT-based conflict resolution
 * - Bandwidth-efficient delta synchronization
 * - Eventual consistency guarantees
 * - Network partition tolerance
 *
 * Architecture:
 * ```
 * Device A ←→ Mesh Coordinator ←→ Device B
 *    ↓              ↓                ↓
 * Local CRDT    Sync Protocol    Local CRDT
 * ```
 */
class DistributedCognitiveSynchronization(
    private val localNodeId: String,
    private val hypergraph: Hypergraph,
    private val config: SyncConfig = SyncConfig()
) {

    // CRDT state for atoms
    private val atomCRDT = LWWElementSet<AtomCRDTEntry>()

    // CRDT state for attention values
    private val attentionCRDT = GCounterMap<String>()

    // Vector clock for causality tracking
    private val vectorClock = VectorClock(localNodeId)

    // Connected peers
    private val connectedPeers = ConcurrentHashMap<String, PeerConnection>()

    // Pending sync operations
    private val pendingSyncs = Channel<SyncOperation>(Channel.BUFFERED)

    // Sync statistics
    private val syncStats = SyncStatistics()

    // Synchronization scope
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isSyncing = false

    /**
     * Start the synchronization system
     */
    fun startSync(): Boolean {
        if (isSyncing) return false
        isSyncing = true

        // Initialize local CRDT state from hypergraph
        initializeCRDTState()

        // Start sync processing
        syncScope.launch { processSyncOperations() }
        syncScope.launch { periodicSyncBroadcast() }

        return true
    }

    /**
     * Stop the synchronization system
     */
    fun stopSync() {
        isSyncing = false
        syncScope.coroutineContext.cancelChildren()
    }

    /**
     * Connect to a peer node
     */
    suspend fun connectPeer(peerId: String, endpoint: String): PeerConnectionResult {
        if (connectedPeers.containsKey(peerId)) {
            return PeerConnectionResult(false, "Peer already connected")
        }

        val connection = PeerConnection(
            peerId = peerId,
            endpoint = endpoint,
            connectedAt = System.currentTimeMillis(),
            lastSyncAt = 0,
            status = ConnectionStatus.CONNECTING
        )

        // Attempt handshake
        val handshakeResult = performHandshake(connection)
        if (!handshakeResult.success) {
            return PeerConnectionResult(false, "Handshake failed: ${handshakeResult.message}")
        }

        connection.status = ConnectionStatus.CONNECTED
        connection.vectorClockSnapshot = handshakeResult.peerVectorClock
        connectedPeers[peerId] = connection

        // Trigger initial sync
        requestFullSync(peerId)

        return PeerConnectionResult(true, "Connected to peer $peerId")
    }

    /**
     * Disconnect from a peer
     */
    fun disconnectPeer(peerId: String): Boolean {
        val connection = connectedPeers.remove(peerId) ?: return false
        connection.status = ConnectionStatus.DISCONNECTED
        return true
    }

    /**
     * Add or update an atom locally (will be synchronized)
     */
    fun localAddAtom(atom: Atom): SyncResult {
        val timestamp = System.currentTimeMillis()
        vectorClock.increment()

        val entry = AtomCRDTEntry(
            atomId = atom.id,
            atom = atom,
            timestamp = timestamp,
            nodeId = localNodeId,
            vectorClock = vectorClock.copy(),
            operation = CRDTOperation.ADD
        )

        atomCRDT.add(entry, timestamp)

        // Queue for sync
        syncScope.launch {
            pendingSyncs.send(SyncOperation(
                type = SyncOperationType.ATOM_ADD,
                data = entry,
                timestamp = timestamp
            ))
        }

        syncStats.localOperations.incrementAndGet()
        return SyncResult(true, "Atom added and queued for sync")
    }

    /**
     * Remove an atom locally (will be synchronized)
     */
    fun localRemoveAtom(atomId: String): SyncResult {
        val timestamp = System.currentTimeMillis()
        vectorClock.increment()

        val entry = AtomCRDTEntry(
            atomId = atomId,
            atom = null,
            timestamp = timestamp,
            nodeId = localNodeId,
            vectorClock = vectorClock.copy(),
            operation = CRDTOperation.REMOVE
        )

        atomCRDT.remove(entry, timestamp)

        syncScope.launch {
            pendingSyncs.send(SyncOperation(
                type = SyncOperationType.ATOM_REMOVE,
                data = entry,
                timestamp = timestamp
            ))
        }

        syncStats.localOperations.incrementAndGet()
        return SyncResult(true, "Atom removal queued for sync")
    }

    /**
     * Update attention value (will be synchronized using G-Counter)
     */
    fun localUpdateAttention(atomId: String, delta: Float): SyncResult {
        if (delta <= 0) return SyncResult(false, "Delta must be positive for G-Counter")

        attentionCRDT.increment(atomId, localNodeId, delta)
        vectorClock.increment()

        syncScope.launch {
            pendingSyncs.send(SyncOperation(
                type = SyncOperationType.ATTENTION_UPDATE,
                data = AttentionUpdate(atomId, localNodeId, delta),
                timestamp = System.currentTimeMillis()
            ))
        }

        syncStats.localOperations.incrementAndGet()
        return SyncResult(true, "Attention update queued for sync")
    }

    /**
     * Receive and merge sync data from a peer
     */
    fun receiveSyncData(peerId: String, syncData: SyncData): MergeResult {
        val peer = connectedPeers[peerId]
            ?: return MergeResult(false, 0, "Unknown peer")

        var mergedCount = 0

        // Merge atom operations
        syncData.atomOperations.forEach { entry ->
            val merged = atomCRDT.merge(entry)
            if (merged) {
                mergedCount++
                applyAtomOperation(entry)
            }
        }

        // Merge attention updates
        syncData.attentionUpdates.forEach { update ->
            attentionCRDT.merge(update.atomId, update.nodeId, update.value)
            mergedCount++
        }

        // Update vector clock
        vectorClock.merge(syncData.vectorClock)

        // Update peer connection
        peer.lastSyncAt = System.currentTimeMillis()
        peer.vectorClockSnapshot = syncData.vectorClock

        syncStats.remoteOperations.addAndGet(mergedCount.toLong())
        syncStats.syncCycles.incrementAndGet()

        return MergeResult(true, mergedCount, "Merged $mergedCount operations from $peerId")
    }

    /**
     * Get delta sync data for a peer (only changes since last sync)
     */
    fun getDeltaSyncData(peerId: String): SyncData? {
        val peer = connectedPeers[peerId] ?: return null
        val peerClock = peer.vectorClockSnapshot ?: return getFullSyncData()

        // Get atom operations that are newer than peer's clock
        val atomOps = atomCRDT.getDeltaSince(peerClock)

        // Get attention deltas
        val attentionDeltas = attentionCRDT.getDeltaSince(peerClock)

        return SyncData(
            sourceNodeId = localNodeId,
            atomOperations = atomOps,
            attentionUpdates = attentionDeltas,
            vectorClock = vectorClock.copy(),
            timestamp = System.currentTimeMillis(),
            isDelta = true
        )
    }

    /**
     * Get full sync data (for initial sync)
     */
    fun getFullSyncData(): SyncData {
        return SyncData(
            sourceNodeId = localNodeId,
            atomOperations = atomCRDT.getAllEntries(),
            attentionUpdates = attentionCRDT.getAllUpdates(),
            vectorClock = vectorClock.copy(),
            timestamp = System.currentTimeMillis(),
            isDelta = false
        )
    }

    /**
     * Get current sync status
     */
    fun getSyncStatus(): SyncStatus {
        val connectedPeerCount = connectedPeers.values.count { it.status == ConnectionStatus.CONNECTED }

        return SyncStatus(
            localNodeId = localNodeId,
            isSyncing = isSyncing,
            connectedPeers = connectedPeerCount,
            totalPeers = connectedPeers.size,
            vectorClock = vectorClock.copy(),
            atomCount = atomCRDT.size(),
            attentionEntryCount = attentionCRDT.size(),
            statistics = syncStats.snapshot()
        )
    }

    /**
     * Get convergence status with a peer
     */
    fun getConvergenceStatus(peerId: String): ConvergenceStatus? {
        val peer = connectedPeers[peerId] ?: return null
        val peerClock = peer.vectorClockSnapshot ?: return null

        val localClockHash = vectorClock.hash()
        val peerClockHash = peerClock.hash()

        return ConvergenceStatus(
            peerId = peerId,
            isConverged = localClockHash == peerClockHash,
            localClock = vectorClock.copy(),
            peerClock = peerClock.copy(),
            pendingDeltaCount = atomCRDT.getDeltaSince(peerClock).size,
            lastSyncAt = peer.lastSyncAt
        )
    }

    // ==================== Private Implementation ====================

    /**
     * Initialize CRDT state from existing hypergraph
     */
    private fun initializeCRDTState() {
        val timestamp = System.currentTimeMillis()

        // Add existing atoms to CRDT
        hypergraph.getAllAtoms().forEach { atom ->
            val entry = AtomCRDTEntry(
                atomId = atom.id,
                atom = atom,
                timestamp = timestamp,
                nodeId = localNodeId,
                vectorClock = vectorClock.copy(),
                operation = CRDTOperation.ADD
            )
            atomCRDT.add(entry, timestamp)
        }
    }

    /**
     * Process pending sync operations
     */
    private suspend fun processSyncOperations() {
        while (isSyncing) {
            val operation = pendingSyncs.receive()

            // Broadcast to all connected peers
            connectedPeers.values
                .filter { it.status == ConnectionStatus.CONNECTED }
                .forEach { peer ->
                    sendToPeer(peer, operation)
                }
        }
    }

    /**
     * Periodic sync broadcast to ensure convergence
     */
    private suspend fun periodicSyncBroadcast() {
        while (isSyncing) {
            delay(config.syncIntervalMs)

            connectedPeers.values
                .filter { it.status == ConnectionStatus.CONNECTED }
                .forEach { peer ->
                    val delta = getDeltaSyncData(peer.peerId)
                    if (delta != null && delta.atomOperations.isNotEmpty()) {
                        sendSyncDataToPeer(peer, delta)
                    }
                }
        }
    }

    /**
     * Apply atom operation to local hypergraph
     */
    private fun applyAtomOperation(entry: AtomCRDTEntry) {
        when (entry.operation) {
            CRDTOperation.ADD -> {
                entry.atom?.let { hypergraph.addAtom(it) }
            }
            CRDTOperation.REMOVE -> {
                hypergraph.removeAtom(entry.atomId)
            }
            CRDTOperation.UPDATE -> {
                entry.atom?.let { hypergraph.updateAtom(it) }
            }
        }
    }

    /**
     * Perform handshake with peer
     */
    private suspend fun performHandshake(connection: PeerConnection): HandshakeResult {
        // In a real implementation, this would involve network communication
        // For now, simulate successful handshake
        return HandshakeResult(
            success = true,
            message = "Handshake successful",
            peerVectorClock = VectorClock(connection.peerId)
        )
    }

    /**
     * Request full sync from peer
     */
    private fun requestFullSync(peerId: String) {
        syncScope.launch {
            pendingSyncs.send(SyncOperation(
                type = SyncOperationType.FULL_SYNC_REQUEST,
                data = FullSyncRequest(localNodeId, vectorClock.copy()),
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Send operation to peer (stub for network layer)
     */
    private suspend fun sendToPeer(peer: PeerConnection, operation: SyncOperation) {
        // In a real implementation, this would send over network
        syncStats.messagesSent.incrementAndGet()
    }

    /**
     * Send sync data to peer (stub for network layer)
     */
    private suspend fun sendSyncDataToPeer(peer: PeerConnection, data: SyncData) {
        // In a real implementation, this would send over network
        syncStats.messagesSent.incrementAndGet()
        syncStats.bytesSent.addAndGet(estimateSyncDataSize(data))
    }

    /**
     * Estimate size of sync data in bytes
     */
    private fun estimateSyncDataSize(data: SyncData): Long {
        return (data.atomOperations.size * 512 + data.attentionUpdates.size * 64).toLong()
    }
}

// ==================== CRDT Implementations ====================

/**
 * Last-Writer-Wins Element Set CRDT
 *
 * Provides add/remove semantics with timestamp-based conflict resolution.
 * Concurrent adds and removes are resolved by timestamp - later wins.
 */
class LWWElementSet<T : CRDTEntry> {
    private val addSet = ConcurrentHashMap<String, Pair<T, Long>>()
    private val removeSet = ConcurrentHashMap<String, Long>()

    fun add(entry: T, timestamp: Long): Boolean {
        val existing = addSet[entry.id]
        if (existing == null || existing.second < timestamp) {
            addSet[entry.id] = Pair(entry, timestamp)
            return true
        }
        return false
    }

    fun remove(entry: T, timestamp: Long): Boolean {
        val addTimestamp = addSet[entry.id]?.second ?: return false
        val existingRemove = removeSet[entry.id]

        if (timestamp > addTimestamp && (existingRemove == null || existingRemove < timestamp)) {
            removeSet[entry.id] = timestamp
            return true
        }
        return false
    }

    fun merge(entry: T): Boolean {
        return when {
            entry is AtomCRDTEntry && entry.operation == CRDTOperation.REMOVE -> {
                remove(entry, entry.timestamp)
            }
            else -> add(entry, entry.timestamp)
        }
    }

    fun lookup(id: String): T? {
        val addEntry = addSet[id] ?: return null
        val removeTimestamp = removeSet[id]

        return if (removeTimestamp == null || addEntry.second > removeTimestamp) {
            addEntry.first
        } else {
            null
        }
    }

    fun getAllEntries(): List<T> {
        return addSet.values
            .filter { (entry, addTs) ->
                val removeTs = removeSet[entry.id]
                removeTs == null || addTs > removeTs
            }
            .map { it.first }
    }

    fun getDeltaSince(clock: VectorClock): List<T> {
        return addSet.values
            .filter { (entry, _) ->
                entry is AtomCRDTEntry && !clock.happensBefore(entry.vectorClock)
            }
            .map { it.first }
    }

    fun size(): Int = getAllEntries().size
}

/**
 * G-Counter Map CRDT
 *
 * A grow-only counter per key, with per-node increments.
 * Supports positive increments only - values can only increase.
 */
class GCounterMap<K> {
    // Map of key -> (nodeId -> value)
    private val counters = ConcurrentHashMap<K, ConcurrentHashMap<String, Float>>()
    private val timestamps = ConcurrentHashMap<K, ConcurrentHashMap<String, Long>>()

    fun increment(key: K, nodeId: String, delta: Float) {
        require(delta > 0) { "G-Counter only supports positive increments" }

        val nodeCounters = counters.getOrPut(key) { ConcurrentHashMap() }
        nodeCounters.merge(nodeId, delta) { old, new -> old + new }

        val nodeTimestamps = timestamps.getOrPut(key) { ConcurrentHashMap() }
        nodeTimestamps[nodeId] = System.currentTimeMillis()
    }

    fun merge(key: K, nodeId: String, value: Float) {
        val nodeCounters = counters.getOrPut(key) { ConcurrentHashMap() }
        nodeCounters.merge(nodeId, value) { old, new -> max(old, new) }
    }

    fun value(key: K): Float {
        return counters[key]?.values?.sum() ?: 0f
    }

    fun getAllUpdates(): List<AttentionUpdate> {
        val updates = mutableListOf<AttentionUpdate>()
        counters.forEach { (key, nodeCounters) ->
            nodeCounters.forEach { (nodeId, value) ->
                @Suppress("UNCHECKED_CAST")
                updates.add(AttentionUpdate(key as String, nodeId, value))
            }
        }
        return updates
    }

    fun getDeltaSince(clock: VectorClock): List<AttentionUpdate> {
        val updates = mutableListOf<AttentionUpdate>()
        counters.forEach { (key, nodeCounters) ->
            nodeCounters.forEach { (nodeId, value) ->
                val ts = timestamps[key]?.get(nodeId) ?: 0L
                if (ts > clock.getTime(nodeId)) {
                    @Suppress("UNCHECKED_CAST")
                    updates.add(AttentionUpdate(key as String, nodeId, value))
                }
            }
        }
        return updates
    }

    fun size(): Int = counters.size
}

/**
 * Vector Clock for causality tracking
 */
class VectorClock(private val localNodeId: String) {
    private val clock = ConcurrentHashMap<String, Long>()

    init {
        clock[localNodeId] = 0L
    }

    fun increment() {
        clock.merge(localNodeId, 1L) { old, _ -> old + 1 }
    }

    fun getTime(nodeId: String): Long = clock[nodeId] ?: 0L

    fun merge(other: VectorClock) {
        other.clock.forEach { (nodeId, time) ->
            clock.merge(nodeId, time) { old, new -> max(old, new) }
        }
    }

    fun happensBefore(other: VectorClock): Boolean {
        var atLeastOneLess = false
        val allNodeIds = clock.keys + other.clock.keys

        for (nodeId in allNodeIds) {
            val thisTime = clock[nodeId] ?: 0L
            val otherTime = other.clock[nodeId] ?: 0L

            if (thisTime > otherTime) return false
            if (thisTime < otherTime) atLeastOneLess = true
        }

        return atLeastOneLess
    }

    fun copy(): VectorClock {
        val copied = VectorClock(localNodeId)
        copied.clock.putAll(clock)
        return copied
    }

    fun hash(): String {
        val sorted = clock.entries.sortedBy { it.key }
        val data = sorted.joinToString(",") { "${it.key}:${it.value}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    override fun toString(): String = clock.toString()
}

// ==================== Data Classes ====================

/**
 * Sync configuration
 */
data class SyncConfig(
    val syncIntervalMs: Long = 5000,
    val maxBatchSize: Int = 100,
    val compressionEnabled: Boolean = true,
    val encryptionEnabled: Boolean = true,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000
)

/**
 * Base interface for CRDT entries
 */
interface CRDTEntry {
    val id: String
    val timestamp: Long
    val nodeId: String
}

/**
 * Atom CRDT entry
 */
data class AtomCRDTEntry(
    val atomId: String,
    val atom: Atom?,
    override val timestamp: Long,
    override val nodeId: String,
    val vectorClock: VectorClock,
    val operation: CRDTOperation
) : CRDTEntry {
    override val id: String get() = atomId
}

/**
 * CRDT operations
 */
enum class CRDTOperation {
    ADD,
    REMOVE,
    UPDATE
}

/**
 * Attention update record
 */
data class AttentionUpdate(
    val atomId: String,
    val nodeId: String,
    val value: Float
)

/**
 * Peer connection
 */
data class PeerConnection(
    val peerId: String,
    val endpoint: String,
    val connectedAt: Long,
    var lastSyncAt: Long,
    var status: ConnectionStatus,
    var vectorClockSnapshot: VectorClock? = null
)

/**
 * Connection status
 */
enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

/**
 * Sync operation
 */
data class SyncOperation(
    val type: SyncOperationType,
    val data: Any,
    val timestamp: Long
)

/**
 * Sync operation types
 */
enum class SyncOperationType {
    ATOM_ADD,
    ATOM_REMOVE,
    ATTENTION_UPDATE,
    FULL_SYNC_REQUEST,
    DELTA_SYNC
}

/**
 * Sync data package
 */
data class SyncData(
    val sourceNodeId: String,
    val atomOperations: List<AtomCRDTEntry>,
    val attentionUpdates: List<AttentionUpdate>,
    val vectorClock: VectorClock,
    val timestamp: Long,
    val isDelta: Boolean
)

/**
 * Full sync request
 */
data class FullSyncRequest(
    val requestingNodeId: String,
    val currentClock: VectorClock
)

/**
 * Sync result
 */
data class SyncResult(
    val success: Boolean,
    val message: String
)

/**
 * Merge result
 */
data class MergeResult(
    val success: Boolean,
    val mergedCount: Int,
    val message: String
)

/**
 * Peer connection result
 */
data class PeerConnectionResult(
    val success: Boolean,
    val message: String
)

/**
 * Handshake result
 */
data class HandshakeResult(
    val success: Boolean,
    val message: String,
    val peerVectorClock: VectorClock?
)

/**
 * Sync status
 */
data class SyncStatus(
    val localNodeId: String,
    val isSyncing: Boolean,
    val connectedPeers: Int,
    val totalPeers: Int,
    val vectorClock: VectorClock,
    val atomCount: Int,
    val attentionEntryCount: Int,
    val statistics: SyncStatisticsSnapshot
)

/**
 * Convergence status with a peer
 */
data class ConvergenceStatus(
    val peerId: String,
    val isConverged: Boolean,
    val localClock: VectorClock,
    val peerClock: VectorClock,
    val pendingDeltaCount: Int,
    val lastSyncAt: Long
)

/**
 * Sync statistics
 */
class SyncStatistics {
    val localOperations = AtomicLong(0)
    val remoteOperations = AtomicLong(0)
    val syncCycles = AtomicLong(0)
    val messagesSent = AtomicLong(0)
    val messagesReceived = AtomicLong(0)
    val bytesSent = AtomicLong(0)
    val bytesReceived = AtomicLong(0)
    val conflicts = AtomicLong(0)
    val conflictsResolved = AtomicLong(0)

    fun snapshot(): SyncStatisticsSnapshot = SyncStatisticsSnapshot(
        localOperations = localOperations.get(),
        remoteOperations = remoteOperations.get(),
        syncCycles = syncCycles.get(),
        messagesSent = messagesSent.get(),
        messagesReceived = messagesReceived.get(),
        bytesSent = bytesSent.get(),
        bytesReceived = bytesReceived.get(),
        conflicts = conflicts.get(),
        conflictsResolved = conflictsResolved.get()
    )
}

/**
 * Sync statistics snapshot
 */
data class SyncStatisticsSnapshot(
    val localOperations: Long,
    val remoteOperations: Long,
    val syncCycles: Long,
    val messagesSent: Long,
    val messagesReceived: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val conflicts: Long,
    val conflictsResolved: Long
)
