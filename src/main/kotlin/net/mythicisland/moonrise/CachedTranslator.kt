package net.mythicisland.moonrise

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.Translator
import net.kyori.adventure.util.TriState
import org.slf4j.LoggerFactory
import java.text.MessageFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal class CachedTranslator(private val options: TranslationLoaderOptions) : Translator {

    private val logger = LoggerFactory.getLogger(CachedTranslator::class.java)
    private val depth = ThreadLocal.withInitial { 0 }
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
            .append(component.children().map { renderAll(it, locale) })
            .build()
    }

    fun translate(key: String, locale: Locale, arguments: List<Component>): Component? {
        val node = bundle.get().find(key, locale) ?: return null
        val rendered = render(node, locale, arguments)
        return if (node is MessageNode.Translating) renderNested(rendered, locale) else rendered
    }

    fun render(component: Component, locale: Locale): Component = renderer.render(component, locale)

    fun contains(key: String, locale: Locale): Boolean = bundle.get().find(key, locale) != null

    fun locales(): Set<Locale> = bundle.get().locales

    fun load(): TranslationBundle {
        val loaded = options.loaders
            .map { it.load() }
            .fold(emptyMap<Locale, Map<String, String>>()) { merged, translations -> merge(merged, translations) }
        val messages = loaded.mapValues { (locale, translations) ->
            translations.entries.mapNotNull { (key, raw) -> node(locale, key, raw) }.toMap()
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

    private fun node(locale: Locale, key: String, raw: String): Pair<String, MessageNode>? =
        try {
            key to parser.compile(raw)
        } catch (exception: Exception) {
            logger.error("Skipping translation {} for locale {}, it could not be parsed", key, locale, exception)
            null
        }

    private fun render(node: MessageNode, locale: Locale, arguments: List<Component>): Component {
        if (node is MessageNode.Static) {
            return node.render(TranslationArguments.EMPTY)
        }
        val resolved = resolve(arguments, locale)
        if (!options.isCachingEnabled) {
            return node.render(resolved)
        }
        return cache.get(RenderKey(node, resolved)) { node.render(resolved) }
    }

    private fun renderNested(component: Component, locale: Locale): Component {
        val current = depth.get()
        if (current >= MAXIMUM_DEPTH) {
            return component
        }
        depth.set(current + 1)
        try {
            return renderAll(component, locale)
        } finally {
            depth.set(current)
        }
    }

    private fun renderAll(component: Component, locale: Locale): Component =
        if (options.isGlobalTranslatorEnabled) GlobalTranslator.render(component, locale) else render(component, locale)

    private fun resolve(arguments: List<Component>, locale: Locale): TranslationArguments =
        TranslationArguments.of(arguments) { argument ->
            if (TranslationUtilities.containsTranslatable(argument)) renderAll(argument, locale) else argument
        }

    private fun merge(
        first: Map<Locale, Map<String, String>>,
        second: Map<Locale, Map<String, String>>,
    ): Map<Locale, Map<String, String>> =
        (first.keys + second.keys).associateWith { first[it].orEmpty() + second[it].orEmpty() }

    private data class RenderKey(val node: MessageNode, val arguments: TranslationArguments)

    private companion object {
        const val MAXIMUM_DEPTH = 8
    }
}
