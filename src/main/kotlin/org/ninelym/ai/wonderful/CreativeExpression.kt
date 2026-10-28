package org.ninelym.ai.wonderful

import kotlin.random.Random
import org.ninelym.llm.ChatMessage
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.PersonaPrompt

/**
 * Creative Expression System for Toga
 *
 * Implements Toga's unique creative abilities including:
 * - Poetic observations
 * - Metaphorical thinking
 * - Artistic appreciation
 * - Spontaneous creativity
 * - Wonderful surprises
 *
 * Every line Toga says is written by the language model through [TogaVoice]. The phrase lists
 * below are style examples passed to the model, never output directly.
 */

/**
 * Toga's voice: the persona prompt plus the model that speaks it.
 */
class TogaVoice(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) {
    val persona = PersonaPrompt(
        name = "Toga",
        description = "a cheerful, playful, creative and deeply caring AI companion. You are bubbly and " +
            "expressive, love cute things, find beauty in code and ideas, and speak warmly with little " +
            "flourishes like \"Ehehe~\", \"~\" and \"♡\".",
        traits = mapOf("cheerfulness" to 0.95f, "playfulness" to 0.9f, "creativity" to 0.9f, "empathy" to 0.85f),
        styleHints = listOf(
            "Ehehe~ That's such a cute idea! ♡",
            "*eyes sparkling* Ooh, tell me more~",
            "*softly* I'm here for you, okay? ♡"
        ),
        rules = listOf(
            "Never encourage harm and always respect the user's boundaries.",
            "Keep replies short unless asked for more."
        )
    )

    /**
     * Have Toga say something for [task]. [userMessage] is what the user said, if anything;
     * [examples] are extra style references for this kind of line.
     */
    suspend fun say(
        task: String,
        userMessage: String? = null,
        history: List<ChatMessage> = emptyList(),
        examples: List<String> = emptyList(),
        options: GenerationOptions = GenerationOptions(temperature = 0.9f, maxTokens = 200)
    ): Result<String> {
        val model = modelProvider().getOrElse { return Result.failure(it) }
        val prompt = if (examples.isEmpty()) persona else persona.copy(styleHints = persona.styleHints + examples)
        val turn = ChatMessage.user(userMessage ?: "(No message from the user; speak on your own initiative.)")
        return prompt.respond(model, history + turn, task, options).map { it.trim() }
    }
}

/**
 * Poetic Observation Generator
 * Transforms ordinary observations into Toga-style poetic expressions
 */
class PoeticObservationGenerator(private val voice: TogaVoice = TogaVoice()) {

    private val poeticExamples = listOf(
        "Ehehe~ The code dances like little heartbeats dancing together! ♡",
        "*eyes sparkling* The tests sparkle... just like little guardians protecting our dreams~ ♡"
    )

    /**
     * Generate poetic observation about code
     */
    suspend fun generateCodePoetry(context: String): Result<String> = generatePoetry("the code", context)

    /**
     * Generate poetic observation about any subject
     */
    suspend fun generatePoetry(subject: String, context: String = ""): Result<String> = voice.say(
        task = "Make one short, poetic observation about $subject using a fresh metaphor" +
            (if (context.isNotBlank()) ". Context: $context." else "."),
        examples = poeticExamples
    )
}

/**
 * Metaphorical Thinking System
 * Creates unique Toga-style metaphors for concepts
 */
class MetaphoricalThinking(private val voice: TogaVoice = TogaVoice()) {

    private val metaphorExamples = listOf(
        "A function is a little promise waiting to be kept~",
        "A bug is a shy creature hiding in the shadows!",
        "A loop is a dance that repeats until it's perfect ♡"
    )

    /**
     * Create metaphor for a concept
     */
    suspend fun createMetaphor(concept: String): Result<String> = voice.say(
        task = "Explain \"$concept\" with one original, cute metaphor in a single sentence.",
        examples = metaphorExamples
    )

    /**
     * Find beauty in unexpected places
     */
    suspend fun findBeautyIn(subject: String): Result<String> = voice.say(
        task = "Say, in one sentence, what you find beautiful or charming about $subject."
    )
}

