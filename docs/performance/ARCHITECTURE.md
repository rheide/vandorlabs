# Programmable block architecture review

Inspection baseline: `a3962677` (2026-09-26).

## Rendering

`ClientProxy` binds the selector, light and trigger tiles to
`TEAnimatedScreenSelector`. Most of their JSON block models have no elements;
the tile renderer draws their housing as well as the selected face every frame.
The comment on `BlockAnimatedScreenSelector.getRenderType` describing a baked
opaque housing is out of date. This is why a programmable cube costs more than
stone even when its texture never changes.

The renderer already shares cuboid drawing, housing meshes and input layouts.
`ScreenHousingMesh` and `InputSurfaceLayout` already precompute finite variants.
Animations decode once and change UV coordinates with world time; there is no
per-frame image decoding. Moving doors use `TESlidingDoor`. Glass combines a
baked frame with tile-rendered transparent faces. Chairs, inactive ramp
controllers and propulsion fixtures use baked geometry. Thruster particles are
a separate tick/render workload.

Repeated work worth addressing:

- Porthole polygon clipping, frame bands and glass bands are reconstructed on
  each render despite depending only on dimensions, shape, border and cell.
- Porthole and light groups are rebuilt once per world tick or configuration
  revision. Light groups run a general rectangle partition even for a single
  cell or an already rectangular assembly.
- Cube/slab/light rendering samples six neighboring light values per frame.
  Any persistent light cache would need a reliable lighting invalidation rule.
- Each tile submits its own draw calls. CPU geometry caching cannot eliminate
  that cost. Static selectable surfaces should eventually use extended block
  states and baked models, with tests for texture orientation, culling, light,
  resource reload and settings updates. This changes the lighting path and
  needs visual acceptance.
- Chair geometry is relatively dense. Model simplification needs a visual
  comparison at ordinary viewing distances.

## Joining

There are several deliberately different policies:

| Family | Policy |
| --- | --- |
| Portholes | Coplanar flood fill by block/facing/depth/shape/join, then rectangular partition |
| Lights | Coplanar flood fill by facing/art/join; visual groups also match on/off |
| Propulsion | Only complete isolated squares within the configured size limit |
| Glass | Local edge/corner connectivity, including depth and join setting |
| Walls | Perpendicular/straight neighbors determine collision and rendered arms |
| Doors | Paired two-block structures with hinge/open-state rules |

A shared plane coordinate system and loaded-cell traversal are appropriate.
Each family should retain its own eligibility policy. In particular, light
redstone groups must ignore visible on/off state to propagate a new signal to
the entire assembly. Square-engine validation and door pairing should remain
separate from arbitrary flood fill.

Porthole discovery, seam checks and rim checks currently repeat the same
eligibility predicate. Consolidating it prevents a future setting from being
applied to one path but omitted from another.

## Placement and collision

`BlockAnimatedScreenSelector.placementFacing` already centralizes inheritance
from an adjacent matching block and the player-facing fallback. Subclasses
have meaningful exceptions: diagonal blocks follow stair placement; half-inputs
use the supporting wall face; lights accept six mount directions.

`PanelDepth` centralizes the three offset slots, but walls and configurable
doors duplicate the conversion from world hit coordinates to local depth.
Wall and input classes also duplicate the same rotation of an axis-aligned box.
These are small, suitable shared helpers. Glass uses a two-axis orientation
rather than four facings and must retain its existing mapping.

## Redstone and ticking

`RedstoneChannelMember` and `RedstoneChannelLatch` already provide shared
interfaces. `RedstoneChannels` owns one network per world, registers loaded tiles
through their lifecycle hooks and reconciles members on input events. It has no
per-tick full-network scan. The copied iteration lists protect against callback
mutation; removing them without reentrancy tests would be unsafe.

Reconciliation is O(members in a channel) per input event. An incremental count
looks cheaper but earlier contributors can become stale when neighbor events
coalesce; the source explicitly documents why full reconciliation exists.
Frequent redstone clocks and bulk placement are the useful stress cases for a
future server benchmark. No server tick speedup should be claimed from render
submission measurements.

`TileEntityAnimatedScreenSelector` is a broad settings holder inherited by lights
and triggers. Splitting its persisted data into components could improve
ownership, but requires migration/packet/copy tests. A wholesale hierarchy
rewrite would add risk without reducing the measured rendering cost.
