package net.mythicisland.moonrise

import net.kyori.adventure.text.Component

internal data class TranslationArguments(
    val named: Map<String, Component>,
    val positional: List<Component>,
) {

    fun find(name: String): Component? {
        val value = named[name]
        if (value != null) {
            return value
        }
        val index = name.toIntOrNull() ?: return null
        return positional.getOrNull(index)
    }

    companion object {
        val EMPTY: TranslationArguments = TranslationArguments(emptyMap(), emptyList())

        fun of(arguments: List<Component>, transform: (Component) -> Component): TranslationArguments {
            if (arguments.isEmpty()) {
                return EMPTY
            }
            val named = arguments
                .mapNotNull { TranslationUtilities.namedArgument(it) }
                .associate { it.name to transform(it.value) }
            val positional = arguments.map { transform(TranslationUtilities.unwrap(it)) }
            return TranslationArguments(named, positional)
        }
    }
}
