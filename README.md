# NEON RINK: OVERDRIVE

A futuristic 3v3 arcade roller-skate arena sports game built for Android with a native 60 FPS OpenGL ES 3.0 3D engine, Jetpack Compose HUD/menus, procedural PCM synthwave audio, intelligent multi-role AI, DataStore local persistence, and Cloud Firestore + Google Sign-In global leaderboard.

> **Environment Note**: Godot 4 CLI / Android export templates are not installed in this cloud Android Gradle container, so the full playable game was built natively in Kotlin + OpenGL ES 3.0 + Jetpack Compose so it compiles and runs directly on Android devices and in the AI Studio Streaming Emulator.

## Features
- **Real-Time 3D Stadium (OpenGL ES 3.0)**: Metallic rink deck, glowing cyan/violet/orange neon markings, translucent dasher boards, dynamic 3D goal lights, animated LED stadium ribbon boards, cheering crowd silhouettes, floor reflections (High quality), ball/skate trails, and particle explosions.
- **Responsive Skating & Ball Physics**: Momentum carving, turn lean, rechargeable energy boost, tackle/dodge steals with cooldown, directional & smart auto-passing, tap snap shots, and hold-and-release **Charged Overdrive Shots**.
- **Intelligent 3v3 AI**: Role-aware `Striker`, `Defender`, and `Guardian` (Goalkeeper) behaviors with anti-clustering spacing and 4 difficulty tiers (`Easy`, `Normal`, `Hard`, `Expert`).
- **5 Playable Modes**:
  1. **Quick Match** (3:00 3v3 match + Sudden-Death Overtime + Goal Replay slow-mo camera)
  2. **Overdrive Cup Tournament** (3-round knockout bracket with saved progress)
  3. **Training Academy** (5 interactive live drills)
  4. **Practice Arena** (Free-skate shot lab with speed radar & ball reset)
  5. **Time Challenge** (90-second high-intensity scoring rush)
- **Progression, Customization & Global Leaderboard**: Earn XP and Credits to unlock 7 cosmetic categories (Outfits, Skates, Neon Trails, Energy Balls, Goal FX, Arenas, Celebrations) and climb the real-time Cloud Firestore Global Leaderboard.

## Controls (Landscape Gameplay)
- **Left Side**: Virtual Analog Joystick for skating movement & shot/pass aiming.
- **Right Side**:
  - **SHOOT**: Tap for a quick snap shot; **Hold & Release** to charge an Overdrive Shot.
  - **PASS**: Pass to the best-positioned or aimed teammate.
  - **BOOST**: Engage thruster speed burst (consumes Energy meter).
  - **TACKLE**: Lunge/steal dash with cooldown indicator.
- Supports **Standard**, **Compact**, and **Lefty Swap** layouts plus adjustable Joystick Sensitivity in Settings.

## Building an APK
- **Local Gradle**: `gradle :app:assembleDebug` (outputs to `app/build/outputs/apk/debug/app-debug.apk` and automatically copies the latest signed build to `debug/debug.apk`).
- **GitHub Actions**: `.github/workflows/build-debug-apk.yml` automatically builds and uploads `debug/debug.apk` on every push to `main`/`master`.
