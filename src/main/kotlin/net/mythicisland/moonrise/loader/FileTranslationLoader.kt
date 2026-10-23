package net.mythicisland.moonrise.loader

import net.mythicisland.moonrise.TranslationLoader
import net.mythicisland.moonrise.TranslationUtilities
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

public abstract class FileTranslationLoader(
    private val directory: Path,
    private val extensions: Set<String>,
) : TranslationLoader {

    private val logger = LoggerFactory.getLogger(FileTranslationLoader::class.java)

    protected abstract fun readMessages(file: Path): Map<String, String>

    override fun load(): Map<Locale, Map<String, String>> {
        if (!Files.isDirectory(directory)) {
            logger.warn("Translation directory {} does not exist", directory.toAbsolutePath())
            return emptyMap()
        }
        val files = Files.walk(directory).use { paths -> paths.filter { isTranslationFile(it) }.sorted().toList() }
        return files
            .mapNotNull { read(it) }
            .groupBy { it.locale }
            .mapValues { (_, translations) -> translations.fold(emptyMap()) { merged, file -> merge(merged, file) } }
    }

    private fun merge(merged: Map<String, String>, file: TranslationFile): Map<String, String> {
        val duplicates = merged.keys intersect file.messages.keys
        if (duplicates.isNotEmpty()) {
            logger.warn("Translation file {} overrides the keys {} for locale {}", file.path, duplicates, file.locale)
        }
        return merged + file.messages
    }

    private fun read(file: Path): TranslationFile? {
        val name = localeName(file)
        val locale = TranslationUtilities.parseLocale(name)
        if (locale == null) {
            logger.warn("Skipping translation file {}, '{}' is not a valid locale", file, name)
            return null
        }
        val messages = try {
            readMessages(file)
        } catch (exception: Exception) {
            logger.error("Skipping translation file {}, it could not be read", file, exception)
            return null
        }
        return TranslationFile(file, locale, messages)
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

    private class TranslationFile(val path: Path, val locale: Locale, val messages: Map<String, String>)
}
