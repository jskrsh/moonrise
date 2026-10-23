package net.mythicisland.moonrise

import java.util.Locale

public object TranslationUtilities {

    private val LOCALE_PATTERN = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z0-9]{2,8})*$")

    public fun parseLocale(value: String): Locale? {
        if (!LOCALE_PATTERN.matches(value)) {
            return null
        }
        val locale = Locale.forLanguageTag(value.replace('_', '-'))
        return if (locale.language.isEmpty()) null else locale
    }
}
