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
        return replaceEvents(component, arguments)
    }

    private fun compile(component: Component): MessageNode {
        val name = TranslationUtilities.markerName(component)
        if (name != null) {
            val format = ArgumentFormat.of(TranslationUtilities.markerFormat(component)) ?: ArgumentFormat.Default
            return MessageNode.Placeholder(name, format, component.style())
        }
        val children = component.children().map { compile(it) }
        val hover = compileHover(component)
        val click = dynamicClick(component)
        val insertion = dynamicInsertion(component)
        if (hover == null && click == null && insertion == null && children.all { it is MessageNode.Static }) {
            return MessageNode.Static(component)
        }
        return MessageNode.Dynamic(component.children(emptyList()), children, hover, click, insertion)
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

    private fun dynamicInsertion(component: Component): String? {
        val insertion = component.insertion() ?: return null
        return if (TranslationUtilities.containsPlaceholder(insertion)) insertion else null
    }

    private fun replaceEvents(component: Component, arguments: TranslationArguments): Component {
        val click = dynamicClick(component)
        val insertion = dynamicInsertion(component)
        val withClick = if (click == null) component else component.clickEvent(TranslationUtilities.replacePlaceholders(click, arguments))
        val withInsertion = if (insertion == null) withClick else withClick.insertion(TranslationUtilities.replacePlaceholders(insertion, arguments))
        return withInsertion.children(component.children().map { replaceEvents(it, arguments) })
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
        val (placeholder, format) = TranslationUtilities.placeholder(name, arguments) ?: return null
        return Tag.selfClosingInserting(TranslationUtilities.marker(placeholder, format))
    }

    override fun has(name: String): Boolean = !reserved.has(name)
}

private class ArgumentTagResolver(private val reserved: TagResolver, private val values: TranslationArguments) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        if (reserved.has(name)) {
            return null
        }
        val (placeholder, format) = TranslationUtilities.placeholder(name, arguments) ?: return null
        val value = values.find(placeholder, ArgumentFormat.of(format) ?: ArgumentFormat.Default)
        return Tag.selfClosingInserting(value ?: TranslationUtilities.missingPlaceholder(placeholder))
    }

    override fun has(name: String): Boolean = !reserved.has(name)
}
