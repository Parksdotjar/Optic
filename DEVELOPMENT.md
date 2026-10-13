# Development log

## 0.10.2 — startup hardening and Minecraft 26.2

Added the exact 26.2 Stonecutter target with Fabric API 0.161.0+26.2 and Java 25. Shared modern capture templates select the 26.2 Blaze3D GPU API versus 26.3 RenderPearl, and select the different GameRenderer render-call descriptor. The ring, queue ownership, asynchronous transfer, GPU resize and GUI composition logic are unchanged. Root build/collect tasks cover all three targets; the active source view remains 1.21.11. Legacy source archives exclude inactive modern templates to avoid duplicate entries.

Removed unconditional Java Sound device discovery from client initialization. Audio settings now request throttled worker-side discovery on demand; recording still opens the configured input normally. Inspected Windows dumps locally: the older LWJGL failure dereferenced its not-yet-initialized JVM pointer in a native thread-detach callback. Eager audio discovery was a plausible trigger. A distinct Java 25/shader-thread fault is still not conclusively attributed. See [STARTUP_STABILITY.md](STARTUP_STABILITY.md); no driver, service, overlay or global JVM settings were changed.

26.2 packaged recording/pause/resume/save passed; FFmpeg decoded H.264 1920×1080/30 and AAC, duration 6.067 seconds. A disposable upgraded world also recorded with clean HUD capture; decoded frames were visually checked. World smoke now reports the blocking screen and times out instead of silently waiting forever. Inspection caught missing panorama state in modern clean capture; the GUI replay now preserves panorama, clear color and hidden-HUD root state. The compatibility screen now describes implemented exclusions instead of the obsolete preview limitation.

This is targeted port validation, not certification of Vulkan, every overlay, every GPU, physical microphone reconnection, or multi-hour synchronization. 26.1 and other 1.21 releases remain separate porting work.

## Stonecutter migration — 1.21.11 / 26.3

One root Gradle wrapper and shared build now configure Stonecutter 0.9.8, Loom Back Compat 0.4.2 and Loom 1.17.21. Root tasks `buildAll` and `collectJars` cover both exact targets. Java 21/25 toolchains and version-specific Fabric API dependencies are selected per target. The two installable artifacts use mod version 0.10.1 and include their Minecraft target in the filename.

Consolidated 18 duplicated UI/entrypoint/recording-manager files into shared Stonecutter-conditioned sources. Kept capture implementation, clean-capture hooks and developer harnesses in explicit `src/legacy/java` and `src/renderpearl/java` adapters. A lexical comparison verified that all 36 resulting target-specific shared files retain their pre-migration Java tokens, excluding comments and whitespace. Retired port sources/build scripts were archived and byte-verified before removal; run directories, recordings and historical build outputs were retained.

The active source target is 1.21.11. Use `gradlew.bat tasks --all` to discover Stonecutter's active-project switching/reset tasks. The exact target run commands are `:1.21.11:runClient` and `:26.3:runClient`; test instances are isolated under `run/<minecraft>/`. Do not edit generated source trees. Keep version conditions near API boundaries, and avoid expanding them into the platform-independent recording/audio/encoder code.

Validation: both unified builds and unit suites passed. Both packaged clients completed recording, pause/resume, and save; FFmpeg decoded the resulting H.264/AAC videos without errors (6.033 seconds each). Several local startup attempts exited with native Windows 0xC0000005 before recording; successful retries do not establish that this instability is fixed. The smoke task now fails if the client exits without logging a successful saved recording.

Migration backups and retired one-shot scripts live in `.migration-backups/`, outside Gradle clean output. The migration's source-parity verifier is archived there too: its baseline comparison predates the intentional 0.10.2 changes and is not a current regression test.

World regression passed on both targets: 1.21.11 completed the 16-stage HUD/menu/resize/resource-reload harness (1,247 clean frames); 26.3 completed a 1080p world recording with clean capture enabled (181 clean frames). All 68 unit tests passed per target. Stonecutter active-version switching to 26.3 and reset to 1.21.11 succeeded, and all 36 source parity checks still passed afterward. These are short functional checks, not a stability certification or OBS benchmark.

Requested expansion is explicitly **1.21.x, 26.1, 26.2, and 26.3**. Next-version gate: validate the existing capture paths, then investigate 26.2 against its actual rendering APIs and test it as its own target, followed by 26.1 and individual 1.21 releases. Do not label a JAR as supporting an entire family without testing each declared version.

## Recording pause and notification modes

Follow-up: pause/play now appears only during a full recording (including when paused), disappears immediately when stopped, and does not reserve collision-layout space while hidden. Pause and play artwork uses tight pixel bounds centered within its 20×20 button.

Added real-time recording pause/resume on F8 and a vanilla-sized pause/play icon immediately left of the existing Record control. Pausing freezes the displayed recording time, prevents new framebuffer transfers, drops microphone and Minecraft-audio samples, and removes the paused interval from video/audio timestamps. Stop remains available while paused. The recording HUD changes to `Ⅱ PAUSED`, then resumes from the same timeline position.

Added Interface → Notifications with Off, Simple, and Advanced modes. Simple is the default and only shows short key recording states in a neutral gray top-right panel; Advanced preserves the existing Minecraft system toasts. Both 1.21.11 and 26.3 compile and pass the full unit suite. A 26.3 production smoke recorded for six active seconds with a two-second mid-recording pause; the saved MP4 measured 6.033 seconds and finalized normally.

