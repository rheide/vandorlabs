# 2.8 compatibility and performance review

The review compares 2.8 with the 2.7 release, including saved blocks and inventories, portable vehicle and turntable snapshots, recovery records, network extensions, client animation, collision queries and retained rendering geometry. A passing release check establishes the exercised cases; it cannot prove that every world, resource pack or third-party capability is safe.

## Saved-world compatibility

The renamed Diagonal Half Screen retains the old block and item aliases, metadata and tile identity. Ordinary 2.7 artwork and newer inserted-reader data need separate upgrade fixtures: 2.7 did not have built-in chip readers. Native upgrade tests save worlds with the previous standard JAR, upgrade nearby blocks, restart, then first load untouched distant chunks and restart again. Glass tests retain vanilla neighbour colours, inventory counts, names and dropped items.

Retired cockpit glass converts through explicit mappings to vanilla glass, cyan stained glass and gray stained glass. Native numerical block mappings are durably retained independently of the registry snapshot so a later-loaded chunk can still recover its original colour. Portable palettes, ramp source materials, nested payloads and both Vehicle Chip codecs use the same named replacements.

Two saved-data defects were reproduced and corrected during review:

- The glass material walker could traverse `ForgeCaps` and rewrite a provider's opaque material references. It now leaves capability state outside the owned material schema, matching the existing rename boundary. The regression checks both capability equality and preservation of the original input NBT while still requiring migration of the ramp's own material.
- Minecraft's defaulted block registry returns air for an unavailable name. The legacy material decoder now checks registry membership before lookup, preserving refusal of unknown blocks and properties while accepting explicitly saved air.

No listed block-state property was added to existing chair states: the new angle is unlisted render state and saved tile data. Absent angle and ramp-axis fields retain their previous defaults. Existing packet operation indices remain in place; new operations append to them. Articulated movement keeps the original 82-byte state packet for ordinary rigid vehicles. A three-joint update uses 108 bytes, with count validation before allocation and exact round-trip checks.

## Ownership and failure recovery

Vehicle and turntable installation failures retain their original snapshot NBT and leave the entity unavailable rather than installing a partial live snapshot. Articulated vehicles also retain an invalid original joint-pose record alongside the craft. Unknown blocks and unsupported tile data remain refusals; they must not be discarded to make a conversion succeed.

Vehicle preparation holds source tiles while refreshing their data, then hands the hold to the durable transfer. A pending journal write yields and retries; it is not a rejected gameplay operation. Live source contents, authorization, loaded boundaries and hardness are rechecked before publication even when immutable preflight data is reused. Inventory and block ownership still publish through the existing recovery journal and save checkpoints.

The review also reproduced unloaded-chunk reads in scan-hold acquisition and conversion-indicator publication, plus a leaked pause lease when a later tile lookup failed. Acquisition now gathers only loaded source tiles before publishing any leases. Optional controller status and preview publication also check loaded state, and an old controller cannot publish through its replacement. Failure tests require no unloaded tile reads and no retained lease; overlapping successful holds must resume the tile exactly once after the final release.

Recovery validation must include abrupt termination and partial chunk saves as well as caught exceptions. Exactly one usable inventory owner is the required result after restart. A corrupt or unavailable recovery record is retained for recovery; a stopped transfer can require intervention rather than silently replacing unknown data. These safeguards reduce world-damage risk without guaranteeing that a failing external tile constructor or storage device cannot interrupt loading.

Shared gameplay code is checked against dedicated-server API availability. A separate physical-server test exercises explicit VehicleWorld chunk requests with Thaumic Augmentation, Thaumcraft, Baubles and previous-release controller NBT. VehicleWorld remains a read-only view whose capabilities initialize against its physical parent world.

## Performance findings

The existing [vehicle measurements](performance/2.8-followup.md) establish lower terrain allocation and faster scan completion, but do not establish an overall shader FPS improvement. Render-thread allocation increased in those whole-scene comparisons, including native control scenes. Treat that as an unresolved measurement finding rather than claiming the release is uniformly faster.

Fresh paired density checks exercise the old and optimized paths in one client, assert visible output and retain CPU/allocation counts. An 841-block Surface floor reduces submissions from 1,682 to one and measures 5.35 ms versus 0.97 ms in the frozen-candidate run. All six ordinary-pane orientation/depth cases improve in that run; 216 panes share one submission. The 78-cell stationary-ramp fixture measures 4.25 ms versus 0.57 ms and reduces allocation from about 4.65 MB to 14 KB per draw. One glass orientation was slower in the initial sample, but that result did not repeat. These software-rendered measurements do not establish hardware FPS or shader-pack acceptance.

New features also have real costs:

- Articulation performs additional terrain, swept-pose and self-collision checks. Default rigid vehicles bypass articulated shape collection. A four-section train fixture measures the additional work and verifies collision, saved poses, independent yaw/pitch and stale/repeated network updates.
- Scan scheduling targets four two-millisecond work slices per world tick, but cannot interrupt individual mod callbacks. Existing full-size measurements still show preparation ticks above 50 ms and a synchronous native-block publication phase around 200 ms. The release does not promise an eight-millisecond conversion ceiling.
- Larger Surface areas permit up to 1,024 cells, increasing bounded traversal and publication work. Turntable contents retain the separate 4,096-block limit. Editing many populated displays can still trigger substantial validation and rendering work.
- Retained geometry trades memory for fewer builds. Variable meshes use byte budgets, including 64 MiB for carried opaque wall payloads, 16 MiB for Sectional Door column records, 8 MiB each for stationary ramps, Surface artwork and chip previews, and smaller panel/template caches. Format, light, artwork, resource and world lifecycle changes invalidate the relevant caches.

