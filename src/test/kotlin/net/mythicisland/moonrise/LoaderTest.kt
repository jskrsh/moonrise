package net.mythicisland.moonrise

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.mythicisland.moonrise.loader.GsonTranslationLoader
import net.mythicisland.moonrise.loader.PropertiesTranslationLoader
import net.mythicisland.moonrise.loader.YamlTranslationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LoaderTest {

    private val directory: Path = createTempDirectory("moonrise-loader")
    private val plain = PlainTextComponentSerializer.plainText()

    @AfterTest
    fun cleanup() {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }

    private fun translations(defaultLocale: Locale, vararg loaders: TranslationLoader): Translations {
        val builder = loaders.fold(Translations.builder()) { builder, loader -> builder.loader(loader) }
        val translations = builder.defaultLocale(defaultLocale).disableGlobalTranslator().build()
        translations.initialize()
        return translations
    }

    private fun Translations.text(key: String, locale: Locale): String = plain.serialize(translate(key, locale))

    @Test
    fun `properties files are loaded`() {
        directory.resolve("en.properties").writeText("welcome=Welcome!\numlaut=Grüße")
        translations(Locale.ENGLISH, PropertiesTranslationLoader(directory)).use { translations ->
            assertEquals("Welcome!", translations.text("welcome", Locale.ENGLISH))
            assertEquals("Grüße", translations.text("umlaut", Locale.ENGLISH))
        }
    }

    @Test
    fun `later files override earlier files of the same locale`() {
        val locale = directory.resolve("en").createDirectories()
        locale.resolve("a.yml").writeText("shared: \"A\"\nonly-a: \"Only A\"")
        locale.resolve("b.yml").writeText("shared: \"B\"")
        translations(Locale.ENGLISH, YamlTranslationLoader(directory)).use { translations ->
            assertEquals("B", translations.text("shared", Locale.ENGLISH))
            assertEquals("Only A", translations.text("only-a", Locale.ENGLISH))
        }
    }

    @Test
    fun `later loaders override earlier loaders`() {
        directory.resolve("en.yml").writeText("shared: \"Yaml\"")
        directory.resolve("en.json").writeText("""{ "shared": "Json" }""")
        translations(Locale.ENGLISH, YamlTranslationLoader(directory), GsonTranslationLoader(directory)).use { translations ->
            assertEquals("Json", translations.text("shared", Locale.ENGLISH))
        }
    }

    @Test
    fun `regions fall back to other regions of the same language`() {
        directory.resolve("en_US.yml").writeText("welcome: \"Welcome\"")
        directory.resolve("de_DE.yml").writeText("welcome: \"Willkommen\"")
        translations(Locale.US, YamlTranslationLoader(directory)).use { translations ->
            assertEquals("Willkommen", translations.text("welcome", Locale.of("de", "AT")))
            assertEquals("Welcome", translations.text("welcome", Locale.UK))
        }
    }

    @Test
    fun `file names that are no locales are skipped`() {
        directory.resolve("en.yml").writeText("welcome: \"Welcome\"")
        directory.resolve("messages.yml").writeText("welcome: \"Broken\"")
        translations(Locale.ENGLISH, YamlTranslationLoader(directory)).use { translations ->
            assertEquals(setOf(Locale.ENGLISH), translations.locales())
        }
    }
}
