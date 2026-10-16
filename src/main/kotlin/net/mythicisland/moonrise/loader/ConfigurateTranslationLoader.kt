package net.mythicisland.moonrise.loader

import net.mythicisland.moonrise.TranslationLoader
import net.mythicisland.moonrise.TranslationUtilities
import org.slf4j.LoggerFactory
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

public abstract class ConfigurateTranslationLoader(
    private val directory: Path,
    private val extensions: Set<String>,
) : TranslationLoader {

    private val logger = LoggerFactory.getLogger(ConfigurateTranslationLoader::class.java)

    protected abstract fun createLoader(path: Path): ConfigurationLoader<*>

    override fun load(): Map<Locale, Map<String, String>> {
        if (!Files.isDirectory(directory)) {
            logger.warn("Translation directory {} does not exist", directory.toAbsolutePath())
            return emptyMap()
        }
        val files = Files.walk(directory).use { paths -> paths.filter { isTranslationFile(it) }.sorted().toList() }
        return files
            .mapNotNull { read(it) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, translations) -> translations.fold(emptyMap()) { merged, messages -> merged + messages } }
    }

    private fun read(file: Path): Pair<Locale, Map<String, String>>? {
        val name = localeName(file)
        val locale = TranslationUtilities.parseLocale(name)
        if (locale == null) {
            logger.warn("Skipping translation file {}, '{}' is not a valid locale", file, name)
            return null
        }
        return locale to flatten(createLoader(file).load(), "").toMap()
    }

    private fun localeName(file: Path): String {
        val relative = directory.relativize(file)
        if (relative.nameCount > 1) {
            return relative.getName(0).toString()
        }
        return relative.fileName.toString().substringBeforeLast('.')
    }

    private fun isTranslationFile(path: Path): Boolean =
        Files.isRegularFile(path) && path.fileName.toString().substringAfterLast('.', "").lowercase() in extensions

    private fun flatten(node: ConfigurationNode, path: String): List<Pair<String, String>> = when {
        node.isMap -> node.childrenMap().flatMap { (key, child) -> flatten(child, join(path, key.toString())) }
        node.isList -> listOf(path to node.childrenList().joinToString("\n") { it.string.orEmpty() })
        path.isEmpty() || node.rawScalar() == null -> emptyList()
        else -> listOf(path to node.string.orEmpty())
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"
}
