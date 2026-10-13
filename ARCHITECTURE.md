# Architecture

## Version boundary

Stonecutter 0.9.8 owns the 1.21.11, 26.2 and 26.3 Gradle subprojects. One shared `build.gradle` uses Loom Back Compat 0.4.2 and Loom 1.17.21 to select mapped/remapped older builds versus unobfuscated newer builds, with per-target Java toolchains, dependencies and metadata. Shared Java sources contain preprocessing comments for small UI/entrypoint/manager differences. These are build-time choices; no Stonecutter runtime dependency is included in the mod.

Legacy adapters live in `src/legacy/java`. Common modern hooks live in `src/renderpearl/java`, selected for both 26.2 and 26.3. The modern `GlFrameCapture`, `CleanCapture`, and `MinecraftCaptureMixin` are guarded templates in `src/main/java/dev/optic/compat`: Stonecutter selects Blaze3D types/copy operations for 26.2 and RenderPearl types for 26.3. The staging ring, bounded queues, GPU resize, and clean GUI composition stay shared. Modern templates are inactive for legacy compilation, and the legacy sources archive excludes those empty templates. No duplicated full mod tree or runtime reflection is required.

Microphone enumeration is requested only when opening Audio settings, on the control worker, with overlapping requests coalesced and a five-second refresh limit. Initialization does not call Java Sound: native audio-driver threads must not race LWJGL loading. Recording still opens the configured microphone during worker-side session preparation. Discovery failures retain the previous device list.

Only the client entrypoint, `ui`, and `compat` depend on Minecraft APIs. RecordingSession, buffer contracts, audio, encoder selection, and subprocess management are separated from UI. Dependencies use official Mojang mappings. Mixin classes live in `compat.mixin`, never alongside ordinary helper classes: Mixin reserves its entire package.

The verified 1.21.11 `Minecraft.runTick` flow renders the final target, presents it, then calls `Window.updateDisplay`. The injection immediately before updateDisplay reads the main target's `GlTexture.glId()`. This includes world, GUI, pause menu, and HUD. A non-OpenGL backend refuses recording. Future ports should revisit this hook first.

Pause is a timeline operation inside `RecordingSession`. While paused, render hooks keep polling already-issued GPU fences but submit no new frame transfers. Audio capture continues owning its devices, drops incoming samples, and subtracts the same monotonic pause interval from future timestamps. Encoding and disk work therefore never move onto the render thread, and resumed footage joins the preceding frame/audio timestamp without a frozen or silent pause segment.

Minecraft's `CommandEncoder.copyTextureToBuffer` and Tracy capture were inspected. They offer asynchronous completion but not a scaled texture-to-texture copy. The V1 GL adapter provides explicit blitting, aspect fitting, and ownership of three readback buffers without attaching another GL context or changing Minecraft's render device.

## Video flow

```text
Minecraft final color texture
  -> private read framebuffer (attachment only)
  -> GPU blit: aspect fit or center crop, resize and Y flip into fixed RGBA8 texture
  -> glReadPixels to one of three GL_PIXEL_PACK_BUFFER objects
  -> glFenceSync
  -> next render tick (also between capture deadlines): glClientWaitSync(flags=0, timeout=0)
  -> signaled only: publish persistent mapping lease (ARB_buffer_storage)
     OR map, copy once to pooled direct buffer, unmap (GL 3.3 fallback)
  -> non-blocking offer to three-frame queue
  -> encoding worker owns frame until all needed duplicates are written
  -> reusable 256 KiB heap transport chunk -> FFmpeg stdin
  -> H.264 encoder -> fragmented MP4
  -> after stop: stream-copy remux to fast-start MP4
```

This is asynchronous GPU readback, not zero-copy encoding. The preferred path persistently maps each PBO once with READ, PERSISTENT and COHERENT flags. A signaled fence precedes publication to the worker. The queue provides Java publication ordering and a volatile lease flag prevents reuse until the consumer releases the frame. The worker reads the mapped data into the reused pipe chunk. The GL 3.3 fallback instead performs a completed-readback copy on the render thread. Java process pipes require a transport copy on the encoding thread. FFmpeg also converts RGBA to YUV420 and, for hardware encoding, uploads to the encoder device.

