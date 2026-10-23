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
    class Placeholder(private val name: String, private val format: ArgumentFormat, private val style: Style) : MessageNode {

        private val missing = TranslationUtilities.missingPlaceholder(name)

        override fun render(arguments: TranslationArguments): Component {
            val value = arguments.find(name, format) ?: missing
            return if (style.isEmpty) value else value.applyFallbackStyle(style)
        }
    }

    /** A part that contains placeholders in its children, its hover text, its click event or its insertion. */
    class Dynamic(
        private val component: Component,
        private val children: List<MessageNode>,
        private val hover: MessageNode?,
        private val click: ClickEvent<*>?,
        private val insertion: String?,
    ) : MessageNode {

        override fun render(arguments: TranslationArguments): Component {
            val rendered = component.children(children.map { it.render(arguments) })
            val withHover = if (hover == null) rendered else rendered.hoverEvent(HoverEvent.showText(hover.render(arguments)))
            val withClick = if (click == null) withHover else withHover.clickEvent(TranslationUtilities.replacePlaceholders(click, arguments))
            return if (insertion == null) withClick else withClick.insertion(TranslationUtilities.replacePlaceholders(insertion, arguments))
        }
    }

    /** A message with placeholders inside tags like `<gradient>`, which is parsed together with its arguments. */
    class Modified(private val raw: String, private val parser: TranslationParser) : MessageNode {
        override fun render(arguments: TranslationArguments): Component = parser.parse(raw, arguments)
    }

    /** A message that contains translatable components like `<lang:key>`. */
    class Translating(private val node: MessageNode) : MessageNode {
        override fun render(arguments: TranslationArguments): Component = node.render(arguments)
    }
}
