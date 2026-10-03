# Programmable lighting audit

Investigated 2026-10-03 against `47314980`, for Complementary Unbound (the owner's existing shader pack). This is an investigation: no production renderer, lighting setting, texture or block behavior was changed.

## Wall versus Porthole Wall

**These two blocks use the same base lighting technique.** Both go through `TEAnimatedScreenSelector.renderProgrammableWall`, disable fixed-function lighting, and apply one world lightmap value to their entire housing. Both have light opacity 0, emit no light, and enable Minecraft's neighbor-brightness rule. They do not use vanilla smooth lighting or ambient occlusion for their tile-rendered surfaces.

The world lightmap call is `getCombinedLight(tilePosition, 0)`. Because neighbor brightness is enabled, Minecraft 1.12.2 takes the maximum light from above and the four horizontal neighbors; it excludes the neighbor below. This is distinct from the explicit six-neighbor maximum used by the full Programmable Block housing, but does **not** distinguish Wall from Porthole Wall.

Their geometry differs: the ordinary wall has solid broad faces, while the porthole has separate frame strips, a recessed rim, and a transparent glass pass. Shader shadows and screen-space occlusion can therefore differ even with identical materials and lightmap values. Changing a glass tint is not a fix for opaque frame lighting.

Both wall types have a concrete shader-input weakness: their ordinary surfaces use `POSITION_TEX`, without explicitly supplied face normals, vertex color or vertex lightmap coordinates. Several faces also have inward winding. Disabling culling hides that winding problem in the base renderer; it does not establish correct surface orientation for a shader.

This makes them candidates for correction together. It does **not** prove which missing attribute, inherited GL state, or shader effect caused the owner's particular comparison. The isolated front faces of both wall types have the same winding problem, so that alone cannot explain a difference between otherwise identical isolated samples. A corner-connected plain wall takes an additional mesh path and has further winding inconsistencies.

## Check of other programmable blocks

The following is a source/geometry audit, not a hardware visual verdict.

| Block/family | Current world lighting path | Finding / proposed treatment |
| --- | --- | --- |
| Wall, Porthole Wall | Tile-rendered, one neighbor-brightness lightmap value; position/texture vertices | Correct face orientation and provide explicit shader inputs together. Include isolated panels, corners and joined portholes. |
| Porthole Block, Diagonal Porthole Wall | Same wall renderer; depth scaling or transformed/clipped porthole geometry | Share the missing-input problem. Include them in the wall fix; handle transformed normals and winding, especially half-height diagonals. |
| Diagonal Wall | Shader-aware format with normals, color and explicit vertex lightmap; cached geometry | Existing reference implementation. Geometry checks confirm normals/winding and distinct block/sky lightmap channels. Preserve its cache and current brightness policy. |
| Block, Slab, Storage | Cached chunk models; six-neighbor maximum brightness; ambient occlusion and diffuse shading disabled | Different flat-lighting policy is intentional. Quad face metadata is deliberately perpendicular to the geometric face to control light sampling. Review shader normal handling separately before changing this; simply enabling AO or changing the declared face could alter brightness and existing performance behavior. |
| Trigger Block; full Programmable Light | Forge's shared tile buffer, six-neighbor maximum lightmap | Shared solid meshes have inward winding and do not explicitly populate normals. Audit the shader-expanded buffer before fixing; preserve light face emission and existing batching. |
| Shaped lights: slab/frame | Legacy tile-rendered housings and artwork | Some use position/texture-only geometry. Include housing in the later shader-input pass, with emitting faces treated separately. |
| Stairs | Retextured vanilla baked stairs; retains vanilla face directions, diffuse shading and ambient occlusion behavior | A real lighting-technique difference relative to Block/Slab. Keep as a comparison/control; do not assume it needs correction because it has more shading. |
| Door | Baked item-model geometry for frame/leaf; selected material faces already have explicit normals/lightmap | Selected faces already use the stronger surface format. Optional control-panel geometry still uses position/texture-only vertices. Check frame, selected leaf and optional panels separately; no basis for replacing the whole door renderer. |
| Trapdoor, Diagonal Trapdoor | Shader-aware surface format; explicit geometric normals, vertex color and lightmap | Reference/control. Existing checks cover diagonal transformations; an additional submission check confirmed normal/winding agreement and correct separate lightmap channels for the flat leaf. |
| Viewscreen, Diagonal Screen, consoles, half consoles, diagonal half console, Input, Full Input | Most housings use legacy position/texture-only tile geometry; some display modes deliberately use full brightness | Housing has the same missing-input concern. Do not dim artwork that is deliberately emissive. Reflected ceiling variants need transformed normals and winding reviewed. |
| Programmable Glass | Baked frame plus tile-rendered translucent panes/tint | Pane geometry lacks explicit normals and vertex lightmap; both pane faces use the same winding. Separate opaque frame lighting from transparent pane/reflection behavior. |
| Blocks moved by Programmable Ramp | One lightmap value per controlled tile; explicit vertex-color shading of 1.0 top, 0.5 bottom, 0.6 X sides, 0.8 Z sides | A real technique difference: manual directional shading, with no submitted normals. Compare the source block and moving version; do not blindly add another layer of directional shading. Moving geometry uses anchor-cell light rather than sampling every displaced surface. |
| Landing gear | Imported baked models use brightness rendering; extending arm uses position/texture-only quads; light sampled at anchor | Mixed submission paths. Check the arm versus fixed model and wheel, including extension into differently lit cells. Candidate for the later shader-input pass. |

## Actual geometry observations

Ran the existing Java/Forge geometry emitters with a synthetic atlas sprite and no GL context. These are measurements of the emitted data, rather than a reimplementation of the mesh in a script. `front` below means the local near face at z=6; `top` is y=16. Counts cover these particular fixtures, not every possible assembly.

| Fixture | Submitted quads | Inward front quads | Inward top quads | Explicit normals / vertex lightmap |
| --- | ---: | ---: | ---: | --- |
| Isolated Wall | 6 | 1 | 1 | Neither |
| Hexagonal Porthole Wall | 22 | 6 | 1 | Neither |
| Octagonal Porthole Wall | 28 | 8 | 1 | Neither |
| Square Porthole Wall | 16 | 4 | 1 | Neither |
| Round Porthole Wall | 66 | 18 | 1 | Neither |
| Corner-connected Wall | 12 | 2 | 2 | Neither |

The corner fixture also emitted one inward back-face quad. The audit does not count every rim-orientation problem or assess shader-generated tangent data. These findings are sufficient to rule out treating either wall's existing mesh as a universally correct shader reference.

Controls passed: diagonal-wall normals, outward winding, UV bounds and packed lighting; flat trapdoor normal/winding agreement and separate block=80 / sky=192 lightmap values. Wall and Porthole Wall propagation settings were also checked at runtime and matched.

The existing `testNonRendering` suite also passed, including cached wall/door vertex equivalence and live-lightmap contracts. No live rendering suite was run because production rendering was not changed, and the available launcher cannot validate the owner's hardware shader appearance.

Reproduce from the repository root:

```sh
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew \
  -I testclient/lighting_audit.gradle auditProgrammableLighting --no-daemon
```

## Recommendation before editing lighting

1. Start with **Wall, Porthole Wall, Porthole Block and Diagonal Porthole Wall together**. Supply outward winding, correct geometric normals, white vertex color, and explicit sky/block lightmap coordinates using the existing shader-aware surface format. Retain the current brightness selection and transparency/tint behavior initially. Reuse/calculate static geometry once where practical; do not add world light lookups per vertex or per-frame ambient-occlusion calculations.
2. Review **screen/console/input housings, shaped lights, transparent glass, ramp geometry, landing-gear arms and the door control panels** as a separate pass, because they have related submission weaknesses but different emission, transparency or motion rules. Keep cached cube/slab/storage and fast solid models as a separate shader investigation rather than changing their lighting policy incidentally.
3. Leave stairs, the already corrected diagonal wall, selected door surfaces and trapdoor surfaces as controls unless a matching-material hardware comparison identifies a problem in those paths.

Before accepting a fix, compare matching materials under Complementary Unbound on hardware: all four horizontal orientations; front/back/top views; daylight, enclosed torch light and light from below; isolated and corner-connected walls; joined portholes with clear/tinted glass; all diagonal modes and inversion. Include nearby programmable Block/Slab/Stairs/Door/Trapdoor controls, and check render order by changing the camera angle. Also check the same scene without shaders so a shader correction does not introduce a base-renderer regression. Run the live client suite for any subsequent renderer change.

**Hardware appearance remains unverified.** The available headless launcher forces software rendering; the exposed DRM device uses `vmwgfx`, and no local copy of the owner's shader pack was found in the inspected client/share directories. No software-rendered images are being presented as Complementary Unbound acceptance. This audit changes diagnostic files and documentation only; changing lighting across these families requires the owner's go-ahead.

## Code references

- [Shared wall renderer, light sampling, flat wall and rim helpers](../src/main/java/com/vandorlabs/client/TEAnimatedScreenSelector.java)
- [Wall block and neighbor-brightness policy](../src/main/java/com/vandorlabs/blocks/BlockProgrammableWall.java)
- [Porthole transformation and vertex emission](../src/main/java/com/vandorlabs/client/DiagonalPortholeMesh.java)
- [Shader-aware surface format](../src/main/java/com/vandorlabs/client/BlockSurfaceFormat.java)
- [Cached Block/Slab/Storage models](../src/main/java/com/vandorlabs/client/ProgrammableHousingModel.java) and [light sampling](../src/main/java/com/vandorlabs/blocks/ProgrammableHousingState.java)
- [Stair model](../src/main/java/com/vandorlabs/client/ProgrammableStairsModel.java), [shared solid renderer](../src/main/java/com/vandorlabs/client/ProgrammableSolidRenderer.java), [door](../src/main/java/com/vandorlabs/client/TESlidingDoor.java), [trapdoor](../src/main/java/com/vandorlabs/client/TEProgrammableTrapdoor.java), [glass](../src/main/java/com/vandorlabs/client/TEProgrammableGlass.java), [ramp](../src/main/java/com/vandorlabs/client/TEControlledRamp.java), [landing gear](../src/main/java/com/vandorlabs/client/TELandingGear.java)
- [Reproducible diagnostic](../testclient/java/com/vandorlabs/client/ProgrammableLightingAudit.java)
