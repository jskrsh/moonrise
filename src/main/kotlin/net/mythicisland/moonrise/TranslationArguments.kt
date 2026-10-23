package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
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

        fun of(arguments: List<Component>, locale: Locale, transform: (Component) -> Component): TranslationArguments {
            if (arguments.isEmpty()) {
                return TranslationArguments(emptyList(), emptyList(), locale)
            }
            val named = arguments.map { TranslationUtilities.namedArgument(it) }
            val values = arguments.zip(named) { argument, value -> Value(transform(value?.value ?: argument), value?.raw) }
            return TranslationArguments(named.map { it?.name }, values, locale)
        }
    }
}
