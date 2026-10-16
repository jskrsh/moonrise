package net.mythicisland.moonrise

import java.util.Locale

/** Loads the messages of every locale. */
public fun interface TranslationLoader {

    public fun load(): Map<Locale, Map<String, String>>
}
