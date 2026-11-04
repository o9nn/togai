package org.ninelym.layla.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.ask
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cloud Sync Service
 * 
 * Provides optional privacy-preserving cloud synchronization for settings,
 * preferences, and encrypted data across multiple devices.
 * 
 * Features:
 * - Privacy-preserving sync with end-to-end encryption
 * - Differential updates for bandwidth efficiency
 * - Conflict resolution strategies
 * - Multi-device support
 * - Selective sync options
 * - Offline mode with queued operations
 * 
 * Phase 3: Integration (Optional)
 * 
 * Privacy Note: This service is entirely optional and disabled by default.
 * All data is encrypted before leaving the device.
 */
class CloudSyncService(
    /** Base URL of a key/value sync server (`GET`/`PUT {serverUrl}/{userId}/{dataType}`). */
    private val serverUrl: String? = null,
    private val http: okhttp3.OkHttpClient = okhttp3.OkHttpClient(),
    /** Resolves MERGE conflicts by asking the language model to reconcile both versions. */
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() },
    private val encryptionKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
) {
    
    // State flows for reactive updates
    private val _syncState = MutableStateFlow<SyncState>(SyncState.DISCONNECTED)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _syncedDevices = MutableStateFlow<List<SyncedDevice>>(emptyList())
    val syncedDevices: StateFlow<List<SyncedDevice>> = _syncedDevices.asStateFlow()
    
    private val _pendingOperations = MutableStateFlow<List<SyncOperation>>(emptyList())
    val pendingOperations: StateFlow<List<SyncOperation>> = _pendingOperations.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()
    
    private var syncEnabled = false
    private var userId: String? = null
    private var totalBytes = 0L
    private val json = Json { ignoreUnknownKeys = true }
    
    /** Local copy of each data type, as last written or merged here. */
    private val localData = java.util.concurrent.ConcurrentHashMap<SyncDataType, Map<String, String>>()
    
    /** Unresolved conflicts: id -> (type, key, local value, remote value). */
    private data class Conflict(val dataType: SyncDataType, val key: String, val local: String, val remote: String)
    private val conflicts = java.util.concurrent.ConcurrentHashMap<String, Conflict>()
    
    /**
     * Initialize cloud sync service
     */
    suspend fun initialize(userId: String): Result<Unit> {
        return try {
            if (!syncEnabled) {
                _syncState.value = SyncState.DISABLED
                return Result.success(Unit) // Sync is disabled
            }
            
            _syncState.value = SyncState.CONNECTING
            val url = serverUrl ?: throw IllegalStateException("No sync server configured")
            // Any HTTP answer means the server is reachable
            withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(url).get().build()).execute().close() }
            this.userId = userId
            _syncState.value = SyncState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Enable or disable sync
     */
    fun setSyncEnabled(enabled: Boolean) {
        syncEnabled = enabled
        if (!enabled) {
            _syncState.value = SyncState.DISABLED
        }
    }
    
    /**
     * Sync data to cloud: merges [data] into the local copy and uploads it encrypted
     */
    suspend fun syncToCloud(
        dataType: SyncDataType,
        data: Map<String, Any>
    ): Result<SyncResult> {
        return try {
            requireReady()
            _syncState.value = SyncState.SYNCING
            
            val merged = (localData[dataType] ?: emptyMap()) + data.mapValues { it.value.toString() }
            localData[dataType] = merged
            val result = uploadToCloud(dataType, encryptData(merged))
            
            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = SyncState.CONNECTED
            Result.success(result)
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Sync data from cloud: downloads, decrypts and merges with the local copy
     */
    suspend fun syncFromCloud(dataType: SyncDataType): Result<Map<String, Any>> {
        return try {
            requireReady()
            _syncState.value = SyncState.SYNCING
            
            val remote = downloadFromCloud(dataType)?.let { decryptData(it) } ?: emptyMap()
            val mergedData = mergeData(dataType, remote)
            
            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = SyncState.CONNECTED
            Result.success(mergedData)
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Perform full bidirectional sync
     */
    suspend fun performFullSync(): Result<FullSyncResult> {
        return try {
            requireReady()
            val synced = mutableListOf<SyncDataType>()
            val failures = mutableListOf<String>()
            
            for (dataType in SyncDataType.values()) {
                // Pull remote changes, then push the merged local copy
                val downloadResult = syncFromCloud(dataType)
                val uploadResult = downloadResult.fold(
                    onSuccess = { syncToCloud(dataType, emptyMap()) },
                    onFailure = { Result.failure(it) }
                )
                if (uploadResult.isSuccess) synced += dataType
                else failures += "${dataType.name}: ${uploadResult.exceptionOrNull()?.message}"
            }
            
            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = if (failures.isEmpty()) SyncState.CONNECTED else SyncState.ERROR
            Result.success(FullSyncResult(
                success = failures.isEmpty(),
                syncedTypes = synced,
                timestamp = System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Unresolved conflicts found while merging remote data, by id
     */
    fun getConflicts(): Map<String, Pair<String, String>> = conflicts.mapValues { it.value.local to it.value.remote }
    
    /**
     * Resolve sync conflict
     */
    suspend fun resolveConflict(
        dataType: SyncDataType,
        conflictId: String,
        resolution: ConflictResolution
    ): Result<Unit> {
        return try {
            val conflict = conflicts[conflictId]?.takeIf { it.dataType == dataType }
                ?: return Result.failure(IllegalArgumentException("No conflict $conflictId for $dataType"))
            val value = when (resolution) {
                ConflictResolution.USE_LOCAL -> conflict.local
                ConflictResolution.USE_REMOTE -> conflict.remote
                ConflictResolution.MERGE -> performIntelligentMerge(conflict)
            }
            syncToCloud(dataType, mapOf(conflict.key to value)).getOrThrow()
            conflicts.remove(conflictId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Register a new device for sync
     */
    suspend fun registerDevice(
        deviceName: String,
        deviceType: DeviceType
    ): Result<SyncedDevice> {
        return try {
            val device = SyncedDevice(
                id = "device_${System.currentTimeMillis()}",
                name = deviceName,
                type = deviceType,
                registeredAt = System.currentTimeMillis(),
                lastSyncTime = null
            )
            
            _syncedDevices.value = _syncedDevices.value + device
            
            Result.success(device)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a device from sync
     */
    suspend fun removeDevice(deviceId: String): Result<Unit> {
        return try {
            _syncedDevices.value = _syncedDevices.value.filter { it.id != deviceId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Queue operation for later sync (offline mode)
     */
    fun queueOperation(operation: SyncOperation) {
        _pendingOperations.value = _pendingOperations.value + operation
    }
    
    /**
     * Process pending operations when connection is restored; failed ones stay queued
     */
    suspend fun processPendingOperations(): Result<Int> {
        return try {
            var processed = 0
            val remaining = mutableListOf<SyncOperation>()
            for (operation in _pendingOperations.value) {
                if (executeOperation(operation).isSuccess) processed++ else remaining += operation
            }
            _pendingOperations.value = remaining
            Result.success(processed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get sync statistics
     */
    fun getSyncStatistics(): SyncStatistics {
        return SyncStatistics(
            enabled = syncEnabled,
            lastSyncTime = _lastSyncTime.value,
            deviceCount = _syncedDevices.value.size,
            pendingOperations = _pendingOperations.value.size,
            totalDataSynced = totalBytes
        )
    }
    
    // Private helper methods
    
    private fun requireReady() {
        if (!syncEnabled) throw IllegalStateException("Cloud sync is disabled")
        if (userId == null || serverUrl == null) throw IllegalStateException("Cloud sync is not connected")
    }
    
    private fun objectUrl(dataType: SyncDataType) = "${serverUrl!!.trimEnd('/')}/$userId/${dataType.name.lowercase()}"
    
    /** AES-256-GCM; the payload is base64(iv || ciphertext). */
    private fun encryptData(data: Map<String, String>): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, encryptionKey) }
        val sealed = cipher.iv + cipher.doFinal(json.encodeToString(data).toByteArray())
        return java.util.Base64.getEncoder().encode(sealed)
    }
    
    private fun decryptData(encryptedData: ByteArray): Map<String, String> {
        val sealed = java.util.Base64.getDecoder().decode(encryptedData)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        }
        return json.decodeFromString(String(cipher.doFinal(sealed.copyOfRange(12, sealed.size))))
    }
    
    private suspend fun uploadToCloud(dataType: SyncDataType, payload: ByteArray): SyncResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(objectUrl(dataType))
            .put(payload.toRequestBody("application/octet-stream".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Upload failed: HTTP ${response.code}")
        }
        totalBytes += payload.size
        SyncResult(success = true, timestamp = System.currentTimeMillis(), bytesTransferred = payload.size.toLong())
    }
    
    /** Null when the server has nothing stored yet. */
    private suspend fun downloadFromCloud(dataType: SyncDataType): ByteArray? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(objectUrl(dataType)).get().build()).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IllegalStateException("Download failed: HTTP ${response.code}")
                else -> response.body!!.bytes().also { totalBytes += it.size }
            }
        }
    }
    
    /**
     * Key-wise merge: keys present on one side are taken; differing values are kept local and
     * recorded as conflicts for [resolveConflict].
     */
    private fun mergeData(dataType: SyncDataType, remoteData: Map<String, String>): Map<String, Any> {
        val local = localData[dataType] ?: emptyMap()
        val merged = remoteData.toMutableMap()
        for ((key, value) in local) {
            val remote = remoteData[key]
            if (remote != null && remote != value) {
                conflicts["${dataType.name}:$key"] = Conflict(dataType, key, value, remote)
            }
            merged[key] = value
        }
        localData[dataType] = merged
        return merged
    }
    
    /** The model reconciles both versions of the value into one. */
    private suspend fun performIntelligentMerge(conflict: Conflict): String =
        modelProvider().getOrThrow().ask(
            "Two devices edited the same synced setting differently. Merge them into a single value that " +
                "preserves the intent of both. Output only the merged value.",
            "Key: ${conflict.key}\nThis device: ${conflict.local}\nOther device: ${conflict.remote}",
            GenerationOptions(temperature = 0f, maxTokens = 256)
        ).getOrThrow().trim()
    
    private suspend fun executeOperation(operation: SyncOperation): Result<*> = when (operation) {
        is SyncOperation.Upload -> syncToCloud(operation.dataType, operation.data)
        is SyncOperation.Download -> syncFromCloud(operation.dataType)
        is SyncOperation.Delete -> runCatching {
            requireReady()
            localData[operation.dataType] = (localData[operation.dataType] ?: emptyMap()) - operation.id
            uploadToCloud(operation.dataType, encryptData(localData[operation.dataType]!!))
        }
    }
    
    /**
     * Disconnect from cloud sync
     */
    fun disconnect() {
        _syncState.value = SyncState.DISCONNECTED
    }
}

// Data classes

data class SyncedDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val registeredAt: Long,
    val lastSyncTime: Long?
)

enum class DeviceType {
    PHONE,
    TABLET,
    DESKTOP,
    WEB
}

enum class SyncDataType {
    PREFERENCES,
    CHAT_HISTORY,
    CHARACTERS,
    WORKFLOWS,
    DOCUMENTS,
    CUSTOM
}

sealed class SyncOperation {
    data class Upload(val dataType: SyncDataType, val data: Map<String, Any>) : SyncOperation()
    data class Download(val dataType: SyncDataType) : SyncOperation()
    data class Delete(val dataType: SyncDataType, val id: String) : SyncOperation()
}

data class SyncResult(
    val success: Boolean,
    val timestamp: Long,
    val bytesTransferred: Long = 0L
)

data class FullSyncResult(
    val success: Boolean,
    val syncedTypes: List<SyncDataType>,
    val timestamp: Long
)

enum class ConflictResolution {
    USE_LOCAL,
    USE_REMOTE,
    MERGE
}

enum class SyncState {
    DISABLED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SYNCING,
    ERROR
}

data class SyncStatistics(
    val enabled: Boolean,
    val lastSyncTime: Long?,
    val deviceCount: Int,
    val pendingOperations: Int,
    val totalDataSynced: Long
)
