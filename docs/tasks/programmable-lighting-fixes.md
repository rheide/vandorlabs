# Programmable shader lighting corrections

Owner request: "Ok, fix your findings. Will this have a performance impact?"

Context: compare Programmable Wall and Porthole Wall under Complementary Unbound; inspect other programmable blocks before changing lighting. The owner authorized corrections after the audit in [programmable-lighting-audit.md](../programmable-lighting-audit.md).

- [x] Record the existing geometry/lighting findings and a reproducible diagnostic.
- [ ] Capture rendering-cost baseline before production edits.
- [ ] Correct plain/porthole walls and transformed porthole variants, with explicit lightmaps and outward normals/winding.
- [ ] Correct related screen/console/input housings and shaped light geometry, preserving emitting artwork.
- [ ] Review cached solid models and Forge-batched solids; preserve chunk caching, batching and brightness rules.
- [ ] Correct transparent panes, controlled ramp geometry, landing-gear arms and door control panels where the audit identified missing shader inputs.
- [ ] Verify geometry, winding, normals, lightmap channels and existing UVs/motion.
- [ ] Build the standard Java 8 mod JAR and run relevant live client coverage.
- [ ] Compare rendering cost and document measured impact and hardware shader validation limits.
- [ ] Commit completed implementation and validation evidence separately from the investigation.

Hardware shader appearance requires the owner's hardware client. The available software launcher may check base-renderer behavior and relative submission costs; it must not stand in for Complementary Unbound visual acceptance.
