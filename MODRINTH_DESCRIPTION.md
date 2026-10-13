# Optic

> **Record Minecraft like recording was built into Minecraft.**

Optic is a performance-first, client-side Fabric recording mod made for Minecraft creators. Press **F9**, play, press it again, and get a ready-to-edit video—with Minecraft audio, optional microphone audio, instant replay, creator markers, clean-capture controls, and an in-game recording library.

There are no scenes to build and no recording software to arrange around your game. Optic is designed around one simple workflow:

**Install → Record → Play → Stop → Video ready**

---

## Why Optic?

Optic records Minecraft from inside Minecraft, giving it access to the game’s frame timing, render pipeline, audio, HUD, and session information. That makes features possible that a normal desktop recorder cannot provide as cleanly—such as hiding selected Minecraft interface elements from the video while leaving them visible to the player, attaching in-game context to editing markers, and capturing Minecraft audio without recording every other application on the computer.

Performance is the core goal. Optic uses asynchronous GPU readback, a small fixed buffer ring, bounded queues, reused memory, GPU scaling, and background encoding so recording work does not intentionally wait on the render thread. The long-term target is equal or lower Minecraft frame-time impact than an equivalent OBS recording on supported hardware. That target is being benchmarked honestly; Optic does **not** currently claim to outperform OBS.

## Simple recording

- Start or stop recording with **F9**.
- Use the compact Optic eye button beside the pause menu’s Advancements button.
- Open Optic Settings or the Recording Library from the pause menu or title screen.
- See an optional, unobtrusive `● REC 04:21` indicator while recording.
- Choose whether the recording-complete screen appears after saving.
- Open the finished video or its folder directly from Minecraft.

Optic is entirely client-side. Servers do not need to install anything, and using Optic does not change the world or server.

## Video that fits your setup

Choose from:

- **1920×1080**
- **2560×1440**
- **Native window resolution**
- **30, 60, or 120 FPS**
- **Fit** to preserve the full image with bars when necessary
- **Fill** to crop the center and completely fill the output

Recording resolution is independent of window size. Scaling and center-cropping happen on the GPU, not through a CPU image-resize step. A smaller game window can be recorded into a larger video, although upscaling cannot create additional world detail.

Built-in starting profiles include **Performance**, **Balanced**, **Creator**, **Cinematic**, and **Custom**. Profiles change video settings while leaving personal choices—such as microphone, file location, and clean-capture preferences—alone.

## Automatic encoder selection

Optic tests available encoders instead of assuming that an encoder works because a particular GPU brand is present. Auto mode can select:

- **NVIDIA NVENC**
- **AMD AMF**
- **Intel Quick Sync Video**
- **Software encoding fallback**

H.264 is the safe default. Advanced settings can expose hardware-tested HEVC or AV1 when the installed FFmpeg build and hardware actually support them. Unsupported choices fail gracefully and fall back instead of intentionally crashing Minecraft.

## Minecraft audio and microphone tracks

On supported Windows systems, Optic captures audio produced by the Minecraft process through process-scoped audio capture. Music or calls from unrelated desktop applications are left out.

An optional microphone source can be recorded at the same time. When both sources are available, Optic writes named **Minecraft** and **Microphone** AAC tracks into the MP4 so they can be adjusted independently in a compatible editor.

Microphone tools include:

- Input-device selection
- Live gain and mute
- Peak-level feedback
- Clipping warnings
- Bounded reconnect attempts if the selected device disconnects

Minecraft’s final processed sound is captured, including volume settings, spatialization, and audio rendered by mods. A dedicated voice-chat track and individual sound categories are not currently provided.

## Clean Capture

Keep useful interface elements on your screen while leaving selected elements out of the recording. Depending on the active capture mode, Optic can independently control recording visibility for:

- Minecraft HUD
- Chat history
- Subtitles
- Scoreboard
- Boss bars
- Action bar
- 2D debug display
- Optic recording indicator

Clean Capture uses an additional GPU color copy and interface pass on captured frames, without rendering the Minecraft world a second time. Arbitrary overlays from other mods are not automatically removable, and menus, chat input, toasts, name tags, waypoints, and world-space overlays may remain visible.

## Instant Replay

Enable Instant Replay and press **F10** after something happens to save the recent moment.

Available history lengths:

- 15 seconds
- 30 seconds
- 60 seconds
- 2 minutes
- 5 minutes

Replay keeps a bounded rolling history of already encoded segments. Saving a clip while a full recording is running reuses the same capture and encoding stream instead of starting another expensive render-capture pipeline.

Instant Replay is optional and off by default because continuously maintaining replay history has a real recording cost.

## Creator markers

Press **F7** during a recording—or while Instant Replay is armed—to bookmark a moment for editing later.

Markers can store:

- Recording timestamp
- Marker type such as `KEEP`, `FUNNY`, `IMPORTANT`, `B-ROLL`, `RETAKE`, or `THUMBNAIL`
- Optional note
- Optional Minecraft context such as coordinates and dimension
- Optional deferred thumbnail
- The recording or replay session associated with the marker

After recording, view the marker timeline in Minecraft or export generic **CSV** and **JSON** data for another workflow. Context and thumbnails are optional and default to off.

## Recording Library

Optic includes a restrained in-game library for capture management—not a video editor.

From the library, you can:

- Open a recording
- Rename it
- Add lightweight notes
- View its markers
- Open its folder
- Move it to a recoverable Trash area
- Restore a trashed recording

The library is accessible from both the title screen and pause menu, so you do not need to load into a world to find your videos.

## Clean folders and useful file names

