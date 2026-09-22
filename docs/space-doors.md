# Space doors and glass

The Space family is independent of the Detailed doors. The creative menu has one
Space Door item, defaulting to Sliding Sideways in the Middle position.
Shift-right-click either half to choose Rotating, Sliding Sideways, Sliding Up or Sliding Down,
the design, Small/Medium/Large texture detail, framed/bare appearance, placement
position, Trigger mode, and redstone channel. Door types use a scrollable list with mouse-wheel,
scrollbar and up/down-key navigation. Each option change applies immediately,
including valid channel edits, like the programmable-screen selector. Done or
Escape closes the dialog; neither rolls back the live changes. Choices are saved in the
lower tile entity and synchronized by the server. Configuring a pair applies
to both leaves; a newly placed matching mate inherits the existing appearance.
An 80x160 native-aspect artwork preview updates with design and detail choices;
The Hinges button below the preview toggles both fixed and moving hardware for
rotating doors without changing their leaf, pivot or frame. It is disabled for
sliding motion, preserving the last rotating choice. Existing doors default to
hinges on. This preference is saved, synchronized to paired leaves, inherited
by new mates and included in creative pick-block items. Trigger cycles through
Disabled, Redstone ON and Redstone OFF. Disabled preserves manual right-click
control and ignores redstone for opening. ON opens while signaled; OFF opens
while unsignaled. In either redstone mode, right-click does not toggle the door.
The selected mode applies immediately, including to paired leaves, and is
saved and copied with creative pick-block. Older doors default to Disabled.
A three-pixel Control Buttons pad sits on the inward face of a jamb,
between the hinge positions. It stays inside its own block at every door
position and opens this dialog when clicked without sneaking. Rotating doors
use the hinge jamb; sliding doors use the opposite jamb. A paired rotating
door has one pad on the visual-left outer jamb; a paired sliding door has one
on the visual-right outer jamb. A door with neighbors on both sides has no pad.
The dialog's Panel: On/Off button sits
below Hinges and applies immediately to both leaves. Existing doors default
to Panel: On; the choice is saved and included in creative pick-block items.
Creative pick-block on either half copies all selector settings, including the
channel, into the item. Placement restores these settings before considering
neighbor inheritance; it does not copy coordinates, power or open state, or
reconfigure the neighbor. Ordinary unconfigured items still inherit a matching mate.
Vertical travel is 31/16
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
Select Sliding Up for lift-style motion; selecting artwork does not
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

Edge-positioned rotating leaves occupy the player-facing block edge.
Hinges use the original thin-door hardware, with one pixel removed from the
frame-side mounting arm. The whole hinge and pivot move one pixel toward the
jamb: the shared pin axis is X=1/Z=11.24 (mirrored X=15 on the right), with
the door mount against the leaf's inner face at Z=12.24. Other hinge dimensions
are unchanged. The leaf is one rectangular cuboid, without a rebate.
Standalone framed leaves span the entire 14-pixel opening, with no free-edge
crop. A shallow relief inside the opposite jamb clears the swinging
corner instead. The jamb uses two closed cuboids: a rear pocket and a full-width
one-pixel-deep front stop. All six faces are retained, including exposed step
shoulders. The former sixteen-strip version omitted these shoulders and could
show the background when viewed obliquely from inside. Paired leaves still meet exactly at the
centre seam. Artwork is cropped at native density, never stretched.
Fixed mounts/pins stay stationary; the door mounts and sleeves rotate with the
leaf. Hinge cuboids are not stretched to the leaf depth. Hinges sit at heights
6/16 and 26/16 and are included
only when Rotating motion is selected.
Bare sliders travel 15/16 block, retaining a one-model-pixel reveal when open.
Door and Space Glass frames are now exactly 4/16 block deep (previously 4.45/16).
Rotating and sliding leaf bodies are uniformly 2/16 block deep, centered with
one pixel on either side. Rotating leaves occupy Z=12.24..14.24 inside
Z=11.24..15.24 frames; sliding leaves occupy Z=7..9 inside Z=6..10 frames, preserving the same
5.24/16 assembly offset for Edge/Middle. With surface-mounted
hinges, the fully open rectangular leaf tip can project 3.76/16 beyond the
block's front edge; open collision bounds follow the actual slab rather than
trimming its geometry.
Their supplied one-model-pixel border width stays unchanged. Sliding models have no hinges.
Observation metal and glass use separate opaque/cutout and translucent passes.

Space Glass has three creative blocks: Small, Medium and Large. The existing
`space_glass` id remains Medium for save compatibility; `space_glass_small` and
`space_glass_large` use the low/high assets. Both frame and shimmer use the
selected native tier. Mixed tiers still connect, including inner corners and
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
Offline checks cover all design/detail model references, constant-thickness
2px rectangular leaves centered in 4px frames, full-width singles, shortened hinge mounts and surface
contact, six closed faces on every jamb cuboid, sealed paired seams and jamb clearance at every
integer angle. Live checks exercise default
placement, saved settings and 2,880 upper/lower pick-and-place combinations,
including both hinge choices and precedence over a differently configured
adjacent door. Mixed glass tiers are checked in both wall orientations; live
fixtures show all three tiers, hinges on/off and the updated door dialog. Four live
inside-frame screenshots look sideways at both jambs from both depth edges.
A magenta backing wall makes missing faces visible; the pixel check rejects any
backing pixels between the jamb edges across every interior scanline (excluding
two silhouette pixels for antialiasing), and checks the backing is present
outside it. The gallery
checks rendering; it does not automate interaction with the configuration GUI.
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
