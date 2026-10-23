# moonrise

A small and fast translation library for Adventure, based on MiniMessage.

## Dependency

```kotlin
repositories {
    maven("https://repo.mythicisland.club/releases")
}

dependencies {
    implementation("net.mythicisland:moonrise:1.2.0")
}
```

Requires Java 25. Adventure is not included, it has to be provided by the platform.

## Setup

```kotlin
val translations = Translations.builder()
    .loader(YamlTranslationLoader(dataFolder.resolve("translations")))
    .defaultLocale(Locale.US)
    .tagResolver(Placeholder.parsed("prefix", "<gray>[<gold>Lobby</gold>]</gray>"))
    .build()

translations.initialize()
```

The locale is taken from the file or folder name, e.g. `en_US.yml` or `de_DE/lobby.yml`. If a key is missing, other loaded locales of the same language are used first, then the default locale.

## Messages

```yaml
welcome: "<prefix> <green>Welcome <player>!"
coins: "You have <gold><coins></gold> <coins:choice:'0#coins|1#coin|1<coins'>"
price: "Price: <price:'#,##0.00'>"
joined: "Joined on <date:'dd.MM.yyyy'>"
status: "<player> is <online:'<green>online':'<red>offline'>"
```

```kotlin
player.sendMessage(Component.translatable("welcome", Argument.string("player", player.name)))
player.sendMessage(Component.translatable("coins", Argument.number("coins", 1337)))

val message = translations.translate("welcome", player, Argument.string("player", player.name))
```

Arguments can't use names of MiniMessage tags like `color` or `key`.

## License

Apache License 2.0
