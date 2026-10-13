# Optic

A client-only Fabric recorder for **Minecraft Java 1.21.11, 26.2, and 26.3**. 1.21.11 uses Java 21; both 26.x targets use Java 25. No server installation is required.

The working source contains the **V10 consolidation development implementation**, building on V9 advanced capture, V8 workflow, V7 markers, V6 replay, V5 resolution/presets and V4 clean capture. Stonecutter builds all three targets from the shared source tree. Production certification is incomplete; this is not a proven OBS replacement. See [PRODUCTION.md](PRODUCTION.md) for the support matrix and remaining release gates.

## Build and test

Run commands from the project root. Use a Java 25 JDK for Gradle; Java toolchains select Java 21 or 25 for the target. Missing toolchains can be provisioned through the Foojay resolver.

```text
gradlew.bat buildAll
gradlew.bat collectJars
gradlew.bat :1.21.11:runClient
gradlew.bat :26.2:runClient
gradlew.bat :26.3:runClient
gradlew.bat :1.21.11:productionSmoke
gradlew.bat :26.2:productionSmoke
gradlew.bat :26.3:productionSmoke
```

`buildAll` compiles and tests all three targets. `collectJars` builds/tests and collects the installable JARs in `dist/`, with the Minecraft version in each filename. Development clients use separate `run/1.21.11/`, `run/26.2/`, and `run/26.3/` directories. Existing worlds and videos in older run directories are retained; copy only disposable test worlds when testing across versions.

For IDE work, use `gradlew.bat "Set active project to 26.3"` to switch the shared source view, then `gradlew.bat "Reset active project"` before committing. Builds for either target work regardless of the active view.

Edit shared features in `src/main/java/`. Stonecutter comments select the small Minecraft API differences; the normal active source target is 1.21.11. Keep rendering backends in `src/legacy/java/` (1.21.11) and `src/renderpearl/java/` (shared modern hooks), with versioned GPU templates in `src/main/java/dev/optic/compat/`. Files under `versions/*/build/generated/stonecutter/` are generated and must not be edited. Small `versions/*/gradle.properties` files declare each target's dependencies. Each JAR accepts its exact tested Minecraft version; 26.1 and other 1.21.x releases are not yet supported.

**V10:** Preview/export a privacy-minimized support report under Recording → Diagnostics. Settings and session updates preserve unknown fields and back up migrations. Core navigation has English/French language keys, long-label tooltips and keyboard focus feedback. Benchmark tools and binary provenance are documented in [benchmarks/V10.md](benchmarks/V10.md) and [THIRD_PARTY.md](THIRD_PARTY.md). Advanced-screen localization and broad hardware/editor/long-session validation remain unfinished.

**V9:** Recording → Advanced → Advanced formats / color / overload offers hardware-tested HEVC/AV1, optional explicit SDR color, and an optional stop after sustained repeated frames. H.264 remains the default; replay uses H.264. HDR, auxiliary render layers and automatic quality adjustment are unavailable. See [ADVANCED_CAPTURE.md](ADVANCED_CAPTURE.md) for support and validation limits.

## Record

Enter a world and press **F9**, or click the eye/red-dot button immediately left of Advancements. Press **F8** or use the pause/play button to its left to pause and resume the recording timeline. The cog beneath it opens settings. Press Record/F9 again to stop; pressing it during setup cancels preparation.

**V8:** Recording settings now offers **Library** (open, rename, notes, markers, recoverable Trash/restore) and **Profiles / naming** (creator presets, a saved Custom profile, optional filename templates). Videos stay in Recordings; metadata stays in Details. See [WORKFLOW.md](WORKFLOW.md) for behavior and current limitations.

Settings use a neutral sidebar with six tabs:

- **Recording:** video preset, resolution, FPS, title/naming, last recording and diagnostics. Advanced opens quality, Fit/Fill and encoder choices.
- **Replay:** opt-in instant replay, 15/30/60/120/300-second history, availability/storage status and Save Clip. **F10** saves recent gameplay; change its binding in Minecraft Controls. Conflicts are indicated in settings.
- **Audio:** Minecraft audio, microphone/device, live gain and mute, peak/clipping indication, selected-device reconnect.
- **Capture:** include/exclude the HUD, chat history, subtitles, scoreboard, boss bars, action bar, 2D debug display and recording indicator from video independently of the player's view.
- **Locations:** shows video/details paths; change the destination using a folder path, or restore the default. Existing videos are not relocated when this setting changes.
- **Interface:** HUD, HUD details, completion-screen ON/OFF, and Off / Simple / Advanced notifications. Simple uses brief neutral top-right messages; Advanced uses Minecraft system toasts.

The bottom-left Recordings button opens finished videos; Details above it opens recording metadata and recovery files. Output defaults to `.minecraft/recordings/`; development output goes to `run/recordings/`.

```text
recordings/
  Optic-2026-10-10_00-00-26-0eabc9ab.mp4
recordings-details/
  Optic-2026-10-10_00-00-26-0eabc9ab/
    ...session metadata, metrics, logs and recovery sources...
```

Finished videos stay alone in recordings; logs and recovery sources live in the separate recordings-details folder. Custom video locations derive a sibling <folder>-details location. Legacy interrupted recordings remain recoverable. Recording settings offer an optional title and Default / V1 / Part 1 / Scene 1 naming. For example, Bounty SMP plus Part 1 produces Bounty SMP Part 1.mp4, then Part 2. Existing videos and reserved sessions are skipped safely. Default uses date/time and a short unique suffix.

## Instant replay

