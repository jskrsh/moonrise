package net.mythicisland.moonrise.loader

import org.spongepowered.configurate.loader.ConfigurationLoader
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

public class YamlTranslationLoader(directory: Path) : ConfigurateTranslationLoader(directory, setOf("yml", "yaml")) {

    override fun createLoader(path: Path): ConfigurationLoader<*> = YamlConfigurationLoader.builder().path(path).build()
}
