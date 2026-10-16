package net.mythicisland.moonrise

import net.kyori.adventure.text.Component

/**
 * A single loaded message. The parsed component contains markers instead of placeholders,
 * which are replaced with the actual arguments when the message is rendered.
 */
internal class Message private constructor(
    val raw: String,
    val component: Component,
    val dynamic: Boolean,
) {

    companion object {
        fun parsed(raw: String, parser: TranslationParser): Message {
            val component = parser.parseWithMarkers(raw)
            return Message(raw, component, TranslationUtilities.containsPlaceholder(component))
        }

        // Used when caching is disabled.
        fun unparsed(raw: String): Message = Message(raw, Component.empty(), true)
    }
}
