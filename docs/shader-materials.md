# Shader material maps

The standard mod JAR includes a LabPBR 1.3 specular companion (`_s.png`) for
every shipped runtime texture. Companions are generated after artwork is
compacted to its runtime resolution, including animation sheets and alternate
detail levels. They use the same dimensions and animation metadata as their
diffuse artwork. Identical maps share packaged PNGs through the existing
resource aliases.
Uniform painted, matte, satin, fabric and composite finishes share maps across
different diffuse colors. Mixed bare-metal surfaces keep their artwork-specific
masks. Sharing does not resize maps or change how transparent cutouts line up.

In Complementary Unbound, select **RP Support: labPBR** in Shader Options and
enable specular maps if disabled. Integrated PBR uses the shader's block-specific
rules instead of these maps. Selecting labPBR also changes how vanilla and
other mods' materials are interpreted; install appropriate material maps for
those textures if their Integrated PBR appearance is desired.

| Surface | Finish |
| --- | --- |
| Bare alloy and steel | Brushed reflections; exposed neutral pixels use metal reflectance |
| Polished metal | Stronger, tighter reflections |
| Painted panels and machinery | Subdued dielectric highlights |
| Matte or weathered hull | Low gloss |
| Satin hull | Soft, broader highlights |
| Carpet and padding | Very low gloss, porous fabric |
| Composite and carbon-fiber panels | Restrained dielectric highlights |
| Glass panes | Smooth dielectric finish |
| Screens | Smooth protective surface |
| GUI artwork and map-only exports | Neutral material data |

Colored paint, illuminated accents and dark recesses are excluded from the
bare-metal mask. Unknown artwork receives a conservative painted finish.
All maps use LabPBR's non-emissive alpha value of 255; they add no glow or
world light. Existing light and screen rendering continues to control those
effects. Diffuse colors and transparency are unchanged.

This pass supplies material properties, without generated normal or height
maps. Brightness in an existing texture includes painted markings and baked
shadows, so interpreting it as height would create false relief.

Maps add download/storage data and are sampled by compatible shaders. They
add no geometry, gameplay ticks, packets or saved block data. They have no
visible effect with shaders disabled. PNG compression and shared files reduce
the JAR size; they do not establish GPU memory usage. Actual GPU allocation
depends on shader settings, atlas dimensions, mipmaps and which auxiliary
atlases OptiFine already allocates.

Material encoding follows the [LabPBR standard](https://shaderlabs.org/wiki/LabPBR_Material_Standard).
The generator is `tools/generate_pbr_materials.py`; `testPbrMaterials` checks
every packaged logical path, decoded pixels, dimensions and animation metadata.
The isolated `pbr-materials` client focus checks resource-manager access and
reloads with OptiFine. Those checks do not establish hardware shader appearance
or frame rate.

## Package size

The current standard package contains 1,797 logical specular companions backed
by 321 physical PNGs; 1,476 paths reuse another map. The physical companions
occupy 484,059 bytes before ZIP compression and 367,889 bytes in the JAR.
The complete JAR is about 52.60 MiB, compared with 52.01 MiB before this set
of material, door and glass changes: approximately 0.59 MiB larger (1.13%).
A companion-only comparison before the three new materials added about
0.48 MiB (0.92%). These are packaged bytes, not measured GPU memory.

The live resource check uses an 8192 by 4096 atlas. One complete RGBA8 atlas
plane at that size is 128 MiB, or about 171 MiB with a full mip chain.
OptiFine can already allocate auxiliary planes, and atlas packing/settings
change the actual result; this is a capacity calculation rather than a
measurement of additional GPU allocation. Reusing PNG files saves package
space and generation work, but does not make different atlas regions share
the same GPU storage.
