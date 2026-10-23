package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

internal class TranslationParser(private val miniMessage: MiniMessage, private val tagResolver: TagResolver) {

    private val reserved = TagResolver.resolver(miniMessage.tags(), tagResolver)
    private val markerResolver = TagResolver.resolver(tagResolver, MarkerTagResolver(reserved))

    fun compile(raw: String): MessageNode {
        val component = miniMessage.deserialize(raw, markerResolver)
        val compiled = compile(component)
        val modified = compiled !is MessageNode.Static && MODIFYING_PATTERN.containsMatchIn(raw)
        val node = if (modified) MessageNode.Modified(raw, this) else compiled
        return if (containsTranslation(component)) MessageNode.Translating(node) else node
    }

    fun parse(raw: String, arguments: TranslationArguments): Component {
        val component = miniMessage.deserialize(raw, TagResolver.resolver(tagResolver, ArgumentTagResolver(reserved, arguments)))
        return replaceClicks(component, arguments)
    }

    private fun compile(component: Component): MessageNode {
        val name = TranslationUtilities.markerName(component)
        if (name != null) {
            return MessageNode.Placeholder(name, component.style())
        }
        val children = component.children().map { compile(it) }
        val hover = compileHover(component)
        val click = dynamicClick(component)
        if (hover == null && click == null && children.all { it is MessageNode.Static }) {
            return MessageNode.Static(component)
        }
        return MessageNode.Dynamic(component.children(emptyList()), children, hover, click)
    }

    private fun compileHover(component: Component): MessageNode? {
        val text = component.hoverEvent()?.value()
        if (text !is Component) {
            return null
        }
        val node = compile(text)
        return if (node is MessageNode.Static) null else node
    }

    private fun dynamicClick(component: Component): ClickEvent<*>? {
        val click = component.clickEvent() ?: return null
        val payload = click.payload()
        if (payload !is ClickEvent.Payload.Text || !TranslationUtilities.containsPlaceholder(payload.value())) {
            return null
        }
        return click
    }

    private fun replaceClicks(component: Component, arguments: TranslationArguments): Component {
        val click = dynamicClick(component)
        val replaced = if (click == null) component else component.clickEvent(TranslationUtilities.replacePlaceholders(click, arguments))
        return replaced.children(component.children().map { replaceClicks(it, arguments) })
    }

    private fun containsTranslation(component: Component): Boolean {
        if (component is TranslatableComponent && TranslationUtilities.markerName(component) == null) {
            return true
        }
        val hover = component.hoverEvent()?.value()
        return (hover is Component && containsTranslation(hover)) || component.children().any { containsTranslation(it) }
    }

    private companion object {
        val MODIFYING_PATTERN = Regex("<(gradient|rainbow|pride)[:>]", RegexOption.IGNORE_CASE)
    }
}

private class MarkerTagResolver(private val reserved: TagResolver) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        if (reserved.has(name)) {
            return null
        }
        val placeholder = TranslationUtilities.placeholderName(name, arguments) ?: return null
        return Tag.selfClosingInserting(TranslationUtilities.marker(placeholder))
    }

    override fun has(name: String): Boolean = !reserved.has(name)
}

private class ArgumentTagResolver(private val reserved: TagResolver, private val values: TranslationArguments) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        if (reserved.has(name)) {
            return null
        }
        val placeholder = TranslationUtilities.placeholderName(name, arguments) ?: return null
        return Tag.selfClosingInserting(values.find(placeholder) ?: TranslationUtilities.missingPlaceholder(placeholder))
    }

    override fun has(name: String): Boolean = !reserved.has(name)
}
