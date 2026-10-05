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

Original door leaf artwork uses **128×256** pixels for Small, **256×512** for
Medium and **512×1024** for Large. These are artwork resolutions, independent of
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
