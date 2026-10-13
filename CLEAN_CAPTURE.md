# V4 clean capture

## Controls and boundaries

Open the pause-menu cog, then **Capture**. Every switch controls the video only and applies at the next frame boundary. All switches default ON, preserving previous recording behavior. Existing settings gain these defaults without changing audio, naming or storage preferences.

| Control | Exclusion scope |
| --- | --- |
| Minecraft HUD | Vanilla screen-space HUD, including its children below |
| Chat | Vanilla chat history, including history in the open chat screen |
| Subtitles | Sound captions |
| Scoreboard | Sidebar scoreboard |
| Boss bars | Boss names and health bars |
| Action bar | Overlay message above the hotbar |
| Debug display | 2D debug text/charts |
| Recording indicator | Optic REC timer and optional recorder details |

HUD OFF overrides child switches without erasing their preferences. The recording indicator is independent: it can remain in the video even with HUD OFF. Interface → Recording HUD still controls whether Optic submits the indicator at all. Capture cannot add an indicator that the player has disabled.

Screens/menus, chat input, toasts, first-person hands, player names, world-space debug graphics and world-space waypoints are not removed by these controls. Excluding chat history is not a privacy guarantee: typed text and third-party chat windows can remain. The compatibility page states these limits and lists detected optional mod versions. There are no pretend nameplate/waypoint toggles.

## Implementation

The 1.21.11 GUI is deferred. Its ordinary element/text/item/picture-in-picture submissions populate `GuiRenderState`; `GuiRenderer` prepares and draws them later. This makes skipping a draw at extraction time incorrect for the player's view, and reading the final framebuffer too late to recover covered pixels.

`CapturePolicy` is independent of Minecraft. Its 256 immutable combinations are cached once; selecting the frame's policy does not allocate. Version-specific mixins bind only `GameRenderer`'s main GUI state and scope vanilla submission methods. `CaptureLayerAdapter` describes that small integration contract. A reusable clean state receives references to allowed commands, with screen strata and blur ordering retained. GUI extraction, animation and screen callbacks execute once. Commands are not retained between frames.

On a due capture frame with exclusions:

1. Copy the pre-GUI final world color on the GPU into a window-sized private `TextureTarget`.
2. Clear its private GUI depth attachment. Bind/restore framebuffer and scissor state through Minecraft's GL state manager so the engine cache stays synchronized.
3. Temporarily render the permitted GUI state into that target. The existing GUI renderer prepares/draws this command stream; it does not render the world again.
4. Feed the private target into the existing GPU resize/flip → triple PBO → bounded queue → encoder pipeline.
5. Restore the original target/state and render the complete player GUI normally.

No exclusions uses the existing final-frame capture hook, with no extra target, GPU color copy or GUI pass. Clean composition runs at recording cadence rather than every rendered frame. A changed window size reallocates the private target; no frame history is retained. The target is released after recording or after returning to include-all. A 256 MiB target budget prevents unbounded window-sized GPU allocation, independently of the existing capture-buffer budget.

The extra GUI preparation can allocate vanilla scratch nodes, text glyph states, meshes and uniform slices. This is not a zero-allocation or zero-copy pipeline. Item-atlas and picture-in-picture work can also increase in menus. No synchronous screenshot call belongs to production capture; the explicit developer harness uses screenshots only as validation evidence.

## Failures

If a due clean frame cannot reach its composition hook, the framebuffer is unsupported/incomplete, or clean composition/capture throws, recording stops with a concise notice and retains recoverable footage. It never silently switches to recording the full player framebuffer. Target/render-state overrides are restored in `finally` before normal GUI rendering. An arbitrary third-party renderer can bypass these hooks, so exclusions are best-effort compatibility features rather than a security boundary.

## Optional mod investigation

No optional mod is required. This development instance has Minecraft 1.21.11, Fabric Loader 0.19.5 and Fabric API 0.141.6+1.21.11; it does not establish compatibility with other mod combinations.

- Flashback's [ReplayUI source](https://github.com/Moulberry/Flashback/blob/master/src/main/java/com/moulberry/flashback/editor/ui/ReplayUI.java) has its own replay/editor rendering. That is not assumed to be a vanilla HUD layer. The existing collision-aware pause controls remain; no replay overlay exclusion adapter is advertised.
- JourneyMap's [integration documentation](https://github.com/TeamJM/journeymap-docs/blob/6.0.x/docs/en/tools/integration.md) and [API](https://github.com/TeamJM/journeymap-api) are starting points for a version-pinned adapter. Creating or managing waypoints is not proof that independent recording visibility is available. No JourneyMap adapter is enabled.
- Xaero minimap/world map, other waypoint mods and performance overlays have no validated Optic adapter. Screen-space and world-space rendering must be checked separately against exact installed versions. Do not toggle a mod's global visibility setting during capture: that would affect the player too.
- [Iris](https://github.com/IrisShaders/Iris) changes shader rendering and must be tested with exact Sodium/Iris/shader-pack versions. Vanilla target behavior is insufficient evidence for compatibility. No shader combination is certified here.

Future adapters must scope command submission without replaying gameplay/UI side effects, identify exact supported versions, preserve player rendering and fail explicitly. Direct OpenGL draws, custom command streams and glyph/blit submissions outside the normal GUI preparation path are outside the current filtering contract.

## Validation and release gates

See DEVELOPMENT.md for executed checks and PERFORMANCE.md for measured costs. Development validation is not a declaration that every V4 roadmap release gate has passed. Broad mod/GPU tests, multi-hour sessions and controlled foreground frame-time/allocation/GPU profiling remain required. Never infer OBS parity from the clean-composition CPU timer.
