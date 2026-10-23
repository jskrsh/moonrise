package net.mythicisland.moonrise.parser

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.mythicisland.moonrise.ArgumentFormat
import java.util.Locale

internal object Placeholders {

    val POSITIONAL_TAGS: Set<String> = setOf("arg", "argument")

    private const val MARKER_PREFIX = "moonrise.placeholder:"
    private val PLACEHOLDER_PATTERN = Regex("""<(arg(?:ument)?:)?([a-zA-Z0-9_-]+)((?::(?:'[^']*'|"[^"]*"|[^:'"<>]*))*)>""")
    private val FORMAT_PATTERN = Regex(""":(?:'([^']*)'|"([^"]*)"|([^:'"<>]*))""")

    fun missing(name: String): Component =
        Component.text(if (name.toIntOrNull() == null) "<$name>" else "<arg:$name>")

    fun placeholder(tag: String, arguments: ArgumentQueue): Pair<String, List<String>>? {
        val name = tag.lowercase(Locale.ROOT)
        val values = generateSequence { if (arguments.hasNext()) arguments.pop().value() else null }.toList()
        if (name !in POSITIONAL_TAGS) {
            return if (ArgumentFormat.isFormat(values)) name to values else null
        }
        val index = values.firstOrNull()
        val format = values.drop(1)
        if (index == null || index.toIntOrNull() == null || !ArgumentFormat.isFormat(format)) {
            return null
        }
        return index to format
    }

    fun marker(name: String, format: List<String>): Component =
        Component.translatable(MARKER_PREFIX + name, format.map { Component.text(it) })

    fun markerFormat(component: Component): List<String> {
        if (component !is TranslatableComponent) {
            return emptyList()
        }
        return component.arguments().map { PlainTextComponentSerializer.plainText().serialize(it.asComponent()) }
    }

    fun markerName(component: Component): String? {
        if (component !is TranslatableComponent) {
            return null
        }
        val key = component.key()
        return if (key.startsWith(MARKER_PREFIX)) key.substring(MARKER_PREFIX.length) else null
    }

    fun contains(text: String): Boolean = PLACEHOLDER_PATTERN.containsMatchIn(text)

    fun replace(text: String, arguments: TranslationArguments): String =
        PLACEHOLDER_PATTERN.replace(text) { replacement(it, arguments) }

    fun replace(click: ClickEvent<*>, arguments: TranslationArguments): ClickEvent<*> {
        val action = click.action()
        val payload = click.payload()
        if (action !is ClickEvent.Action.TextCarrier || payload !is ClickEvent.Payload.Text) {
            return click
        }
        return ClickEvent.clickEvent(action, ClickEvent.Payload.string(replace(payload.value(), arguments)))
    }

    private fun replacement(match: MatchResult, arguments: TranslationArguments): String {
        val name = match.groupValues[2].lowercase(Locale.ROOT)
        if (match.groupValues[1].isNotEmpty() && name.toIntOrNull() == null) {
            return match.value
        }
        val format = textFormat(match.groupValues[3]) ?: return match.value
        val value = arguments.find(name, format) ?: return match.value
        return PlainTextComponentSerializer.plainText().serialize(value)
    }

    private fun textFormat(text: String): ArgumentFormat? {
        val values = FORMAT_PATTERN.findAll(text).map { (it.groups[1] ?: it.groups[2] ?: it.groups[3])?.value.orEmpty() }.toList()
        return try {
            ArgumentFormat.of(values)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
