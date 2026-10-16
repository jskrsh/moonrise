package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

internal class TranslationParser(private val miniMessage: MiniMessage, private val tagResolver: TagResolver) {

    private val markerResolver = TagResolver.resolver(MarkerTagResolver(TagResolver.resolver(miniMessage.tags(), tagResolver)), tagResolver)

    fun parseWithMarkers(raw: String): Component = miniMessage.deserialize(raw, markerResolver)

    // Used when caching is disabled.
    fun parse(raw: String, arguments: TranslationArguments): Component =
        miniMessage.deserialize(raw, TagResolver.resolver(ArgumentTagResolver(arguments), tagResolver))

    fun render(component: Component, arguments: TranslationArguments): Component {
        val name = TranslationUtilities.markerName(component)
        if (name != null) {
            return placeholder(component, name, arguments)
        }
        val children = component.children()
        val rendered = if (children.isEmpty()) component else component.children(children.map { render(it, arguments) })
        return renderHover(rendered, arguments)
    }

    private fun placeholder(marker: Component, name: String, arguments: TranslationArguments): Component {
        val value = arguments.find(name) ?: Component.text("<$name>")
        val styled = if (marker.style().isEmpty) value else value.applyFallbackStyle(marker.style())
        val children = marker.children()
        return if (children.isEmpty()) styled else styled.children(styled.children() + children)
    }

    private fun renderHover(component: Component, arguments: TranslationArguments): Component {
        val text = component.hoverEvent()?.value()
        if (text !is Component || !TranslationUtilities.containsPlaceholder(text)) {
            return component
        }
        return component.hoverEvent(HoverEvent.showText(render(text, arguments)))
    }
}

private class MarkerTagResolver(private val reserved: TagResolver) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        val placeholder = TranslationUtilities.placeholderName(name, arguments) ?: return null
        return Tag.selfClosingInserting(TranslationUtilities.marker(placeholder))
    }

    override fun has(name: String): Boolean = !reserved.has(name)
}

private class ArgumentTagResolver(private val arguments: TranslationArguments) : TagResolver {

    override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
        val placeholder = TranslationUtilities.placeholderName(name, arguments) ?: return null
        val value = this.arguments.find(placeholder) ?: return null
        return Tag.selfClosingInserting(value)
    }

    override fun has(name: String): Boolean = name == "arg" || name == "argument" || arguments.find(name) != null
}
