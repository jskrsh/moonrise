package net.mythicisland.moonrise

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.Locale

/** Settings of a [Translations] instance, created by [Translations.Builder]. */
public class TranslationLoaderOptions internal constructor(
    public val loaders: List<TranslationLoader>,
    public val defaultLocale: Locale,
    public val isCachingEnabled: Boolean,
    public val cacheMaximumSize: Long,
    public val miniMessage: MiniMessage,
    public val tagResolver: TagResolver,
    public val name: Key,
    public val isGlobalTranslatorEnabled: Boolean,
) {

    internal fun copy(
        loaders: List<TranslationLoader> = this.loaders,
        defaultLocale: Locale = this.defaultLocale,
        caching: Boolean = this.isCachingEnabled,
        cacheMaximumSize: Long = this.cacheMaximumSize,
        miniMessage: MiniMessage = this.miniMessage,
        tagResolver: TagResolver = this.tagResolver,
        name: Key = this.name,
        globalTranslator: Boolean = this.isGlobalTranslatorEnabled,
    ): TranslationLoaderOptions = TranslationLoaderOptions(
        loaders,
        defaultLocale,
        caching,
        cacheMaximumSize,
        miniMessage,
        tagResolver,
        name,
        globalTranslator,
    )

    internal companion object {
        val DEFAULT: TranslationLoaderOptions = TranslationLoaderOptions(
            loaders = emptyList(),
            defaultLocale = Locale.ENGLISH,
            isCachingEnabled = true,
            cacheMaximumSize = 10_000,
            miniMessage = MiniMessage.miniMessage(),
            tagResolver = TagResolver.empty(),
            name = Key.key("moonrise", "translations"),
            isGlobalTranslatorEnabled = true,
        )
    }
}
