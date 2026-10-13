# Audio architecture and validation

## Sources and ownership

`AudioCapture` owns source startup/shutdown. `MinecraftAudioCapture` selects the current game adapter, `WindowsProcessAudio`; unsupported systems fail only that source. `MicrophoneCapture` owns a Java Sound input. `AudioControls` carries live gain/mute and decimated meter/health data. `AudioTrack` identifies format and title for `AudioEncoder.muxTracks`.

`PcmTrackWriter` accepts timestamped blocks into a fixed pool, preserving source identity through separate files. All PCM is 48 kHz signed 16-bit little-endian; game has two channels and mic one. Capture callers never write disk. Game's owned C# helper has ten reused 10 ms blocks, the Java game queue twelve, and Java input buffering 4 KiB. The mic queue holds twenty-five 10 ms blocks. OS pipe/device buffers add latency beyond those explicit queue capacities and require measurement. Full queues drop incoming blocks and count losses; timestamps leave the missing interval silent rather than shortening the track. Microphone driver reads can still block, but not Minecraft rendering.

The game helper invokes Windows WASAPI application loopback for the current JVM PID and its children. PCM packets retain WASAPI QPC timestamps. JNA samples the shared QPC clock against Java monotonic time; packet positions are placed on the recording timeline. Startup/mux work is off-thread. The helper exits when its stdin closes, so a dead parent does not intentionally leave background recording running.

The microphone uses a smoothed estimate of sample-clock rate against monotonic elapsed time. Available device bytes compensate for queued samples. A continuous linear interpolator makes small rate corrections on the input worker. It is intended for microphone drift correction, not high-quality arbitrary-rate music conversion. Reconnect creates a new segment on the same timeline; gaps remain silence. Device latency, startup alignment and scheduler-induced estimation error still require physical impulse measurements.

Gain and mute operate on the disk worker. Clipping is saturation, with a warning after three consecutive clipped blocks; it is not a compressor. There is no live microphone monitoring. Failed audio does not intentionally stop video. A stalled native device can retain resources until the driver returns; bounded caller waits do not make native driver code cancellable.

## Track export and recovery

Microphone PCM's offset sidecar remains compatible with legacy recordings. New sources are already aligned to recording origin and use zero offset. Session schema 3 names successful source roles. AAC tracks have `title` and `handler_name`; game is mapped first, mic second. Both are decoded during final validation. Ordinary players may select one track; editors should import both.

Source files remain under the recording's matching support directory until verified completion. Legacy root partials also work. Invalid mic-offset metadata preserves its source while allowing a valid game track. Audio mux errors retain PCM and can fall back to video; a full disk may prevent even video finalization. File completion and editor behavior are separately tested concerns.

## Voice chat and category feasibility

Investigated the public Simple Voice Chat API on 2026-10-10:

- [ClientReceiveSoundEvent](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/ClientReceiveSoundEvent.html) provides decoded audio before playback. It is not a post-spatialized mix tap.
- [OpenALSoundEvent](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/OpenALSoundEvent.html) exposes an OpenAL source and positioning/category context. The Post event occurs after buffers are queued, not as a ready-made mixed PCM output.
- [ClientSoundEvent](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/ClientSoundEvent.html) concerns outgoing local voice before encoding. Capturing that alongside Optic's physical microphone would create overlapping local audio.

Inference: adding a raw receive track today would not remove those voices from the process master and could differ in positional/volume processing. Subtracting a pre-effect stream is not a clean solution. No adapter or working-looking isolated-voice control is shipped, and no Simple Voice Chat version is claimed as tested. The mod is not mandatory.

Similarly, process audio has no per-Minecraft-category labels. Minecraft sound categories exist before mixing, but that alone cannot recover isolated post-spatialized music/mobs/blocks/weather/UI. A later OpenAL mirror or source-specific mixer needs isolation and overhead validation. [LWJGL SOFTLoopback](https://javadoc.lwjgl.org/org/lwjgl/openal/SOFTLoopback.html) renders a separate loopback device, not a tap of Minecraft's existing mixer; replacing playback or duplicating every channel is not assumed free.

## Platform basis

The [Microsoft application-loopback sample](https://learn.microsoft.com/en-us/samples/microsoft/windows-classic-samples/applicationloopbackaudio-sample/) documents process-tree capture without unrelated applications. Optic's helper is its own minimal implementation using the installed Windows SDK interface definitions and includes no copied sample source or third-party executable. Windows x64 build 20348+ is checked at runtime. Other OS/CPU combinations remain unavailable for game capture.

## Evidence and release gates

Passing automated checks cover mute, gain clipping, timestamp gaps, interpolation continuity and a simulated four-hour clock with ±500/±2000 ppm drift, within 20 ms in the model. This is not a four-hour microphone measurement. Windows integration plays a synthetic signal in the target JVM and verifies non-silent stereo PCM with no Java queue drops. MP4 integration decodes two separate AAC tracks and checks the matching-folder output layout. The client capture smoke saves named stereo game audio.

Still required before declaring V3 complete: actual visual/audio impulse alignment across four hours; USB, integrated and Bluetooth devices; reconnect and default-device changes; off-process sound exclusion with simultaneous reference signals; endpoint-volume/HRTF effects; supported Premiere/Resolve editor imports; frame-time impact compared to V2. No current test establishes an OBS advantage.
