# Vandor Labs 2.4

Version 2.4 focuses on dedicated-server stability, safe saved-data recovery and
smaller vehicle movement packets. See the [changelog](../../CHANGELOG.md) and
[detailed server compatibility notes](../server-compatibility.md).

## Dedicated-server fixes

Large Front Canopy Glass and the other canopy types use server-compatible
collision bounds. Existing saved canopies load automatically without block
removal or world conversion. Contained-fluid geometry, escalator landing steps
and turntable collision also avoid client-only Minecraft bounding-box APIs.

Canopy, Autopilot, cockpit and credential-reader initialization waits until chunk
tile loading has finished. Saved canopy poses and footprint offsets are bounded.

## Cockpit animation

Cockpit screen-bank and sliding-seat movement, rider visuals and display playback
use a pause-aware client clock. Remote server time corrections cannot rewind or
skip visual motion, and repeated settings packets or replacement vehicle tiles do
not restart it. Collision, reservations and saved motion remain server-authoritative;
no extra animation packets are needed.

## Vehicle discovery and rendering

Vehicle discovery follows actual overhanging diagonal-wall and canopy contacts,
including ceiling slabs outside the usual six-neighbour attachment path. It
excludes corner-only contacts and retains slab separation from hangar floors.
New Taxi/Hover conversion requires a dedicated Vehicle Controller; Vehicle Status
reports **Vehicle Controller missing** when absent. Legacy captured craft retain
their safe parking recovery path.

Vehicle meshes retain their build vertex format and shader attributes, and rebuild
when the active format changes. This corrects the hardcoded vanilla vertex stride
that interpreted extended OptiFine shader records as positions. Hardware shader-pack
appearance remains a separate compatibility check.

Filled diagonal walls retain exposed top and bottom caps at flat-wall contacts.

## Ramp controllers

Ramp Controller active faces can scan above or below while deployment stays
horizontal. Existing horizontal placement metadata remains compatible. Ramp
Controller Slab provides the same modes and dialog with top/bottom slab placement,
new half-height side panels and padded configured item icons. Top slab placement
keeps the empty lower half disconnected from the ground during vehicle discovery.

## Cockpit controls

Cockpit Redstone displays use square-pixel text and aligned control buttons. Clicked
screens and controls select their own configuration page. Throttles support custom
Low/High limits and four detents, with a saved Single/Twin layout. Vehicle Status
omits Autopilot controls when no Autopilot Controller is present.

## Recovery protection

Vehicle and turntable snapshot installation failures leave the entity inert and
preserve its original component data. Vehicle recovery refuses to overwrite
changed blocks, newer inventory contents or an unrelated entity UUID; the recovery
journal remains available when a conflict blocks recovery.

## Network compatibility

Vehicle movement state payloads decrease from 105 to 82 bytes, approximately
22 percent, preserving numeric precision, steering, timing and input acknowledgements.
This measures payload size, rather than total network traffic or frame rate.
Install matching 2.4 builds on the server and clients.

## Regression coverage

The build checks shared bytecode for client-only APIs, deferred load callbacks,
failed snapshot retention, recovery conflicts and malformed movement packets.
Physical-server checks exercise canopy and contained-fluid fixtures through entity
ticks, saving and restarting. Live client checks include 44 saved-canopy collision
cases. Release validation additionally requires diagonal geometry, glass/contact
frames, gameplay and fluid regressions against the same standard JAR.
