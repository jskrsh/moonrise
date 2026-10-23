package net.mythicisland.moonrise.loader

import org.slf4j.LoggerFactory
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Path

public abstract class ConfigurateTranslationLoader(
    directory: Path,
    extensions: Set<String>,
) : FileTranslationLoader(directory, extensions) {

    private val logger = LoggerFactory.getLogger(ConfigurateTranslationLoader::class.java)

    protected abstract fun createLoader(path: Path): ConfigurationLoader<*>

    override fun readMessages(file: Path): Map<String, String> {
        val messages = flatten(createLoader(file).load(), "")
        val duplicates = messages.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            logger.warn("Translation file {} contains the keys {} multiple times", file, duplicates)
        }
        return messages.toMap()
    }

    private fun flatten(node: ConfigurationNode, path: String): List<Pair<String, String>> = when {
        node.isMap -> node.childrenMap().flatMap { (key, child) -> flatten(child, join(path, key.toString())) }
        node.isList -> listOf(path to node.childrenList().joinToString("\n") { it.string.orEmpty() })
        path.isEmpty() || node.rawScalar() == null -> emptyList()
        else -> listOf(path to node.string.orEmpty())
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"
}
