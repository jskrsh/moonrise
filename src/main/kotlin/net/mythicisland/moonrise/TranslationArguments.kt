package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.TranslationArgument
import java.util.Locale

internal data class TranslationArguments(
    val names: List<String?>,
    val values: List<Value>,
    val locale: Locale,
) {

    fun find(name: String, format: ArgumentFormat = ArgumentFormat.Default): Component? {
        val value = value(name) ?: return null
        val raw = value.raw ?: return value.component
        val formatted = format.format(raw, locale) ?: return value.component
        return Component.text(formatted)
    }

    private fun value(name: String): Value? {
        val named = names.lastIndexOf(name)
        if (named >= 0) {
            return values[named]
        }
        val index = name.toIntOrNull() ?: return null
        return values.getOrNull(index)
    }

    data class Value(val component: Component, val raw: Any?)

    companion object {
        val EMPTY: TranslationArguments = TranslationArguments(emptyList(), emptyList(), Locale.ROOT)

        fun of(arguments: List<ComponentLike>, locale: Locale, transform: (Component) -> Component): TranslationArguments {
            if (arguments.isEmpty()) {
                return TranslationArguments(emptyList(), emptyList(), locale)
            }
            val components = arguments.map { it.asComponent() }
            val named = components.map { TranslationUtilities.namedArgument(it) }
            val values = arguments.indices.map { value(arguments[it], components[it], named[it], transform) }
            return TranslationArguments(named.map { it?.name }, values, locale)
        }

        private fun value(argument: ComponentLike, component: Component, named: Argument?, transform: (Component) -> Component): Value {
            if (named != null) {
                return Value(transform(named.value), named.raw)
            }
            val raw = if (argument is TranslationArgument && argument.value() !is Component) argument.value() else null
            return Value(transform(component), raw)
        }
    }
}
