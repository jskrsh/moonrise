package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator
import net.mythicisland.moonrise.loader.GsonTranslationLoader
import net.mythicisland.moonrise.loader.YamlTranslationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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
            prefixed: "<prefix> Hi <player>"
            lang: "<lang:item> is shiny"
            loop: "Loop <lang:loop>"
            gradient: "<gradient:red:blue><player></gradient>"
            insert: "<insert:'/msg <player> '>Message</insert>"
            coins: "<coins> Coins"
            price: "<price:'#,##0.00'>"
            amount: "<amount:choice:'0#no coins|1#one coin|1<{0} coins'>"
            day: "<day:'dd.MM.yyyy'>"
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
            .tagResolver(Placeholder.parsed("prefix", "<gray>[Test]</gray>"))
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
        val result = translations.translate("positional", Locale.ENGLISH, Component.text("A"), Argument.string("second", "B"))
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
    fun `arguments inside insertions are inserted`() = bothModes { translations ->
        val result = translations.translate("insert", Locale.ENGLISH, Argument.string("player", "Steve"))
        assertEquals("/msg Steve ", findInsertion(result))
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
        assertFailsWith<IllegalStateException> { translations.contains("welcome", Locale.ENGLISH) }
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
        assertFailsWith<IllegalArgumentException> { Argument.string("bold", "value") }
    }

    @Test
    fun `custom tags are resolved`() = bothModes { translations ->
        val result = translations.translate("prefixed", Locale.ENGLISH, Argument.string("player", "Steve"))
        assertEquals("[Test] Hi Steve", plain.serialize(result))
    }

    @Test
    fun `lang tags are translated`() = bothModes { translations ->
        assertEquals("Diamond is shiny", plain.serialize(translations.translate("lang", Locale.ENGLISH)))
    }

    @Test
    fun `recursive lang tags do not overflow`() = bothModes { translations ->
        translations.translate("loop", Locale.ENGLISH)
    }

    @Test
    fun `children of translatable components are translated`() = bothModes { translations ->
        val component = Component.translatable("welcome", Argument.string("player", "Steve")).append(Component.translatable("item"))
        assertEquals("Welcome Steve!Diamond", plain.serialize(translations.render(component, Locale.ENGLISH)))
    }

    @Test
    fun `gradients are applied to arguments`() = bothModes { translations ->
        val result = translations.translate("gradient", Locale.ENGLISH, Argument.string("player", "Steve"))
        assertEquals("Steve", plain.serialize(result))
        assertTrue(colors(result).size > 1)
    }

    @Test
    fun `missing keys are reported`() = bothModes { translations ->
        val missing = translations.missingKeys(Locale.GERMAN)
        assertTrue("only-english" in missing)
        assertFalse("welcome" in missing)
        assertEquals(emptySet(), translations.missingKeys(Locale.US))
    }

    @Test
    fun `numbers are formatted for the locale`() = bothModes { translations ->
        val coins = Argument.number("coins", 1337)
        assertEquals("1,337 Coins", plain.serialize(translations.translate("coins", Locale.ENGLISH, coins)))
        assertEquals("1.337 Coins", plain.serialize(translations.translate("coins", Locale.GERMAN, coins)))
        val price = Argument.number("price", 1234.5)
        assertEquals("1,234.50", plain.serialize(translations.translate("price", Locale.ENGLISH, price)))
        assertEquals("1.234,50", plain.serialize(translations.translate("price", Locale.GERMAN, price)))
    }

    @Test
    fun `choices are formatted`() = bothModes { translations ->
        val text = { amount: Int, locale: Locale ->
            plain.serialize(translations.translate("amount", locale, Argument.number("amount", amount)))
        }
        assertEquals("no coins", text(0, Locale.ENGLISH))
        assertEquals("one coin", text(1, Locale.ENGLISH))
        assertEquals("1.337 coins", text(1337, Locale.GERMAN))
    }

    @Test
    fun `dates are formatted`() = bothModes { translations ->
        val result = translations.translate("day", Locale.GERMAN, Argument.date("day", LocalDate.of(2026, 10, 23)))
        assertEquals("23.10.2026", plain.serialize(result))
    }

    @Test
    fun `reload loads changed files`() = bothModes { translations ->
        directory.resolve("de.yml").writeText("welcome: \"Hallo <player>!\"")
        translations.reload()
        assertEquals("Hallo Steve!", plain.serialize(translations.translate("welcome", Locale.GERMAN, Argument.string("player", "Steve"))))
    }

    @Test
    fun `translations are registered at the global translator`() {
        Translations.builder().loader(YamlTranslationLoader(directory)).build().use { translations ->
            translations.initialize()
            val component = Component.translatable("welcome", Argument.string("player", "Steve"))
            assertEquals("Willkommen Steve!", plain.serialize(GlobalTranslator.render(component, Locale.GERMAN)))
        }
    }

    @Test
    fun `translating while reloading works`() {
        translations(caching = true).use { translations ->
            Executors.newFixedThreadPool(4).use { executor ->
                val futures = (0 until 4).map { thread ->
                    executor.submit(
                        Callable {
                            repeat(1_000) { index ->
                                translations.translate("welcome", Locale.GERMAN, Argument.string("player", "Player$thread-$index"))
                            }
                        },
                    )
                }
                repeat(10) { translations.reload() }
                futures.forEach { it.get() }
            }
        }
    }

    private fun colors(component: Component): Set<TextColor> {
        val color = component.color()
        val own = if (component is TextComponent && component.content().isNotEmpty() && color != null) setOf(color) else emptySet()
        return own + component.children().flatMap { colors(it) }
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

    private fun findInsertion(component: Component): String? =
        component.insertion() ?: component.children().firstNotNullOfOrNull { findInsertion(it) }

    private fun findClick(component: Component): ClickEvent<*>? =
        component.clickEvent() ?: component.children().firstNotNullOfOrNull { findClick(it) }
}