Turn on **Settings → Replay → Instant Replay**, enter a world and let history warm up. Press **F10** after a moment you want to keep. Clips are ordinary MP4s in Recordings; temporary segments and recovery information stay in Details. Replay is off by default and continuously captures/encodes when enabled, so it has a real recording cost.

F9 starts a full recording from the same stream while replay is armed; clip saving keeps that recording running without another capture or video encoder. Stopping the full recording saves it and restarts replay history. Change video/audio source settings with replay OFF. Clip boundaries may include up to two extra seconds on either side; full recordings can begin with up to two seconds of pre-roll. One clip saves at a time. History can shorten under storage pressure. See [REPLAY.md](REPLAY.md) for budgets, recovery and current validation limits.

## Audio

**V7 markers:** press F7 while recording or replay is armed. Configure types, optional notes/context and deferred thumbnails under Interface → Markers / timeline. View markers after saving; generic CSV/JSON and thumbnails stay in Details. F7 bookmarks, F10 saves clips. Context/thumbnails default OFF. See [MARKERS.md](MARKERS.md) for timing, recovery, privacy and editor limitations.

On **Windows x64 build 20348 or newer**, Optic captures audio rendered by Minecraft's process through WASAPI process loopback. Other applications are excluded. This includes mod audio, including voice chat and any mod microphone monitoring. It is not a clean voice-chat/category split. Minecraft volume settings and spatialization are already applied; endpoint processing/volume semantics need additional device validation.

Optional microphone input is captured separately. Gain and mute apply live; device selection and ON/OFF apply next recording. A peak meter appears while recording. Sustained clipping warns; bounded reconnect attempts target the same named input for up to roughly thirty seconds (driver call duration can extend this). An unidentified default input does not silently switch devices.

When both sources are present, the MP4 contains named **Minecraft (including mod audio)** and **Microphone** AAC tracks. Most players play one selected audio track at a time; import both into an editor to mix them. There is no automatic combined listening track. Mic defaults OFF in new configs. Old V1/V2 unavailable game-audio settings migrate to ON because that control previously could not be selected.

The Windows helper is built from the included `native/windows/ProcessAudio.cs`, bundled as a resource and extracted per session. No audio-helper binary is downloaded. Unsupported platforms continue video/microphone recording without game audio. Voice-chat isolation and Minecraft categories are deliberately not offered; see AUDIO.md.

## Performance and reliability

Performance (1080p30/Compact), Balanced (1080p60/Standard), Creator (1440p60/High), Cinematic (1440p30/Very high) and Custom are video-only presets. They retain your microphone, capture exclusions and file preferences. Presets request Auto encoding with safe fallback; they are not hardware performance guarantees. Existing settings migrate as Custom. Manual video changes select Custom. These choices apply to the next recording.

Recording size is independent of the window and fixed for the session. **Fit** preserves the full image with black bars when needed; **Fill** crops its center to fill the output. Both resize on the GPU. A 1080p window recorded as 1440p is upscaled, not additional world detail. No second world render is used. See [benchmark tools and protocol](benchmarks/README.md) for measured evidence and outstanding comparison work.

GPU resize/flip, triple PBO capture and non-waiting fences remain isolated from UI. Persistent coherent mappings avoid a frame-sized render-thread CPU copy on supported GPUs. The GL 3.3 fallback still copies a completed PBO into reused memory. Raw pipe transfer, encoder upload and driver synchronization have real costs; there is no OBS performance claim.

Video/audio queues are bounded. Encoder overload stops safely instead of growing RAM indefinitely. Microphone/device failures isolate the affected source. Audio timestamps preserve gaps as silence; microphone clock correction has synthetic drift tests, not a certified four-hour hardware sync guarantee.

In-progress fragmented MP4 and PCM remain inside the support folder. Do not delete this folder after a crash. Next launch scans both current and legacy layouts, locks active sessions, refuses overwrite, remuxes to a temporary file, decodes the result, and only then publishes it. Recovery cannot guarantee preservation of the last incomplete fragment or survive all filesystem/power failures.

FFmpeg is discovered on PATH or obtained from a fixed, checksum-verified Gyan Windows x64 archive over HTTPS. No arbitrary URL setting, elevation or PATH modification is used. Cache and global probe logs now live under `config/optic-tools/`. See [FFmpeg downloads](https://ffmpeg.org/download.html) and [Gyan builds](https://www.gyan.dev/ffmpeg/builds/).

## Development and remaining gates

```text
gradlew.bat test integrationTest audioIntegrationTest
gradlew.bat runClient
```

The audio integration task plays a quiet synthetic tone; it does not open a microphone. Windows builds use the installed .NET Framework C# compiler for the owned helper. Non-Windows builds skip that resource and cannot provide game audio. Java 21 and Fabric API are required.

Still pending: measured four-hour physical-device A/V sync, USB/Bluetooth reconnect matrix, editor import checks, Simple Voice Chat isolation, category capture, non-Windows game capture, broad GPU/mod compatibility, disk-full fault injection, production-JAR launch and controlled OBS benchmarks. 1440p120 has produced valid but heavily duplicated footage and needs throughput tuning. Menus, chat input, toasts and world-space overlays remain part of video. See DEVELOPMENT.md, ARCHITECTURE.md, AUDIO.md, CLEAN_CAPTURE.md and PERFORMANCE.md for evidence and limits.

Clean capture is optional and defaults to include-all. HUD OFF overrides child elements; the recording indicator has an independent switch. Exclusions apply next frame. Names, waypoints and arbitrary mod overlays have no validated exclusion adapter. The compatibility page explains this instead of offering nonfunctional toggles. Clean mode adds a GPU color copy and an extra GUI pass on captured frames, without a second world render; it has a measurable performance cost.

