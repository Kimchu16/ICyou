# ICyou

A Fabric Minecraft mod for Minecraft **1.21.1**.

## Requirements

- **Java 21** (JDK, not just a JRE)
- **Minecraft 1.21.1** (Fabric Loader)

## Development

This is a standard Fabric Loom project. The bundled Gradle wrapper downloads Gradle 8.9 automatically.

Run the mod locally:

```bash
./gradlew runClient     # starts the modded Minecraft client
./gradlew build         # compiles and packages the mod
```

The built mod jar is produced at `build/libs/`.

## Mod details

- **Mod ID:** `icyou`
- **Package:** `com.matissjurevics.icyou`
- **Entrypoint:** `ICyouMod`

## Structure

```
build.gradle           # Gradle build script (Loom)
gradle.properties      # Version / mod configuration
fabric.mod.json        # Mod metadata
src/main/java/...      # Mod source code
src/main/resources/    # Assets, lang files, etc.
```
