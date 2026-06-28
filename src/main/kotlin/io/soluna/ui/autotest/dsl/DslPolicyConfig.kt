package io.soluna.ui.autotest.dsl

class DslPolicyConfig {
    val parameterizedTextReasons: Set<String> = allowedTextReasons
    val hardcodedTextReasons: Set<String> = allowedTextReasons

    companion object {
        const val LANGUAGE_INSENSITIVE_TEXT_REASON = "language_insensitive_text"
        val allowedTextReasons: Set<String> = setOf(LANGUAGE_INSENSITIVE_TEXT_REASON)
    }
}
