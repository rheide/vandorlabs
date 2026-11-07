# Ordinary programmable glass native boundary

Ordinary programmable glass retains its own settings format, independently of diagonal panel glass. Configured items keep the `ProgrammableGlassSettings` envelope; generic block-entity data is merged before that envelope is applied. Invalid layouts are refused before placement. Picking copies the current settings, while ordinary harvesting yields one unconfigured item. Failed placement rollback retains one inert, recoverable owner.

Player bucket interactions insert and extract contained fluid on either side through the shared contained-fluid interaction handler. Volume checks apply to player insertion; placement captures the original boundary fluid even when that side has no currently visible gap. Ordinary glass deliberately does not implement a vanilla liquid-container interface: dispenser-style insertion remains refused, and flowing water or lava cannot replace its solid glass material.

Default authored frame rails belong to the terrain model. Pane surfaces and selected or bent frames use the block-entity renderer and their separate transparency passes. Both consume the same immutable publication prepared on the owning thread. Geometry, ray tracing and contained-fluid regions remain independent of cosmetic rendering. Public registration is held until placed-world rendering and editor acceptance are complete.