The adapter restores read/draw framebuffer bindings, pixel-pack buffer binding, pack alignment/row/skip settings, texture binding during setup, and scissor state. It does not change Minecraft's texture object, viewport, shader program, or render target size. Every GL object is owned and deleted on the context thread. Unsupported backends and capture failures produce a message and stop the session.

## V5 resolution and capability decisions

`ScaleLayout` is Minecraft-independent integer geometry for fit/crop. The GL adapter caches it and only replaces it on source resize; output dimensions and framing are snapshotted per session. Native output uses the initial even window size, never a moving codec size. Fit clears black bars; full coverage/crop skips the clear. The RGBA8 scale target remains necessary for orientation and fixed dimensions even if V4's clean target happens to match output dimensions. The clean target retains window-size GUI coordinates. Unifying those targets without redesigning GUI projection would risk incorrect UI scale and exclusions for a marginal copy reduction.

Persistent capture initialization can fail despite a reported extension. After constructor cleanup, the preparation worker allocates the six-frame portable pool; the render thread retries GL 3.3 setup before the session clock/encoding thread starts. No live mapped resources change backend. Cancellation still owns the same generation. The manifest records the chosen backend, preset, quality and aspect alongside the existing stream dimensions. Old schema-4 manifests missing the optional fields remain recoverable.

Poll completed transfers on every render tick rather than only a recording deadline. Fence checks use zero timeout and stop at the oldest unsignalled transfer. Clean composition checks ring/queue availability before mirroring or copying, so backpressure avoids extra GUI work. The next accepted timestamp accounts for skipped indices; the worker's existing CFR duplicate policy and five-second stall limit remain unchanged. Poll and submission timers are separate because the portable copy now occurs in the earlier hook. No GL work moves to a thread without a context.

Presets translate to existing resolution/FPS plus backend-specific quality options. Auto runs a small real encoder probe with those options; missing hardware falls back with a notice. Software is not advertised as low impact. Presets do not alter audio sources, clean-capture preferences, paths or naming. Their quantizer numbers are convenience mappings, not equivalent perceptual quality promises across encoders. Settings changed while recording configure the next session.

The standalone scaling experiment retained the existing blit: a bilinear shader gave identical test pixels on this GPU and only small isolated timing differences. A more elaborate filter would need its own visual and end-to-end comparison. Persistent PBOs already eliminate the extra render-thread CPU copy but do not eliminate pipe transport, FFmpeg conversion or encoder upload.

Native encoder interop remains a separate potential `VideoEncoder`/`FrameCapture` backend, not an advertised current capability. NVIDIA's SDK supports OpenGL encoder devices only on Linux; Windows native sharing would require additional CUDA or DirectX interop and owned resource/synchronization lifetimes. GPU planar YUV conversion could reduce readback bandwidth but would add plane packing, color/range validation, format capability negotiation and fallback paths. There is no measured end-to-end benefit here to justify replacing the proven RGBA/FFmpeg path yet. These are explicit engineering decisions, not fake zero-copy stubs. Reference: [NVIDIA NVENC device initialization](https://docs.nvidia.com/video-technologies/video-codec-sdk/13.1/nvenc-video-encoder-api-prog-guide/index.html#initializing-encode-device).

## Interfaces and ownership

| Boundary | Responsibility |
| --- | --- |
| FrameCapture | Poll completed transfers and submit capture; context thread only |
| FrameBufferPool | Fixed storage; acquire/release ownership exactly once |
| VideoEncoder | Consume RGBA on encoding worker; no Minecraft dependency |
| AudioCapture | Device thread and PCM lifetime |
| AudioEncoder | Encode/mux independent audio track after capture |
| RecordingSession | Monotonic frame clock, bounded queue, CFR duplicates, metrics |
| RecordingManager | Client-thread state machine and asynchronous preparation/finalization |
| FFmpegManager | Local discovery, trusted installation, verification |
| EncoderDetector | Actual short encode probes with output size and FPS |