## Pause-menu icon revision
Replaced the wide recorder control with stacked 20×20 vanilla-style icon buttons: Optic eye/red record badge left of the actual Advancements widget, settings cog below beside Give Feedback. Anchors use translation keys rather than English labels; collision handling moves the stack together if occupied. Tooltips and narration describe actions and recording state. Validated compilation through `runClient` only; no new release JAR packaged. Development client left running for user testing, with smoke/auto-exit disabled.

Target: Minecraft 1.21.11, Fabric, Java 21, official Mojang mappings.

## Bootstrap
Empty workspace inspected. Dependencies pinned. Establishing a compilable client before Phase A.

## Validation policy
Each phase gets a build. Development client launch and manual capture validation are recorded separately: a successful compile is not evidence of runtime recording correctness.

## Phase A
Build passed. Development client launched, initialized Optic, created GL renderer and sound engine, and loaded title assets. Offline dev account produces the expected session-property 401 warning. Settings, client-only entrypoint, F9, pause controls and directory implemented. No simulated recording state was used before capture integration.

## Phase B
Build passed. Inspected the mapped 1.21.11 bytecode: final target presentation precedes Window.updateDisplay in Minecraft.runTick. GlTexture exposes glId; Minecraft's command encoder also supports readback callbacks but no scaling copy. Chose isolated GL 3.3 framebuffer blit + triple PBO for explicit scaling and fence ownership. Readback prototype and raw diagnostic sink implemented. Running an opt-in raw title-screen capture smoke test.

## Phase C
Build passed. Real encoder probes, bounded process IO, watchdog, fragmented MP4, remux, metadata, and trusted Windows installer implemented. Awaiting end-to-end MP4 smoke validation.

## Phase D
Build passed. Java Sound microphone device enumeration/capture, start-offset sidecar and separately named AAC track implemented. Synthetic 48 kHz input successfully muxed and decoded. No real microphone was opened for automated tests. Physical device access, hot-unplug and long-term sync still require manual testing.

Minecraft game audio is explicitly unsupported, disabled in UI, and documented in `MinecraftAudioCapture`. OpenAL loopback is not a tap on the existing playback mixer. Correct game-only output capture and two simultaneous audio tracks remain unfinished.

## Phase E and observed results

- Fixed runtime-only Mixin reserved-package error discovered on the first capture launch; helpers moved outside the mixin package.
- Fixed Windows PATH lookup capitalization and replaced a redirecting publisher URL with the exact versioned asset URL.
- Raw framebuffer smoke: 854×480, 30 FPS, 151 frames, decoded PNG visually checked for orientation/colors.
- Software libx264 MP4 smoke: 854×480, 30 FPS, approximately five seconds, successful save.
- NVENC MP4 smoke: 1920×1080, 60 FPS, 5.033 seconds verified by ffprobe; decoded H.264 output.
- Synthetic encoder integration: actual auto-detection selected h264_nvenc; AAC track decoded; truncated fragmented MP4 recovered and decoded.
- FFmpeg installer integration with empty PATH: downloaded pinned Gyan 9.0.2, verified archive checksum, extracted fixed destination, executable startup passed.
- Six JUnit tests passed: CFR duplication, queue bounds, excessive backlog cleanup, empty capture failure, encoder command consistency, persistent-mapping lease release through duplicated frames.
- Exit-during-recording smoke passed: normal stop was deferred without blocking the render thread, recording finalized, then the client exited after worker leases were released.
- Added persistent coherent PBO path after baseline metrics identified the render-thread copy. Successful 1080p60 NVENC smoke on this machine. One short sample showed whole-hook average around 0.51 ms versus 1.72 ms for fallback. These are non-controlled five-second title-screen samples, not an OBS comparison or a performance guarantee. Both runs skipped some frames and used duplicates to preserve duration.
- 2560×1440 at 120 FPS NVENC smoke saved and decoded successfully, but had substantial frame skipping/duplication (live sample: 514 dropped events, 473 duplicates, about 111 pipe frames/s). This confirms format/timing and bounded overload behavior, NOT smooth 120 FPS capture. Raw pipe bandwidth and duplicate catch-up need profiling before recommending this mode.

## Reproduce

```text
gradlew.bat build
gradlew.bat runClient
gradlew.bat runClient -Psmoke -Praw
gradlew.bat runClient -Psmoke -PsmokeResolution=1080 -PsmokeFps=60
gradlew.bat runClient -Psmoke -PsmokeResolution=1080 -PsmokeFps=60 -PexitSmoke
gradlew.bat integrationTest
gradlew.bat bootstrapTest
```

Smoke mode starts a five-second recording after title/world load and exits automatically. It changes capture settings only in memory and keeps the microphone OFF. Raw mode writes large `.rgba` files, with dimensions/FPS in its session JSON; it is a developer diagnostic, not a user-facing format. The bootstrap task deliberately empties PATH and may download about 109 MB. Integration tests use synthetic audio, never a physical mic.

## Remaining release gates

### V2 reliability preview — 2026-10-09

Implemented generation-guarded lifecycle transitions, cancellation during setup, encoder health/watchdog checks, disk reserve checks, bounded microphone setup/cleanup waits, microphone disconnect notification, stable session locks, schema-versioned atomic manifests, safe filename conflict handling, and validated finalization. Added saved-recording actions, last-recording access, optional HUD details and diagnostics. Fixed ownership cleanup when encoder failure occurs while duplicating a previous frame. Native capture architecture and the eye/cog pause controls remain in place.

Validation completed:

- Unit suite passes for lifecycle generations, cancellation, subprocess cancellation, session locks/manifests, timing statistics, queue/timestamp behavior, buffer lease cleanup, and simulated microphone denial/disconnect. Simulated microphone tests do not establish physical-device compatibility.
- Actual FFmpeg/NVENC integration passes synthetic AAC decode, truncated fragment recovery, corrupt-source retention, existing-output protection, active-session exclusion, and invalid microphone metadata fallback.
- A real forcibly terminated encoder initially exposed missing on-disk fragments for small output. Added FFmpeg packet flushing; the regression now recovers and decodes completed fragments after forced termination. This does not test whole-machine power loss.
- Development-client 1080p60 smoke passed start/record/save/exit. A separate test performed three immediate preparation cancellations followed by a fifteen-second recording and successful decoded completion. Title-screen capture is not gameplay performance validation.
- Exit-while-recording smoke completed a five-second 1080p60 recording before client shutdown.

Reproduce without packaging a release:

```text
gradlew.bat test integrationTest
gradlew.bat runClient -Psmoke -PlifecycleSmoke -PsmokeResolution=1080 -PsmokeFps=60 -PsmokeSeconds=15
gradlew.bat runClient
```

V2 remains a preview. Missing V1 game audio/dual tracks, physical device changes, multi-hour sync/recording, disk-full fault injection, world/server transitions, window/minimize stress and creator-mod compatibility remain release gates. GPU validation here covers NVIDIA only. No OBS comparison was performed. Full-decode finalization has time limits and needs a more scalable validation policy if long-session testing exceeds them. No V3 features or release artifact were added in this pass.

Minecraft game audio; dual audio tracks; physical microphone error/drift testing; full resolution/FPS gameplay matrix; minimized-window and window-resize stress; repeated-start/stop and encoder death; disk-full tests; recovery after actual process crash; Flashback/widget coexistence; shaders/Sodium; AMF/QSV real hardware; Linux/macOS discovery; production remapped-JAR launch; full OBS comparison. These are not marked complete by a compile or a title-screen smoke test.

## V3 development implementation — 2026-10-10

Current source adds Windows x64 process-only game audio, separate named Minecraft/microphone AAC tracks, bounded audio queues, live microphone gain/mute/meter, sustained clipping warning, selected-input reconnect and microphone clock correction. Rebuilt settings as Recording/Audio/Storage/Interface sidebar tabs. Added destination-path changes, completion-screen preference, bottom-left folder icon and per-recording support directories. Moved 52 existing completed-session supporting files into matching folders in the development instance; videos were not changed. Old interrupted root sessions remain supported.

Validation completed:

- All prior unit tests and new audio tests pass (23 tests at this checkpoint). New tests cover gain saturation, mute retaining duration, gap silence, resampling continuity and a simulated four-hour clock at ±500/±2000 ppm with under 20 ms modeled error.
- Windows audio integration builds the owned C# helper, captures a quiet synthetic signal from the test JVM, verifies stereo samples and no Java queue overruns. No microphone is opened by automated audio integration.
- Encoder integration now also muxes and decodes two AAC tracks and verifies video publication beside its matching support folder. Existing encoder-kill, corrupt-source, lock, output-conflict and legacy microphone checks pass.
- Development-client 1080p60 ten-second smoke saved H.264 plus stereo AAC named `Minecraft (including mod audio)`. Manifest recorded game audio available and 602 decoded/accepted video frames. This smoke used an in-process synthetic tone; it is not a spatial-audio or long-session validation.
- All four settings tabs rendered in the actual Minecraft client and screenshots were inspected. Layout uses near-black/neutral gray, a sidebar divider and no tinted dashboard panel.
- User reported their earlier physical microphone recording works. That confirms the earlier device path, not the newly added drift/reconnect code across all hardware.

Commands:

```text
gradlew.bat test integrationTest audioIntegrationTest
gradlew.bat runClient -PsettingsSmoke
gradlew.bat runClient -Psmoke -PaudioSmoke -PsmokeResolution=1080 -PsmokeFps=60 -PsmokeSeconds=10
gradlew.bat runClient
```

V3 is **not release-certified complete**. API investigation found no validated clean voice-chat/category split with the current process master; those controls are not offered. Real four-hour A/V impulse alignment, physical hot-unplug/Bluetooth tests, simultaneous unrelated-process exclusion, editor imports, disk-full faults and comparative frame-time testing remain outstanding. The model test does not substitute for those. Non-Windows game capture remains unsupported. See AUDIO.md for decisions and source links. No V4 capture features were implemented and no release JAR was packaged.

## Separate locations and simple naming — 2026-10-10

New sessions keep every temporary file, log and recovery artifact under a sibling `recordings-details/<video name>/` directory. The `recordings/` folder receives only verified MP4 output. Locations shows both paths; the Details folder button is above Recordings in the sidebar. Changing the video directory derives a matching sibling `<folder>-details` directory. Previously generated details in this development instance were moved without changing the video.

Recording settings now have just two naming controls: an optional title and a cyclic Default / V1 / Part 1 / Scene 1 selector. Default retains date/time plus a short unique suffix. Numbered styles scan existing videos and reserved detail folders, then atomically reserve the next number with a new directory. This avoids duplicate names across concurrent instances and never overwrites a completed video; cancelled attempts can leave gaps in numbering. Titles are normalized for Windows filename restrictions.

Session schema 4 stores the chosen local output directory and filename so recovery retains the original destination even if settings change later. Recovery reads both the legacy layout and the separate details location. Naming/sanitization/concurrent reservation tests and a real named MP4 finalization check were added.

