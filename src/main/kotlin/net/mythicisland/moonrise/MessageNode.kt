package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.Style

internal sealed interface MessageNode {

    fun render(arguments: TranslationArguments): Component

    /** A part without placeholders. */
    class Static(private val component: Component) : MessageNode {
        override fun render(arguments: TranslationArguments): Component = component
    }

    /** A placeholder like `<player>` or `<arg:0>`. */
    class Placeholder(private val name: String, private val style: Style) : MessageNode {

        private val missing = Component.text(if (name.toIntOrNull() == null) "<$name>" else "<arg:$name>")

        override fun render(arguments: TranslationArguments): Component {
            val value = arguments.find(name) ?: missing
            return if (style.isEmpty) value else value.applyFallbackStyle(style)
        }
    }

    /** A part that contains placeholders in its children, its hover text or its click event. */
    class Dynamic(
        private val component: Component,
        private val children: List<MessageNode>,
        private val hover: MessageNode?,
        private val click: ClickEvent<*>?,
    ) : MessageNode {

        override fun render(arguments: TranslationArguments): Component {
            val rendered = component.children(children.map { it.render(arguments) })
            val withHover = if (hover == null) rendered else rendered.hoverEvent(HoverEvent.showText(hover.render(arguments)))
            return if (click == null) withHover else withHover.clickEvent(renderClick(click, arguments))
        }

        private fun renderClick(click: ClickEvent<*>, arguments: TranslationArguments): ClickEvent<*> {
            val action = click.action()
            val payload = click.payload()
            if (action !is ClickEvent.Action.TextCarrier || payload !is ClickEvent.Payload.Text) {
                return click
            }
            val text = TranslationUtilities.replacePlaceholders(payload.value(), arguments)
            return ClickEvent.clickEvent(action, ClickEvent.Payload.string(text))
        }
    }

    /** A message that contains translatable components like `<lang:key>`. */
    class Translating(private val node: MessageNode) : MessageNode {
        override fun render(arguments: TranslationArguments): Component = node.render(arguments)
    }

    /** Used when caching is disabled. */
    class Unparsed(private val raw: String, private val parser: TranslationParser) : MessageNode {
        override fun render(arguments: TranslationArguments): Component = parser.compile(raw).render(arguments)
    }
}
