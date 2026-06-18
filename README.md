# Pocket Tanks AR

A native Android (Kotlin) implementation of the Pocket Tanks AR MVP, built
entirely via Gradle + GitHub Actions — **no Android Studio or Unity
required**. ARCore is pulled in as a normal Gradle dependency for real plane
detection and anchoring; rendering is a small hand-written OpenGL ES 2.0
pipeline.

## Getting the APK

Every push to this repo triggers `.github/workflows/build-apk.yml`, which
builds `app-debug.apk` on GitHub's runners and uploads it as a workflow
artifact. Open the **Actions** tab → latest "Build Debug APK" run → download
`pocket-tanks-ar-debug-apk` from the Artifacts section, then install it on an
ARCore-supported Android device (Android 7.0+ / API 24+, with Google Play
Services for AR installed from the Play Store).

## What's implemented (MVP scope)

- Real ARCore session: horizontal plane detection, frictionless auto-anchor
  placement once a large-enough flat surface is found (no manual tap/scan
  required).
- Procedurally generated, fully destructible terrain (1D heightfield with
  midpoint-displacement fractal generation), carved/mounded on impact.
- Turn-based Player-vs-Computer loop with two weapons: Standard HE (crater)
  and Dirt Mover (raises terrain).
- Ballistic physics with gravity + per-round crosswind, integrated each
  frame.
- Computer opponent: inverse ballistic formula for a perfect baseline shot,
  with a difficulty/turn-scaled random error so early shots miss and later
  shots converge on the player.
- Dual-view interface: 3D AR camera view (`SceneRenderer`) + a 2D
  top-down Picture-in-Picture radar (`RadarView`) showing terrain, tanks,
  wind, and the live shell.
- Tactile bottom-left radial dial (0–180° turret elevation) and bottom-right
  power bar, matching the PRD's HUD layout.
- Synthwave neon color palette (cyan `#00F3FF`, magenta `#FF0055`, charcoal
  `#1A1A24`, neon green ridge highlights), unlit/flat shading throughout.

## Known simplifications vs. the full PRD

These were necessary to ship a real, compilable MVP without Unity, a 3D
asset pipeline, or a physical device/Android Studio in this environment:

- **Terrain is a zero-thickness silhouette "wall"** anchored to the table,
  not a full 3D voxel mountain — a deliberate diorama-style simplification
  that still reads as a destructible mountain range from the AR camera.
- Tanks/turrets are procedurally built boxes (no modeled/textured assets).
- No real-world object occlusion, local multiplayer, or expanded armory —
  all explicitly deferred in the PRD's own roadmap (section 6.2).
- Physics constants are stylized for tabletop scale/pacing, not real-world
  9.8 m/s² gravity.

## Project layout

```
app/src/main/java/com/pockettanks/ar/
  SplashActivity.kt, GameActivity.kt   – screens & ARCore session lifecycle
  game/                                – pure Kotlin physics/AI/turn logic
  render/                              – ARCore + OpenGL ES scene renderer
  ui/                                  – RadialDialView (angle), RadarView (PiP)
```

## Local build (optional, requires Android SDK + internet access to
Google's Maven repo for ARCore/AGP)

```
./gradlew assembleDebug
```
