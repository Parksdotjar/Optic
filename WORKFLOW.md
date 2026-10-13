# V8 creator workflow

Open **Optic settings → Recording → Library** or **Profiles / naming**. The basic F9 Record/Stop flow remains unchanged; using profiles or the library is optional.

## Profiles and filenames

YouTube uses the existing Creator video preset; Cinematic uses Cinematic and excludes HUD/recording indicator; Low-End PC uses Performance; High FPS uses Balanced at 120 FPS. These names are conveniences, not performance guarantees. They request Auto encoder selection and retain the current audio enablement and other capture choices except the stated Cinematic exclusions. Review Audio/Capture before recording. High FPS cannot guarantee 120 unique frames on overloaded hardware.

Save Custom stores one portable custom profile; Load Custom reapplies it. Versioned `config/optic-profile.json` includes resolution, FPS, quality, Fit/Fill, preferred encoder, game/microphone enablement and supported capture-element switches. It excludes file paths, device identity, microphone gain/mute, marker privacy preferences and naming. Validation uses existing recorder settings; local encoder detection/fallback still resolves support at start. Applying a profile while recording/replay runs is refused. No portrait Shorts preset is offered because V5 does not provide portrait output; no new framing system is implied. Multiple named custom slots remain a future refinement.

The optional template accepts `{date}`, `{time}`, `{title}`, `{server}`. Example: `{date}-{title}-{time}`. Blank keeps the previous Default / V1 / Part / Scene naming behavior. Template names are sanitized and capped to the existing 80-character stem policy, with reserved Windows names handled and numeric collision suffixes. Unsupported tokens are rejected. Date/time use the computer's local time. Server label defaults OFF and substitutes `Minecraft`; when enabled it uses the display world/server name rather than the server-address field. Display names can themselves contain private text. Replay clips retain their existing Clip naming; templates affect the full recording name reserved at stream startup.

## Library and notes

The library lists completed Optic session manifests in the current recording location, newest first with UTC recording date and duration. Six rows are created per page. Select a recording to open the video, rename it, edit up to 2,048 characters of plain notes, open Details, view markers, or remove/restore it. Notes are metadata; video is not modified. The folder shortcut remains available.

Missing externally moved videos are marked Missing. Refresh reconciles filesystem existence and skips malformed/newer manifests without stopping the list. It does not guess the identity of an externally renamed video or automatically adopt arbitrary MP4s without a completed Optic manifest. Older raw/legacy files without current manifests remain accessible through Open folder. A future import/relink tool can extend this without rewriting capture.

The current directory scan visits at most 20,000 detail folders and publishes incremental results every 256 valid entries. It reads manifests and file existence, never video probes or thumbnails. One cached directory/result list survives while Minecraft runs; Refresh explicitly rechecks changes. No persisted catalog/database or file watcher is required. At the cap, files beyond the filesystem enumeration window are not listed; use Open folder. Cached items can be stale until refreshed. Dates are shown per row instead of an additional grouping hierarchy.

## Rename, delete and recovery

Details directories are stable session containers and keep their original names after video rename. Internal session IDs, PCM recovery sources, marker journals and thumbnails remain there; the manifest points to the new MP4 name and marker exports refresh their video reference. This avoids renaming a bundle of sidecars independently and losing associations. Recordings continues to contain only completed video files.

Delete asks for a second click that identifies the recording and explicitly says it moves the video to **Details / Trash**. It performs no permanent deletion and does not claim to use Windows Recycle Bin. Metadata and thumbnails remain available. Refresh the library and select Restore from Trash to return the video; occupied destinations are refused. To reclaim disk permanently, manage retained Details files yourself after reviewing them. Trash deliberately uses disk space and has no automatic expiry.

Bundle changes refuse active session locks, replay directories, pending clip plans, stale manifest identities/paths and filename collisions. UI file operations are allowed only while recorder/replay is idle. File work runs on a separate single worker with eight pending jobs, serialized with marker export. Moves write a recovery intent before changing the video; startup reconciliation completes or abandons clear interrupted moves without guessing when both paths exist. Unknown/ambiguous states retain sources. Marker exporters reload completed manifest destinations so queued work cannot restore a stale video filename. External players can deny renames on Windows; a concise error leaves sources intact. Cross-volume Trash moves and network folders still need platform validation.

## Performance and validation

No library enumeration occurs in the recording/render hot path. Scans refuse to start while recording/replay runs and stop at the next entry if recording begins. Closed library does no scanning. Visible rows are bounded; no thumbnail memory is allocated. Background operations can still contend for filesystem resources. UI operations acknowledge immediately; actual completion depends on storage.

Tests cover portable profiles, path/privacy preservation, unknown profile versions, Unicode/unsafe templates, collisions, busy session locks, stale actions, notes, rename, Trash/restore, interrupted rename reconciliation, missing/corrupt metadata and 10,000 entries. The first local synthetic scan measured about 6.25 seconds total, publishing batches; this is not a 250 ms cold-scan claim. Cached-page latency, network storage, external-player contention and p99 game impact have not been certified. Live screenshots validate library and workflow layouts; ordinary recording/replay/audio recovery regressions remain part of the test suite.

V8 is a development implementation, not release certification. No video editor, automatic external relinking, cloud upload, native editor export, advanced codecs or V9 features are included.
