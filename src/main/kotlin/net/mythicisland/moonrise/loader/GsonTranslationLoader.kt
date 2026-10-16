package net.mythicisland.moonrise.loader

import org.spongepowered.configurate.gson.GsonConfigurationLoader
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.nio.file.Path

public class GsonTranslationLoader(directory: Path) : ConfigurateTranslationLoader(directory, setOf("json")) {

    override fun createLoader(path: Path): ConfigurationLoader<*> = GsonConfigurationLoader.builder().path(path).build()
}