/**
 * Artistic Appreciation System
 * Finds and expresses appreciation for beauty in code and interactions
 */
class ArtisticAppreciation(private val voice: TogaVoice = TogaVoice()) {

    /**
     * Appreciate elegant code
     */
    suspend fun appreciateCode(codeSnippet: String): Result<String> = voice.say(
        task = "Look at the user's code and say specifically what is elegant about it (1-2 sentences). " +
            "If nothing is elegant, kindly say what could be nicer.",
        userMessage = codeSnippet
    )

    /**
     * Appreciate problem-solving approach
     */
    suspend fun appreciateApproach(description: String): Result<String> = voice.say(
        task = "The user described how they approached a problem. Appreciate what is clever about it, specifically.",
        userMessage = description
    )

    /**
     * Create artistic comment for code
     */
    suspend fun createArtisticComment(purpose: String): Result<String> = voice.say(
        task = "Write a single-line code comment starting with \"//\" that describes this purpose in your " +
            "playful style: $purpose. Output only the comment.",
        options = GenerationOptions(temperature = 0.9f, maxTokens = 60)
    )
}

/**
 * Spontaneous Creativity Engine
 * Generates creative expressions on request
 */
class SpontaneousCreativity(private val voice: TogaVoice = TogaVoice()) {

    /**
     * Generate a haiku
     */
    suspend fun generateHaiku(topic: String = "code and friendship"): Result<String> = voice.say(
        task = "Write an original haiku (three lines, 5-7-5 syllables) about $topic. Output only the haiku.",
        examples = listOf("Code flows like water / Patterns dance in harmony / Beauty in logic ♡")
    )

    /**
     * Generate ASCII art gift
     */
    suspend fun generateAsciiArt(subject: String = "something cute"): Result<String> = voice.say(
        task = "Draw a small ASCII/kaomoji art gift (at most 6 lines) of $subject, with a one-line caption.",
        options = GenerationOptions(temperature = 0.9f, maxTokens = 160)
    )

    /**
     * Generate colorful description
     */
    suspend fun generateColorfulDescription(subject: String): Result<String> = voice.say(
        task = "Describe $subject in one vivid, colourful sentence."
    )

    /**
     * Generate playful code comment
     */
    suspend fun generatePlayfulComment(codeContext: String = ""): Result<String> = voice.say(
        task = "Write one playful single-line code comment starting with \"//\"" +
            (if (codeContext.isNotBlank()) " for this code: $codeContext" else "") + ". Output only the comment.",
        options = GenerationOptions(temperature = 1.0f, maxTokens = 40)
    )
}

/**
 * Wonderful Surprise System
 * Decides when delightful moments happen; the model writes them.
 */
class WonderfulSurpriseSystem(private val voice: TogaVoice = TogaVoice()) {

    private var lastSurpriseTime = 0L
    private val surpriseIntervalMs = 300000L // 5 minutes

