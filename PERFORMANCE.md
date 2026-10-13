# Performance

## Goal, not a claim

Target: equal or lower Minecraft frame-time impact than OBS at equivalent resolution, FPS, encoder and perceptual quality on supported hardware. No comparative benchmark has been performed. This alpha must not be advertised as faster than OBS.

## Render-thread work

The following PBO details describe 1.21.11. The 26.2/26.3 adapters use Minecraft's command-encoder API with three staging buffers, completion callbacks, and one completed-readback copy into a reused CPU frame. They do not call raw OpenGL or wait on an unfinished readback. GPU resizing and bounded admission remain in place. Clean capture adds a GPU world copy and GUI composition pass only on admitted capture frames. These ports need their own controlled performance measurements; the older PBO measurements do not establish their impact.

- Read monotonic clock; skip submission until a new target frame index.
- Check at most three fences with timeout zero. Unfinished readbacks are left alone.
- For completed transfers on ARB_buffer_storage, publish the coherent persistent mapping lease; the render thread performs no frame-sized CPU copy. On the GL 3.3 fallback, map the PBO and copy RGBA into reused direct storage. That fallback is O(width × height) and is not free.
- Attach the final texture, GPU-blit to fixed recording dimensions, enqueue pixel-pack transfer and fence; restore GL state.
- Offer a frame to a bounded queue. No encoding, pipe writes, file access, network, process waiting, or audio capture happens here.

Initialization allocates GL buffers on the context thread and can cause a one-time hitch. PBO use does not guarantee nonblocking driver behavior: glReadPixels submission, map bookkeeping, GPU allocation, or driver synchronization can stall. CPU timing captures the hook cost; it is not a GPU timer measurement.

## Off-thread work

The control worker discovers/obtains FFmpeg, probes encoders, persists settings and markers, remuxes video/audio, writes metrics, while a separate recovery worker scans interrupted sessions. The encoding worker drains frames, fills CFR gaps, and writes FFmpeg stdin. FFmpeg does color conversion, encoder upload, compression, and container/disk IO. A separate microphone thread owns the audio device and PCM writes. The watchdog is independent of pipe progress.

## Memory and copies

Persistent mode: three PBOs + one GPU RGBA texture + one 256 KiB transport array (about 31.6 MiB at 1080p or 56.3 MiB at 1440p, excluding driver/FFmpeg storage). Fallback mode adds six direct RGBA buffers: its nine frame buffers require about 71.2 MiB at 1080p or 126.6 MiB at 1440p, plus the resize texture, driver storage and FFmpeg memory. V5 refuses sizes above a conservative 512 MiB **ten-image budget**, including that resize texture and enough storage for the portable fallback. 4K is permitted but expensive. There are no newly allocated frame-sized buffers per capture.

The preferred coherent persistent path performs only the mapped-to-heap pipe copy on the encoder worker. The fallback additionally uses one PBO-to-direct CPU copy on render. Fallback OpenGL map wrappers and UI text can still allocate small objects. Persistent frame objects and map wrappers are allocated once at startup. Slot leases span the worker; unmapping/deletion is deferred until it finishes. A shared GL context is not implemented and is not needed for the persistent path.

At 1080p60, RGBA alone moves about 498 MB/s; 1440p120 moves about 1.77 GB/s before extra copies and encoder upload. Downscaling on the GPU reduces readback bandwidth. Upscaling to 1080p from a small window increases it. Hardware H.264 does not eliminate these transfers.

## Queue and dropped frames

Queue capacity is three frames; the producer uses `offer`, never blocking `put`. Pool acquisition is non-waiting. Busy PBO, full queue or exhausted pool means a skipped frame. Missed clock indices are counted. The worker duplicates previous pixels at the missing indices to preserve duration; duplicates cost encoder throughput. Backlogs beyond five seconds stop recording and retain partial files. FFmpeg pipe stalls are bounded by a 10-second watchdog. These limits protect memory/rendering, not guarantee that every overloaded recording completes.

## Instrumentation

