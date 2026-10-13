# V10 consolidation and release status

V10 development work is available for local testing. **Production certification is incomplete.** No release JAR has been packaged, and none of the evidence below establishes an OBS performance advantage. The target remains client-only Fabric, Minecraft 1.21.11 and Java 21.

## Everyday workflow

Pause → eye/record button (or F9) → play → stop → video ready. The title screen and pause menu retain Settings and Library shortcuts. Videos and Details stay separate. Existing profiles, clean capture, replay and markers remain optional. Settings retain the requested sidebar instead of adding a diagnostics dashboard.

Recording → Diagnostics → Support report opens a fixed, paginated preview. Export writes exactly that preview to `optic-support/` in the game directory and opens that folder. Nothing is uploaded or copied to the clipboard. Paths, account IDs, server/world names, chat, notes, device names and raw logs are excluded by construction. Selected hardware/version/configuration fields and numeric active-session metrics are allowed. Installed versions of seven relevant optional mods are included, not the complete mod list. Review all pages before sharing.

While recording, effective dimensions/FPS/encoder/backend are taken from the active session. When idle, next-recording settings are shown and active metrics are absent. FFmpeg version is collected during preparation on a worker; before first use it is explicitly unavailable. Reports do not contain an error-log excerpt; diagnosing a specific failure can still require manually reviewed logs. This is a deliberate privacy boundary, not automatic comprehensive support diagnosis.

## Settings and metadata safety

Settings schema 4 accepts prior known fields. Writes merge known values into the existing JSON, preserving unknown extensions. Older documents receive a byte-for-byte `.before-vN.bak` before replacement. Small malformed JSON gets an `.before-invalid.bak`; defaults can then be saved. A distinct backup is used if an existing backup differs. Future schemas, oversized/unreadable files and failed backups are not replaced. Writes use a temporary file and atomic replacement where the filesystem supports it. Save failures produce concise feedback.

Session manifest updates use the same preservation and future-schema protection. No migration rewrites a video. Existing profile schemas and recovery readers remain in place. Backups are not automatically deleted; rollback requires closing Minecraft and restoring the desired backup. Concurrent independent Minecraft instances editing the same settings file are not transactionally coordinated; use separate game directories. Atomic rename alone is not a guarantee against physical power loss.

## Compatibility matrix

| Area | Current evidence | Limits / workaround |
| --- | --- | --- |
| Minecraft 1.21.11 / Fabric | Shared Stonecutter build, 68 unit tests, packaged recording/pause/resume, world HUD/resize/reload harness | Short functional tests; long-session certification remains |
| Minecraft 26.2 / Fabric | Stonecutter port, packaged recording/pause/resume/save, decoded 1080p30 H.264/AAC, clean world capture | Vulkan, mod combinations, and long sessions unvalidated |
| Minecraft 26.3 / Fabric | Shared Stonecutter build, 68 unit tests, packaged recording/pause/resume, clean world capture | Intermittent native startup exits remain unresolved; Vulkan and long sessions unvalidated |
| Windows 11 x64, RTX 5050 Laptop, driver 610.74 | Live H.264/HEVC/AV1; persistent and portable readback evidence | Not a claim for every NVIDIA driver/GPU |
| Intel HEVC QSV | Actual probe and synthetic chart/audio mux tests | Full gameplay/long sessions unverified |
| AMD AMF / other Intel formats | Graceful unsuccessful probes on this host | Needs appropriate physical hardware |
| Windows process audio | Process-only capture integration | Physical device changes and multi-hour sync still need coverage |
| Other platforms | Video/mic design retains fallback paths | No automatic FFmpeg setup or game-audio certification |
| Flashback | Collision-aware controls; detected version in report | Simultaneous recording/load test pending |
| Sodium | Presence reported; vanilla recording architecture retained | No tested-version certification |
| Iris / shaders | Presence reported | SDR/HDR assumptions and clean GUI pass need shader-specific tests; use full HUD/existing color if needed |
| Simple Voice Chat (`voicechat`) | Presence reported; rendered mod audio belongs to Minecraft track | No separate voice-chat track adapter |
| Xaero / JourneyMap | Presence reported | No independent overlay/waypoint exclusion adapter; unknown overlays may remain in video |

Presence detection never enables an unverified adapter or makes optional mods mandatory. The compatibility screen and support report share one registry. No broad mixin override was added for these mods. Unsupported V9 auxiliary layers/HDR remain unavailable.

## Accessibility and localization

Core menu actions, navigation tabs, quality/resolution headings, completion actions and support-report buttons now use language keys. English and French catalogs cover that set with matching placeholders. Long button labels retain full tooltips and narration; sidebar text is clipped within its width and keyboard focus has a visible cue independent of selection. The support preview narrates the visible page and has keyboard-operable page/export actions.

This is not complete localization of every advanced screen or message. Advanced explanatory text, many dynamic settings, and notifications remain English. Full narration review, French native-speaker review, small-screen/GUI-scale coverage and translation of the remaining strings are release gates. A smoke screenshot is not accessibility certification.

## Required release evidence

- Eight-hour physical A/V recording and repeated lifecycle sessions with bounded RAM/VRAM/disk growth.
- Device unplug/reconnect, disk exhaustion, permissions, interrupted rename/mux, actual process crash and GPU/context-loss tests with retained recovery sources.
- Clean launcher installation of a remapped JAR, offline startup after setup, and startup/shutdown with the declared optional-mod versions.
- NVIDIA/AMD/Intel matrix on actual machines, editor imports and shader-specific color validation.
- Four-state randomized, warmed benchmarks with quality-matched OBS; publish unfavorable outcomes and uncertainty. See `benchmarks/V10.md`.
- Full localization/accessibility review and distribution/license review before publication.

No claim of a finished production release should be made until these gates have evidence. Existing V1–V9 release limitations continue to apply. Exact checks executed for this development pass are recorded in DEVELOPMENT.md.
