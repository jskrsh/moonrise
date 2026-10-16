package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.VirtualComponentRenderer

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
) : VirtualComponentRenderer<Void> {

    override fun apply(context: Void): ComponentLike = value

    public companion object {
        @JvmStatic
        public fun component(name: String, value: ComponentLike): Component =
            Component.virtual(Void::class.java, Argument(TranslationUtilities.argumentName(name), value.asComponent()))

        @JvmStatic
        public fun string(name: String, value: String): Component = component(name, Component.text(value))

        @JvmStatic
        public fun number(name: String, value: Number): Component = component(name, Component.text(value.toString()))

        @JvmStatic
        public fun bool(name: String, value: Boolean): Component = component(name, Component.text(value))
    }
}
