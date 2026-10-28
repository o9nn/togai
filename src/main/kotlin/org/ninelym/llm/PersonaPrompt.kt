package org.ninelym.llm

/**
 * Builds the system prompt that makes a model speak as a character. Characters' former
 * canned phrases are passed only as style hints for the model, never emitted directly.
 */
data class PersonaPrompt(
    val name: String,
    val description: String,
    val traits: Map<String, Float> = emptyMap(),
    val backstory: String? = null,
    val styleHints: List<String> = emptyList(),
    val rules: List<String> = emptyList()
) {
    fun system(task: String? = null): String = buildString {
        append("You are $name. $description")
        backstory?.takeIf { it.isNotBlank() }?.let { append("\nBackground: $it") }
        if (traits.isNotEmpty()) {
            append("\nPersonality traits (0 = absent, 1 = very strong): ")
            append(traits.entries.joinToString(", ") { "${it.key} ${"%.1f".format(it.value)}" })
        }
        if (styleHints.isNotEmpty()) {
            append("\nYour voice sounds like these examples; capture the style in your own words, never copy them verbatim: ")
            append(styleHints.joinToString(" | "))
        }
        append("\nStay in character and reply in first person as $name. Respond only with what $name says.")
        rules.forEach { append("\n- ").append(it) }
        task?.takeIf { it.isNotBlank() }?.let { append("\n\nCurrent task: ").append(it) }
    }

    suspend fun respond(
        model: LanguageModel,
        history: List<ChatMessage>,
        task: String? = null,
        options: GenerationOptions = GenerationOptions(),
        onToken: ((String) -> Unit)? = null
    ): Result<String> = model.chat(listOf(ChatMessage.system(system(task))) + history, options, onToken)

    suspend fun respond(
        model: LanguageModel,
        userMessage: String,
        task: String? = null,
        options: GenerationOptions = GenerationOptions(),
        onToken: ((String) -> Unit)? = null
    ): Result<String> = respond(model, listOf(ChatMessage.user(userMessage)), task, options, onToken)
}
