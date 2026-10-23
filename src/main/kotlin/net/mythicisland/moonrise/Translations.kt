package net.mythicisland.moonrise

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.Translator
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

public class Translations private constructor(public val options: TranslationLoaderOptions) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(Translations::class.java)
    private val translator = CachedTranslator(options)
    private val initialized = AtomicBoolean(false)
    private val lock = Any()

    /** Whether the translations are currently loaded. */
    public val isInitialized: Boolean
        get() = initialized.get()

    /** Loads all translations and registers them at the [GlobalTranslator]. */
    public fun initialize(): Unit = synchronized(lock) {
        check(!initialized.get()) { "Translations are already initialized" }
        val bundle = translator.load()
        initialized.set(true)
        if (options.isGlobalTranslatorEnabled) {
            GlobalTranslator.translator().addSource(translator)
        }
        logger.info("Loaded {} translations for {} locales", bundle.size, bundle.locales.size)
    }

    /** Loads all translations again and clears the cache. */
    public fun reload(): Unit = synchronized(lock) {
        check(initialized.get()) { "Translations are not initialized" }
        val bundle = translator.load()
        logger.info("Reloaded {} translations for {} locales", bundle.size, bundle.locales.size)
    }

    /** Unregisters the translations and clears the cache. */
    public fun shutdown(): Unit = synchronized(lock) {
        if (!initialized.compareAndSet(true, false)) {
            return@synchronized
        }
        if (options.isGlobalTranslatorEnabled) {
            GlobalTranslator.translator().removeSource(translator)
        }
        translator.clear()
    }

    override fun close(): Unit = shutdown()

    /** Returns the adventure [Translator], e.g. to register it manually. */
    public fun translator(): Translator = translator

    /** Translates [key] for [locale]. Returns a translatable component if the key does not exist. */
    public fun translate(key: String, locale: Locale, vararg arguments: ComponentLike): Component {
        checkInitialized()
        val components = arguments.map { it.asComponent() }
        return translator.translate(key, locale, components) ?: Component.translatable(key, components)
    }

    /** Translates [key] for the locale of [audience]. */
    public fun translate(key: String, audience: Audience, vararg arguments: ComponentLike): Component =
        translate(key, localeOf(audience), *arguments)

    /** Translates all translatable components inside [component]. */
    public fun render(component: Component, locale: Locale): Component {
        checkInitialized()
        return translator.render(component, locale)
    }

    /** Translates all translatable components inside [component] for the locale of [audience]. */
    public fun render(component: Component, audience: Audience): Component = render(component, localeOf(audience))

    /** Whether a translation exists for [key] and [locale]. */
    public fun contains(key: String, locale: Locale): Boolean = translator.contains(key, locale)

    /** All locales that have at least one translation. */
    public fun locales(): Set<Locale> = translator.locales()

    private fun localeOf(audience: Audience): Locale =
        audience.getOrDefault(Identity.LOCALE, options.defaultLocale) ?: options.defaultLocale

    private fun checkInitialized() = check(initialized.get()) { "Translations are not initialized, call initialize() first" }

    public class Builder internal constructor(private val options: TranslationLoaderOptions) {

        /** Adds a [TranslationLoader]. Keys of later loaders override keys of earlier loaders. */
        public fun loader(loader: TranslationLoader): Builder = Builder(options.copy(loaders = options.loaders + loader))

        /** Sets the locale that is used when no translation for the requested locale exists. */
        public fun defaultLocale(locale: Locale): Builder = Builder(options.copy(defaultLocale = locale))

        /** Disables component caching. */
        public fun disableCaching(): Builder = Builder(options.copy(caching = false))

        /** Sets the maximum amount of cached messages with arguments. */
        public fun cacheMaximumSize(size: Long): Builder {
            require(size >= 0) { "Cache size must not be negative" }
            return Builder(options.copy(cacheMaximumSize = size))
        }

        /** Sets the MiniMessage instance used to parse messages. */
        public fun miniMessage(miniMessage: MiniMessage): Builder = Builder(options.copy(miniMessage = miniMessage))

        /** Adds tags that can be used in every message, e.g. `<prefix>`. */
        public fun tagResolver(resolver: TagResolver): Builder =
            Builder(options.copy(tagResolver = TagResolver.resolver(options.tagResolver, resolver)))

        /** Sets the name of the translator. */
        public fun name(name: Key): Builder = Builder(options.copy(name = name))

        /** Does not register the translations at the [GlobalTranslator]. */
        public fun disableGlobalTranslator(): Builder = Builder(options.copy(globalTranslator = false))

        public fun build(): Translations {
            require(options.loaders.isNotEmpty()) { "At least one TranslationLoader is required" }
            return Translations(options)
        }
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder(TranslationLoaderOptions.DEFAULT)
    }
}
