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
        for (candidate in fallbacks.computeIfAbsent(locale) { TranslationUtilities.fallbacks(it, defaultLocale) }) {
            val message = messages[candidate]?.get(key)
            if (message != null) {
                return message
            }
        }
        return null
    }

    companion object {
        fun empty(defaultLocale: Locale): TranslationBundle = TranslationBundle(emptyMap(), defaultLocale)
    }
}
