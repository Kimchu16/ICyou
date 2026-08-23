# Releasing ICyou on CurseForge

This file captures everything that needs to happen (**and your copy/paste text**) to publish
**ICyou** on [CurseForge](https://authors.curseforge.com/). It is based on the official
[Creating and Submitting a Project](https://support.curseforge.com/en/support/solutions/articles/9000197241-creating-and-submitting-a-project)
guide and the [How to Pass Moderation Review](https://blog.curseforge.com/how-to-pass-moderation-review-on-curseforge-2/) article.

> The **project page** (name/summary/description/images/license/categories) is filled in on the
> CurseForge website. The **file** (the `.jar`) is uploaded there too. Only code/content lives
> in this repo; this file is your cheat-sheet of what to paste where.

---

## 0. Pre-flight (done / to-do in this repo)

- [x] Mod builds cleanly: `./gradlew build` -> `build/libs/icyou-1.0.0.jar`
- [x] `fabric.mod.json` has a real description + GitHub contact/source/issue links
- [x] README updated with install/build/source info
- [x] MIT `LICENSE` present (bundled into the jar as `LICENSE_icyou`)
- [ ] Rebuild the jar so all latest resources are included (it already is, but re-run `./gradlew build` before uploading)
- [ ] Commit & push to `https://github.com/MatissJurevics/ICyou.git`

## 1. Create the CurseForge project

Sign in / register at <https://authors.curseforge.com> and create a project:
**Minecraft** → the type of project = **Mod**.

### Name
> **ICyou**

- Unique and identifiable ✓ (no loader/version words, not generic).

### Summary (short blurb — shows in listings & search)
> A working CCTV surveillance system for Minecraft — place cameras, watch live feeds on
> screens & terminals, and track mobs. Plus icy decorative blocks.

### Description (the big one — most common rejection reason; English, clear, engaging)
Copy/paste the following (adjust to taste):

```markdown
## ICyou — CCTV & Surveillance for Minecraft

Turn your base into a security fortress. ICyou adds a **working camera network**:
place surveillance cameras around your territory, then view them live on **Screens** and
**Camera Terminals**. It's a self-contained surveillance system that works in survival.

### What it does
- 🔵 **Cameras** — place them wherever you need eyes on a spot.
- 🖥️ **Screens & Camera Terminals** — view a live feed and **cycle between channels**
  (sneak while looking at a screen to switch cameras).
- 👀 **Mouse-look pan/tilt** — look around right from inside the camera view.
- 📍 **Networked blips** — mobs moving in front of a camera show up as markers on the feed.
- 🎮 **Setup Remote** & **Portable Screen** — configure and view feeds while on the move.
- ❄️ **Decorative blocks** — the **Icy Block** and **Glacier Block**.

### How to get it
Find the **ICyou** item group in Creative, or build devices via your favourite crafting
workflow / commands.

### Requirements
- Minecraft **1.21.1** (Java 21+)
- **Fabric Loader** 0.16.9 or newer
- **Fabric API**

### License
MIT. Source and issue tracker: <https://github.com/MatissJurevics/ICyou>
```

- Avoid a wall of text, keep it structured and skimmable (the above does that).
- You can embed screenshots/images directly in this box (helpful and encouraged for a visual mod).

### License
> **MIT** (matches `LICENSE` and `fabric.mod.json`).

### Class / Categories
- **Class:** Minecraft
- **Main category:** `Misc` (or a search-relevant one)
- **Additional categories (up to 4, only if they actually apply):** e.g. `Technology`, `Decoration`, `Adventure`/`Redstone`.
  > Don't add unrelated categories — moderators will bounce it back.

### Logo / Avatar
- Upload a **unique project logo** on CurseForge (no blank/gradient-only images, no copyrighted art).
- The in-game mod icon (`assets/icyou/icon.png`) is already a valid 64×64 PNG.
  You may want a higher-res logo (recommended) — a 512×512 or larger PNG is ideal.

### Additional Images (optional for mods, recommended)
- Add a few **screenshots** of cameras + screens in-game. This step is only *required* for
  texture packs / Sims, but previews improve click-through and are nice to have.

## 2. Upload the file

On the **Files** tab of the project dashboard:

- **Upload file:** `build/libs/icyou-1.0.0.jar`
  - Minecraft mods must be a **JAR** ✓ (not zipped).
  - Keep it under 2 GB ✓ (it's ~70 KB).
- **Display name:** `ICyou 1.0.0` (recommended; don't give every file the same name).
- **Release Type:** `Release` (synced to the CurseForge app by default).
- **Changelog:** (REQUIRED — files without a changelog are flagged as duplicates and may be removed):

  ```markdown
  ### ICyou 1.0.0
  - Initial release
  - Add CCTV surveillance: Cameras, Screens, Camera Terminals, Setup Remote, Portable Screen
  - Channel cycling and mouse-look pan/tilt while viewing a feed
  - Networked mob blips on screen feeds
  - Add Icy Block and Glacier Block decorations
  ```

- **Supported Versions:** `1.21.1`
- **Mod Loader:** `Fabric` (CurseForge uses this + `fabric.mod.json` to validate).
- **Related projects / Dependencies:**
  - Add **Fabric API** as a **Required** dependency (link the matching 1.21.1 project).
    > This is important: without it as a required dependency, CurseForge/the client may not
    > pull Fabric API automatically, and some installations will fail.

## 3. Submit & moderate

- Click **Upload**. The file goes to **"Under Review"**.
- Common rejection reasons to avoid: incomplete/grammar-broken description, blank/gradient
  logo, unrelated categories, missing dependency links, or files that crash/can't load.
- If it's returned, fix the noted items and resubmit (or open a support ticket).

## 4. After approval
- Post the CurseForge link in your GitHub repo README/issues.
- Add the project to a modpack-appropriate group / your author profile.
- For future versions: bump `mod_version` in `gradle.properties`, rebuild, upload as new
  file with a **changelog** (alpha/beta/release).

---

## Notes
- All titles/descriptions must be **English** (translations are optional, English first).
- The repository is the authoritative source of the mod content; CurseForge is just the
  distribution mirror plus metadata.
- If you change gameplay in a future release, update the description/changelog accordingly.
