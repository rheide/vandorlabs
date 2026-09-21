# Sliding-door animation: attempts log

Sliding doors (`sliding_*`) animate their leaves via a tile-entity renderer
(`TESlidingDoor` + `TileEntitySlidingDoor`). Blockstate logic (open/close,
collision, redstone, sound) flips instantly and is never part of the problem;
only the *visual sweep* is at issue. This file records what was tried, what
evidence ruled what out, and where things stand.

## Architecture (current)

- Each sliding half-block carries a `TileEntitySlidingDoor`, currently a
  **marker only** (render bounding box, no ticking).
- `TESlidingDoor` draws the leaves every frame from a renderer-side animation
  map keyed by world and lower-half block position: on each render it reads the live blockstate,
  restarts an eased ~9-tick travel on change, and interpolates with partial
  ticks. No tile ticking and no network packets are involved.
- Forge creates both marker tiles during normal block placement; the renderer
  reads live blockstate every frame, so no global loaded-tile scan is needed.
- The renderer owns the leaves at rest and in motion; generated block models
  are leaf-free frames. Transparent (cutout) art is alpha-tested there.

## Attempt history

1. **Incremental chase in the tile** (`progress`/`prevProgress` eased toward
   the blockstate, `markBlockRangeForRenderUpdate` per moving tick).
   Opening animation confirmed working once; closing reportedly snapped.
2. **Time-based tile** (pose as pure function of blockstate + world time).
   No observable motion in testing.
3. **Hybrid statics** (real closed/open block models + renderer sweeping only
   mid-motion). Guarantees visible doors even if the renderer path fails.
4. **Magenta probe** (unlit, untextured quads): if a sweep shows, tiles +
   dispatch work and the fault is textured-quad setup; if not, tiles or
   dispatch never reach the renderer. Still live at time of writing.
5. **Renderer-side animation map** (current): removes the tile-tick
   dependency entirely after client ticks proved unreliable for our tiles.
6. **Tick-driven rebuild marks** (superseded): a global loaded-tile scan was
   removed once integrated-world coverage proved ordinary placement creates
   both renderer tiles and live state changes reach the TESR directly.

## Evidence so far (client logs)

- Tiles construct on both sides at placement.
- Server tile ticks and advances (motion-ticking line, `remote=false`).
- Client tiles appear in `loadedTileEntityList`, valid, at door positions.
- Client tile `update()` never fires; renderer entry never logs.
- No TESR/binding/model exceptions anywhere; static models bake cleanly.
- Same behavior with and without OptiFine.
- 2026-09-18 live test (build t13): no magenta quads ever visible, static
  end-states render correctly and flip on toggle (Airlock Sliding Door open
  and closed both look right), but nothing sweeps between them. So the
  renderer draws nothing at all — not even wrong pixels — while statics
  prove the client blockstate flips normally.

## Proven engine facts (read from 1.12 bytecode, not memory)

- `RenderGlobal` renders tiles **only** from the compiled chunk tile list,
  refreshed on chunk mesh rebuilds — not from the live chunk map. A tile
  created after the last rebuild is invisible to the renderer until the
  next rebuild, no matter what it does.
- `World.setTileEntity` fully registers (chunk map + loaded/tickable lists
  + validate), *provided* it runs; construction alone (`createTileEntity`
  log lines) proves nothing about registration.
- `World.addTileEntity` is what lands tickables in the tick list; several
  creation paths skip it.
- `ClientRegistry.bindTileEntitySpecialRenderer` + init ordering verified
  in our code and logs; no binding errors observed.

## Current verification

The opt-in integrated-client harness (`testclient/test_viewscreen.sh`) runs
door metadata, pairing, incomplete-neighbor, motion-type, and collision
contracts in a real world before taking its render screenshots. Production
door code no longer emits the temporary census/construction diagnostics.