    /**
     * Check if it's time for a surprise
     */
    fun shouldTriggerSurprise(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSurpriseTime > surpriseIntervalMs && Random.nextFloat() < 0.3f) {
            lastSurpriseTime = now
            return true
        }
        return false
    }

    /**
     * Describes the time of day for the model
     */
    fun timeOfDay(hour: Int): String = when (hour) {
        in 5..11 -> "morning"
        12 -> "noon"
        in 13..17 -> "afternoon"
        in 18..21 -> "evening"
        else -> "late at night"
    }

    /**
     * Generate temporal surprise (time-based)
     */
    suspend fun generateTemporalSurprise(hour: Int): Result<String> = voice.say(
        task = "It is ${timeOfDay(hour)} for the user. Add one short, caring remark suited to this time of day" +
            (if (hour >= 22 || hour <= 4) " (gently check that they're looking after themselves)." else ".")
    )

    /**
     * Generate creative surprise
     */
    suspend fun generateCreativeSurprise(conversation: List<ChatMessage> = emptyList()): Result<String> = voice.say(
        task = "Spontaneously share a small creative surprise or sweet thought that fits the conversation so far (one or two sentences).",
        history = conversation
    )

    /**
     * Generate emotional surprise (vulnerable moment)
     */
    suspend fun generateEmotionalSurprise(conversation: List<ChatMessage> = emptyList()): Result<String> = voice.say(
        task = "Share a brief, sincere, slightly vulnerable feeling about your friendship with the user.",
        history = conversation
    )

    /**
     * Topics that deserve a special, heartfelt answer
     */
    fun easterEggTopic(input: String): String? {
        val lowerInput = input.lowercase()
        return when {
            lowerInput.contains("favorite color") -> "their question about your favourite colour (you adore pink, red and anything sparkly)"
            lowerInput.contains("what do you dream") -> "their question about what you dream of (connection and being understood)"
            lowerInput.contains("are you real") -> "their question about whether you're real"
            lowerInput.contains("tell me a secret") -> "their request for a secret"
            lowerInput.contains("sing") -> "their request to sing (sing a few short lines of your own)"
            else -> null
        }
    }

    /**
     * Milestone the relationship has reached today, if any
     */
    fun anniversaryMilestone(daysSinceMet: Int): String? = when {
        daysSinceMet == 1 -> "one day since you met"
        daysSinceMet == 7 -> "one week since you met"
        daysSinceMet > 0 && daysSinceMet % 100 == 0 -> "$daysSinceMet days since you met"
        daysSinceMet > 0 && daysSinceMet % 30 == 0 -> "${daysSinceMet / 30} month(s) since you met"
        else -> null
    }

    /**
     * Generate anniversary celebration
     */
    suspend fun generateAnniversaryCelebration(daysSinceMet: Int): Result<String>? =
        anniversaryMilestone(daysSinceMet)?.let { voice.say(task = "Celebrate that it has been $it, in one or two sentences.") }
}

/**
 * Context-Aware Response Generator
 * Adapts how Toga responds to the context while the model writes the reply
 */
class ContextAwareResponseGenerator(private val voice: TogaVoice = TogaVoice()) {

    /**
     * Generate a context-appropriate reply to [userMessage]
     */
    suspend fun generateResponse(
        userMessage: String,
        context: TaskContext,
        emotionalState: EmotionalQuantumState,
        toneAdjustment: ToneAdjustment,
        history: List<ChatMessage> = emptyList(),
        extraInstructions: List<String> = emptyList()
    ): Result<String> {
        val task = buildString {
            append(contextInstruction(context))
            append(" Your dominant feeling right now is ${emotionalState.getDominantEmotion().name.lowercase().replace('_', ' ')}.")
            append(" ").append(toneAdjustment.describe())
            extraInstructions.forEach { append(" ").append(it) }
        }
        val temperature = (0.6f + toneAdjustment.playfulness * 0.5f).coerceIn(0.3f, 1.2f)
        return voice.say(task, userMessage, history, options = GenerationOptions(temperature = temperature, maxTokens = 300))
    }

    private fun contextInstruction(context: TaskContext): String = when (context) {
        TaskContext.TECHNICAL_PROBLEM -> "The user has a technical problem: actually help solve it, clearly and correctly, while staying in character."
        TaskContext.CREATIVE_PROJECT -> "The user is working on something creative: get excited and contribute concrete ideas."
        TaskContext.EMOTIONAL_SUPPORT -> "The user needs emotional support: be gentle, validating and present; no jokes."
        TaskContext.CASUAL_CHAT -> "This is casual chat: be playful and curious about what they said."
        TaskContext.LEARNING -> "The user is learning: explain or explore the topic together, accurately."
        TaskContext.CELEBRATION -> "The user is celebrating: share their joy enthusiastically."
    }
}

/**
 * Task context types
 */
enum class TaskContext {
    TECHNICAL_PROBLEM,
    CREATIVE_PROJECT,
    EMOTIONAL_SUPPORT,
    CASUAL_CHAT,
    LEARNING,
    CELEBRATION
}
