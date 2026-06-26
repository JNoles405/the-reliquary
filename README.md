# The Reliquary

A personal media-collection manager for the things you **own** — Movies, Books,
Music, Games, and Comics — plus your own custom categories. Cross-platform
(Windows desktop + Android), offline-first with a local database on each device,
and **manual sync** between them. Think Collectorz.com, but with a cleaner,
Netflix-style interface and better usability.

## Why "Reliquary"

A reliquary is a container for treasured things. This app is the container for
your collection.

## Highlights (planned)

- **Tabs per media type** — Movies, Books, Music, Games, Comics, and unlimited
  user-defined **custom tabs**.
- **Barcode + search import** — scan a barcode (camera on Android, manual/USB
  scanner entry on desktop) or search by title / ISBN / catalog number, then
  import full metadata and cover art automatically.
- **Loans** — lend an item to someone, record who has it and for how long
  ("Josh borrowed *Dune* for 2 weeks"), and track due dates.
- **Netflix-style UI** — a large hero graphic for the selected item with details
  below, and horizontally scrolling shelves.
- **Manual sync** — export/import a portable library file (via cloud drive or
  USB) as the reliable baseline, with direct **LAN sync** layered on later.

## Tech stack

| Concern        | Choice |
| -------------- | ------ |
| Language       | Kotlin (Multiplatform) |
| UI             | Compose Multiplatform |
| Android build  | Android Studio → APK (Gradle) |
| Windows build  | Compose for Desktop (JVM) → `.msi` / `.exe` |
| Local database | SQLDelight *(incoming)* |
| Networking     | Ktor client *(incoming)* |
| Images         | Coil 3 *(incoming)* |

One Kotlin codebase produces both apps, so the data model, metadata lookups, and
sync logic are written **once** and shared.

## Metadata providers

Keyless providers work out of the box. Key-gated providers are fully coded and
activate once you paste a (free) API key into the in-app **Settings** screen —
keys are stored locally and **never committed**.

| Media  | Keyless (default)         | Key-gated (optional)     |
| ------ | ------------------------- | ------------------------ |
| Books  | Open Library, Google Books | —                       |
| Music  | MusicBrainz / Cover Art    | Discogs                 |
| Movies | —                         | TMDB                     |
| Games  | —                         | IGDB (Twitch)            |
| Comics | —                         | ComicVine                |

## Repository layout

```
composeApp/
  src/
    commonMain/   # shared UI, domain, data, metadata, sync (the bulk of the app)
    androidMain/  # Android entry point (MainActivity) + manifest
    desktopMain/  # Windows desktop entry point (main.kt)
build.gradle.kts  # root
settings.gradle.kts
gradle/libs.versions.toml  # version catalog
```

## Building

> Requires the Android Studio bundled JDK (JBR 21) or any JDK 17+. The Java 8 on
> a default Windows PATH is **too old** — point `JAVA_HOME` at the JBR, e.g.
> `C:\Program Files\Android\Android Studio\jbr`.

### Windows desktop

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :composeApp:run                      # run from source
.\gradlew.bat :composeApp:packageDistributionForCurrentOS   # build .msi/.exe
```

### Android

Open the project in **Android Studio**, let it sync, then Run the `composeApp`
configuration on a device/emulator, or build an APK:

```powershell
.\gradlew.bat :composeApp:assembleDebug            # APK in composeApp/build/outputs/apk/
```

A `local.properties` pointing at your Android SDK is required for Android builds
(created automatically by Android Studio; it is git-ignored).

### Signed release builds

Release signing reads a **git-ignored** `keystore.properties` at the repo root:

```
storeFile=keystore/reliquary-release.jks
storePassword=...
keyAlias=reliquary
keyPassword=...
```

Generate a keystore once (keep it and the passwords safe — losing them means you
can't ship updates under the same identity):

```powershell
keytool -genkeypair -v -keystore keystore\reliquary-release.jks -alias reliquary `
  -keyalg RSA -keysize 2048 -validity 10000
```

Then build (release is signed only when `keystore.properties` is present):

```powershell
.\gradlew.bat :composeApp:assembleRelease   # signed APK  → composeApp/build/outputs/apk/release/
.\gradlew.bat :composeApp:bundleRelease      # signed AAB (Play) → composeApp/build/outputs/bundle/release/
```

Desktop installers are produced with a full JDK 17+ (the JBR lacks `jpackage`):

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

## Roadmap

- [x] Project scaffold: KMP + Compose Multiplatform shell on Windows & Android
- [x] Local database (SQLDelight) + repositories
- [x] Domain model for items, people, loans, custom tabs
- [x] Metadata provider framework + keyless providers (Open Library, Google Books, MusicBrainz)
- [x] Key-gated providers (TMDB, IGDB, ComicVine, Discogs) + Settings screen
- [x] Barcode scanning (Android camera via ML Kit) + manual / USB-scanner entry
- [x] Item detail screen (Netflix hero layout)
- [x] Loan management
- [x] Custom tabs
- [x] Manual sync: file export/import with last-write-wins merge
- [x] LAN sync (direct device-to-device over local Wi-Fi)
- [x] Cover-image caching to local files (covers survive offline)
- [x] App icons (Android adaptive icon, desktop window icon) + shared run configs
