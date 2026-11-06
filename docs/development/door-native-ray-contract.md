# Ordinary Door trace compatibility

The inactive native Door geometry adapter preserves the released Forge selection ray behavior. It traces the selection leaf even when an open sliding door has no collision box. It then considers each configured control panel in authored order. A panel hit on the control-facing side replaces the current hit even when farther from the ray origin; other panel faces replace only a strictly farther current hit. Equal distances retain the earlier candidate.

`NativeDoorGeometry.trace` accepts coordinates relative to the queried half, resolved facing/hinge/pair state, and the lower-host settings. An absent lower half uses the block prototype outline and disables panel tracing. The world adapter resolves each half before calling this method and translates the returned coordinates back to the queried block position. `LegacyBoxTrace` retains the 1.12 plane order, inclusive edge tests, inside-to-exit hits and squared near-parallel cutoff. Modern `AABB.clip` is not interchangeable with this contract.

`exportDoorRayReference` records actual `BlockConfigurableSpaceDoor.collisionRayTrace` results and asserts the released result digest. `testNativeDoorRays` compares the native adapter against those records, including both halves, cardinal facings, motion/hinge/frame/depth combinations, paired doors, panel modes, security padding and stale states after lower removal. Targeted rays prove the farther control-face priority. The Forge exporter also compares the scalar kernel with the actual legacy AABB method on deterministic arbitrary boxes and segments.

These are focused geometry checks. Door registration, world interaction routing, authored mesh emission and rendered acceptance remain separate requirements.
