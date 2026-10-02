# Programmable block improvements

These captures show the programmable blocks, material pickers and connected trapdoors in the live Forge test world.

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

Four independent Next-block mounts close the 2×2 opening from its west and east
edges. Extending the gear rotates the leaves upright into their mounting cells. Sliding remains a separate movement choice. They
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

The categorized lists use large, aspect-preserving thumbnails, alphabetic categories and entries, and a category heading that stays visible while scrolling. Native screens keep their animation controls. Programmable Light lists show the On artwork and switch to Off automatically with the light state.

## Trapdoor door artwork and controls

Tile / mirror keeps door artwork one block wide and two blocks long/high, with alternating columns mirrored. Fit stretches one image over the connected surface. Custom vanilla and mod `BlockDoor` items retain their upper/lower sprites. Both layouts keep the standard artwork on the four thin edges.

| Surface | Built-in door, Tile / mirror | Custom oak door, Tile / mirror | Built-in door, Fit |
| --- | --- | --- | --- |
| Flat 2×2 | ![Tiled flat doors](../images/gallery/tasks/trapdoor-flat-door-tile.png) | ![Tiled flat oak doors](../images/gallery/tasks/trapdoor-flat-custom-door-tile.png) | ![Fitted flat door](../images/gallery/tasks/trapdoor-flat-door-fit.png) |
| Diagonal 2×2 | ![Tiled diagonal doors](../images/gallery/tasks/trapdoor-diagonal-door-tile.png) | ![Tiled diagonal oak doors](../images/gallery/tasks/trapdoor-diagonal-custom-door-tile.png) | ![Fitted diagonal door](../images/gallery/tasks/trapdoor-diagonal-door-fit.png) |

The diagonal dialog separates width and height, so Full/Half width updates a tall group together. The normal dialog separates closed-leaf placement from Rotating/Sliding and offers a direction control for Next block mounts.

| Normal controls | Diagonal controls |
| --- | --- |
| ![Next-block placement and independent rotating movement](../images/gallery/tasks/trapdoor-config.png) | ![Separate diagonal width, height and texture layout](../images/gallery/tasks/diagonal-trapdoor-config.png) |

## Motion clearance and next-block placement

Rotating normal leaves keep their thickness inside the mounting cell when open. Sliding diagonal leaves lift clear of the continuation wall before moving sideways. Next block places the untriggered leaf across the neighboring cell with a one-pixel overhang beyond its far edge; rotating then folds it upright into its own mount. Normal and diagonal leaf edges use the standard programmable door’s metal side artwork.

| Rotating beside blocks | Sliding over a diagonal wall |
| --- | --- |
| ![Open rotating leaf beside solid neighbors](../images/gallery/tasks/trapdoor-flat-rotate-neighbors.png) | ![Open diagonal leaf clear of its continuation wall](../images/gallery/tasks/trapdoor-diagonal-slide-wall.png) |

| Next-block leaf, closed | Next-block leaf, rotating open |
| --- | --- |
| ![Closed leaf covers the neighboring cell](../images/gallery/tasks/trapdoor-next-rotating-closed.png) | ![Rotated leaf folds back into its mounting cell](../images/gallery/tasks/trapdoor-next-rotating-open.png) |

## Flush floor and ceiling mounts

Closed Bottom and Top leaves sit flush visually against their support, with only a 1/64-pixel inset to prevent coplanar faces. Rotating leaves retain clearance from their supporting floor or ceiling throughout opening.

| Floor mount | Ceiling mount |
| --- | --- |
| ![Closed trapdoor sits flush on a floor](../images/gallery/tasks/trapdoor-flush-floor.png) | ![Closed trapdoor sits flush beneath a ceiling](../images/gallery/tasks/trapdoor-flush-ceiling.png) |

## Next-block selection and joined-group protection

Individual Next block mounts can be selected at the leaf's actual position after changing the hinge. Collision follows the shifted leaf in both open and closed states. Clicking the visible leaf operates its owning trapdoor.

| Closed offset leaf and selection outline | Rotating open leaf and selection outline |
| --- | --- |
| ![Selection follows the closed leaf into the neighboring cell](../images/gallery/tasks/trapdoor-offset-closed-selection.png) | ![Selection follows the open leaf after changing the hinge](../images/gallery/tasks/trapdoor-offset-open-selection.png) |

Joined trapdoors retain This block placement. The dialog disables the placement switch, and server configuration and Duplifier copies preserve the group if they request Next block.

![Joined trapdoor placement control is disabled](../images/gallery/tasks/trapdoor-joined-config.png)

For an assembly split by an older version, follow the recovery instructions in the [trapdoor guide](programmable-trapdoor.md).

These are software-rendered Forge regression captures. Hardware shader appearance still needs verification in the target client.
