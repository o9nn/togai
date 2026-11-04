package org.ninelym.togai.ai

import android.content.Context
import org.ninelym.llm.ModelEndpointConfig

/**
 * Persisted language-model endpoint. Defaults to a locally served Ollama model; if nothing is
 * serving there, model calls fail and the failure is shown to the user.
 */
object ModelSettings {
    private const val PREFS = "togai_llm"
    const val DEFAULT_BASE_URL = "http://127.0.0.1:11434/v1"
    const val DEFAULT_MODEL = "llama3.2"

    fun load(context: Context): ModelEndpointConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ModelEndpointConfig(
            baseUrl = prefs.getString("base_url", null) ?: DEFAULT_BASE_URL,
            model = prefs.getString("model", null) ?: DEFAULT_MODEL,
            apiKey = prefs.getString("api_key", null)?.takeIf { it.isNotBlank() }
        )
    }

    fun save(context: Context, config: ModelEndpointConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("base_url", config.baseUrl)
            .putString("model", config.model)
            .putString("api_key", config.apiKey)
            .apply()
    }
}