The fallback's six reusable direct frames cover three queued frames, a retained previous frame for duplication, the producer, and spare capacity. Persistent mode allocates no direct frame pool: the three mappings themselves are leased. Retaining a previous frame for duplication occupies one slot. A frame must not be reused while owned by the queue or worker. Stop disables capture and signals the worker. GL deletion and unmapping happen only after the worker finishes, on the context thread. Normal application exit is deferred while the render loop continues servicing cleanup; no render-thread join is used. On abnormal close bypassing stop, native context destruction is skipped until process teardown to avoid invalidating worker pointers. Queue overload drops frames, never waits for the consumer.

## State and time

`IDLE -> PREPARING -> RECORDING -> STOPPING -> IDLE`. Preparation performs discovery/download, probing, metadata creation and process startup off-thread. Only after GL initialization succeeds does the recording clock start. Rapid repeated commands during PREPARING or STOPPING do nothing.

Frame indices derive from monotonic nanoseconds at the requested FPS. Only one frame is submitted per due index. The worker repeats the previous frame for missing indices; encoded playback does not speed up when frames drop. The first available frame fills any initial gap. Final short gaps are filled through the stop index. More than five seconds of missing/catch-up frames fails safely; no unlimited catch-up queue is created.

FFmpeg stdin can block only the encoding worker. A watchdog kills a process without pipe-write progress for 20 seconds. Shutdown waits have deadlines off-thread. GPU fences are never waited on with a positive timeout.

## Audio

Game and microphone audio use independent capture workers, bounded timestamped PCM queues and named AAC tracks. Windows x64 build 20348+ uses process-scoped WASAPI capture; other platforms retain video/microphone support. Microphone gain, mute, clipping feedback, drift correction and bounded reconnect are implemented. Detailed source semantics, clock alignment, queue sizes and validation limits are in AUDIO.md.

## Reliability and privacy

All process arguments are separate ProcessBuilder arguments, never shell concatenation. Logs go to session files. Filenames are generated locally with timestamps plus random suffixes. A session marker survives interruption. Files are muxed to a separate finalizing output and renamed after successful processing. Failed/corrupt partials stay available for manual repair. Recording shutdown during application exit may leave a partial; next-launch recovery is the fallback.

Downloaded executable archives are fixed publisher assets verified against an embedded SHA-256. Extraction never uses archive paths as destination paths. There is no telemetry or network streaming. Network access is used only for missing FFmpeg installation.

## Porting checklist

### V2 lifecycle and recovery changes

`SessionLifecycle` owns IDLE/PREPARING/RECORDING/STOPPING transitions. Each run has a generation identity; late callbacks cannot activate or finish another run. Preparation cancellation travels through process probes and downloads. State returns to idle only after resource cleanup. Capture stops submitting before native buffers are freed, and worker-owned leases survive until the consumer exits.

Session schema 2 manifests are atomically replaced and coexist with legacy sidecars. Stable OS lock files protect active sessions from another instance's recovery scan. Unknown future schemas and existing final filenames are preserved. Recovery runs on a separate worker. FFmpeg flushes fragmented output to the OS to improve process-crash recovery (this is not a power-loss durability guarantee). Finalization uses a unique candidate, decodes it before publication, and retains sources on failure. Invalid microphone metadata permits video-only completion while retaining the audio source.

Encoder progress is parsed separately from pipe input counts. A stalled write has a ten-second watchdog; no captured frames for ten seconds or less than 64 MiB free space stops the consumer. Start requires 256 MiB free. These reserves are heuristics, not disk-full guarantees. Application exit requests normal stop and cancels remaining work after twenty seconds; driver/process cleanup can still exceed this deadline.

Microphone open/close work uses bounded dedicated executors, with three/two-second caller waits. A failed device warns once while video continues. Reconnect attempts target the same identifiable device; an unidentified default device is not silently replaced. Live device selection applies to the next recording. A stuck native audio driver may retain resources until it returns or Minecraft exits.

Saved recordings expose duration, size, FPS and open actions. Diagnostics refresh once per second only when open. The normal HUD remains compact; extra recording details are opt-in.

