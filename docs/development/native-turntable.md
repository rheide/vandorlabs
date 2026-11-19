# Native Turntable ownership

The development property `vandorlabs.turntableControllers` enables the
canonical controller and the block-only capture/restore journal in 1.21.1.
The current checkpoint is exercised by the dedicated fixture. Ordinary
controller interaction, continuous rendering, riding and redstone motion
remain open. Programmable Surfaces are available in normal registration.

The scan finds a complete loaded square from the controller's horizontal
front and extends the prism out of the Surface's textured face. Both
versions use the same prism and grid quarter-turn policies. Capture admits
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
failure, retry, occupied-output refusal, and successful rotated restore. A
separate process saves only the controller chunk after ARMED publication and
halts; restart restores a single Sign owner before gameplay.
