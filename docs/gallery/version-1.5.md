# Version 1.5

Version 1.5 adds larger doors, White Glass door leaves with pull handles, eight
new light designs, screen row ordering and curved ramp profiles. It also batches
compatible opaque Programmable Doors to reduce repeated renderer submissions.

## Door performance compared with other mods

**Vandor's measured door baseline takes substantially less render-thread CPU
time than MrCrayfish’s Furniture Mod’s sliding doors.** Controlled fixtures of 64 complete
doors, with shaders disabled, produced these median costs:

| Door | Render-thread CPU | Java draw submissions |
| --- | ---: | ---: |
| MrCrayfish’s Furniture Mod modern sliding door | 3.443 ms | 128 |
| Malisis iron door | 0.597 ms | 1 |
| Vandor Labs 1.4 default Programmable Door | 0.605 ms | 192 |
| Vandor Labs 1.4 sliding Programmable Door | 0.370 ms | 128 |

The default Vandor fixture used about **5.7 times less CPU time** than the
MrCrayfish’s Furniture Mod fixture; its sliding fixture used about **9.3 times less**. Default
Vandor doors were comparable to Malisis iron doors in CPU work, while sliding
Vandor doors took about **38% less**. Glass and detailed artwork have different
costs, so these comparisons apply to the specified fixtures.

Version 1.5 adopts the shared-buffer approach demonstrated by Malisis for
compatible opaque doors. It caches geometry, applies door motion on the CPU,
and submits it through Forge's batched tile renderer. Glass, custom textures,
OptiFine and incompatible renderer/model combinations retain the existing
rendering path. Large doors have one renderer for the whole assembly.

The table is the measured **1.4 baseline**, not a measured 1.5 speedup or an FPS
promise. Final 1.5 graphics timings and Complementary Unbound appearance have
not been verified. Geometry, UVs, lightmaps, persistence and gameplay contracts
are covered by non-rendering checks. See the
[rendering implementation notes](../performance/1.4-to-1.5.md).

## Large Programmable Door

Place the door at the bottom-center of a **3×3 opening**, with three solid
supports below it. It is always a double door, with two 1.5×3 leaves. Frame,
artwork, movement, hinges, button panel, placement depth and redstone settings
use the regular Programmable Door controls. Configure any cell to edit the
whole door; break any cell to remove the assembly. Placement rejects blocked,
unloaded or protected cells without leaving a partial door.

Craft one from **two Programmable Doors and seven Programmable Matter Ingots**.

The **White Glass** and **Dark Glass** designs are available for both door sizes.
Their thicker moving rails surround a large glass opening, with simple rectangular
handles on both faces. Dark Glass uses **Door Interior (Dark)** from Materials,
with contrasting light handles. Materials
are cropped or repeated at consistent proportions. The stationary frame remains
independently optional. Open large doors no longer produce phantom collision
boxes inside the passage.

Large doors animate over **12 ticks (0.6 seconds)**; regular doors retain
**9 ticks (0.45 seconds)**. The large door's optional control panel is lower,
centered **1.125 blocks above the base**, matching the regular door. Its visible
geometry, collision and click region move together.

Original door leaf artwork uses **128×256** pixels for Small and **256×512** for
Large. These are artwork resolutions, independent of
the physical opening size. White Glass's border uses the existing 16×16 Light
Alloy texture; Dark Glass uses the 64×64 Door Interior (Dark) texture. Both crop
or repeat those materials instead of stretching them over the leaf.

## Lights, screens and ramps

- **Programmable Light:** Bussard Classic, Bussard Modern, Deflector Amber,
  Deflector Blue and Nacelle 1–4,
  each with matching On/Off artwork. Existing saved selections retain their indices.
- **Redstone Screen:** Up/Down moves the selected label and its complete channel
  list together. Background panels now use textured vertices with normals and
  explicit lightmaps, addressing the untextured rendering path used previously.
- **Ramp and Filled Ramp:** choose Linear, Curve In or Curve Out. Curve In
  changes height gently near the hinge; Curve Out changes it more rapidly there.
  Endpoint heights stay fixed, and tread size controls the curve's step detail.
  Collision, rendering and rider motion use the same profile. Older saves remain
  Linear, and the Duplifier can copy interpolation independently.

Cargo artwork adds **Plain Cargo, Stepped Freight, Observation Leaf and Reinforced
Leaf** to Doors, using mirrored left-hand artwork. Large doors also offer a
**Double Doors** category: Warehouse Shutter, Slotted Bay, Cross Braced Bay and
Split View Bay. Each square image spans both moving leaves. **Cross Braced Bay**
is the default for newly placed large doors; saved selections are preserved.
All twelve cargo designs have Small/Large artwork. Complete bay textures are
256×256 and 512×512 respectively.

The Double Doors category also includes **Offset Cargo, Twin Observation, Armored
Biparting and Service Freight**, with complete square artwork at both artwork
resolutions. Their asymmetric halves are sampled separately, never mirrored.

## Sliding X for large doors

Choose **Sliding X** with the movement button to open a large door along an X
meeting at its center. The face divides into four triangular panels; each slides
1.5 blocks outward, to the left, right, top or bottom. The panels retract outside
the full opening. The fixed frame and optional control panel stay in place.
This option is available only on Large Programmable Doors and preserves the
existing 12-tick animation, trigger, redstone, depth, artwork and copying options.

Clipped geometry retains its original UVs, including the complete bay compositions
and their transparent cutouts. White/Dark Glass panes and Fit/Tile replacement
materials use the same cuts and motion. Prepared panels are cached until a
resource reload; compatible opaque panels still share Forge's rendering buffer.
Large-door collision boxes are reused across all nine cells. Open triangular
collision/selection surfaces use narrow conservative strips along their diagonals;
the strips clear the passage completely.

Non-rendering checks cover closed coverage, interpolated UVs, diagonal edge caps,
four movement directions, every facing, shared-buffer/fallback transform agreement,
persistence, size/category filtering and open passage collision. Hardware appearance
and shader compatibility of this new mode still need visual acceptance.

## Smaller artwork tiers and texture selection

Door artwork now offers **Small** and **Large**. Large uses the former Medium
resolution; former Large choices load as the new Large tier, retaining their
designs and all door settings. Physical 2×2 and 3×3 doors remain available.
Imported hatch artwork offers 128×128 Small and 256×256 Large in the trapdoor
pickers. Standard inventory texture images are 128×128; model icon padding is
retained.

Programmable Glass likewise offers Small and Large, using 256×256 and 512×512
pane/frame textures. The old high-resolution pane contained a different,
coarser-looking reflection pattern; the renderer's resolution lookup was not
reversed. Both retained pane sizes now use the former Medium pattern, with
Small resized from that master. Former Large block metadata and saved settings
load as the new Large tier, preserving tint and connected edges.

The shared **Screens** and **Controls** lists show one row for each paired
design, such as **Extra Door Controller**. **Bare/Framed** changes the artwork and
thumbnail while keeping that row selected. Existing saved variants and native
animations remain supported. Standalone designs without a matching variant do
not offer the toggle.

Custom Malisis door artwork is read from its optional component icon provider,
including separate upper/lower faces, instead of its missing baked-model
placeholder. Malisis remains optional. Ordinary door sampling skips missing
sprites and uses the existing fallback if no usable artwork is available.
Provider compatibility was checked against Malisis Doors 7.3.0/Core 6.5.1;
actual in-game appearance remains unverified.

The standard JAR also shares repeated generated model geometry through parent
templates. See [asset sizes and model compaction](../performance/1.5-assets.md)
for the measured savings and validation scope.
