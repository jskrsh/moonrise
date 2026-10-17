package net.mythicisland.moonrise

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class TranslationBundle(
    private val messages: Map<Locale, Map<String, MessageNode>>,
    private val defaultLocale: Locale,
) {

    private val fallbacks = ConcurrentHashMap<Locale, List<Locale>>()

    val locales: Set<Locale> = messages.keys

    val size: Int = messages.values.sumOf { it.size }

    fun find(key: String, locale: Locale): MessageNode? {
        for (candidate in fallbacks.computeIfAbsent(locale) { fallbacks(it) }) {
            val message = messages[candidate]?.get(key)
            if (message != null) {
                return message
            }
        }
        return null
    }

    private fun fallbacks(locale: Locale): List<Locale> {
        val related = listOf(locale.language, defaultLocale.language).flatMap { language ->
            locales.filter { it.language == language }.sortedBy { it.toLanguageTag() }
        }
        return (TranslationUtilities.fallbacks(locale, defaultLocale) + related).distinct()
    }

    companion object {
        fun empty(defaultLocale: Locale): TranslationBundle = TranslationBundle(emptyMap(), defaultLocale)
    }
}
