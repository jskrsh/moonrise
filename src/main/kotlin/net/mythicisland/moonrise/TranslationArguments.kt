package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import java.util.Locale

internal data class TranslationArguments(
    val named: Map<String, Value>,
    val positional: List<Value>,
    val locale: Locale,
) {

    fun find(name: String, format: ArgumentFormat = ArgumentFormat.Default): Component? {
        val value = named[name] ?: positional(name) ?: return null
        val raw = value.raw ?: return value.component
        val formatted = format.format(raw, locale) ?: return value.component
        return Component.text(formatted)
    }

    private fun positional(name: String): Value? {
        val index = name.toIntOrNull() ?: return null
        return positional.getOrNull(index)
    }

    data class Value(val component: Component, val raw: Any?)

    companion object {
        val EMPTY: TranslationArguments = TranslationArguments(emptyMap(), emptyList(), Locale.ROOT)

        fun of(arguments: List<Component>, locale: Locale, transform: (Component) -> Component): TranslationArguments {
            if (arguments.isEmpty()) {
                return EMPTY
            }
            val named = arguments.map { TranslationUtilities.namedArgument(it) }
            val positional = arguments.zip(named) { argument, value -> Value(transform(value?.value ?: argument), value?.raw) }
            val byName = named
                .zip(positional)
                .mapNotNull { (argument, value) -> if (argument == null) null else argument.name to value }
                .toMap()
            return TranslationArguments(byName, positional, locale)
        }
    }
}
