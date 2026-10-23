package net.mythicisland.moonrise

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.mythicisland.moonrise.loader.YamlTranslationLoader
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
open class TranslationBenchmark {

    private val directory: Path = createLanguageDirectory()
    private val miniMessage = MiniMessage.miniMessage()
    private val cached = create(caching = true)
    private val uncached = create(caching = false)
    private val player = Argument.string("player", "Steve")
    private val coins = Argument.number("coins", 1337)
    private val translatable = Component.translatable("dynamic", player, coins)

    @Setup
    fun setup() {
        cached.initialize()
        uncached.initialize()
    }

    @TearDown
    fun tearDown() {
        cached.shutdown()
        uncached.shutdown()
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }

    @Benchmark
    fun miniMessageBaseline(): Component =
        miniMessage.deserialize(DYNAMIC, Placeholder.unparsed("player", "Steve"), Placeholder.unparsed("coins", "1337"))

    @Benchmark
    fun uncachedStatic(): Component = uncached.translate("static", Locale.ENGLISH)

    @Benchmark
    fun cachedStatic(): Component = cached.translate("static", Locale.ENGLISH)

    @Benchmark
    fun uncachedArguments(): Component = uncached.translate("dynamic", Locale.ENGLISH, player, coins)

    @Benchmark
    fun cachedArguments(): Component = cached.translate("dynamic", Locale.ENGLISH, player, coins)

    @Benchmark
    fun cachedChangingArguments(): Component {
        val random = Argument.string("player", "Player" + ThreadLocalRandom.current().nextInt(1_000_000))
        return cached.translate("dynamic", Locale.ENGLISH, random, coins)
    }

    @Benchmark
    fun cachedRenderTranslatable(): Component = cached.render(translatable, Locale.GERMANY)

    @Benchmark
    fun uncachedFormatted(): Component = uncached.translate("formatted", Locale.GERMANY, coins)

    @Benchmark
    fun cachedChangingFormatted(): Component {
        val random = Argument.number("coins", ThreadLocalRandom.current().nextInt(1_000_000))
        return cached.translate("formatted", Locale.GERMANY, random)
    }

    @Benchmark
    fun uncachedGradient(): Component = uncached.translate("gradient", Locale.ENGLISH, player)

    @Benchmark
    fun cachedGradient(): Component = cached.translate("gradient", Locale.ENGLISH, player)

    @Benchmark
    fun uncachedLang(): Component = uncached.translate("lang", Locale.ENGLISH, player)

    @Benchmark
    fun cachedLang(): Component = cached.translate("lang", Locale.ENGLISH, player)

    private fun create(caching: Boolean): Translations {
        val builder = Translations.builder()
            .loader(YamlTranslationLoader(directory))
            .defaultLocale(Locale.ENGLISH)
            .disableGlobalTranslator()
        return if (caching) builder.build() else builder.disableCaching().build()
    }

    private fun createLanguageDirectory(): Path {
        val directory = createTempDirectory("moonrise-benchmark")
        val messages = mapOf("static" to STATIC, "dynamic" to DYNAMIC, "formatted" to FORMATTED, "gradient" to GRADIENT, "lang" to LANG)
        directory.resolve("en.yml").writeText(messages.entries.joinToString("\n") { (key, value) -> "$key: \"$value\"" })
        return directory
    }

    private companion object {
        const val STATIC = "<gradient:#ff0000:#00ff00>Welcome to the server!</gradient> <gray>Have fun and enjoy your stay."
        const val DYNAMIC = "<green>Hello <player>, <hover:show_text:'<gray>Coins: <coins>'>you have <gold><coins></gold> coins</hover>!"
        const val FORMATTED = "<gray>You have <gold><coins:'#,##0.00'></gold> <coins:choice:'0#coins|1#coin|1<coins'>"
        const val GRADIENT = "<gradient:#ff0000:#00ff00>Welcome <player>!</gradient>"
        const val LANG = "<lang:static> <gray>Hello <player>"
    }
}