Confirm runTick timing, final texture ownership, GL state assumptions, Fabric screen/key/HUD events, and input signatures. Keep queueing, clocks, FFmpeg commands, metadata and audio contracts stable. Validate with a remapped production JAR as well as a development launch.

## References

- [Fabric's 1.21.11 development notes](https://fabricmc.net/2025/12/05/12111.html)
- [Khronos synchronization reference](https://wikis.khronos.org/opengl/GLAPI/glClientWaitSync)
- [ARB_buffer_storage specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_buffer_storage.txt)
- [FFmpeg container formats and fragmentation](https://ffmpeg.org/ffmpeg-formats.html)
- Version-sensitive method names and hook order were checked against the locally resolved official-mapped 1.21.11 JAR, not assumed from older tutorials.

## V3 audio and storage update

Game capture uses the platform-neutral `MinecraftAudioCapture` boundary backed by `WindowsProcessAudio` on Windows x64 build 20348+. It captures Minecraft's process tree, including mod audio, through an owned C# WASAPI helper built from source. It never substitutes whole-desktop loopback. The shared QPC/session clock, microphone correction, bounded packet ownership and separate-track mux are detailed in AUDIO.md.

Audio source threads feed reusable timestamped blocks into dedicated file writers; gain/mute/meter processing happens there. Microphone clock correction uses continuous linear interpolation on the input worker. Disconnects preserve timeline gaps; retries target the same device. Stop requests signal sources before video draining finishes. UI reads only decimated fields and edits live controls. Advanced category/voice isolation remains deferred.

Schema 4 records successful audio roles plus the local destination directory/filename. New sessions start in `recordings-details/<stem>/<stem>.partial.mp4`; logs, PCM, manifests and metrics remain there. `RecordingStorage.target` publishes the verified video at `recordings/<stem>.mp4`. Legacy flat/nested partials remain supported. Recovery scans both video and details locations at depth two, refuses overwrite and retains source data on failure. Global FFmpeg cache/probe artifacts live in `config/optic-tools`. Naming reservations use atomic details-directory creation off-thread; numbered styles advance beyond existing videos and reserved names.

Storage changes are validated asynchronously with a temporary write probe and apply only while idle. They do not relocate existing media. Completion-screen preference affects only automatic opening from a menu. Sidebar layout is separate from capture and audio workers.



## V4 clean GUI composition

`CapturePolicy` defines recording visibility without Minecraft dependencies; the version-sensitive `CleanCapture` coordinator and `CaptureLayerAdapter` scopes live in compat. At a render-frame boundary, choose a cached immutable policy. Bind the main GameRenderer GUI stream explicitly. Mirror permitted GUI command references while preserving all original player commands. Wrap vanilla HUD/history/caption/debug submission methods with nestable scopes; indicator scope is independent of the HUD parent.

At the GUI composition boundary, copy the pre-GUI world color to a reusable private GPU target, prepare/render only the permitted GUI commands there, and feed it to the existing asynchronous PBO pipeline. Restore the normal target and command stream in a finally block, then render the full player GUI. UI extraction and world rendering each happen once. GUI preparation/draw happens twice on due clean-capture frames. This can duplicate item-atlas/PIP preparation, but never the level render. Default include-all retains the original final-frame hook and does not allocate a clean target.

Minecraft caches framebuffer/scissor state. Clean target setup MUST use GlStateManager binding/scissor methods when interleaving Minecraft command encoders; mixing raw binds with cached engine calls caused a player-HUD regression during testing and was corrected. The existing isolated raw PBO block restores its incoming GL state without interleaving engine commands.

Target storage follows window dimensions, bounded to 256 MiB, and releases on stop/default mode. Capture still resizes/flips on GPU into fixed output dimensions. A failed clean path stops recording explicitly, preserving recovery state, instead of falling back to an unfiltered frame. Commands and overrides never cross render frames. See CLEAN_CAPTURE.md for unsupported world-space content, mod adapter requirements and complete control semantics.
# V6 encoded replay extension

Replay reuses the V5 capture path unchanged: final/clean framebuffer → GPU scaling → asynchronous PBO ring → bounded frame queue → one FFmpeg video encoder. Optional replay changes only the encoder's mux destination to closed two-second Matroska segments. A background `ReplayHistory` worker owns bounded recent metadata, eviction and export pins. Normal recording while armed promotes retention on this same stream.

`PcmStorage` lets the existing audio writers target either ordinary PCM files or sample-aligned `SegmentedPcm` chunks. `ReplayExport` stream-copies video and assembles rebased audio into the existing finalization/validation pipeline. It adds no render-thread file work. `ReplayRecovery` consumes persisted clip plans and full-recording markers before normal recovery scans. UI remains a state consumer; renderer classes do not know about segments. See REPLAY.md for ownership, disk reserves, transitions and explicit recovery limits.

## V7 metadata path

F7 snapshots an immutable bounded Marker (monotonic session time, type/note, optional Minecraft context). RecordingManager offers it to MarkerJournal's bounded writer queue. Journal writes and confirmations are independent of video success. MarkerExport reads complete lines, filters/rebases them against the output media range, and atomically publishes generic JSON/CSV. Replay outputs retain filtered source journals and stable IDs. Thumbnail generation decodes the saved media on a separate bounded worker; it adds no framebuffer work and follows recorded visibility. Minecraft context extraction is confined to compat/MarkerContext. See MARKERS.md for schema, ownership and recovery limits.

## V8 library and profiles

workflow/CreatorProfile applies an allowlist of existing recording fields; device identities, paths and privacy choices stay local. NamingTemplate resolves a fixed token set from a game-thread label snapshot and worker-side time/naming reservation. Existing RecordingStorage collision rules remain for non-template modes.

RecordingLibrary reads current complete manifests without probing video. A single WorkflowJobs worker with eight queued operations serves paged UI and metadata operations; scans pause/stop during capture. One in-memory directory cache bounds repeated UI work, and scans cap at 20,000 folders. Rename keeps the stable session directory, updates output references and refreshes marker exports. Trash is recoverable inside Details and has explicit restore. Session locks, marker-export serialization and persisted move intents isolate bundle changes from recording/recovery. See WORKFLOW.md for compatibility and failure limits.

## V9 format and color boundaries

AdvancedEncoders probes actual RGBA conversion → selected hardware encoder → fragmented MP4 → full decode on a background worker. The selected capability is rechecked for the actual recording size/FPS at startup. EncoderDetector supplies codec options; FFmpegVideoEncoder retains the same pipe/worker contract. Existing recovery remuxes video and separate AAC tracks without replacing the capture architecture. Advanced failures visibly select the existing H.264 fallback; replay remains H.264 pending segment validation.

ColorMode is a persisted optional contract. LEGACY preserves prior behavior. SDR_SRGB explicitly sets same-size matrix/range conversion and frame/container metadata for 8-bit limited BT.709 YUV with sRGB transfer. It assumes SDR source bytes, with no HDR tone mapping. SessionManifest records requested and effective encoder, color contract, timing policy, optional overload policy and stop reason. Profiles carry these optional fields without importing machine paths.

OverloadGuard runs on the encoding worker and checks repeated-frame ratio across three consecutive sampled windows of at least five seconds. It requests ordinary stop/finalization, never changes quality silently. The manager observes the request on its existing render tick; no probe, file work or color conversion is introduced there. See ADVANCED_CAPTURE.md for auxiliary-layer investigation and explicit unsupported paths.

## V10 consolidation boundaries

SettingsStore performs bounded JSON reads, extension-preserving merge, pre-migration backups and atomic replacement on existing IO/control workers. Future schemas remain read-only. SessionManifest reuses this persistence boundary for updates; no media file is migrated. The capture pipeline and buffer ownership are unchanged.

CompatibilityStatus is a small Minecraft/Fabric/GL-facing snapshot adapter. SupportReport is independent of Minecraft and accepts only named non-sensitive fields plus allowlisted numeric metrics. SupportReportScreen captures once on entry, paginates/narrates that fixed text and exports exactly it on WorkflowJobs. No support collection runs per rendered frame and no raw log is ingested. FFmpeg version inspection is bounded/off-thread and failure does not disable recording. BenchmarkSummary runs offline only. Optional-mod discovery is presence information, not an adapter or certification.
