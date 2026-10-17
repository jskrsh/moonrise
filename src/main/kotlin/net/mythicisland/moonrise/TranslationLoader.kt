package net.mythicisland.moonrise

import java.util.Locale

public fun interface TranslationLoader {

    public fun load(): Map<Locale, Map<String, String>>
}