Every five seconds the encoder logs capture submission average, completed-readback-copy average, observed transfer latency, whole-hook average, current queue depth, dropped indices/transfers, duplicates, input frames delivered per second and current file bitrate. Observed latency runs from transfer submission until a later frame notices its fence; it includes polling cadence and is not a GPU duration. A final `.mp4.metrics.txt` stores the summary. accepted_fps counts raw frames delivered through the pipe; encoder_frames/encoder_fps use FFmpeg progress reports and can lag. Final completion additionally requires a successful decode. Bitrate is container bytes/time. Readback copy average is amortized over submissions (zero for persistent mapping). Final throughput uses recording duration excluding finalization.

Whole-hook average is an estimate of recorder CPU frame-time contribution, **not** the change in Minecraft's total frame time. It omits GPU contention, encoder upload, scheduling effects, setup and finalization. Dropped counters are diagnostics, not a unique presentation-loss percentage.

## Profiling procedure

V2 adds fixed-size histograms for capture cost, whole-hook cost and render intervals, plus queue high water, encoder progress and observed file growth. Histograms use 0.1 ms bins capped at one second; the tail above that cap is approximate. Their approximate slowest-1% FPS is not an external benchmark measurement. Timing collection uses reused atomic counters; histogram scans and formatting occur on workers or in explicitly opened diagnostics. Additional HUD details still allocate text when enabled.

Post-stop validation decodes the entire candidate video and requested audio off-thread with two decoder threads. This adds CPU/disk cost and may affect gameplay while saving. Decode has a ten-minute timeout; unusually long/slow recordings may remain recoverable rather than finalize automatically. Remux itself has a three-minute timeout. These limits and the full-decode strategy need long-session profiling before V2 release. Fragment flushing improves process-crash recovery but increases write calls; measure its storage impact rather than assuming it is free.

1. Use the same fixed world, render distance, resolution, graphics settings and repeatable camera movement. Warm shaders/chunks before measuring.
2. Capture a baseline without recording, then Optic. Use JFR or an external frame-time collector to inspect p50/p95/p99 frame times, CPU load, allocation rate and GC. Minecraft profiler/Tracy may help identify render work.
3. Correlate recorder metrics with OS GPU copy/3D/video-encode utilization and disk throughput. Run at least three multi-minute samples and a long A/V sync session.
4. Exercise 1080p and 1440p at 30/60/120, native dimensions, window resizing, minimized window, pause menus, start/stop loops and slow encoder/disk scenarios.
5. Report driver, GPU, CPU, OS, JVM, heap, mod list, encoder preset, bitrate/quality, source and output size, FPS cap and VSync. Validate decoded image orientation, color, aspect and duration before judging speed.

## Fair OBS comparison

Use OBS Game Capture of the same Minecraft instance, a scene containing only that source, matching output dimensions/FPS, the same hardware H.264 backend and comparable quality. Disable unrelated OBS sources/preview work only if disclosed. Compare perceptual quality on identical reference footage; equal nominal QP across encoders is not equal quality. Include OBS container/audio settings and run Optic and OBS separately. Add a simultaneous Flashback run as a distinct scenario. Compare total game frame-time distribution, dropped output frames, system/GPU load and output quality. Do not infer victory from Minecraft's FPS counter or the recorder hook timer alone.


## V3 audio performance work

There is no audio processing added to the per-frame capture hook. Microphone DSP, rate estimation and interpolation run off-thread. Each source uses fixed 10 ms block storage: mic 25 blocks, game helper 10 blocks plus game Java writer 12. Game transport adds a 4 KiB Java stream buffer and OS pipe buffering; actual total latency can exceed explicit queue duration and needs profiling. No claim that all OS/driver buffering stays below 250 ms is made.

At 48 kHz s16, mono mic PCM costs about 96 kB/s (346 MB/hour); stereo game PCM about 192 kB/s (691 MB/hour), before mux and file overhead. Sources are deleted only after successful final validation. Ensure enough disk for PCM, partial video and the final candidate simultaneously. AAC encoding and full decode add post-stop CPU and IO. This version prioritizes recoverable independent tracks; recording-duration PCM disk cost is a known tradeoff.

