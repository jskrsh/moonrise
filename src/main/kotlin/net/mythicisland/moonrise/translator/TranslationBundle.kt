package net.mythicisland.moonrise.translator

import net.mythicisland.moonrise.parser.MessageNode
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class TranslationBundle(
    private val messages: Map<Locale, Map<String, MessageNode>>,
    private val defaultLocale: Locale,
) {

    private val fallbacks = ConcurrentHashMap<Locale, List<Map<String, MessageNode>>>()

    val locales: Set<Locale> = messages.keys

    val size: Int = messages.values.sumOf { it.size }

    fun find(key: String, locale: Locale): MessageNode? {
        for (candidate in candidates(locale)) {
            val message = candidate[key]
            if (message != null) {
                return message
            }
        }
        return null
    }

    fun missingKeys(locale: Locale): Set<String> =
        keys(sameLanguage(defaultLocale)) - keys(sameLanguage(locale))

    private fun keys(locales: List<Locale>): Set<String> = locales.flatMap { messages[it].orEmpty().keys }.toSet()

    private fun candidates(locale: Locale): List<Map<String, MessageNode>> {
        val cached = fallbacks[locale]
        if (cached != null) {
            return cached
        }
        val created = fallbacks(locale).mapNotNull { messages[it] }
        if (fallbacks.size < MAXIMUM_FALLBACKS) {
            fallbacks.putIfAbsent(locale, created)
        }
        return created
    }

    private fun fallbacks(locale: Locale): List<Locale> {
        val requested = listOf(locale, Locale.of(locale.language, locale.country), Locale.of(locale.language))
        val default = listOf(defaultLocale, Locale.of(defaultLocale.language))
        return (requested + sameLanguage(locale) + default + sameLanguage(defaultLocale)).distinct()
    }

    private fun sameLanguage(locale: Locale): List<Locale> =
        locales.filter { it.language == locale.language }.sortedBy { it.toLanguageTag() }

    companion object {
        private const val MAXIMUM_FALLBACKS = 256

        fun empty(defaultLocale: Locale): TranslationBundle = TranslationBundle(emptyMap(), defaultLocale)
    }
}
