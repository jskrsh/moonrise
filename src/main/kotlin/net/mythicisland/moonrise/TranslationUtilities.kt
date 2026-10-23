package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.VirtualComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.Locale

public object TranslationUtilities {

    private const val MARKER_PREFIX = "moonrise.placeholder:"
    private val LOCALE_PATTERN = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z0-9]{2,8})*$")
    private val ARGUMENT_NAME_PATTERN = Regex("^[a-z0-9_-]+$")
    private val POSITIONAL_TAGS = setOf("arg", "argument")
    private val PLACEHOLDER_PATTERN = Regex("<(?:arg(?:ument)?:)?([a-zA-Z0-9_-]+)>")
    private val STANDARD_TAGS = StandardTags.defaults()

    public fun parseLocale(value: String): Locale? {
        if (!LOCALE_PATTERN.matches(value)) {
            return null
        }
        val locale = Locale.forLanguageTag(value.replace('_', '-'))
        return if (locale.language.isEmpty()) null else locale
    }

    public fun fallbacks(locale: Locale, defaultLocale: Locale): List<Locale> = listOf(
        locale,
        Locale.of(locale.language, locale.country),
        Locale.of(locale.language),
        defaultLocale,
        Locale.of(defaultLocale.language),
    ).distinct()

    internal fun argumentName(name: String): String {
        val normalized = name.lowercase(Locale.ROOT)
        require(ARGUMENT_NAME_PATTERN.matches(normalized)) { "Invalid argument name '$name', only [a-z0-9_-] is allowed" }
        require(normalized !in POSITIONAL_TAGS && !STANDARD_TAGS.has(normalized)) {
            "Invalid argument name '$name', it is already used by a MiniMessage tag"
        }
        return normalized
    }

    internal fun missingPlaceholder(name: String): Component =
        Component.text(if (name.toIntOrNull() == null) "<$name>" else "<arg:$name>")

    internal fun placeholderName(tag: String, arguments: ArgumentQueue): String? {
        val name = tag.lowercase(Locale.ROOT)
        if (name !in POSITIONAL_TAGS) {
            return if (arguments.hasNext()) null else name
        }
        if (!arguments.hasNext()) {
            return null
        }
        val index = arguments.pop().value()
        return if (arguments.hasNext() || index.toIntOrNull() == null) null else index
    }

    internal fun marker(name: String): Component = Component.translatable(MARKER_PREFIX + name)

    internal fun markerName(component: Component): String? {
        if (component !is TranslatableComponent) {
            return null
        }
        val key = component.key()
        return if (key.startsWith(MARKER_PREFIX)) key.substring(MARKER_PREFIX.length) else null
    }

    internal fun containsPlaceholder(text: String): Boolean = PLACEHOLDER_PATTERN.containsMatchIn(text)

    internal fun replacePlaceholders(text: String, arguments: TranslationArguments): String =
        PLACEHOLDER_PATTERN.replace(text) { match ->
            val value = arguments.find(match.groupValues[1].lowercase(Locale.ROOT))
            if (value == null) match.value else PlainTextComponentSerializer.plainText().serialize(value)
        }

    internal fun replacePlaceholders(click: ClickEvent<*>, arguments: TranslationArguments): ClickEvent<*> {
        val action = click.action()
        val payload = click.payload()
        if (action !is ClickEvent.Action.TextCarrier || payload !is ClickEvent.Payload.Text) {
            return click
        }
        return ClickEvent.clickEvent(action, ClickEvent.Payload.string(replacePlaceholders(payload.value(), arguments)))
    }

    internal fun containsTranslatable(component: Component): Boolean =
        component is TranslatableComponent || component.children().any { containsTranslatable(it) }

    internal fun namedArgument(component: Component): Argument? {
        if (component !is VirtualComponent) {
            return null
        }
        val renderer = component.renderer()
        return renderer as? Argument
    }

    internal fun unwrap(component: Component): Component {
        val argument = namedArgument(component)
        return argument?.value ?: component
    }
}
