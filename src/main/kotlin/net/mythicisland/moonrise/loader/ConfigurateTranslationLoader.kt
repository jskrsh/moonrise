package net.mythicisland.moonrise.loader

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Path

public abstract class ConfigurateTranslationLoader(
    directory: Path,
    extensions: Set<String>,
) : FileTranslationLoader(directory, extensions) {

    protected abstract fun createLoader(path: Path): ConfigurationLoader<*>

    override fun readMessages(file: Path): Map<String, String> = flatten(createLoader(file).load(), "").toMap()

    private fun flatten(node: ConfigurationNode, path: String): List<Pair<String, String>> = when {
        node.isMap -> node.childrenMap().flatMap { (key, child) -> flatten(child, join(path, key.toString())) }
        node.isList -> listOf(path to node.childrenList().joinToString("\n") { it.string.orEmpty() })
        path.isEmpty() || node.rawScalar() == null -> emptyList()
        else -> listOf(path to node.string.orEmpty())
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"
}