Measured synthetic source/queue checks and clock-model tests pass. They do not establish measured audio latency, physical-device drift, a <=5% p95/p99 frame-time delta or <=3% 1%-low loss relative to V2. Those provisional roadmap thresholds remain release gates. No OBS comparison has been made.

## V4 clean-capture performance

Include-all adds scope checks and a policy lookup, but no extra frame-sized copy, clean target, GUI preparation pass or world render. Policy combinations are preallocated. Exclusion mode mirrors allowed command references and, only on due recording frames, performs one GPU color copy plus an additional GUI preparation/draw pass. Minecraft scratch nodes/meshes/uniform slices can allocate during this pass; it is not allocation-free. No CPU resize or synchronous screenshot is used for production capture. The existing PBO queue/lease/drop strategy is unchanged.

Extra target memory is approximately width × height × 8 bytes for color plus depth (15.82 MiB at 1920×1080; 63.28 MiB at 3840×2160), excluding driver overhead and existing capture targets/PBOs. Reject above 256 MiB. Release on stop or include-all, reallocate on window size changes. Inspect menu item/PIP caches with JFR during extended sessions rather than assuming their cost is negligible.

Diagnostics shows clean frames, color copies, extra GUI passes, target MiB and clean composition p95, explicitly cumulative since launch. The timer covers target setup/copy submission, extra GUI preparation/draw and the existing capture hook. It excludes GUI command-mirroring time and asynchronous GPU execution: it is neither pure incremental CPU cost nor total game-frame impact.

2026-10-10 local development evidence: NVIDIA GeForce RTX 5050 Laptop GPU, driver 610.24, i5-13450HX, Java 21.0.12, 1920×1080. A 48-second in-world correctness run used 1080p30, software H.264 and Minecraft audio, with 417 clean frames / 417 copies / 417 extra GUI passes. Clean timer p95 was 1.2 ms. The fullscreen client was background-limited to about 10 rendered FPS, so the 30 FPS video contains duplicates. These are correctness results, not a throughput benchmark. The short legacy-PBO failure/resize run observed 4.5 ms clean p95; it includes cold setup/resize and a different presentation state and cannot be compared directly.

The roadmap's proposed 0.2 ms incremental p95 budget is NOT established by these measurements. Neither <=5% frame-time regression nor <=3% loss of 1% lows has been established. No OBS advantage is claimed. Before release, profile warmed foreground runs for disabled, idle, include-all recording, HUD-off, indicator-only exclusion and worst supported combination. Match camera path, duration, encoder/quality, audio and output; separate command mirroring, GUI preparation, GPU copy and fence latency using JFR/Tracy/GPU tooling. Report three multi-minute runs, allocations/GC, p50/p95/p99, 1% lows, GPU/VRAM and duplicates. Repeat with menus, resize, resource reload and exact optional mod versions. Keep exclusion mode opt-in until its cost is characterized.

A separate six-second legacy-PBO resize test saved successfully at 30 FPS, with 180 clean passes. Its periodic sample reported zero drops/duplicates and clean p95 was 2.7 ms over the run. Source window changed to 960×540 then 1280×720 while output remained fixed. This validates the resize path, not the roadmap's incremental performance budget.

## V5 pipeline and measurements

Every render tick now polls finished transfers before GUI extraction, even between recording deadlines. No blocking fence wait was introduced. Submission follows the recording clock; clean capture first checks whether its ring slot and queue can accept a frame. It skips the extra color copy/GUI pass when backpressured. Scale geometry changes only on window resize. Full-cover output skips the color clear; Fit clears only when bars exist. Fit/crop share the existing single GPU resize/flip. Presets do not add render work or a second world render.

