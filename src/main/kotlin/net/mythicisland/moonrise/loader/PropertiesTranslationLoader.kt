package net.mythicisland.moonrise.loader

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

public class PropertiesTranslationLoader(directory: Path) : FileTranslationLoader(directory, setOf("properties")) {

    override fun readMessages(file: Path): Map<String, String> {
        val properties = Properties()
        Files.newBufferedReader(file).use { properties.load(it) }
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }
}
