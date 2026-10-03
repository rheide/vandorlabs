# Diagonal wall alignment — 3 October 2026

## Requested outcome

> The goal: I would like these diagonal walls to align perfectly with each other across diagonal connections (e.g. X+1 AND Y+1 change or a Z+1 AND Y+1 change). The test cases in the screenshots illustrate the connections that should line up smoothly.

Reference images: `/home/rheide/LLMShareDrive/diag/`:

- `half-width-full-height.png`
- `full-width-full-height.png`
- `full-width-half-height.png`
- `progwall-connect-half.png`
- `progwall-connect-full.png`

Additional requirements:

> Programmable Wall blocks currently have three placements modes: far edge, middle of block, and near edge. These should continue to align perfectly with diagonal walls.

> Other blocks affected by a change in behavior are Programmable Diagonal Trapdoor and Programmable Diagonal Porthole. Any alignment changes you make should be affected in these blocks as well.

> I would like there to be test coverage for the alignment, but don't start on that until you ask me to manually confirm how it looks first.

> I don't know if this means that diagonals have to overlap in unused blocks surrounding the diagonal, but if it does, I do not want this to prevent me from placing other blocks there.

> Diagonal walls have an option to extrude the inside or outside, this should continue to work like it does now.

The owner approved a short flush transition at regular-wall junctions. The repeating diagonal centres follow the block grid, with thickness extending two pixels beyond the cell at the ends. Neighbouring cells remain available for placement.

## Implementation and review

- [x] Identify the drift: slopes used 12/16 and 6/16 instead of 1 and 1/2.
- [x] Share corrected slopes between walls, portholes, plane matching, and trapdoors.
- [x] Keep collision slices and selection/render bounds consistent with the new wall geometry.
- [x] Add short transitions to existing near/middle/far regular walls at tall diagonal ends.
- [x] Find diagonal row neighbours and suppress internal end caps.
- [x] Retain inside/outside extrusion and leave adjacent cells unreserved.
- [x] Attempt existing client regression run and record the blocking assertion below.
- [x] Owner approved the second preview: “This is excellent.”
- [x] After owner confirmation, add/update alignment coverage and run non-rendering regression checks. The focused live client suite also passed with the picker follow-ups (`testclient/render-run.H5EBpB`).

Alignment tests were deferred through both preview rounds. After owner approval, repeated-plane, neighbour-clipping, trapdoor inset, placement and open-leaf selection checks were added and passed.

## Preliminary validation

- Java 8 standard Gradle build passed.
- Existing live suite attempted with `bash testclient/test_viewscreen.sh --focus trapdoors`.
- The client loaded its world, then stopped at `Version12RuntimeChecks:147`: `diagonal collision outside block`. This assertion forbids the newly approved overhang. It remains unchanged until owner visual confirmation.
- Live log: `testclient/render-run.rHbS1X/client.log`. No visual acceptance is claimed from that run.
- At this preview stage, new alignment coverage and updates to old geometry expectations were deferred. These have now been added after owner approval.

## Second visual review: overlapping neighbours

Owner feedback: the first preview was close, but `z1.png` showed opposite diagonals overlapping at a bend, and `z2.png` showed overlap with a neighbouring block. The owner subsequently approved this second preview.

Changes for the second preview:

- Clip solid-wall and porthole polygons at occupied neighbouring full-cube boundaries, including their interpolated texture coordinates.
- Trim overhangs at opposing diagonal neighbours and remove shared end caps at bends as well as straight continuations.
- Apply the same clipping bounds to wall collision slices.
- Use loaded neighbours only; removing a neighbour restores the overhang automatically. No neighbouring cells are reserved.
- Alignment tests remained deferred until the owner confirmed the appearance; they are now included.

The new neighbour trimming currently handles full cubes and opposing diagonal walls/portholes. It does not subtract arbitrary partial-block shapes or alter moving trapdoor leaves.

Second-preview validation: the standard Java 8 build passed after the final clipping changes, and `git diff --check` passed. The live-client attempt at `testclient/render-run.bswjyK/client.log` reached the same unchanged `Version12RuntimeChecks:147` assertion. Full-cube occlusion subtracts only the overlapping region; exposed portions of a diagonal face remain visible. The preview artifact is `build/libs/vandorlabs-1.3.jar`.
