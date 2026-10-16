package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.mythicisland.moonrise.loader.GsonTranslationLoader
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranslationsTest {

    private val directory: Path = createTempDirectory("moonrise")
    private val plain = PlainTextComponentSerializer.plainText()

    init {
        directory.resolve("en.yml").writeText(
            """
            welcome: "<green>Welcome <player>!"
            positional: "<arg:0> and <arg:1>"
            only-english: "English only"
            colored: "<red><player></red>"
            hover: "<hover:show_text:'Hello <player>'>Hover me</hover>"
            click: "<click:run_command:'/tp <player> <arg:1>'>Teleport</click>"
            item: "Diamond"
            gift: "<player> got <item>"
            messages:
              nested:
                key: "Nested"
            lore:
              - "First line"
              - "Second line"
            """.trimIndent(),
        )
        directory.resolve("de.yml").writeText("welcome: \"<green>Willkommen <player>!\"")
        directory.resolve("fr").createDirectories().resolve("messages.yml").writeText("welcome: \"Bienvenue <player>!\"")
        directory.resolve("es.json").writeText("""{ "welcome": "Bienvenido <player>!" }""")
        directory.resolve("not a locale.yml").writeText("welcome: broken")
    }

    @AfterTest
    fun cleanup() {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }

    private fun translations(caching: Boolean): Translations {
        val builder = Translations.builder()
            .loader(YamlTranslationLoader(directory))
            .loader(GsonTranslationLoader(directory))
            .defaultLocale(Locale.ENGLISH)
            .disableGlobalTranslator()
        val translations = if (caching) builder.build() else builder.disableCaching().build()
        translations.initialize()
        return translations
    }

    private fun bothModes(test: (Translations) -> Unit) {
        listOf(true, false).forEach { caching ->
            translations(caching).use(test)
        }
    }

    @Test
    fun `named arguments are inserted`() = bothModes { translations ->
        val result = translations.translate("welcome", Locale.ENGLISH, Argument.string("player", "Steve"))
        assertEquals("Welcome Steve!", plain.serialize(result))
    }

    @Test
    fun `positional arguments are inserted`() = bothModes { translations ->
        val result = translations.translate("positional", Locale.ENGLISH, Component.text("A"), Argument.string("b", "B"))
        assertEquals("A and B", plain.serialize(result))
    }

    @Test
    fun `locales fall back to language and default locale`() = bothModes { translations ->
        val player = Argument.string("player", "Steve")
        assertEquals("Willkommen Steve!", plain.serialize(translations.translate("welcome", Locale.GERMANY, player)))
        assertEquals("Willkommen Steve!", plain.serialize(translations.translate("welcome", Locale.of("de", "AT"), player)))
        assertEquals("English only", plain.serialize(translations.translate("only-english", Locale.GERMAN)))
        assertEquals("Welcome Steve!", plain.serialize(translations.translate("welcome", Locale.JAPANESE, player)))
    }

    @Test
    fun `locale directories and json files are loaded`() = bothModes { translations ->
        val player = Argument.string("player", "Steve")
        assertEquals("Bienvenue Steve!", plain.serialize(translations.translate("welcome", Locale.FRENCH, player)))
        assertEquals("Bienvenido Steve!", plain.serialize(translations.translate("welcome", Locale.of("es"), player)))
        assertEquals(setOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.of("es")), translations.locales())
    }

    @Test
    fun `nested keys and lists are flattened`() = bothModes { translations ->
        assertEquals("Nested", plain.serialize(translations.translate("messages.nested.key", Locale.ENGLISH)))
        assertEquals("First line\nSecond line", plain.serialize(translations.translate("lore", Locale.ENGLISH)))
    }

    @Test
    fun `missing arguments stay visible`() = bothModes { translations ->
        assertEquals("Welcome <player>!", plain.serialize(translations.translate("welcome", Locale.ENGLISH)))
    }

    @Test
    fun `missing keys return a translatable component`() = bothModes { translations ->
        assertFalse(translations.contains("unknown", Locale.ENGLISH))
        assertEquals(Component.translatable("unknown"), translations.translate("unknown", Locale.ENGLISH))
    }

    @Test
    fun `arguments inherit the surrounding style`() = bothModes { translations ->
        val result = translations.translate("colored", Locale.ENGLISH, Argument.string("player", "Steve"))
        assertEquals(NamedTextColor.RED, colorOf(result, "Steve", null))
    }

    @Test
    fun `arguments inside hover texts are inserted`() = bothModes { translations ->
        val result = translations.translate("hover", Locale.ENGLISH, Argument.string("player", "Steve"))
        val hover = assertNotNull(findHover(result))
        assertEquals("Hello Steve", plain.serialize(hover))
    }

    @Test
    fun `arguments inside click events are inserted`() = bothModes { translations ->
        val result = translations.translate("click", Locale.ENGLISH, Argument.string("player", "Steve"), Component.text("Alex"))
        val payload = assertNotNull(findClick(result)).payload()
        assertEquals(ClickEvent.Payload.string("/tp Steve Alex"), payload)
    }

    @Test
    fun `nested translatable arguments are translated`() = bothModes { translations ->
        val result = translations.translate(
            "gift",
            Locale.ENGLISH,
            Argument.string("player", "Steve"),
            Argument.component("item", Component.translatable("item")),
        )
        assertEquals("Steve got Diamond", plain.serialize(result))
    }

    @Test
    fun `translatable components are rendered by the translator`() = bothModes { translations ->
        val component = Component.translatable("welcome", Argument.string("player", "Steve"))
        assertEquals("Willkommen Steve!", plain.serialize(translations.render(component, Locale.GERMAN)))
    }

    @Test
    fun `cached and uncached results are equal`() {
        val cached = translations(caching = true)
        val uncached = translations(caching = false)
        listOf("welcome", "positional", "colored", "hover", "click", "lore").forEach { key ->
            val arguments = arrayOf(Argument.string("player", "Steve"), Component.text("B"))
            assertEquals(
                uncached.translate(key, Locale.ENGLISH, *arguments),
                cached.translate(key, Locale.ENGLISH, *arguments),
            )
        }
        cached.shutdown()
        uncached.shutdown()
    }

    @Test
    fun `lifecycle is checked`() {
        val translations = Translations.builder().loader(YamlTranslationLoader(directory)).disableGlobalTranslator().build()
        assertFailsWith<IllegalStateException> { translations.translate("welcome", Locale.ENGLISH) }
        translations.initialize()
        assertTrue(translations.isInitialized)
        assertFailsWith<IllegalStateException> { translations.initialize() }
        translations.shutdown()
        translations.shutdown()
        assertFalse(translations.isInitialized)
    }

    @Test
    fun `invalid argument names are rejected`() {
        assertFailsWith<IllegalArgumentException> { Argument.string("no spaces", "value") }
    }

    private fun colorOf(component: Component, content: String, inherited: TextColor?): TextColor? {
        val color = component.color() ?: inherited
        if (component is TextComponent && component.content() == content) {
            return color
        }
        return component.children().firstNotNullOfOrNull { colorOf(it, content, color) }
    }

    private fun findHover(component: Component): Component? {
        val hover = component.hoverEvent()?.value()
        if (hover is Component) {
            return hover
        }
        return component.children().firstNotNullOfOrNull { findHover(it) }
    }

    private fun findClick(component: Component): ClickEvent<*>? =
        component.clickEvent() ?: component.children().firstNotNullOfOrNull { findClick(it) }
}
