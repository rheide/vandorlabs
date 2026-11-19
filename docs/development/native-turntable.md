# Native Turntable ownership

The development property `vandorlabs.turntableControllers` enables the
canonical controller and the block-only capture/restore journal in 1.21.1.
Empty-hand use and the Configurizer open the same native controller panel.
Set the scan height and select Scan. Set the speed in RPM and use Turn to
rotate the captured blocks by a quarter turn, then use Restore to place them
back in the world. The captured payload stays in the controller until Restore
completes. Loading a moving controller resumes its saved angle and target.
Riding and payload interaction remain open.
Programmable Surfaces are available in normal registration.

Open Redstone to set the scan and rotation modes, the initial direction and
the auto-snap tolerance. A bank triggers only when all of its channels are
powered. Empty Scan and Rotation banks use adjacent vanilla redstone. Direction
and Restore need a nonempty bank and respond once to each rising edge. A falling
rotation signal requests Restore only within the configured angle tolerance.
Signal-triggered Scan and Restore use the player who saved the Redstone
settings and require that player to be online and within reach. They use the
same permission checks and journal as the manual buttons.

The scan finds a complete loaded square from the controller's horizontal
front and extends the prism out of the Surface's textured face. Both
versions use the same prism, grid quarter-turn and rotated collision geometry. Capture admits
ordinary vanilla solid blocks, selected shaped blocks, and supported native
Signs. Unsubstantiated tile layouts and occupied volumes are refused before
mutation. Sideways quarter turns currently accept plain blocks and pillar
axes. Sign text and other admitted tile fields stay in the exact raw
snapshot; native tile factories prove a round trip before publication.

Capture and restore use the common native cell journal. The held footprint
includes the root, Surface cells, and the complete prism. A captured
controller retains its payload across saves and refuses direct destruction.
Malformed controller data remains inert with its original data retained.

The dedicated checks cover all six authored normals, an even-square
quarter turn, Sign data and opposite-heading restore. The real journal
fixture exercises saving-disabled pending admission, a caught publication
failure, retry, occupied-output refusal and successful rotated restore. The
rotation fixture checks captured collision before and after motion and verifies
that an obstacle in the sweep preserves the payload and permits retry. A
separate process saves only the controller chunk after ARMED publication and
halts; restart restores a single Sign owner before gameplay.

A connected Survival client uses the dedicated panel through both right-click
paths, sets the height and speed, and observes capture, rendering through a
quarter turn and restoration of a Sign while retaining the controller identity.
The connected fixture uses the Redstone editor, then checks a partial channel
bank, a complete bank, reversal and a signal-triggered restore.
