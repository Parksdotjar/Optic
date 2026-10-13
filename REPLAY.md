# V6 instant replay

Development implementation for Minecraft Java 1.21.11, Fabric, Java 21. Replay defaults OFF. Enabled replay automatically starts in a world and stops on disconnect; it continuously encodes even before the user presses Record. F10 / Save Clip exports recent history. Minecraft Controls owns the remappable binding; settings show binding conflicts. Ordinary F9 recording remains available with replay disabled.

## One capture and encoder

The existing clean-capture/GPU scaling/PBO ring feeds the existing bounded frame queue and encoding worker. FFmpeg's segment muxer writes two-second Matroska video segments, with a forced keyframe at each boundary and reset timestamps. A bounded CSV index describes closed segments. No packet parser, raw-frame history, second world render or second video encoder is introduced. The portable and persistent capture paths both use this backend.

`ReplayHistory` owns retention, promotion, single-request admission and background monitoring. `ReplayExport` concatenates closed video segments by stream-copy. `SegmentedPcm` implements the small `PcmStorage` interface used by existing audio capture. `ReplayRecovery` handles persisted export plans and promoted recordings. These classes have no Minecraft rendering dependencies; `RecordingManager` integrates their lifecycle. UI methods read published state or enqueue requests, never scan files or await export.

Game and microphone PCM are chunked at exact two-second sample boundaries from the session origin, separately. This deliberately retains bounded uncompressed **audio**, not lossless video: about 0.288 MB/s total for stereo game plus mono microphone at 48 kHz/16 bit. Export rebases both tracks with video, encodes them to separate named AAC tracks and uses the existing decode-and-publish finalizer. Missing/late audio is silence-padded with a notice/log; it is not invented or shifted earlier. Process-audio platform limitations from V3 remain.

## Duration and transitions

Choose 15, 30, 60, 120 or 300 seconds. A warm-up save exports only available history. Clip boundaries expand to closed keyframe segments, by less than/about two seconds on each side. A request waits for the segment containing the press to close; an encoder that does not reach it within twelve seconds fails the request. Requested audio gets up to three additional seconds to close its matching chunk. Export completion also depends on disk speed and validation.

F9 while armed promotes the same stream to a full recording starting at the current segment (up to two seconds of pre-roll). Full recording and clips share resolved resolution/FPS/quality/encoder/audio sources and capture policy. Incompatible changed source/video settings refuse promotion with an OFF/ON instruction. Live gain/mute and capture switches still work. Stop finalizes the entire stream, then automatically warms a fresh replay history. There is a gap/reset during this operation. Replay cannot be disabled in the middle of a promoted full recording; stop it first. Enabling replay during an ordinary recording also requires stopping first.

## Bounded storage and overload

- Reuse the existing three-frame bounded video queue and audio queues. No minutes of pixels reside in memory.
- Maintain at most 512 CSV rows, a 128 KiB index bound, and only recent retention metadata even during long full recordings. Poll/evict on a background worker every 250 ms.
- Recent history retains selected duration plus roughly eight seconds of slack, subject to a 1 GiB rolling target. A 2 GiB combined rolling/export staging budget accounts conservatively for source audio and three copies of the requested export. Active video has a 256 MiB safety guard. These are admission/monitoring bounds, not a filesystem hard quota: an active segment and polling delay can overshoot the target briefly.
- One outstanding export; additional presses are refused immediately. Requests pin their source range before waiting for the closing boundary. Before allocating export files, admission checks combined budget and free disk with a 256 MiB reserve. Under pressure, shorten unpinned history, cancel an export if necessary, then stop on shared fatal disk failure. No unbounded request queue.
- Promoted full recordings intentionally retain all selected segments and are outside the rolling-history cap. Their accumulated size determines an additional two-copy finalization disk reserve. They cannot fit forever on finite storage; the worker stops when reserve is threatened.
- Failed prepared exports retain their requested range and plan, reject further clip exports, and block replay re-arming until recovery. They do not retain all future unrequested history. An otherwise healthy full recording can continue.

## Files and recovery

Only completed MP4s enter Recordings. Each session/clip has its own sibling Details subfolder. Active segments live under its `replay/` directory. A history marker persists full-recording promotion; a clip plan persists source identity/range before assembly. Session IDs and source start/end times map rebased clips to the original session for future V7 work.

On launch, recover accepted persisted clip plans and promoted full recordings from closed segments before deleting unrequested abandoned history. Lock both source and destination; validate paths stay under the owned Details directory; reject symbolic sources and never overwrite completed videos. Existing output collisions get a separate recovered filename. Generic MP4 recovery skips replay plans/history to avoid publishing a half-assembled export after replay recovery failed. Failed recovery preserves sources. Unsaved replay history is intentionally temporary and is cleaned on launch; an in-memory request interrupted before its plan is written is not guaranteed recoverable. The last open segment can be lost in a crash.

## Validation and remaining work

Automated tests exercise all duration retention policies, byte-pressure eviction, late audio chunks, unsafe recovery paths, actual FFmpeg stream-copy/decode, overlapping request rejection, two AAC tracks, full-recording promotion and interrupted clip/full recovery. Live Minecraft tests exercise NVENC, game audio, warm-up clips, saves during a full recording and both PBO backends. See DEVELOPMENT.md for evidence.

Multi-hour replay/full/export contention, physical microphone reconnect while clipping, AMD AMF/Intel QSV replay output, hard power loss and matched OBS replay benchmarks are still release gates. Timestamp/container correctness on tested paths is not certification for all drivers. Provisional roadmap performance targets remain unproven. No V7 marker, cloud sharing, highlight detection or editor features are included.
