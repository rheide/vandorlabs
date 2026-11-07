# Canonical Screen acceptance

The seven Screen roles retain their existing `ScreenFamily.id` block and item IDs. They use the shared `programmable_viewscreen` block entity and dedicated menu. The shared `NativeProgrammableRenderer` dispatches Screen rendering while preserving the visibility contract of its other block entities.

## Configured items and private ownership

A configured Screen item deliberately contains its complete supported `ScreenChipReader` snapshot, matching the released configured-drop and pick behavior. Authorized Creative picking can copy that configured item. Survival picking only selects or swaps an existing exact item; it must not create a new configured stack. Ordinary block-entity updates publish bounded display metadata and exclude the physical chip stack and its private data.

Proper-tool mining produces one configured Screen item containing the embedded chip, without a separately ejected chip. Placement consumes that item and installs the sole usable embedded owner. Failed or unsupported snapshots remain recovery items and refuse placement. Restart checks preserve that distinction.

## Focused checks

| Runner | Player path and assertions |
| --- | --- |
| `test_screen_canonical_items.py` | Canonical item placement and mounting against 1,890 released Source cases; configured pick, proper-tool mining, failed-load recovery, one embedded owner, item replacement and full process restart for all seven roles. Uses production harvest tags. |
| `test_screen_editor_player.py --canonical --xvfb` | Actual Configurizer and Creative sneak openings for seven roles, actual CHIP widget selection on four integrated roles, current-window commands and stale-window rejection. |
| `test_screen_pick_client.py --canonical --xvfb` | Actual middle-click input: 28 Creative and 21 Survival cases, private imported chip/capability preservation, inventory multiset checks and current actor/window/reach authorization. |
| `test_screen_render_world.py --canonical --chips --xvfb` | Placed canonical Screens through the actual shared renderer; seven role assemblies and ROWS, four trays and a four-cell reader-backed journal, Source culling corpus, disabled dispatcher control, resource reload, actual Survival row input, cached light and full process restart. The adjacent reader is an isolated fixture. |

The renderer observer delegates drawing, visibility and bounds to the installed `NativeProgrammableRenderer`; it does not substitute a private Screen renderer. Reload replaces that renderer, so observation is reattached to the new instance. The disabled control removes the actual shared-type dispatcher entry. Warm draw measurements also call the original shared renderer.

The focused software-rendered canonical acceptance passed with 280 Source culling profiles, 2,970 client assertions, no changed pixels after resource reload and zero allocated bytes across 4,096 warm CHIP/ROWS draw pairs. The separate whole-render Source CHIP corpus retains 432 profiles for body, base-pose tray, inset deck and UV behavior. These checks do not establish hardware FPS, shader-pack behavior or a full release gate.
