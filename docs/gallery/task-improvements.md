# Programmable block improvements

These captures show the updated blocks in the live Forge test world. See the
[task checklist](../TASKS-2026-10-02.md) for the complete requested scope.

## Connected trapdoors

Rotating and sliding groups support rectangles up to eight cells on each axis,
opposite-slope diagonal rows, and staggered full-width diagonal panels. Their
membership and configuration survive saving and copying.

| Assembly | Closed | Open |
| --- | --- | --- |
| Flat 2×4, rotating | ![Closed flat rectangle](../images/gallery/tasks/trapdoor-flat-rotating-closed.png) | ![Open flat rectangle](../images/gallery/tasks/trapdoor-flat-rotating-open.png) |
| Diagonal 5×2, sliding | ![Closed diagonal rectangle](../images/gallery/tasks/trapdoor-rectangle-sliding-closed.png) | ![Open diagonal rectangle](../images/gallery/tasks/trapdoor-rectangle-sliding-open.png) |
| Opposite slopes, rotating | ![Closed V assembly](../images/gallery/tasks/trapdoor-v-rotating-closed.png) | ![Open V assembly](../images/gallery/tasks/trapdoor-v-rotating-open.png) |
| Staggered panels, sliding | ![Closed staggered assembly](../images/gallery/tasks/trapdoor-stagger-sliding-closed.png) | ![Open staggered assembly](../images/gallery/tasks/trapdoor-stagger-sliding-open.png) |

See the [normal](programmable-trapdoor.md) and
[diagonal](programmable-diagonal-trapdoor.md) trapdoor guides for placement.

## Landing gear covers

Four independent Cover mounts close the 2×2 opening from its west and east
edges. Extending the gear slides the leaves into their mounting cells. They
stay open until retraction finishes.

| Retracted and covered | Extended and clear |
| --- | --- |
| ![Closed gear covers](../images/gallery/tasks/gear-cover-closed.png) | ![Open gear covers](../images/gallery/tasks/gear-cover-open.png) |

Follow the [gear alignment and cover setup](../landing-gear-covers.md).

## Shared materials

The same categorized picker offers built-in finishes, door artwork, lights,
static screens, filesystem PNGs and a Custom block or door sample. Custom
selection uses the sampled block's texture while keeping the programmable
block's shape.

![Material examples in the live world](../images/gallery/tasks/material-choices.png)

See [material selection](../unified-materials.md) and
[filesystem texture setup](../filesystem-textures.md). Texture transparency
does not generate geometry or collision holes; that follow-up is documented
in the material guide.

## Door material proportions

Ordinary programmable blocks use the lower square half of a door image.
Door leaves keep the complete image and their native edge textures. A sampled
block image can fit once across the leaf or tile at one-block scale.

| Door image on a block | Block image fitted to a door | Block image tiled on a door |
| --- | --- | --- |
| ![Square lower door half](../images/gallery/tasks/door-block-half.png) | ![Fitted brick door face](../images/gallery/tasks/door-face-fit.png) | ![Tiled brick door face](../images/gallery/tasks/door-face-tile.png) |

The [material picker follow-up checklist](../TASKS-material-picker-followup.md)
tracks the thumbnail, category, dialog and material fixes.
