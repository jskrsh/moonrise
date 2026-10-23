package net.mythicisland.moonrise

import java.text.Format
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal abstract class LocaleFormats<T : Format> {

    private val formats = ConcurrentHashMap<Locale, T>()

    protected abstract fun create(locale: Locale): T

    @Suppress("UNCHECKED_CAST")
    fun get(locale: Locale): T {
        val cached = formats[locale]
        if (cached != null) {
            return cached.clone() as T
        }
        val created = create(locale)
        if (formats.size < MAXIMUM_LOCALES) {
            formats.putIfAbsent(locale, created)
        }
        return created.clone() as T
    }

    private companion object {
        const val MAXIMUM_LOCALES = 256
    }
}
