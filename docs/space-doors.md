# Space doors and glass

The Space family is independent of the Detailed doors. The creative menu has one
Space Door item. Shift-right-click either half to choose Rotating/Sliding motion,
the design, Small/Medium/Large texture detail, framed/bare appearance, placement
position, and redstone channel. Door types use a scrollable list with mouse-wheel,
scrollbar and up/down-key navigation. Each option change applies immediately,
including valid channel edits, like the programmable-screen selector. Done or
Escape closes the dialog; neither rolls back the live changes. Choices are saved in the
lower tile entity and synchronized by the server. Configuring a pair applies
to both leaves; a newly placed matching mate inherits the existing appearance.
An 80x160 native-aspect artwork preview updates with design and detail choices;
the adjacent hinge label reflects the selected motion.
Sliding doors also offer Sideways / Up / Down motion. Vertical travel is 31/16
block: framed leaves retract one pixel beyond the inner frame edge, while bare
leaves preserve their one-pixel open-edge reveal. Sideways travel is 15/16 for
single framed leaves and 16/16 for paired framed leaves; bare leaves retain 15/16.
Frames remain fixed and glazed panes move with their leaf. The renderer's
bounds cover the full vertical travel; the tile still has no tick loop.
Both motions offer Edge / Middle placement. Rotating art is edge-native and
sliding art is middle-native, so the renderer applies the corresponding 5.24/16
offset to the whole assembly, including frame, hinge geometry and collision.
Existing sliding-block saves migrate to Middle; rotating saves retain their
previous position. A middle-positioned open rotating leaf can naturally extend
beyond the back of the door block during its swing.

Fifteen designs are available: Observation, Airlock, Standard, Security, Reactor
Service, Viewport, Laboratory, Cargo, Ventilation, Cargo Lift, Blast Shield,
Glazed Hangar, Quarantine Seal, Reactor Barrier and Modular Shutter. Observation,
Viewport, Laboratory and Glazed Hangar retain their translucent windows.
Select Sliding and Up for lift-style motion; selecting artwork does not
override the current motion setting. All designs also support rotating doors.
Modular Shutter uses the existing door/pair sizes, not arbitrary-sized assemblies.
Place matching motion types with
outer hinges to join them; the inner frame rails disappear. Breaking either
restores the standalone frame. The old Space Rotating Door and Space Sliding
Door IDs, plus the previous fixed-design IDs, remain registered but hidden from
creative to preserve old worlds and inventories. Existing
legacy doors remain fixed-design; use the unified Space Door for the selector.

Assets come from `scifi_industrial_door_pack_v2.zip`,
`scifi_industrial_door_expansion.zip` and `scifi_industrial_lift_doors.zip`.
Medium detail is the new-door default:
256x512 door leaves, 512x512 frame and repeating glass, 64x64 hinge atlas.
All three independently authored sets are preserved unchanged under
`texture-packs/space-doors/{low,medium,high}`. Door sizes are respectively
128x256, 256x512 and 512x1024. All three sets ship together and are selectable
per door without rebuilding. `--detail` on the importer changes only the legacy
fixed-design artwork and item defaults. It does not remove the selectable sets.
Framed geometry crops UVs at the native texel density. It never rescales or
rewrites a PNG. Rails use nine-slice sampling of the supplied square frame.
Right leaves mirror the same texture; there are no separate double-door textures.
The client uses custom atlas sprites because vanilla 1.12 assumes non-animated
sprites are square. Native pixels occupy square atlas slots with edge padding;
UV accessors expose only the original rectangle. PNGs are never resized.
Glass mipmaps preserve low alpha instead of using vanilla's cutout threshold.
Texture reloads use the same loader.

Rotating leaves occupy the player-facing block edge, like Detailed doors.
Hinges mount on the interior rebate, with their shared pin axis at X=2.5/Z=13.5
(mirrored X=13.5 on the right). The inset keeps the open leaf inside its own block.
A 0.26-model-pixel free-edge clearance on standalone leaves prevents the corner
clipping the opposite jamb during its circular sweep; paired leaves omit that
crop and meet exactly at the centre seam. Artwork is cropped, never stretched.
Fixed mounts/pins stay stationary; the door mounts and sleeves rotate with the
leaf. All hinge cuboids are depth-scaled 2.5x around the pin axis to match the
thick leaf/frame assembly. Hinges sit at heights 6/16 and 26/16 and are included
only when Rotating motion is selected.
Bare sliders travel 15/16 block, retaining a one-model-pixel reveal when open.
Door and Space Glass frames are 4.45/16 block deep, matching Detailed Engineering.
Rotating leaf bodies are also 4.45/16 block deep, with a narrower hinge-edge
rebate to clear the jamb. Sliding leaves are 4.25/16 deep for track clearance.
Their supplied one-model-pixel border width stays unchanged. Sliding models have no hinges.
Observation metal and glass use separate opaque/cutout and translucent passes.

Space Glass uses the existing connection rules, including inner corners and
both wall orientations. Its metal frame is solid-pass geometry. A non-ticking
tile draws the repeating glass in the translucent pass with the pack's faint
alpha preserved. Glass has a per-visible-block render call but no server ticks.
Doors reuse existing animation and channel handling; pairing examines only
immediate neighbors, with no world scans or added ticking logic.

`tools/import_space_doors.py <zip> --expansion <expansion.zip> --lift <lift.zip> --detail medium` refreshes all three source
sets and regenerates the selected runtime assets. Pack instructions are archived in
`docs/space-door-pack/`. `tools/test_space_doors.py [zip]` checks density,
frame seams, hinge separation and optionally exact texture byte preservation.
The ReproLab retains the legacy door galleries and an L-shaped glass fixture.
The configurable menu has not been verified in a live client in this revision;
the requested test build skips the slow in-game overview. Offline checks cover
all design/detail model references and hinge clearance at every integer angle.
Dynmap has static simplified door/glass fallbacks, not animated joined geometry.

The supplied medium/high `double_frame_metal.png` has damaged opaque outer
pixels, consistent with previously transparent RGB data exposed by an alpha
conversion. This is present in the ZIP, not introduced by this importer. The
medium outermost row includes pure cyan, green, red and black pixels at alpha
255; high detail has a wider damaged gutter. No original conversion script was
found, so its exact origin cannot be proven. Interior cyan glow is also present,
but the frame geometry does not sample that opening.

`SpaceFramePixels` extends intact steel into the outer 1 source-pixel gutter of
medium and 8-source-pixel gutter of high, before mipmap generation. It copies
both RGB and alpha, never just forces opacity. The small texture and all pixels
inside these gutters remain unchanged. Both doors and Space Glass use this
corrected atlas sprite. CRC checks scope the repair to the exact supplied images,
leaving resource-pack replacements untouched. Original PNGs are retained
byte-for-byte. `testSpaceFramePixels` verifies recognition, every repaired pixel,
unchanged interiors and resource-pack overrides against the actual source PNGs.
There is no per-frame/tick repair cost; it runs only on texture loading/reloading.

An earlier, separate importer artifact came from sampling the hinge atlas on
extruded frame sides. Those sides now sample the frame material.