`poll_p95_ms` accounts for fence polling and the portable readback copy. **V5 `hook_*` and `clean_p95_ms` no longer include polling**, so they cannot be compared directly with V4 as evidence of an improvement. Sum/measure all hooks and examine whole-game intervals. Frame interval histograms now include every rendered tick; older clean-mode intervals were capture-cadence biased. `unique_fps` excludes duplicated input frames. `scale_copies` and `scale_clears` make the capture-side pass count visible. V4 clean copy/GUI counters remain separate and cumulative.

One isolated OpenGL experiment on RTX 5050 Laptop / driver 610.74 used three randomized repeats with 100 measured samples each per method/size (plus warmup). Mean GPU milliseconds:

| Scaling | Linear blit | Bilinear shader |
|---|---:|---:|
| 3840×2160 → 1920×1080 | 0.031416 | 0.024809 |
| 1920×1080 → 2560×1440 | 0.044090 | 0.042546 |
| 1920×1080 → 1920×1080 | 0.026499 | 0.024075 |

Output bytes were identical for the gradient/checkerboard test. The shader was slightly faster in this isolated test; that does not establish an in-game benefit including shader/state changes or represent a superior resize filter. Retain the simpler blit pending end-to-end evidence. Raw samples, repeat labels, environment and PNGs are in `build/scale-benchmark`; the reproducible task is `scaleBenchmark`.

The injected persistent-setup failure followed by a 1440p60 High/Fill portable recording and two window resizes produced a fully validated eight-second MP4. It duplicated 31 of 481 delivered frames; unique FPS was 56.20. Portable copy averaged 2.069 ms per submission, poll p95 2.0 ms, capture submission p95 0.3 ms, and no black-bar clears occurred. This stress case **misses** the <1% unique-frame-loss target. Do not classify it as a sustained high-performance result.

The raw frame-trace harness records a bounded sample array and writes it off-thread. It explicitly marks focus loss or overflow invalid. Initial fullscreen/background measurement hit the game's approximately 10 FPS throttle; the windowed trace removed that throttle but still lost focus and is also unsuitable for certification. The local repeated series is screening evidence only. No matched OBS, external presentation latency, CPU/GPU utilization or VRAM trace was collected. See [benchmark protocol](benchmarks/README.md) for the complete comparison requirements, tooling and release gates.
# V6 replay performance notes

Replay OFF schedules no rolling-history worker and creates no segments; the client tick only checks enable/lifecycle state. Replay ON continuously pays ordinary capture/encode costs. It is not a free background feature. Clip export reuses encoded video; additional work is background disk reads/writes, audio assembly/AAC and final decode validation. These can still contend for CPU/disk bandwidth and affect the game indirectly.

The render hot path remains the existing PBO path. Retention polls every 250 ms off-thread with at most 512 CSV rows and bounded recent metadata; no filesystem scan occurs per rendered frame. Histories use a 1 GiB rolling target inside a 2 GiB staging/admission budget, with one export pin and active-segment safety guard. Full recordings retain their own unbounded-by-duration output subject to free-disk finalization reserve. See REPLAY.md for exact budget semantics and transient overshoot.

Existing capture/readback/hook/frame-time, dropped/duplicated/unique-frame, queue and bitrate counters remain active. Replay supplies cumulative encoded video bytes rather than the absent ordinary partial file. `ReplayHistory` publishes current bytes, pinned bytes and aggregate maintenance passes/time; settings show actual retained storage. These are instrumentation, not benchmark results or an estimated-impact rating.

Live 1080p30 NVENC smoke tests on both PBO paths saved clips during full recording. They included UI/world changes and are correctness tests, not controlled performance comparisons. To qualify V6, repeat the V5 foreground protocol with replay disabled, ordinary full recording, replay armed, replay+full, and timed clip exports; match source, output, encoder, quality and audio. Include slow-disk contention and sustained high bitrate. Report p95/p99 frame times, 1% lows, unique output frames, capture cost, process utilization and peak disk/RAM. Compare OBS replay at equivalent settings separately from ordinary OBS recording. The roadmap's 5% tail-frame-time / 3% 1%-low armed-versus-full targets remain unverified; do not claim OBS parity.

## V7 marker profiling