## V4 clean capture — 2026-10-10

Implemented the Capture sidebar tab, persistent inclusion switches, independent recorder-indicator policy, HUD/child precedence, compatibility report, cached frame policy, main-GUI command mirroring and private GPU composition target. Vanilla HUD, chat history (also in open chat), subtitles, sidebar scoreboard, boss bars, action bar and 2D debug display can be absent from video while remaining visible to the player. No second world render, CPU resize, unbounded history or new synchronous framebuffer readback was added. Default include-all has no extra composition copy/pass. Details/naming/audio and existing pause controls remain intact.

Validation completed:

- 27 unit tests pass, including all 256 visibility combinations, independent indicator precedence and reused policy instances. The process-cancellation test exposed a test-fixture PID publication race; the fixture now publishes atomically. Actual NVENC encode/two-track mux/decode/recovery and Windows process-audio integration also pass.
- A synthetic GUI comparison showed the excluded red test layer remaining on screen and absent from decoded MP4, while the kept green layer remained in both.
- In-world harness uses a COPY named `Optic V4 Validation`, never the original `New World`. It populates actual vanilla chat, sound subtitles, scoreboard, boss bar, action bar and debug display; captures each policy and combinations; then exercises chat, pause/inventory screens and resource reload. Final 48-second run saved and fully decoded successfully. Player screenshots and decoded video were inspected, including independent REC exclusion and HUD-off/REC-on. Normal player hotbar, inventory items and excluded text remained visible.
- Those comparisons found a real GL cache desynchronization bug from mixing raw FBO restoration with Minecraft's cached binds. Replaced the interleaved clean-target bindings/scissor changes with GlStateManager calls, repeated the world sequence and verified full player HUD retention.
- All five settings tabs rendered in the actual client; Capture layout was inspected. Unsupported names/waypoints/mod overlays are explained instead of represented by working-looking toggles.
- Injected failure after 50 clean frames on the forced legacy PBO path stopped recording, returned to IDLE, retained fragments/manifest for recovery and exited Minecraft normally. It did not capture the full framebuffer as a fallback.

Useful commands:

```text
gradlew.bat test integrationTest audioIntegrationTest
gradlew.bat runClient -PsettingsSmoke
gradlew.bat runClient -PcleanWorldSmoke="Optic V4 Validation"
gradlew.bat runClient -Psmoke -PcleanSmoke -PlegacyCapture -PsmokeResize -PsmokeSeconds=6
gradlew.bat runClient -Psmoke -PcleanSmoke -PlegacyCapture -PcleanFailureAfter=50 -PsmokeSeconds=12
gradlew.bat runClient
```

World harness output is isolated in `run/v4-validation` and its sibling details directory. Player screenshots are `run/screenshots/optic-v4-player-*.png`; development evidence logs are under `build/v4-*.log`. Harness switches are opt-in, never enabled in the normal client. The world harness changes only the copied world's test state and in-memory Optic preferences.

V4's supported vanilla feature implementation is available for testing, but **V4 is not release-certified complete**. World-space names/waypoints and third-party overlay adapters remain explicitly unsupported, as allowed by the feasibility gate. No exact Flashback/Sodium/Iris/Xaero/JourneyMap combination has been run. Broad GPU tests, prolonged GUI-cache/memory tests, user-input pacing and controlled foreground performance budgets remain gates; see CLEAN_CAPTURE.md and PERFORMANCE.md. A resource reload is not a shader-pack compatibility test. Earlier V3 long-session/audio gates still apply. No V5 features or release JAR were built.

Follow-up V4 validation: forced legacy PBO capture resized from fullscreen to 960×540 and then 1280×720, saved six seconds successfully, and reported 180 clean frames with no dropped/duplicated frames in its periodic sample. The previous injected-failure recording recovered automatically on this launch; its manifest reached complete and the recovered MP4 was published. Clean p95 in this short resize test was 2.7 ms, including capture/readback work. This is still not a controlled performance comparison.

## V5 resolution pipeline — 2026-10-10

Implemented video-only Performance/Balanced/Creator/Cinematic/Custom presets, backend-specific quality, a compact Advanced video page, fit/center-crop GPU geometry, fixed session output independent of window resize, and persistent-buffer initialization fallback to the portable PBO backend. Existing settings remain Custom and keep their values. Presets preserve microphone/audio, capture exclusions, naming and folders. Requested quality participates in real encoder probing and actual FFmpeg options; it is not a decorative setting. Manifests preserve preset/quality/framing for diagnostics.

Capture now polls completed transfers on every render tick, preserves fence submission order, skips unnecessary color clears on full-cover output, and avoids clean GUI composition when the ring/queue is full. Poll timing is separate; frame intervals cover all render ticks, with unique-frame FPS and scale copy/clear counters. The 512 MiB limit now includes the resize texture and portable fallback buffers. No second world render, CPU scaling, unbounded queue or native encoder dependency was added.

Executed checks so far:

- 34 unit tests pass (zero failures/errors), including geometry/odd dimensions/portrait/upscale, preset isolation/custom validation and encoder quality options. Actual encoder/two-track mux/recovery and Windows process-audio integrations pass. These remain synthetic audio checks, not long-session hardware certification.
- Actual client rendered all five settings tabs plus Advanced. Screenshots are `run/screenshots/optic-settings-*.png`; the new controls and notes were inspected.
- Injected failure after allocating/mapping the first persistent PBO. Constructor cleaned up, the worker prepared portable memory, and the same session began on `fenced-pbo`. A 1440p60 High/Fill recording with clean capture and two window resizes finalized and decoded successfully. The stress run duplicated 31 frames out of 481 and misses the <1% target; portable copies averaged 2.069 ms per submission. Evidence: `build/v5-fallback.log` and the `Optic-2026-10-10_14-50-05-d124663c` session in the normal development video/details locations (later smoke harness output is isolated under `smoke-videos`).
- Standalone OpenGL blit/bilinear-shader experiment: three repeats per size/method, 300 measured samples each, byte-identical gradient/checkerboard output. Shader timings were slightly lower in the isolated context; retaining the simpler existing blit until an end-to-end improvement is established. Re-run with `gradlew.bat scaleBenchmark`; raw evidence under `build/scale-benchmark`.
- Added bounded raw frame tracing, a deterministic camera sweep and seeded repeated idle/record runner, plus exact percentile/1%-low reporting. First fullscreen trace was background-throttled near 10 FPS; the windowed trace removed that throttle but still lost focus. Such runs are explicitly flagged invalid, not used to claim an advantage. Details and fair OBS protocol are in `benchmarks/README.md`.

Useful development commands:

```text
gradlew.bat test integrationTest audioIntegrationTest
gradlew.bat scaleBenchmark
gradlew.bat runClient -PsettingsSmoke
gradlew.bat runClient -Psmoke -PcleanSmoke -PsmokeResize -PsmokeWide -PsmokeResolution=1440 -PsmokeFps=60 -PsmokeQuality=HIGH -PsmokeAspect=CROP -PfailPersistentSetup -PsmokeSeconds=8
powershell -File benchmarks/Run-Local.ps1 -Seconds 30
gradlew.bat runClient
```

V5 implementation is available for development testing, **not release-certified complete**. A matched OBS comparison, external CPU/GPU/VRAM/presentation traces, foreground multi-minute repeatability, AMD/Intel hardware, and the previous V3/V4 long-session/audio/mod gates remain outstanding. No zero-copy encoder path, native higher-resolution world render or unmeasured impact rating is exposed. GPU YUV conversion/native interop were investigated but not adopted without end-to-end evidence; see ARCHITECTURE.md. No V6 features or new release JAR were built.

The seeded local runner completed all six 15-second measured trials (three idle/record pairs) and every recording finalized successfully. Four traces lost focus; two did not. These short samples also lack a frozen world simulation and external utilization counters, and background filesystem activity occurred during part of the series. They are **harness/stability screening**, not a qualified benchmark result. All raw traces, validity flags and summaries remain under `run/v5-benchmark`, with per-run logs `build/v5-{idle,record}-{1,2,3}.log`. A final polling optimization subsequently removed GL buffer queries/binds from persistent polling and returns immediately when no transfers are outstanding; those screening numbers predate that last optimization.

Final capture checks after that optimization:

- Both Fit and Fill at 1440p120/High survived 960×720 → 1280×540 source changes. MP4s stayed 2560×1440/120 CFR and fully decoded (722/723 frames). Inspected PNGs at 1 and 4 seconds confirm pillarbox/letterbox Fit, centered Fill without stretching, correct orientation and preservation of the kept test overlay while excluded red content stays absent. Evidence: `build/v5-evidence/{FIT,CROP}-{1,4}.png`, `build/v5-aspect-*.log`, `run/smoke-videos` and sibling details. Source menu pacing was about 60 FPS; unique output was 51.73/49.15 FPS with 411/427 duplicates. This is resize/overload correctness evidence, **not successful unique 120 FPS capture**. Fit cleared bars on 311 transfers; Fill cleared zero.
- The complete V4 disposable-world sequence ran again on the updated pipeline: vanilla layer switches, independent indicator, chat/inventory/pause screens, source resize and resource reload. It stopped, validated and saved normally. Evidence: `build/v5-world-regression.log`, the latest `run/v4-validation` video and corresponding details. Prior V4 long-duration and third-party-mod limitations still apply.
- Session manifests now also retain initial source dimensions and actual encoder option tokens for reproducibility. Optional added fields do not change recovery schema compatibility.
- Final automated suite: **36 unit tests**, zero failures/errors; includes pre-V5 config migration and persisted preset/framing round trips. NVENC mux/decode, interrupted recording recovery, failed encoder detection and Windows process-audio integration all pass. Full log: `build/v5-final-tests.log`.
- Repeated the injected persistent-allocation failure after the final polling change at 1080p30/Fit with the two different window shapes. Portable capture saved six seconds: 181 decoded frames, zero dropped frames, one end-padding duplicate, 180 black-bar clears. Log: `build/v5-final-fallback.log`. This also exercises cleanup of a partially mapped persistent ring before portable initialization.
# V6 development implementation — 2026-10-10

Implemented opt-in encoded instant replay, F10/remappable Save Clip, 15/30/60/120/300-second history, compact Replay sidebar tab, binding-conflict feedback, warm-up/storage state, single-flight export and shared full-recording promotion. Video uses two-second keyframe segments; existing audio writes aligned PCM chunks and exports separate AAC tracks. All temporary files stay in Details. Added bounded retention/export admission, free-disk reserve, persisted clip/full recovery, safe collision handling and startup cleanup of unrequested history. Existing capture queues, clean-capture policy, presets, naming, folder separation and ordinary recording remain in place. See REPLAY.md for operational limitations.

Validation evidence:

- 41 unit tests passed: all history-duration retention policies, byte-pressure eviction, delayed PCM cleanup and unsafe recovery path rejection join the previous 36 tests. All-duration policy tests use synthetic segment metadata, not real five-minute GPU captures.
- `build/v6-final-tests.log`: unit suite, actual FFmpeg replay integration, ordinary encoder/recovery integration and Windows process-only audio integration passed. Existing forced encoder termination is detected and its completed fragments recover; this is not a hard-power-loss certification.
- Replay integration writes forty seconds of synthetic video/two audio sources, evicts expired history, rejects a concurrent save, stream-copies a sixteen-second clip, promotes a twelve-second full recording and recovers both that interrupted full recording and a four-second pending clip. Full decodes verify 360/120 recovered video frames. FFprobe confirms separate stereo game and mono microphone AAC tracks, within one AAC frame of video duration. This is synthetic sync evidence, not a physical microphone drift test.
- `build/v6-client-smoke.log`: real copied-world NVENC/persistent-PBO run saved warm-up and full-history clips while the shared full recording continued. Inspected `run/screenshots/optic-v6-replay.png` for six-tab settings layout. MP4s contain 1920×1080 H.264 and stereo Minecraft audio; 6/16-second clips have 180/480 frames, full recording 451 frames (~15.03 seconds). No second capture session was started at promotion.
- `build/v6-client-legacy.log`: repeated copied-world sequence using portable fenced PBOs; two clips and full recording saved and validated. A short game-audio tail was padded with silence and reported, preserving timeline. The development harness restores original settings; user microphone mute/device and recording preferences were preserved.
- Final post-change unit/replay and ordinary-client regression log: `build/v6-regression.log`. Normal client is launched separately without smoke flags for manual testing. No new release JAR is packaged.

Commands: `gradlew.bat test replayIntegrationTest integrationTest audioIntegrationTest`; `gradlew.bat runClient -PreplaySmoke="Optic V4 Validation"` (requires the disposable copied world); add `-PlegacyCapture` for portable readback; `gradlew.bat runClient` for normal testing.

Remaining release gates: multi-hour combined replay/full/export stress; actual disk-full and export/rename crash injection on Windows; physical mic disconnect/reconnect while clipping; AMF/QSV segment validation; mod compatibility; matched OBS and replay/full tail-frame-time comparisons. Current full-recording stop restarts history, source-setting changes require replay OFF/ON, and clip boundaries are segment-rounded. Five-minute histories can shorten under byte pressure. V6 is implemented for development testing, not release-certified complete. No V7 features are implemented.

## V7 creator markers — 2026-10-10

Implemented F7 markers with KEEP/FUNNY/IMPORTANT/B-ROLL/RETAKE/THUMBNAIL types, optional notes, opt-in context and deferred thumbnails. Interface settings opens compact marker controls; saved recordings expose a paginated timeline with event details. JSONL recovery journal, generic JSON/CSV exports and thumbnails remain in Details. Context/thumbnails default OFF. Shared monotonic source times rebase into clips/promoted recordings without extra capture work. See MARKERS.md for limits and exact behavior.

Validation: 46 unit tests passed, including torn UTF-8 journal recovery, burst bounds, escaping/redaction, duplicate times, and 30/60/120 FPS clip boundaries. Real FFmpeg replay integration verifies recovered marker rebasing and saved-frame thumbnail extraction. Logs: build/v7-tests.log and build/v7-final-tests.log. The live copied-world NVENC run (build/v7-client.log) saved warm-up/full-history clips and a promoted recording. Source marker 25.006 seconds mapped to frame 150 / 5 seconds in the full video and frame 450 / 15 seconds in the clip. Inspected settings and timeline screenshots; thumbnail files and exported context/Unicode notes exist. User config was restored after the harness. One short end-of-recording audio gap was explicitly padded/reported by the existing V6 path.

Journals retain complete lines after interruption; append is not per-event fsync. Queue limits: 128 pending / 4,096 accepted per session / 8 MiB. Text export and image failures cannot invalidate video. Thumbnail extraction uses recorded media, up to eight 320-pixel images, not synchronous framebuffer screenshots. Input and append latency histograms are available for further profiling.

Remaining gates: controlled marker acknowledgment/input cost and foreground frame-time comparisons; multi-hour combined workloads; broad mod/GPU tests; actual native editor import/version certification. Generic CSV/JSON is not advertised as Premiere/Resolve/AE native marker import. Thumbnails are deferred, not live previews. No V8 library/editor or audio WAV-export UI implemented. No release JAR packaged; normal development client is launched for manual testing.

## V8 creator workflow — 2026-10-11

Implemented worker-driven completed-session library, six-row pagination/cached reopening, profiles and a saved portable Custom profile, validated naming tokens, recording notes, stable-details rename, recoverable Trash/restore and startup move reconciliation. Library lives in Recording settings alongside Profiles / naming; original folder shortcuts remain. No capture architecture replacement, release JAR or V9 features.

Evidence: build/v8-tests.log passed unit, FFmpeg replay/marker/thumbnail and ordinary encoder/recovery integration. New unit coverage exercises profile portability, naming/Unicode/collision checks, locked/stale bundle actions, notes, rename, Trash/restore and interrupted rename. A synthetic 10,000-manifest scan produced incremental snapshots and correctly identified absent media; first scan measured 6248.7622 ms (not a cold-start latency promise). build/v8-client.log passed the live workflow/library visual smoke. Inspected run/screenshots/optic-v8-workflow.png and optic-v8-library.png. Final suite and actual template-named recording: build/v8-final-validation.log.

