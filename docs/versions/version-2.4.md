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
