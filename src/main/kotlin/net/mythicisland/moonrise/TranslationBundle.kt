package net.mythicisland.moonrise

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.Locale

internal class TranslationBundle(
    private val messages: Map<Locale, Map<String, MessageNode>>,
    private val defaultLocale: Locale,
) {

    private val fallbacks: Cache<Locale, List<Locale>> = Caffeine.newBuilder()
        .maximumSize(MAXIMUM_FALLBACKS)
        .build()

    val locales: Set<Locale> = messages.keys

    val size: Int = messages.values.sumOf { it.size }

    fun find(key: String, locale: Locale): MessageNode? {
        for (candidate in fallbacks.get(locale) { fallbacks(it) }) {
            val message = messages[candidate]?.get(key)
            if (message != null) {
                return message
            }
        }
        return null
    }

    fun missingKeys(locale: Locale): Set<String> =
        keys(sameLanguage(defaultLocale)) - keys(sameLanguage(locale))

    private fun keys(locales: List<Locale>): Set<String> = locales.flatMap { messages[it].orEmpty().keys }.toSet()

    private fun fallbacks(locale: Locale): List<Locale> {
        val requested = listOf(locale, Locale.of(locale.language, locale.country), Locale.of(locale.language))
        val default = listOf(defaultLocale, Locale.of(defaultLocale.language))
        return (requested + sameLanguage(locale) + default + sameLanguage(defaultLocale)).distinct()
    }

    private fun sameLanguage(locale: Locale): List<Locale> =
        locales.filter { it.language == locale.language }.sortedBy { it.toLanguageTag() }

    companion object {
        private const val MAXIMUM_FALLBACKS = 256L

        fun empty(defaultLocale: Locale): TranslationBundle = TranslationBundle(emptyMap(), defaultLocale)
    }
}