Scope limits and release gates: only completed managed manifests are indexed; arbitrary/externally renamed videos are not auto-adopted. One saved Custom profile, no portrait Shorts output. The 20,000-folder cap and refresh/cache model are explicit in WORKFLOW.md. Controlled cached-page/game-frame-time benchmarks, network/removable-volume moves, physical power-loss and external-player contention need further testing. Existing V3–V7 hardware/audio/editor gates remain. User configuration retained; normal development client is left open separately.

Final V8 validation: 51 unit tests, zero failures/errors. The template-enabled normal recording smoke saved and decoded six seconds as V8-2026-10-11-00-55-47-Minecraft.mp4; clean-capture remained enabled. User recording/microphone settings were unchanged. Normal client log: build/v8-dev-client.log.

## Direct menu shortcuts

Added a third vanilla-sized Optic library icon below Record and Settings on the pause screen, aligned to the actual Options row. The title screen now has Settings and Library icons stacked to the left of Singleplayer/Multiplayer. Existing translation-key anchors and widget collision relocation are shared across both menus; no world entry is needed to browse videos. Tooltips/narration identify each button, and screens return to their originating menu. Existing library access in Recording settings remains available. Validation: compileJava plus menu-shortcuts-smoke.log and title/pause screenshots; the smoke scenario clicks both title shortcuts and the pause Library shortcut.

Shortcut validation note: the first developer harness successfully exercised title Library/Settings, then attempted to create a vanilla pause menu without a player and hit a vanilla null-player exception. The harness was corrected to validate pause controls in the disposable copied world instead; normal menu code does not create a pause menu outside gameplay.

Final pause shortcut smoke passed in the copied world (build/menu-pause-smoke.log). Inspected screenshots confirm Record beside Advancements, Settings beside Give Feedback, Library beside Options, and Settings/Library beside the title menu. Normal development client reopened with smoke flags disabled.

## V9 advanced capture — 2026-10-11

Implemented optional runtime-probed HEVC/AV1 hardware encoders, explicit SDR sRGB/BT.709 limited-range color, and opt-in stopping after sustained duplicated output. Existing H.264/default color, bounded capture pipeline, separate audio, recovery, markers and library remain. Advanced choices are behind Recording → Advanced → Advanced formats / color / overload. Probes exercise actual RGB encode, fragmented MP4 mux and full decode; failed advanced selections fall back visibly to H.264. Replay deliberately retains H.264. See ADVANCED_CAPTURE.md for exact scope and deferred experiments.

Validation: 54 unit tests passed. build/v9-final-tests.log passed unit, advanced codec/audio finalization and Windows process-only audio integrations. Advanced evidence: build/advanced-integration/run-35862545969400. libx264, HEVC NVENC, HEVC QSV and AV1 NVENC each passed 60-frame full decode, separate synthetic audio mux, six RGB chart patches and explicit post-mux color-tag assertions. AMF and AV1 QSV did not pass this machine's probes. build/v9-integration.log also passed previous replay and ordinary encoder/recovery integrations. Tests exposed missing transfer/primaries tags in the initial color implementation; explicit frame setparams fixed that, and final assertions verify preservation through finalization.

Live HEVC and AV1 1080p30 recording checks succeeded. The final post-color-fix AV1 run (build/v9-final-client.log) saved six seconds, 180 clean-capture frames using persistent PBOs. build/v9-ui-client.log exercised the advanced screen; inspected run/screenshots/optic-v8-workflow.png (legacy harness filename). These are short smoke checks, not performance comparisons. Current test config restores the user's existing recording/audio preferences. Normal client reopened without smoke flags using build/v9-dev-client.log; no release JAR packaged.

Remaining release gates: multi-hour sync/throughput, actual editor import, AMF and broader GPU/driver support, shader color contracts, overload-stop end-to-end stress and matched OBS benchmarks. Depth/entity/HUD auxiliary outputs, HDR/10-bit, lossless/intermediate encoding, VFR and automatic quality adaptation were investigated but not implemented; no working-looking controls are provided for them. No V10 implementation begun.

## V10 consolidation development pass — 2026-10-11

Added schema-4 settings persistence with bounded reads, unknown-field preservation, backups before migration/corrupt-JSON replacement, future-schema refusal, atomic replacement and save-failure feedback. Session manifest writes share these protections. Added an explicit paginated/narrated support preview and exact-preview export to optic-support, with a structured field allowlist and numeric-only telemetry; no raw logs, identifying paths, device names, world/server context or uploads. GPU/driver and active-session dimensions/backend are available; FFmpeg version is inspected off-thread during preparation and never blocks recording solely for a diagnostic failure. Optional-mod presence uses a shared registry including Simple Voice Chat, without claiming validated adapters.

Core navigation/menu/completion/report actions now use English/French language keys; long button labels retain full tooltips, sidebar clipping and visible keyboard focus. Many advanced messages remain English: this is a localization foundation, not full localization certification. Added tested offline benchmark CSV summaries with exact percentiles, 1% lows, 50ms stutter counts, invalid-trace rejection and four-state eligible-run reporting. The existing workload disables replay/advanced color/overload in its temporary benchmark config. See benchmarks/V10.md, PRODUCTION.md and THIRD_PARTY.md for the measured-release procedure, support matrix and binary/distribution audit.

Evidence:

