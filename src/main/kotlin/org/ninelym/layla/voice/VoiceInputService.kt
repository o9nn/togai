package org.ninelym.layla.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.OpenAICompatibleModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Voice Input Service
 * Phase 2: Enhancement - Speech-to-Text capabilities
 * 
 * Records the microphone (16 kHz mono PCM) and transcribes the recording with the served
 * speech model (a Whisper-compatible `/audio/transcriptions` endpoint, e.g. a local
 * whisper.cpp server). Requires the RECORD_AUDIO permission.
 */
class VoiceInputService(
    private val endpointProvider: () -> Result<OpenAICompatibleModel> = { LanguageModels.currentEndpoint() },
    private val transcriptionModel: String = "whisper-1"
) {
    
    private var isInitialized = false
    private var isRecording = false
    private var currentEngine: SpeechEngine = SpeechEngine.DEFAULT
    
    private val recognitionListeners = mutableListOf<RecognitionListener>()
    private val recordingSessions = ConcurrentHashMap<String, RecordingSession>()
    
    /**
     * Initialize voice input service
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            // Initialize speech recognition engine
            initializeEngine(currentEngine)
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Start voice recording and recognition
     */
    suspend fun startRecording(
        language: String = "en-US",
        continuous: Boolean = false
    ): Result<String> {
        if (!isInitialized) {
            return Result.failure(Exception("Service not initialized"))
        }
        
        if (isRecording) {
            return Result.failure(Exception("Already recording"))
        }
        
        val sessionId = generateSessionId()
        val session = RecordingSession(
            id = sessionId,
            startTime = System.currentTimeMillis(),
            language = language,
            continuous = continuous
        )
        
        val recorder = try {
            startCapture()
        } catch (e: SecurityException) {
            return Result.failure(Exception("Microphone permission (RECORD_AUDIO) not granted", e))
        } catch (e: Exception) {
            return Result.failure(e)
        }
        recordingSessions[sessionId] = session
        captures[sessionId] = recorder
        isRecording = true
        
        return Result.success(sessionId)
    }
    
    private class Capture(val record: AudioRecord, val pcm: ByteArrayOutputStream, val thread: Thread)
    
    private val captures = ConcurrentHashMap<String, Capture>()
    
    private fun startCapture(): Capture {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IllegalStateException("Microphone recording is not supported on this device")
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Could not open the microphone")
        }
        val pcm = ByteArrayOutputStream()
        record.startRecording()
        val thread = Thread {
            val buffer = ByteArray(minBuffer)
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) synchronized(pcm) { pcm.write(buffer, 0, n) }
            }
        }.apply { isDaemon = true; start() }
        return Capture(record, pcm, thread)
    }
    
    private fun finishCapture(capture: Capture): ByteArray {
        capture.record.stop()
        capture.thread.join(1000)
        capture.record.release()
        return synchronized(capture.pcm) { capture.pcm.toByteArray() }
    }
    
    /**
     * Stop recording and get transcribed text
     */
    suspend fun stopRecording(sessionId: String): Result<TranscriptionResult> {
        val session = recordingSessions[sessionId]
            ?: return Result.failure(Exception("Session not found"))
        
        val capture = captures.remove(sessionId)
            ?: return Result.failure(Exception("No audio captured for session"))
        return try {
            isRecording = false
            
            val wav = withContext(Dispatchers.IO) { writeWav(finishCapture(capture)) }
            val transcribedText = try {
                transcribe(wav).getOrThrow()
            } finally {
                wav.delete()
            }
            
            val result = TranscriptionResult(
                text = transcribedText,
                confidence = null,
                duration = System.currentTimeMillis() - session.startTime,
                language = session.language
            )
            
            recordingSessions.remove(sessionId)
            
            // Notify listeners
            recognitionListeners.forEach { it.onRecognitionComplete(result) }
            
            Result.success(result)
        } catch (e: Exception) {
            recordingSessions.remove(sessionId)
            recognitionListeners.forEach { it.onRecognitionError(e.message ?: "Transcription failed") }
            Result.failure(e)
        }
    }
    
    /**
     * Transcribe an existing audio file with the served speech model
     */
    suspend fun transcribe(audio: File): Result<String> {
        val endpoint = endpointProvider().getOrElse { return Result.failure(it) }
        return endpoint.transcribe(audio, transcriptionModel)
    }
    
    /**
     * Cancel ongoing recording
     */
    fun cancelRecording(sessionId: String): Result<Unit> {
        return try {
            recordingSessions.remove(sessionId)
            captures.remove(sessionId)?.let { finishCapture(it) }
            isRecording = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add recognition listener
     */
    fun addListener(listener: RecognitionListener) {
        recognitionListeners.add(listener)
    }
    
    /**
     * Remove recognition listener
     */
    fun removeListener(listener: RecognitionListener) {
        recognitionListeners.remove(listener)
    }
    
    /**
     * Set speech recognition engine
     */
    suspend fun setEngine(engine: SpeechEngine): Result<Unit> {
        return try {
            currentEngine = engine
            initializeEngine(engine)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get supported languages
     */
    fun getSupportedLanguages(): List<String> {
        return listOf(
            "en-US", "en-GB", "es-ES", "fr-FR", "de-DE",
            "it-IT", "pt-BR", "ja-JP", "ko-KR", "zh-CN"
        )
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
    
    private fun initializeEngine(engine: SpeechEngine) {
        if (engine != SpeechEngine.DEFAULT && engine != SpeechEngine.WHISPER) {
            throw UnsupportedOperationException("$engine recognition isn't bundled; use a Whisper-compatible server")
        }
        endpointProvider().getOrThrow()
    }
    
    /** Wraps 16-bit mono PCM in a WAV container. */
    private fun writeWav(pcm: ByteArray): File {
        val file = File.createTempFile("voice-input", ".wav")
        RandomAccessFile(file, "rw").use { out ->
            fun intLE(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun shortLE(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            out.write("RIFF".toByteArray()); out.write(intLE(36 + pcm.size)); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); out.write(intLE(16)); out.write(shortLE(1)); out.write(shortLE(1))
            out.write(intLE(SAMPLE_RATE)); out.write(intLE(SAMPLE_RATE * 2)); out.write(shortLE(2)); out.write(shortLE(16))
            out.write("data".toByteArray()); out.write(intLE(pcm.size)); out.write(pcm)
        }
        return file
    }
    
    private fun generateSessionId(): String {
        return "rec_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
    
    companion object {
        private const val SAMPLE_RATE = 16000
    }
}

/**
 * Speech recognition engine types
 */
enum class SpeechEngine {
    DEFAULT,
    GOOGLE,
    WHISPER,
    VOSK
}

/**
 * Recording session data
 */
data class RecordingSession(
    val id: String,
    val startTime: Long,
    val language: String,
    val continuous: Boolean
)

/**
 * Transcription result
 */
data class TranscriptionResult(
    val text: String,
    /** Null when the recognizer doesn't report confidence. */
    val confidence: Float?,
    val duration: Long,
    val language: String
)

/**
 * Recognition listener interface
 */
interface RecognitionListener {
    fun onRecognitionComplete(result: TranscriptionResult)
    fun onRecognitionError(error: String)
}
