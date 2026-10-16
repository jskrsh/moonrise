package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

internal class TranslationParser(private val miniMessage: MiniMessage, private val tagResolver: TagResolver) {

    private val markerResolver = TagResolver.resolver(MarkerTagResolver(TagResolver.resolver(miniMessage.tags(), tagResolver)), tagResolver)

    fun compile(raw: String): MessageNode = compile(miniMessage.deserialize(raw, markerResolver))

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
}

private class MarkerTagResolver(private val reserved: TagResolver) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        val placeholder = TranslationUtilities.placeholderName(name, arguments) ?: return null
        return Tag.selfClosingInserting(TranslationUtilities.marker(placeholder))
    }

    override fun has(name: String): Boolean = !reserved.has(name)
}
