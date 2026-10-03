# Programmable shader lighting corrections

Owner request: "Ok, fix your findings. Will this have a performance impact?"

Context: compare Programmable Wall and Porthole Wall under Complementary Unbound; inspect other programmable blocks before changing lighting. The owner authorized corrections after the audit in [programmable-lighting-audit.md](../programmable-lighting-audit.md).

- [x] Record the existing geometry/lighting findings and a reproducible diagnostic.
- [x] Capture rendering-cost baseline before production edits.
- [x] Correct plain/porthole walls and transformed porthole variants, with explicit lightmaps and outward normals/winding.
- [x] Correct related screen/console/input housings and shaped light geometry, preserving emitting artwork.
- [x] Review cached solid models and Forge-batched solids; preserve chunk caching, batching and brightness rules.
- [x] Correct transparent panes, controlled ramp geometry, landing-gear arms and door control panels where the audit identified missing shader inputs.
- [x] Verify geometry, winding, normals, lightmap channels and existing UVs/motion.
- [ ] Build the standard Java 8 mod JAR and run relevant live client coverage.
- [x] Compare rendering cost and document measured impact and hardware shader validation limits.
- [x] Commit completed implementation and validation evidence separately from the investigation; full live validation is recorded in a subsequent checkpoint.

Hardware shader appearance requires the owner's hardware client. The available software launcher may check base-renderer behavior and relative submission costs; it must not stand in for Complementary Unbound visual acceptance.

Follow-up request: "Did we actually remove the textures from the build that are no longer selectable? No need to keep those in"

- [x] Remove all sixteen retired built-in PNGs from the standard JAR and atlas while retaining required/visible shared artwork and source archives.
- [x] Preserve numeric saved choices with default-material fallback; retarget legacy item models and Dynmap aliases.
- [x] Inspect the actual packaged assets and verify all sixteen saved choices and their fallback/persistence in the live client.

Final request: "Cool, when done, make sure changelog and 1.3 md file is up to date, commit and push"

- [x] Update `CHANGELOG.md`, `docs/gallery/version-1.3.md` and the [correction/performance report](../performance/SHADER_LIGHTING_FIXES.md).
- [ ] Finish the full live client suite and record its result.
- [ ] Commit the completed work and push to `origin/master`.

Owner regression report: "Whatever you did, the programmable ramp is completely broken now when extending the ramp, blocks disappear and all I see are flickering strips of black and blue"

- [x] Reproduce corrupted ramp color/UV packing through the actual production emitter; the added regression check fails before the correction.
- [x] Correct ramp vertex attribute order to match the new format, retaining opacity, source UVs and directional shading.
- [x] The corrected standard build, lighting diagnostic and full non-rendering suite pass; the production-emitter check covers all six faces, two tints and four slice thicknesses.
- [ ] Verify the correction with the packed-vertex check and a fresh live run including deployed ramp modes.
