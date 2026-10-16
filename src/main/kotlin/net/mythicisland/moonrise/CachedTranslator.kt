package net.mythicisland.moonrise

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer
import net.kyori.adventure.translation.Translator
import net.kyori.adventure.util.TriState
import java.text.MessageFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Translator that parses every message once on load.
 */
internal class CachedTranslator(private val options: TranslationLoaderOptions) : Translator {

    private val parser = TranslationParser(options.miniMessage, options.tagResolver)
    private val bundle = AtomicReference(TranslationBundle.empty(options.defaultLocale))
    private val renderer = TranslatableComponentRenderer.usingTranslationSource(this)
    private val cache: Cache<RenderKey, Component> = Caffeine.newBuilder()
        .maximumSize(options.cacheMaximumSize)
        .build()

    override fun name(): Key = options.name

    override fun translate(key: String, locale: Locale): MessageFormat? = null

    override fun canTranslate(key: String, locale: Locale): Boolean = contains(key, locale)

    override fun hasAnyTranslations(): TriState = if (bundle.get().size > 0) TriState.TRUE else TriState.FALSE

    override fun translate(component: TranslatableComponent, locale: Locale): Component? {
        val arguments = component.arguments().map { it.asComponent() }
        val translated = translate(component.key(), locale, arguments) ?: return null
        if (component.style().isEmpty && component.children().isEmpty()) {
            return translated
        }
        return Component.text()
            .style(component.style())
            .append(translated)
            .append(component.children())
            .build()
    }

    fun translate(key: String, locale: Locale, arguments: List<Component>): Component? {
        val message = bundle.get().find(key, locale) ?: return null
        if (!options.isCachingEnabled) {
            return parser.parse(message.raw, resolve(arguments, locale))
        }
        if (!message.dynamic) {
            return message.component
        }
        val resolved = resolve(arguments, locale)
        return cache.get(RenderKey(message, resolved)) { parser.render(message.component, resolved) }
    }

    fun render(component: Component, locale: Locale): Component = renderer.render(component, locale)

    fun contains(key: String, locale: Locale): Boolean = bundle.get().find(key, locale) != null

    fun locales(): Set<Locale> = bundle.get().locales

    fun load(): TranslationBundle {
        val loaded = options.loaders
            .map { it.load() }
            .fold(emptyMap<Locale, Map<String, String>>()) { merged, translations -> merge(merged, translations) }
        val messages = loaded.mapValues { (_, translations) ->
            translations.mapValues { (_, raw) -> message(raw) }
        }
        val created = TranslationBundle(messages, options.defaultLocale)
        bundle.set(created)
        cache.invalidateAll()
        return created
    }

    fun clear() {
        bundle.set(TranslationBundle.empty(options.defaultLocale))
        cache.invalidateAll()
    }

    private fun message(raw: String): Message =
        if (options.isCachingEnabled) Message.parsed(raw, parser) else Message.unparsed(raw)

    private fun resolve(arguments: List<Component>, locale: Locale): TranslationArguments =
        TranslationArguments.of(arguments) { argument ->
            if (TranslationUtilities.containsTranslatable(argument)) render(argument, locale) else argument
        }

    private fun merge(
        first: Map<Locale, Map<String, String>>,
        second: Map<Locale, Map<String, String>>,
    ): Map<Locale, Map<String, String>> =
        (first.keys + second.keys).associateWith { first[it].orEmpty() + second[it].orEmpty() }

    private data class RenderKey(val message: Message, val arguments: TranslationArguments)
}
