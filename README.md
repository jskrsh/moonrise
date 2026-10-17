# moonrise

A small and fast translation library based on Adventure MiniMessage.

## Features

- Named (`<player>`) and positional (`<arg:0>`) placeholders
- Loaders for `.properties`, `.yml`/`.yaml` and `.json` files
- Works with the Adventure `GlobalTranslator`, so `Component.translatable(...)` just works
- Messages are parsed once and rendered results are cached
- Reloading at runtime

## Usage

### Dependency

```kotlin
repositories {
    maven("https://repo.mythicisland.club/releases")
}

dependencies {
    implementation("net.mythicisland:moonrise:1.1.0")
}
```

moonrise requires Java 25. Adventure is not included, it is expected to be provided by the platform (e.g. Minestom or Velocity).

### Translation files

The locale is taken from the folder or the file name. All files inside a locale folder are merged.

```
translations/
├── en_US/
│   ├── lobby.properties
│   └── game.properties
└── de_DE.properties
```

```properties
lobby.welcome=<prefix> <green>Welcome <player>!
lobby.coins=<gray>You have <gold><arg:0></gold> coins
lobby.discord=<click:open_url:'https://discord.gg/example'>Join our Discord, <player></click>
```

### Setup

```kotlin
val translations = Translations.builder()
    .loader(PropertiesTranslationLoader(dataFolder.resolve("translations")))
    .defaultLocale(Locale.US)
    .tagResolver(Placeholder.parsed("prefix", "<dark_gray>[<gold>Lobby</gold>]</dark_gray>"))
    .build()

translations.initialize()
```

### Sending messages

Translations are registered at the `GlobalTranslator`, so translatable components are translated for the locale of each player:

```kotlin
player.sendMessage(Component.translatable("lobby.welcome", Argument.string("player", player.name)))
player.sendMessage(Component.translatable("lobby.coins", Component.text(1337)))
```

You can also translate a message directly:

```kotlin
val message = translations.translate("lobby.welcome", player, Argument.string("player", player.name))
```

## Performance

moonrise parses every message only once while loading. Each message is split into static parts and placeholders:

- Messages without placeholders are parsed into a single component that is returned every time.
- Messages with placeholders keep their static parts and only insert the arguments when rendering.
- The rendered result of a message with arguments is cached. Sending the same message with the same arguments again returns the cached component. The cache holds up to 10,000 entries by default.
Caching can be turned off with `disableCaching()`.

### Benchmarks

Run the benchmarks yourself with:

```
./gradlew jmh
```

## License

This project is licensed under the Apache License 2.0.
