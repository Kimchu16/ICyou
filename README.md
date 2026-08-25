# ICyou

A **Fabric** Minecraft mod that adds a working **CCTV surveillance system** plus a pair of icy decorative blocks.

Set up a network of security **Cameras**, then view them on **Screens** and **Camera Terminals**. Watch mobs appear as live position **blips**, cycle between channels, and even look around while you view the feed.

## Features

- **Cameras** — place them around your base and wire up a surveillance network.
- **Screens & Camera Terminals** — view a live camera feed and cycle between cameras (sneak while looking to switch).
- **Mouse-look pan/tilt** — look around from inside the camera view.
- **Networked blips** — mobs show up as small markers on the feed.
- **Setup Remote** & **Portable Screen** items for viewing and configuring on the go.
- **Decorative blocks** — the **Icy Block** and **Glacier Block**.

## Requirements

- **Java 21+**
- **Minecraft 1.21.1** with **Fabric Loader 0.16.9+**
- **Fabric API**

## Installing

1. Install the [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1.
2. Drop the `icyou-*.jar` into your `.minecraft/mods` folder (Fabric API is required — install it too).
3. Launch the game.

## Building from source

This is a standard Fabric Loom project using the bundled Gradle 8.9 wrapper (Java 21).

```bash
./gradlew runClient     # launch a modded client for testing
./gradlew build         # compile and package the release jar
```

The built mod jar is produced at `build/libs/`.

## Continuous Integration & Releases

A GitHub Actions workflow (`.github/workflows/build.yml`) builds the mod every
push. When you push a version tag like `v1.0.0`, it:

1. Builds `icyou-<version>.jar` (the version is taken from the tag).
2. Attaches the jar to a **GitHub Release** so it can be downloaded and
   uploaded to [CurseForge](https://authors.curseforge.com) (see
   `RELEASE_CURSEFORGE.md`).

To create a release, tag the commit and push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Mod details

- **Mod ID:** `icyou`
- **Package:** `com.matissjurevics.icyou`
- **Entrypoint:** `ICyouMod`
- **License:** MIT

## Source

- [GitHub repository](https://github.com/MatissJurevics/ICyou)
- Report issues [here](https://github.com/MatissJurevics/ICyou/issues)