Further performance changes should follow profiling of populated hardware shader scenes and large-train collision workloads. Spreading atomic inventory publication over ticks without durable partial-save recovery would weaken ownership safety.

## Release checks

The release gate includes portable geometry matrices, dedicated-server linkage, load and packet boundaries, real shader startup/reload, rendered glass/contact regressions, fluids, machines, vehicle and arm gameplay, and the new 2.8 Sectional Door, articulation, ramp-axis, screen-chip, furniture and density checks. Optional installed-mod checks and native previous-release/cold-restart upgrade tests complement that gate.

The complete Java 8 gate passed on the final standard JAR, including all 48 rotating-endpoint combinations, delayed rider updates, shared Surface rebuilding, saved generated vehicle ramps, Taxi and installed Mekanism tank cold restart/reformation. Separate 2.7 native-world upgrade checks passed for nearby and untouched distant glass and renamed-screen chunks. Eight abrupt-termination/partial-save scenarios passed across arm endpoints, painting transfers and coupling ownership. The catalog check retains a pinned 512-ID 2.7 manifest and permits only the three glass removals and the Half Screen rename; unrelated removals, renames, reordering and duplicate IDs remain failures.

Windows focus routing is checked for a JVM property and completion marker for every advertised focus. Linux execution does not establish a native Windows runtime result. Journal directory sync continues through the platform-aware `JournalFiles` path; genuine Linux I/O failures remain errors.


## Follow-up behavior and recovery checks

Sectional Door opening and glass choices are now per block; shared artwork, travel, placement and redstone settings remain common to the linked door. Legacy settings omit the new position field and load in Middle placement. Existing settings packets retain their legacy decoder; local edits use a distinct operation. Visible-panel interaction packets are fixed at 64 bytes and include the current motion identity, sequence and displayed pose. Replay, malformed length, non-finite pose, obstruction, loaded-source and permission checks remain server-side. Cosmetic motion can lead a slow server during the current stroke without changing authoritative collision.

The live five-by-four fixture changes only its top row to windows and closes the door with a real client click on a moved middle panel. It also sends a real bucket interaction, observes liquid rendering and native open-door flow, then closes the moved panel and checks that outlets drain while the stored source remains. The renderer matrix covers 11,520 cached/reference cases across positions, directions, angles, openings and vanilla/extended vertex formats. Default settled opaque panels retain terrain rendering; transparent Frame artwork stays out of the solid terrain path. Water uses the existing independent contained sides and reversible native outlets. Dry motion avoids scheduling fluid updates or invalidating unrelated fluid geometry, and unchanged members do not publish redundant settings updates. Carried-door selection unwraps the physical parent world before choosing cosmetic or authoritative motion.

Surface regression coverage includes actual Better Builder’s Wands packets for 7×1 to 7×2 to 7×3 growth, saved drawing-root/member removal, temporary holes, expansion, all 24 orientation lifecycles, and output-only chip-mode pads on all six normals. Wand copies carry artwork without copying a stale shared renderer origin or controller claim.

The straight-driving regression starts with a two-degree trailer bend and advances the native movement path 100 times on flat ground. Before the subdivision fix, two sections travelled 5.4 blocks and repeatedly reset speed. With the fix, one, two, four and eight sections each travel 21.644 blocks and retain the 0.23-block/tick unpowered Taxi speed. The frozen-candidate physics medians were 0.062, 0.313, 0.909 and 2.376 ms respectively; these small-hull measurements do not establish costs for arbitrary dense vehicles or hardware rendering performance. The allocation-free separating-axis implementation matches 100,000 previous-implementation cases. New assemblies are limited to eight independent segments; existing longer saved vehicles still load for recovery.

Parking accepts bounded small joint angles and cumulative displacement without modifying the live pose during preflight. Its durable recovery record now retains the original joint pose, including after partial native publication. Native recovery checks require exactly one world vehicle, no remaining native hull and the original trailer angle. Controller scans select the controller in either piloted physical segment while retaining the other controller’s saved block; duplicates in the same segment and a missing controller in the piloted segment still refuse.

The iron-ramp rendering regression checks the actual emitted UVs against the iron atlas sprite. Filled Y-layout geometry previously escaped into neighbouring atlas entries; corrected repeats remain within the source sprite. The same live fixture passes with OptiFine G5 and Complementary Unbound loaded.

Follow-up validation follows the changed player paths: Sectional Door dialog, local openings, placement, moved-panel interaction and fluids; Surface wand growth and Copy Controller pads; piloted coupler-controller selection, Taxi movement and parking recovery; Track steering; and ramp atlas sampling. The full release gate also revisits arm endpoints, joined arms, shared Surface roots, saved generated vehicle ramps, previous-version vehicle data, glass/contact rendering and Mekanism cold restart. The isolated Dynmap check uses the exact standard JAR with WorldEdit installed. These checks complement the earlier native upgrade and abrupt-restart cases; they do not establish native Windows execution or whole-world hardware FPS.

The follow-up candidate passed all 82 Java 8 `verifyRelease` tasks, including all 48 rotating-endpoint configurations and installed Mekanism cold restart. The separate exact-JAR Dynmap check passed with 546 rendered PNG tiles and the same artifact hash. The validated standard JAR SHA-256 is `f6115dd99fa87fec643af8a6ceb4d1ef59e5256a64b9be9e5470a8435ba793ba`. See [running release checks](release-checks.md) for the repeatable command, dependencies and evidence retention.
