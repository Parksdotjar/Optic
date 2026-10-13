# V7 creator markers

Press **F7** during recording or armed replay to bookmark a moment. Change the binding in Minecraft Controls. Configure markers under **Optic settings → Interface → Markers / timeline**. The default is KEEP; available types are KEEP, FUNNY, IMPORTANT, B-ROLL, RETAKE and THUMBNAIL. The optional note field applies to the Add marker button. F7 uses the current type with an empty note; it does not interrupt gameplay. F10 still saves actual replay clips.

After saving, use **View markers** on the completion screen, or Timeline in marker settings. The list is paginated; select an event to read its complete note/context or open its thumbnail. It includes video start/end reference points. The current/last session is available, and after restarting Minecraft the timeline finds the latest local session containing markers. This is not the V8 recording library or a video player.

## Files and privacy

Videos remain alone in Recordings. Each session's Details folder contains:

- `markers.jsonl`: append journal for recovery, with stable event and source-session IDs, monotonic elapsed nanoseconds, source FPS, type and note.
- `markers.json`: versioned generic export containing video identity, source offset, rebased frame/seconds and optional context/thumbnail references.
- `markers.csv`: generic UTF-8 editing reference. Quoted Unicode/newline/quote handling is supported; leading spreadsheet formula characters are neutralized. JSON keeps the original note.
- Optional `marker-<id>.jpg` thumbnails and a local thumbnail processing log.

Context and thumbnails default OFF. Context opt-in collects block coordinates, dimension and the displayed local world/server label. It does not read the server address field, account tokens, account UUIDs or other players. A user-entered display label can itself contain private information. Turning context OFF affects new markers and does not erase previous details. Exports include any deliberately collected context; inspect them before sharing. Text notes can also contain sensitive information.

## Timing and replay

The event captures elapsed monotonic time from the recording session's existing video/audio origin at the keypress. Its source FPS is retained. For a completed media range, export includes only events in `[sourceStart, sourceEnd)` and maps each to the containing CFR output frame. Stable input ordering preserves multiple markers at the same time. Duplicated frames under overload do not change the timeline; the selected frame can be an earlier repeated image. Thumbnail/timing accuracy therefore does not imply unique-frame capture.

V6 clips copy only their in-range events to their own journal and preserve original source timestamps/IDs while rebasing media time. A promoted full recording excludes earlier replay-only markers. A marker never pins replay video or guarantees that footage survives eviction. The live/replay-only timeline explicitly warns that footage can expire. Press F10 to retain it. Full recording and clip creation do not start another capture path for markers.

## Performance and reliability

Marker input snapshots bounded text/context and offers an immutable event to a worker. No marker file write, image readback, screenshot, serialization or export runs on the render thread. There is no marker sampling on every rendered frame. The small acknowledgment uses the V4 recording-indicator visibility scope; hiding the indicator from capture hides this HUD confirmation too. Existing menus and toasts retain V4's documented capture limitations.

Each session accepts at most 4,096 events, with a 128-event pending queue and 8 MiB journal limit. Queue/limit rejection is reported rather than silently dropping a requested marker. Append failures notify the player and leave video running. Successfully appended complete lines survive normal process interruption; torn/malformed final records are ignored on read. This is not per-marker fsync or a guarantee against disk/controller power loss. Shutdown drains the worker for up to ten seconds off-thread; delayed writes are reported.

Thumbnails are **deferred until the video/clip is saved**. A separate bounded export worker decodes the already-recorded frame and scales it to 320 pixels wide. This avoids an additional GPU readback and inherently uses the recorded view, including supported clean-capture exclusions. They are not full-resolution screenshot captures. There are at most eight attempted thumbnails per media export, 256 KiB per image and 2 MiB total; other markers retain text without images. Each extraction has a fifteen-second timeout. The export queue holds eight jobs; saturation/failure is logged, with journals retained for retry. Thumbnail work can contend for CPU/disk after saving and during subsequent recordings; no zero-impact claim is made.

JSON/CSV publication uses temporary files and replace. Video validation/publication never depends on marker export succeeding. Generic and replay recovery schedule exports when recovering video; complete marker journals remain browsable even if export was interrupted. Use Export JSON / CSV to retry generic metadata for a completed session. This retry preserves existing image links but does not regenerate missing images. File associations use session IDs and output names; renaming externally does not automatically update metadata. V8 should own coordinated rename operations.

## Editor support

CSV/JSON are portable references, **not certified native Premiere, Resolve, After Effects or Final Cut timeline imports**. No untested editor-specific controls are exposed. Adobe documents composition/layer markers and metadata workflows, but an editor/version-specific adapter still needs actual import tests with frame-rate/timecode verification before Optic claims support. Native editor adapters remain planned.

Optic's MP4 audio remains independent Minecraft and microphone AAC streams. After Effects supports the video/audio formats, but separate embedded-stream selection has not been verified in the installed editor. For independent AE layers, extract each stream to its own WAV and align both with the video start; Optic currently has no WAV-export button. See [Adobe supported formats](https://helpx.adobe.com/after-effects/desktop/get-started/supported-file-formats/supported-file-formats.html).

## Validation gates

Unit tests cover 30/60/120 FPS mapping, duplicate times, clip boundaries, Unicode/CSV escaping, torn UTF-8, bounded bursts, closed queues, default context redaction and copied clip journals. Real FFmpeg integration verifies a marker at source 30 seconds maps to frame 60 in a 30 FPS full recording beginning at second 28 and produces a thumbnail from that media. The disposable-world harness exercises warm-up clips, promotion, notes/context, settings/timeline UI and thumbnail generation.

Still required for release certification: multi-hour high-marker-count recording/replay workloads; matched foreground p95/p99 and 1%-low measurements; actual editor import adapters; power-loss durability testing; broader mod/GPU combinations. No markers-to-editor timing or OBS performance claims are made from unit/smoke results alone.
