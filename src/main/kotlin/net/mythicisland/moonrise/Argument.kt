package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.VirtualComponent
import net.kyori.adventure.text.VirtualComponentRenderer
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.mythicisland.moonrise.parser.Placeholders
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * A named argument that can be used as `<name>` in a message.
 *
 * ```
 * Component.translatable("welcome", Argument.string("player", player.name))
 * ```
 */
public class Argument private constructor(
    public val name: String,
    public val value: Component,
    internal val raw: Any?,
) : VirtualComponentRenderer<Void> {

    override fun apply(context: Void): ComponentLike = value

    override fun fallbackString(): String = PlainTextComponentSerializer.plainText().serialize(value)

    public companion object {
        @JvmStatic
        public fun component(name: String, value: ComponentLike): Component = create(name, value.asComponent(), null)

        @JvmStatic
        public fun string(name: String, value: String): Component = component(name, Component.text(value))

        @JvmStatic
        public fun number(name: String, value: Number): Component = create(name, Component.text(value.toString()), value)

        @JvmStatic
        public fun bool(name: String, value: Boolean): Component = create(name, Component.text(value), value)

        @JvmStatic
        public fun date(name: String, value: TemporalAccessor): Component = create(name, Component.text(value.toString()), value)

        private val NAME_PATTERN = Regex("^[a-z0-9_-]+$")
        private val STANDARD_TAGS = StandardTags.defaults()

        internal fun of(component: Component): Argument? {
            if (component !is VirtualComponent) {
                return null
            }
            return component.renderer() as? Argument
        }

        private fun create(name: String, value: Component, raw: Any?): Component =
            Component.virtual(Void::class.java, Argument(validName(name), value, raw))

        private fun validName(name: String): String {
            val normalized = name.lowercase(Locale.ROOT)
            require(NAME_PATTERN.matches(normalized)) { "Invalid argument name '$name', only [a-z0-9_-] is allowed" }
            require(normalized !in Placeholders.POSITIONAL_TAGS && !STANDARD_TAGS.has(normalized)) {
                "Invalid argument name '$name', it is already used by a MiniMessage tag"
            }
            return normalized
        }
    }
}
