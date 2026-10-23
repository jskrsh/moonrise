package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.VirtualComponentRenderer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.time.temporal.TemporalAccessor

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

        private fun create(name: String, value: Component, raw: Any?): Component =
            Component.virtual(Void::class.java, Argument(TranslationUtilities.argumentName(name), value, raw))
    }
}
