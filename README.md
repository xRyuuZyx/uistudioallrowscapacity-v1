# UIStudio All-Rows Capacity Fix

Paper companion plugin for the uploaded UIStudio 2.2.5 build.

## Why it exists

UIStudio 2.2.5 computes `allowedRows` from the saved per-player `screen-height`, then removes rows from the player's HUD list when that calculated capacity is smaller than the configured profile. The earlier ScreenCapacityFix stored `actualScaledHeight * 3`, so fullscreen and small window clients were allowed different row counts.

This version stores UIStudio's maximum supported `screen-height` value (`4320`) for every valid `uistudio:screen` message. UIStudio's `allowedRows(int, int)` therefore permits up to 76 rows, rather than trimming the profile based on the current window height. The client-side BossBarExtender must also be updated to fit the received rows into the actual GUI using adaptive spacing.

## Compatibility notes

- This plugin listens to the existing `uistudio:screen` Bukkit plugin-message channel. It does not create or register a client payload.
- Keep the existing `uistudio_screen_sync` Fabric mod. Do not add another mod that registers `uistudio:screen`.
- Keep UIStudio and RoleplayHUD installed. This companion depends on UIStudio's public `players().setScreenHeight(UUID, int)` method.
- The server-side maximum is based on the inspected UIStudio 2.2.5 bytecode. A future UIStudio version may change the cap or method.

## Build

Use Java 21 and Gradle 9.2.1. Run `gradle build`. The JAR is created in `build/libs`.

## Install

Replace the previous `UIStudioScreenCapacityFix-1.0.0.jar` with the newly built JAR. Keep only one copy of this companion plugin. Restart the Paper server fully; do not use `/reload`.