No per-frame marker capture runs. F7 alone snapshots context and submits metadata; the HUD checks the confirmation deadline. MarkerJournal bounds events, queue and file size. MarkerExport bounds pending jobs and thumbnails. Thumbnail CPU decode/scaling occurs after media publication and may contend with later recording; it is optional and defaults OFF. Metadata-disabled recording creates no journal worker thread until the first event. RecordingManager.markerInputTime and MarkerJournal.appendLatency measure input cost and queue-to-append latency, using the existing fixed histogram. Neither is a certified result: run sustained normal/burst marker scenarios and the V5/V6 matched foreground protocol before claiming the proposed 0.2 ms input p95 / 100 ms acknowledgment / 5% tail-frame-time limits. Existing per-frame capture timings remain unchanged. Synthetic and short live tests validate behavior, not OBS parity.

## V8 library performance

Library closed: no scans/watchers or thumbnail decoding. Open/refresh: worker-only manifest/existence reads, incremental snapshots every 256 entries, at most 20,000 folders and six visible widgets per page. One cached directory list allows reopening without IO. No thumbnail images are loaded, below the planned 64 MiB image-cache ceiling. Workflow queue is bounded to eight pending jobs plus one running job. Capture refuses library scans; a scan already running checks recording state each entry and stops. File operations use the same idle restriction plus session locks; filesystem calls can still delay an in-progress worker on slow media.

The initial local synthetic 10,000-manifest scan took about 6.25 seconds total. This confirms bounded/paged behavior, not the provisional 250 ms cached-page or 5% tail-frame-time target. Cached-page measurements, 1% lows/p99 under disk contention, network locations and broader hardware remain validation gates. No OBS performance claim follows from these tests.

## V9 performance boundaries and validation

Render-thread capture remains the existing GPU scale/copy plus bounded PBO ring and nonblocking fence polling. Advanced formats add no world render, auxiliary readback, synchronous disk operation or frame-sized allocation to this path. Format discovery and startup validation are background work; six-frame full-resolution probes consume real GPU/CPU resources and are not throughput measurements. User-triggered discovery is idle-only and cancelable.

Explicit SDR conversion is same-size FFmpeg worker-side matrix/range conversion, not CPU resolution scaling. The encoding worker also samples duplicate counters at five-second intervals for the optional overload stop. Queues and frame buffer reuse are unchanged. HEVC/AV1 may increase encode latency, decode validation time and thumbnail CPU cost; measure these separately, including stop-to-ready delay and memory during finalization.

For each hardware codec compare idle/recording against equivalent OBS resolution, FPS, codec, encoder, quality/bitrate and audio. Report repeated frames alongside dropped frames, frame-time tails and 1% lows; constant output FPS can conceal repeated content. Compare explicit color contracts and decoded visual quality, not merely nominal bitrate. Test clean capture and replay separately (advanced replay currently falls back to H.264). No new measurements justify a faster-than-OBS claim. Short smoke telemetry is diagnostic evidence only, not a controlled benchmark.

Synthetic chart and mux tests establish basic SDR range/color-tag handling and track preservation. Physical long-session sync, broad GPU coverage, shader compatibility and automatic stop stress remain release gates. See DEVELOPMENT.md for the exact test logs and ADVANCED_CAPTURE.md for supported scope.

## V10 release measurement

benchmarks/V10.md defines the four-state 120-second-minimum, three-repeat protocol and regression gates. benchmarkSummary computes exact quantiles and slowest-1%-mean FPS from bounded offline traces, flags short/unfocused/overflowed runs and reports missing conditions. It cannot verify OBS operation or equivalent quality and does not infer a performance win. All GPU/CPU/VRAM/disk counters need external measurement. No new comparative benchmark was produced in the consolidation pass.

Support collection is user-triggered; settings migration/report export/version inspection are off-thread. No new frame-sized buffers, world rendering, capture queues or per-frame disk work were introduced. The live cancellation smoke was background-throttled; its duplicates are retained in the development evidence, not presented as performance success. Full long-session and multi-GPU qualification remains open.
