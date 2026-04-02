# ZCB Live Forge 1.8.9 Backport

This directory contains a standalone Forge 1.8.9 MDK backport of `zcblive-mc`, synced to upstream commit `0d2b7cb` ("Fix side mouse click sounds").

## Scope

- Targets Minecraft Forge `1.8.9-11.15.1.2318`.
- Mirrors current click classification/input behavior from upstream Fabric code, including:
  - side mouse button handling (`MOUSE4`/`MOUSE5`)
  - duplicate mouse event suppression
  - hard-click toggle support
  - fallback sample resolution between keyboard/mouse clickpacks
  - dedicated clickpack options screen (instead of sound-options injection)

## Layout

- `src/main/java/...` Forge implementation
- `src/main/resources/...` resources/lang files
- `build.gradle`, `gradlew`, `gradle/wrapper/...` for standalone Forge MDK builds

## Notes For Reviewers

- This backport is intentionally isolated in `backports/` so it can be reviewed without affecting the Fabric build.
- If this is accepted, maintainers can:
  1. keep this as a legacy/compat target under `backports/`, or
  2. promote it into a first-class multi-loader/module setup in a follow-up PR.

