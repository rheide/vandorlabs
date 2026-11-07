# Screen chip interaction reference

The 1.12.2 callback reference is frozen in `testclient/fixtures/screen-chip-interaction-reference.json.gz`. Its uncompressed SHA-256 is `e96c73f26be0835e34471f69eb05fb3af8f3a8c8a029c53d882699aa33e46cd7`. `exportScreenChipInteractionReference` runs the actual released Screen, reader and journal methods in an in-memory world. It does not establish network delivery, vanilla block selection, claims integration, durable inventory transfer or a live player interaction.

The schema contains these independent observations:

| Section | Cases | Evidence |
| --- | ---: | --- |
| `tray` | 13,440 | Actual `ScreenChipSupport.activate` and block activation callbacks across four integrated families, valid saved facings/halves, ceiling positions, both hands and server/client prediction |
| `pages` | 2,240 | Actual page callbacks, row/column helpers and shared `ScreenControlLayout` samples with capacity twelve, including row gaps and held-item cases |
| `joined` | 504 | Actual six-facing 3×2 group page callbacks, owner orientation and clicked-member column mapping |
| `saved` | 108 | Actual tile read/write and lazy journal getter observations for empty, master and three-page journal contents |
| `distantOwner` | 6 | Actual page callbacks with an editable clicked member and a distant, non-editable display owner |

The `nbt` dictionary interns the exact initial tile and held-stack SNBT used by tray callbacks; `initial` and `heldInput` index it. Every ray is recorded in block-local coordinates. Page-ray observations include positions outside a physical housing: they describe the callback's twelve-row policy, not proof that vanilla selection can reach each ray. The existing shared projection check supplies placement transforms; the reference does not define a second projection implementation.

## Tray cases

`caseNames` supplies the stable numeric case order:

| Case | Input |
| ---: | --- |
| 0 | Empty hand, empty reader |
| 1 | Three valid chips, empty reader |
| 2 | Wrong item, empty reader |
| 3 | Invalid company metadata, empty reader |
| 4 | Valid held chips, occupied journal reader |
| 5 | Empty hand, occupied journal reader |
| 6 | Creative insertion |
| 7 | Sneaking Survival player |
| 8 | Configurizer |
| 9 | Denied clicked-member edit permission |
| 10 | Back-side tray ray |
| 11 | Hidden occupied reader with another display selected |
| 12 | Creative sneak configuration routing |
| 13 | Enabled rows hiding the raw selected chip surface and occupied reader |
| 14 | Modern activation artwork override with a raw chip surface |

`direct=true` invokes the chip helper. `direct=false` invokes the actual block callback, which first routes through `RedstoneScreenInteractions`. Hand zero is main hand. `mainInventory` includes the selected main-hand stack; it must not be added to the recorded main-hand count again. Client prediction is sampled only at the ordinary mounting position, while server callbacks cover every configured mount.

The helper itself accepts an offhand request; the player-facing outer route rejects it. Client tray prediction consumes the click before server item/occupied validation. An empty-hand request on an empty selected reader still consumes and resets page state. Valid Survival insertion consumes one chip; Creative insertion preserves the held count. Tray handling precedes journal paging, so some rays remove the journal before a page callback can run. Page clicks can use ordinary held items; the Configurizer and sneaking checks still refuse them.

## Pages and persistence

A joined page click authorizes the clicked member, then changes the rectangle owner's page. It does not repeat owner or reader proximity/claim checks. Horizontal groups and saved UP/DOWN facings retain the released owner orientation and column reversal. Joined page observations use a three-page journal and start at page one.

Read clamps `ChipDisplayPage` to 0–256. Before the journal getter runs, a save retains that pending value. The first journal query clamps it against the actual journal length and clears the pending value. Repeated reads of the same physical stack identity reuse the view. The `saved` section deliberately sets the public counter to 999 after that first query to demonstrate that the getter does not normalize it on every call; this is a getter probe, not an ordinary player action. Reader insert/remove callbacks reset current and pending page state to zero. The page key is written only while a raw primary or secondary surface selects CHIP; hidden reader ownership remains separately saved.

The existing `screen-chips` live harness also exercises Vehicle preview/control paths. It is not part of this non-Vehicle reference. Public native Screen activation and real packet acceptance remain separate checks.