Finished videos stay by themselves in the **Recordings** folder. Logs, session metadata, metrics, recovery data, audio sources, and other technical files go into a separate **Details** folder.

```text
.minecraft/
├── recordings/
│   └── My Video Part 3.mp4
└── recordings-details/
    └── My Video Part 3/
        └── session metadata, metrics, logs, and recovery files
```

Choose the default date-and-time name or set a reusable title with a numbering style such as:

- `Bounty SMP V1.mp4`
- `Bounty SMP Part 1.mp4`
- `Bounty SMP Scene 1.mp4`

If that name already exists, Optic safely chooses the next available number. You can also change the video destination from the Locations tab; its matching Details folder stays separate.

## Recovery and graceful failure

Recordings are created as in-progress fragmented media and finalized only after recording stops. Optic keeps session metadata and recovery sources in the Details folder so interrupted sessions can be detected on a later launch and recovered when enough valid data remains.

Optic also treats common failures as recording errors instead of reasons to take Minecraft down:

- Missing FFmpeg
- Unsupported hardware encoder
- Microphone access failure
- Microphone disconnect
- Encoder process failure
- Stalled encoder pipe
- Bounded queue overload
- Interrupted finalization

No recovery system can guarantee the final incomplete fragment after every driver, disk, filesystem, or power failure, so important footage should still be checked after recording.

## FFmpeg without the scavenger hunt

Optic first checks for a working FFmpeg installation. On Windows x64, it can automatically obtain a pinned FFmpeg package from a fixed publisher URL over HTTPS and verify it against a bundled SHA-256 checksum.

Optic does not search the web for arbitrary executables, does not silently modify the system PATH, and does not require administrator access. The downloaded tool is cached locally and checked again before use.

## Nerd facts

For anyone who wants to know what happens under the hood:

- Minecraft’s final color texture is resized and vertically corrected on the GPU.
- Capture uses a **triple Pixel Buffer Object ring** on Minecraft 1.21.11.
- GPU fences are polled with a zero timeout; Optic does not intentionally wait for a frame transfer on the render thread.
- Supported OpenGL drivers use persistent mapped buffers, avoiding a full-frame render-thread CPU copy.
- The portable OpenGL fallback copies only after readback completion and reuses pooled direct memory.
- Video queues are fixed and bounded. If encoding cannot keep up, Optic drops or repeats frames according to its recording clock instead of consuming unlimited RAM.
- Encoding, FFmpeg pipe writes, audio capture, muxing, library scans, and normal file operations run away from the render thread.
- Fit and Fill share one GPU resize path; there is no CPU-side scaling and no second world render.
- The recording timeline uses monotonic time, so dropped render frames do not make the finished video play too quickly.
- Session telemetry tracks capture cost, completed-readback cost, queue depth, drops, repeated frames, unique FPS, encoder FPS, bitrate, write speed, frame-time percentiles, and approximate 1% lows.
- FFmpeg writes a fragmented working file and then stream-copies it into a checked, fast-start MP4.
- Diagnostic support reports are generated locally and exclude raw logs, paths, account IDs, server names, world names, chat, marker notes, and device names by design.

This is asynchronous readback, not magical zero-copy encoding. FFmpeg still receives image data, converts RGBA into an encoder format, and may upload that data to the hardware encoder. Optic documents those costs instead of disguising them.

## Interface philosophy

Optic is meant to feel like part of Minecraft. Its controls use compact, Minecraft-sized buttons, neutral colors, simple labels, and small status indicators. Advanced recording details stay behind secondary screens so the default experience remains easy:

**Press Record. Play Minecraft. Press Stop.**

## Requirements and compatibility

### Minecraft 1.21.11 build

- Minecraft Java Edition **1.21.11**
- Fabric Loader
- Fabric API
- Java **21 or newer**
- Client-side installation only

Windows x64 build 20348 or newer is currently required for Minecraft process-audio capture. Other systems can continue recording video and supported microphone input, but automatic FFmpeg setup and Minecraft-audio support are not currently certified there.

Optic is designed to coexist with creator mods. Its pause-menu controls search for an open nearby position instead of assuming that no other mod adds buttons. Flashback, Sodium, Iris, Simple Voice Chat, and common waypoint mods are optional; none are required. Their presence does not imply that every interaction or overlay exclusion has been certified.

### Minecraft 26.3 preview

The separate Minecraft 26.3 port requires Java 25 and uses the newer RenderPearl capture APIs. It is an experimental preview: selective overlay exclusion is disabled, and Windows testing has found unresolved native Java crashes during some packaged-client startup and shutdown runs. Do not use that preview for important recording sessions yet.

Optic JARs declare an exact Minecraft version. A 1.21.11 build should not be installed in 1.21.10, 26.3, or another release merely because the version number looks close.

## Current development status

Optic is a serious development build, not a production-certified OBS replacement. Core recording, hardware probing, audio tracks, clean capture, replay, markers, library workflows, recovery, diagnostics, and the performance-oriented pipeline are implemented, but broad release qualification is still in progress.

Outstanding validation includes multi-hour physical audio synchronization, device reconnect matrices, disk-full and GPU-loss stress tests, broader NVIDIA/AMD/Intel coverage, shader and mod combinations, editor-import coverage, and controlled quality-matched comparisons against OBS. Some high-throughput combinations—especially 1440p at 120 FPS—still need optimization.

Performance claims will follow evidence. If OBS performs better in a tested scenario, that result belongs in the documentation too.

---

## The goal

Optic is for creators who want to record Minecraft, not configure a broadcast studio. The finished experience should be simple enough for anyone to use and engineered deeply enough that experienced creators can trust what comes out:

> **Install one client-side mod. Press Record. Make the video.**
