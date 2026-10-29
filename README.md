# OTG Explorer

OTG Explorer is a native Android file organizer for storage locations that you grant through
Android's Storage Access Framework (SAF). It is designed for USB OTG drives, SD cards, and other
document providers, but it never assumes that a selected folder is removable storage and never
turns a document URI into a raw filesystem path.

- **Package:** `com.juanmediahub.usbotgorganizer`
- **Minimum SDK:** 26 (Android 8.0)
- **Target / compile SDK:** 36
- **UI:** single-activity Kotlin, Jetpack Compose, Material 3
- **Architecture:** manual dependency injection, one activity-scoped `AppViewModel`, `StateFlow`
  UI state, Preferences DataStore for settings
- **Network:** none. The app declares no permissions and requests no internet access.

## Features

### Locations
- Add folders with the system `ACTION_OPEN_DOCUMENT_TREE` picker.
- Keep multiple locations, with provider-derived display names and editable labels.
- Persist only the read and/or write URI grants that Android actually returned.
- Validate access on launch and on refresh; unavailable locations are marked and can be
  re-authorized or forgotten.
- Release persisted permissions when a location is forgotten.
- Show whether each location is read-only or read/write.

### Browsing
- List and grid views with folder-first sorting by name, date, or size.
- Breadcrumb navigation and an up action.
- Current-folder-only search (search is intentionally not recursive).
- Refresh, multi-select, select-all, and a details screen with metadata that the provider exposes.
- Hidden-file visibility and default view/sort preferences.
- Empty, loading, permission-lost, and provider-error states with recovery actions.

### File operations
- Open files with the system handler; open folders.
- Create folder, rename, delete, copy, move, and organize by category.
- Multi-item clipboard with copy or move, then paste into another folder or another location.
- Conflict handling for every destination collision: **Keep both**, **Skip**, or **Cancel**.
- Keep-both naming produces unique names such as `photo (1).jpg`.
- Organize selected regular files into category folders (`Images`, `Videos`, `Audio`,
  `Documents`, `Archives`, and `Other`) with a preview before applying.
- Bounded streaming buffers, cancellation, and progress reporting for long transfers.
- Provider move is attempted when it is safe; otherwise the engine copies, verifies the
  destination, and only then deletes the source.
- Failed moves preserve the source and report a partial result.
- Cancellation cleans up incomplete destination work.
- Self-copy and copy-into-descendant operations are refused.
- Operation summaries report completed, skipped, and failed counts accurately, including after
  cancellation.

## Important SAF and USB behavior

OTG Explorer is a document-provider client, not a USB host driver.

- A selected folder is called a **Location**, not a "USB drive". The picker may return internal
  storage, an SD card, a cloud provider, or a USB device.
- Access is limited to the exact trees that were granted. Paths outside those grants are invisible
  to the app even if they exist on the same physical device.
- Not every phone exposes USB mass storage through SAF. Some devices, filesystems, or drives may
  not appear, may be read-only, or may disconnect during an operation.
- Document providers can rename, deduplicate, or move documents in provider-specific ways. The app
  verifies what it can, but it cannot guarantee behavior beyond the provider's API.
- Moving or copying a folder between two different tree grants is treated conservatively. The
  provider-move fast path is used only where the app can prove both endpoints belong to the same
  tree.
- Transfers run only while the app is open. There is no background service, WorkManager job, or
  foreground service; closing the app or having Android stop its process ends the operation.
- Metadata such as size and modified time is optional. The UI tolerates missing metadata and sorts
  those items predictably.

## Build and run

### Requirements
- Android Studio with Android SDK Platform 36 installed.
- JDK 17 or newer (the project was built with JetBrains Runtime 21).
- Windows, macOS, or Linux. The Gradle wrapper is included.

### Commands

From the repository root:

```bat
REM Windows
gradlew.bat :app:assembleDebug
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Tests

The unit tests are plain JVM tests and do not need a device:

```bat
gradlew.bat :app:testDebugUnitTest
```

They cover:

- Filename validation and illegal characters.
- Keep-both unique naming.
- Category assignment for files and extensions.
- Sorting, including items with missing metadata and folder-first ordering.
- Move fallback preserving the source when deletion fails.
- Cancellation cleanup of partial destinations.
- Refusal to copy a directory into itself or a descendant.
- Partial-result and summary reporting.

### Lint

```bat
gradlew.bat :app:lintDebug
```

`local.properties` is machine-specific and should stay untracked. On Windows it needs escaped
separators, for example:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

## Project layout

```text
app/src/main/java/com/juanmediahub/usbotgorganizer/
  MainActivity.kt                 Activity, picker launcher, theme wiring
  di/AppContainer.kt              Manual dependency container
  data/preferences/               DataStore settings and saved locations
  data/storage/                   SAF implementation and permission handling
  domain/model/                   Storage models
  domain/operations/              Filename, sorting, categorizing, ancestry, engine
  domain/storage/                 Storage abstraction
  ui/                             ViewModel, root host, components, screens
app/src/test/java/...             JVM unit tests with an in-memory storage fake
```

## Physical-device checklist

These checks have **not** been performed as part of the automated build in this repository. Run
them on real hardware before treating a release as device-verified.

1. Connect a USB OTG drive and confirm Android offers it to the system picker.
2. Add the drive as a Location; confirm the label is editable and access survives an app restart.
3. Confirm a read-only grant is shown as read-only and write actions are disabled or fail cleanly.
4. Browse deeply nested folders, use breadcrumbs, and verify folder-first sorting.
5. Open a file with an installed app, then return to OTG Explorer.
6. Create, rename, and delete a folder and a file.
7. Copy several files, including one name collision, using each conflict choice.
8. Move files between folders in the same location and confirm the source is gone only after the
   destination exists.
9. Move files between two separate Locations and confirm the result or the reported failure.
10. Organize a mixed set of files and verify the preview matches the applied result.
11. Start a large copy and cancel it; confirm progress stops and no partial destination remains.
12. Unplug the drive during a transfer; confirm the app reports a recoverable error and does not
    crash or claim success.
13. Forget a Location and confirm its persisted permission is released and it disappears from the
    list.
14. Rotate the device during browsing and during a transfer; confirm state is retained.
15. Check TalkBack labels for the main actions and dialogs.

## Known limitations

- No background or scheduled transfers.
- No root, raw filesystem, or USB host access; only SAF document trees.
- Search covers the current folder only.
- No cloud sync, accounts, analytics, ads, or network features.
- Folder moves depend on provider support; the app falls back to copy-verify-delete when needed.
- Very large folders depend on the provider's speed and the device's memory/CPU.