- build/v10-regression.log: unit, ordinary encoder/recovery, replay/markers, advanced HEVC/AV1/color/separate-track finalization and real Windows process-audio integrations passed.
- build/v10-final-tests.log: unit suite, healthy pinned FFmpeg cache reuse, corrupted-receipt repair from a hash-verified publisher archive, and benchmark summary execution passed. Existing short traces yield zero eligible comparative runs, correctly; no OBS result is claimed.
- build/v10-unit-final.log: 66 unit tests passed, including migration/unknown-field/future-schema/malformed-file/backup-failure protection, support export/privacy bounds, exact benchmark calculations, manifest preservation and translation placeholder parity.
- build/v10-support-client.log: first real support-preview launch passed. Visual inspection found the strict sanitizer hid the GPU model's OpenGL suffix; the hardware adapter now strips the API suffix before sanitization. build/v10-support-final.log exercises both preview pages after that fix.
- build/v10-lifecycle-client.log: three immediate preparation cancellations returned to idle, followed by a six-second completed recording. This run was background-throttled to roughly 10 unique game frames/sec with repeated output frames; it validates lifecycle/timeline behavior, NOT sustainable 30 FPS capture or recording overhead.

No release JAR packaged. Version label is 0.10.0-dev.1, explicitly development status. Production release gates remain: clean remapped-JAR install, eight-hour physical A/V soak, disk/device/GPU failure stress, editor/mod/hardware matrices, complete localization/accessibility review, distribution obligations and quality-matched four-state OBS benchmarks. V10 must not be called production-certified based on these development checks.

Final V10 checks: build/v10-support-final.log successfully paged through the support preview; both screenshots were inspected and GPU model now appears correctly. build/v10-exit-client.log requested Minecraft shutdown while recording, saved the six-second video, and exited normally. Focus throttling also affected this smoke, so its frame statistics are not a throughput benchmark. User settings remain intact (migration happens on the next settings save). Old build/libs JAR timestamps remain October 9; none was rebuilt. Normal 0.10.0-dev.1 client launched without smoke flags in build/v10-dev-client.log for manual testing.

## Dual-version packaging — 2026-10-11

The previous no-packaging notes above describe the earlier V10 milestone; packaging is now explicitly requested. The 1.21.11 remapped development JAR built successfully and passed an isolated packaged-client six-second recording smoke (build/package-1.21.11-smoke-final.log). It remains a development build with the production gates above outstanding.

An isolated versions/26.3 build uses Java 25, Loom 1.17.21, Gradle 9.6, Loader 0.19.5 and Fabric API 0.162.0+26.3. Shared recorder code is overlaid with version-specific UI/capture adapters. RenderPearl replaces direct GL calls with a triple staging ring and completion callbacks. A short development recording saved H.264 1920x1080/30 plus AAC. The first inspected output was upside down; the corrected staging copy follows Minecraft screenshot orientation and the subsequent extracted frame was visually verified upright (build/port26-corrected.png, build/port26-orientation.log).

This port is explicitly a preview: selective overlay exclusions are disabled and explained in the UI. Only Windows/NVIDIA/OpenGL short recording has been exercised; full gameplay/UI workflows, Vulkan, long physical microphone synchronization and cross-mod compatibility remain validation work. No performance equivalence is claimed. Both manifests use exact Minecraft versions; neighboring releases are not supported by these artifacts. See dist/README.md and versions/26.3/README.md.

Packaging validation follow-up: 66 Java 25 unit tests passed. The first 26.3 packaged-client attempt exited during resource loading with Windows status 0xC0000005; Windows identified jvm.dll as the faulting module, without a Java crash report. A repeat launched successfully but stopped at first-run onboarding because the copied 1.21.11 options were not suitable for 26.3. The final smoke uses the already initialized 26.3 test options. This isolated native startup failure is unresolved and is another reason the artifact is a preview; a successful retry is not proof of its cause or resolution.

Final packaged 26.3 recording evidence: build/port26-package-bounded.log reports OPTIC_26_SMOKE_FINISHED saved=true, and ffprobe confirms H.264 1920x1080/30 plus AAC in the saved six-second MP4. However, the process then exited with 0xC0000005 during shutdown, so the productionSmoke task FAILED. Another startup attempt failed with 0xC0000409. The bounded JVM flags are test configuration only, not a proven fix. This remains an explicit preview limitation in both distribution and port READMEs. The packaged 1.21.11 smoke exited successfully.

Final standalone 26.3 `build` completed successfully (build/port26-build-final.log); the 66-test suite passed. Distribution JARs and SHA256SUMS.txt are in dist/. The normal 1.21.11 development client was reopened without smoke flags for manual testing (build/packaging-manual-client.log). Native 26.3 lifecycle failures remain unresolved as documented above.
## Minecraft 26.3 release port

The isolated release port lives in `versions/26.3-release/`. It restores selective GUI/HUD capture on Minecraft 26.3 using RenderPearl GPU target copies and a filtered GUI command pass, without a second world render. The packaged recording smoke, in-world HUD-hidden smoke, shared tests, video decode, and clean shutdown pass. Windows packaged-client lifecycle runs were repeated with Microsoft OpenJDK 25 because Temurin 25.0.4.1 intermittently crashed in `jvm.dll` before Optic initialized. See the port README for the validation boundary.

0.10.2 packaging: all 204 unit tests passed; the final collectJars run reused that validation with tests excluded and succeeded. Artifacts and SHA-256 sums are in dist/. Testing was stopped at user request; no client was left open. See STARTUP_STABILITY.md for exact repeat-run results.
