package net.mythicisland.moonrise.parser

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.mythicisland.moonrise.ArgumentFormat

internal class TranslationParser(private val miniMessage: MiniMessage, private val tagResolver: TagResolver) {

    private val resolver = TagResolver.resolver(miniMessage.tags(), tagResolver)
    private val mm = MiniMessage.builder().tags(TagResolver.empty()).build()

    fun compile(raw: String): MessageNode {
        val markers = MarkerTagResolver(resolver)
        val component = miniMessage.deserialize(raw, TagResolver.resolver(tagResolver, markers))
        val compiled = compile(component)
        val modified = compiled !is MessageNode.Static && containsModifying(raw)
        val node = if (modified || markers.count() > handledMarkers(component)) MessageNode.Modified(raw, this) else compiled
        return if (containsTranslation(component)) MessageNode.Translating(node) else node
    }

    fun parse(raw: String, arguments: TranslationArguments): Component {
        val component = miniMessage.deserialize(raw, TagResolver.resolver(tagResolver, ArgumentTagResolver(resolver, arguments)))
        return replaceEvents(component, arguments)
    }

    private fun compile(component: Component): MessageNode {
        val name = Placeholders.markerName(component)
        if (name != null) {
            val format = ArgumentFormat.of(Placeholders.markerFormat(component)) ?: ArgumentFormat.Default
            return MessageNode.Placeholder(name, format, component.style())
        }
        val children = component.children().map { compile(it) }
        val hover = compileHover(component)
        val click = dynamicClick(component)
        val insertion = dynamicInsertion(component)
        val hasEvents = hover != null || click != null || insertion != null
        if (!hasEvents && children.all { it is MessageNode.Static }) {
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
        if (payload !is ClickEvent.Payload.Text || !Placeholders.contains(payload.value())) {
            return null
        }
        return click
    }

    private fun dynamicInsertion(component: Component): String? {
        val insertion = component.insertion() ?: return null
        return if (Placeholders.contains(insertion)) insertion else null
    }

    private fun replaceEvents(component: Component, arguments: TranslationArguments): Component {
        val click = dynamicClick(component)
        val insertion = dynamicInsertion(component)
        val withClick = if (click == null) component else component.clickEvent(Placeholders.replace(click, arguments))
        val withInsertion = if (insertion == null) withClick else withClick.insertion(Placeholders.replace(insertion, arguments))
        return withInsertion.children(component.children().map { replaceEvents(it, arguments) })
    }

    private fun containsModifying(raw: String): Boolean {
        val modifying = ModifyingTagResolver(resolver)
        mm.deserialize(raw, TagResolver.resolver(modifying, MarkerTagResolver(resolver)))
        return modifying.isFound()
    }

    private fun handledMarkers(component: Component): Int {
        if (Placeholders.markerName(component) != null) {
            return 1
        }
        val hover = component.hoverEvent()?.value()
        val inHover = if (hover is Component) handledMarkers(hover) else 0
        return inHover + component.children().sumOf { handledMarkers(it) }
    }

    private fun containsTranslation(component: Component): Boolean {
        if (component is TranslatableComponent && Placeholders.markerName(component) == null) {
            return true
        }
        val hover = component.hoverEvent()?.value()
        return (hover is Component && containsTranslation(hover)) || component.children().any { containsTranslation(it) }
    }
}
