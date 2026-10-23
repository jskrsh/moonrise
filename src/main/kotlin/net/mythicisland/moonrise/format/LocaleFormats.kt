package net.mythicisland.moonrise.format

import java.text.Format
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class LocaleFormats<T : Format> {

    private val formats = ConcurrentHashMap<Locale, T>()

    @Suppress("UNCHECKED_CAST")
    fun get(locale: Locale): T? = formats[locale]?.clone() as T?

    @Suppress("UNCHECKED_CAST")
    fun put(locale: Locale, format: T): T {
        if (formats.size < MAXIMUM_LOCALES) {
            formats.putIfAbsent(locale, format)
        }
        return format.clone() as T
    }

    private companion object {
        const val MAXIMUM_LOCALES = 256
    }
}
