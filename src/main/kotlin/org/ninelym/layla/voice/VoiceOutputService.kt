package org.ninelym.layla.voice

import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import org.ninelym.ai.CharacterSystem
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.OpenAICompatibleModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Voice Output Service
 * Phase 2: Enhancement - Text-to-Speech capabilities
 * 
 * Synthesizes speech with the served TTS model (an OpenAI-compatible `/audio/speech` endpoint,
 * e.g. a local Kokoro/Piper server) and plays it. Character-specific voices map to the
 * server's voice names.
 */
class VoiceOutputService(
    private val endpointProvider: () -> Result<OpenAICompatibleModel> = { LanguageModels.currentEndpoint() },
    private val ttsModel: String = "tts-1"
) {
    
    private var isInitialized = false
    private var currentEngine: TTSEngine = TTSEngine.DEFAULT
    private val characterVoices = ConcurrentHashMap<String, VoiceProfile>()
    
    private val speechQueue = mutableListOf<SpeechRequest>()
    private var isSpeaking = false
    
    /**
     * Initialize voice output service
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            initializeEngine(currentEngine)
            loadDefaultVoiceProfiles()
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Speak text using TTS
     */
    suspend fun speak(
        text: String,
        characterId: String? = null,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        volume: Float = 1.0f
    ): Result<Unit> {
        if (!isInitialized) {
            return Result.failure(Exception("Service not initialized"))
        }
        
        return try {
            val voiceProfile = characterId?.let { characterVoices[it] } ?: getDefaultVoice()
            
            val request = SpeechRequest(
                text = text,
                voiceProfile = voiceProfile,
                pitch = pitch,
                rate = rate,
                volume = volume,
                timestamp = System.currentTimeMillis()
            )
            
            performSpeech(request)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Queue speech for later playback
     */
    fun queueSpeech(
        text: String,
        characterId: String? = null,
        pitch: Float = 1.0f,
        rate: Float = 1.0f
    ): Result<Unit> {
        return try {
            val voiceProfile = characterId?.let { characterVoices[it] } ?: getDefaultVoice()
            
            val request = SpeechRequest(
                text = text,
                voiceProfile = voiceProfile,
                pitch = pitch,
                rate = rate,
                volume = 1.0f,
                timestamp = System.currentTimeMillis()
            )
            
            speechQueue.add(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Play queued speech
     */
    suspend fun playQueue(): Result<Unit> {
        return try {
            while (speechQueue.isNotEmpty()) {
                val request = speechQueue.removeAt(0)
                performSpeech(request)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stop current speech
     */
    fun stop(): Result<Unit> {
        return try {
            isSpeaking = false
            speechQueue.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Set character-specific voice profile
     */
    fun setCharacterVoice(characterId: String, profile: VoiceProfile): Result<Unit> {
        return try {
            characterVoices[characterId] = profile
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get character voice profile
     */
    fun getCharacterVoice(characterId: String): VoiceProfile? {
        return characterVoices[characterId]
    }
    
    /**
     * Set TTS engine
     */
    suspend fun setEngine(engine: TTSEngine): Result<Unit> {
        return try {
            currentEngine = engine
            initializeEngine(engine)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Voices of the OpenAI speech API; local servers (Kokoro, Piper...) may name theirs differently
     */
    fun getAvailableVoices(): List<VoiceInfo> {
        return listOf(
            VoiceInfo("nova", "Nova", "en-US"),
            VoiceInfo("shimmer", "Shimmer", "en-US"),
            VoiceInfo("alloy", "Alloy", "en-US"),
            VoiceInfo("echo", "Echo", "en-US"),
            VoiceInfo("onyx", "Onyx", "en-US")
        )
    }
    
    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = isSpeaking
    
    private fun initializeEngine(engine: TTSEngine) {
        if (engine != TTSEngine.DEFAULT) {
            throw UnsupportedOperationException("$engine isn't bundled; serve a TTS model behind /audio/speech")
        }
        endpointProvider().getOrThrow()
    }
    
    /**
     * Synthesize [text] to an audio file without playing it
     */
    suspend fun synthesize(text: String, characterId: String? = null): Result<File> {
        val voice = characterId?.let { characterVoices[it] } ?: getDefaultVoice()
        val endpoint = endpointProvider().getOrElse { return Result.failure(it) }
        return endpoint.speech(text, voice.voiceId, ttsModel).map { bytes ->
            File.createTempFile("voice-output", ".mp3").apply { writeBytes(bytes) }
        }
    }
    
    private fun loadDefaultVoiceProfiles() {
        // Load default voice profiles for characters
        characterVoices["assistant"] = VoiceProfile(
            voiceId = "nova",
            pitch = 1.0f,
            rate = 1.0f,
            language = "en-US"
        )
        
        characterVoices["creative"] = VoiceProfile(
            voiceId = "shimmer",
            pitch = 1.1f,
            rate = 1.05f,
            language = "en-US"
        )
        
        characterVoices["analyst"] = VoiceProfile(
            voiceId = "onyx",
            pitch = 0.95f,
            rate = 0.95f,
            language = "en-US"
        )
    }
    
    private fun getDefaultVoice(): VoiceProfile {
        return VoiceProfile(
            voiceId = "alloy",
            pitch = 1.0f,
            rate = 1.0f,
            language = "en-US"
        )
    }
    
    private suspend fun performSpeech(request: SpeechRequest) {
        val endpoint = endpointProvider().getOrThrow()
        val audio = endpoint.speech(request.text, request.voiceProfile.voiceId, ttsModel).getOrThrow()
        val file = File.createTempFile("voice-output", ".mp3").apply { writeBytes(audio) }
        isSpeaking = true
        try {
            play(file, request)
        } finally {
            isSpeaking = false
            file.delete()
        }
    }
    
    private suspend fun play(file: File, request: SpeechRequest) = suspendCancellableCoroutine<Unit> { cont ->
        val player = MediaPlayer()
        cont.invokeOnCancellation { player.release() }
        try {
            player.setDataSource(file.path)
            player.setVolume(request.volume, request.volume)
            player.setOnCompletionListener { it.release(); if (cont.isActive) cont.resume(Unit) }
            player.setOnErrorListener { mp, what, extra ->
                mp.release()
                if (cont.isActive) cont.resumeWithException(IllegalStateException("Playback failed ($what/$extra)"))
                true
            }
            player.prepare()
            if (request.rate != 1.0f || request.pitch != 1.0f) {
                player.playbackParams = player.playbackParams.setSpeed(request.rate).setPitch(request.pitch)
            }
            player.start()
        } catch (e: Exception) {
            player.release()
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
}

/**
 * TTS engine types
 */
enum class TTSEngine {
    DEFAULT,
    GOOGLE,
    RHVOICE,
    ACAPELA,
    SHERPA_ONNX
}

/**
 * Voice profile for character-specific voices
 */
data class VoiceProfile(
    val voiceId: String,
    val pitch: Float,
    val rate: Float,
    val language: String
)

/**
 * Voice information
 */
data class VoiceInfo(
    val id: String,
    val name: String,
    val language: String
)

/**
 * Speech request
 */
data class SpeechRequest(
    val text: String,
    val voiceProfile: VoiceProfile,
    val pitch: Float,
    val rate: Float,
    val volume: Float,
    val timestamp: Long
)
