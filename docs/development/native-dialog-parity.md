# Dialog parity between Forge and NeoForge

Forge 1.12.2 dialogs are the visual and interaction reference. The NeoForge adapter should change the Minecraft drawing, inventory and network APIs while retaining logical pixel positions, visible controls, labels, option order, enabled conditions, reverse clicks and compact-layout behavior. Native functionality tests establish their own scope; they do not by themselves establish presentation parity.

## Shared presentation boundary

`com.vandorlabs.ui.DialogStyle` contains the authored panel palettes, title offsets, logo geometry and flat notebook-tab states. The Forge `DialogHeader` and `DialogTabButton` consume its logo and tab calculations. `NativeDialogVisuals` and `NativeDialogTabButton` adapt those same values to native drawing and widget APIs. Native family adapters now select their Source style explicitly. This does not make their family-specific control layouts shared.

The logo uses the original `textures/gui/vandor_logo.png`, a maximum height of 21 logical pixels, aspect 12:7 and an eight-pixel right margin. Header-fill height and logo positioning height are separate: the Ramp fills 28 pixels but uses the original default 24-pixel logo positioning. Resource conversion must copy the original logo without resampling.

Shared appearance does not mean applying one color to every dialog. The Source families use dark programmable panels, teal machine panels and translucent inventory panels. The Infuser now follows the standard mod header and slot/progress presentation in both versions; its former Furnace background and flames are removed. The Light's manually drawn level track and the Ramp's Forge slider are also distinct widget presentations.

## Complete native screen inventory

The inventory below covers the 30 main native screen classes. All consume the Source family shell and applicable logo, notebook-tab and ordinary-button presentation. Light retains its separate manual track. The table records the remaining layout and option-sharing boundary; shared domain settings are not counted as shared visual geometry.

| Native screen | Forge reference | Existing sharing and concrete remaining presentation boundary |
|---|---|---|
| `NativeCubeScreen` | `GuiProgrammableWall` and shape-specific dialogs | Shared programmable controls/layout, now with the Source dark shell, logo and notebook/button skin. Shape variants retain their existing portable control definitions. |
| `NativeStorageScreen` | Programmable block housing dialog | Reuses `ProgrammableBlockDialog` and the Source dark shell, logo and widget presentation. |
| `NativeTriggerScreen` | `GuiProgrammableTrigger` | Shared trigger controls with the Source 24-pixel dark shell, title offset, logo and notebook/button presentation. |
| `NativeBinaryControlScreen` | `GuiRedstoneChannel` | Shared mounted-control/dialog input policy; Source dark body and teal 24-pixel header, logo and notebook/button presentation are bound. Dedicated transmission widget geometry remains an adapter boundary. |
| `NativeDoorScreen` | `GuiSpaceDoor` | Shared `SpaceDoorDialog` controls with the Source opaque teal shell, 24-pixel header, logo and notebook/button skin. |
| `NativeGlassScreen` | `GuiProgrammableGlass` | Shared glass options with the Source dark shell, 24-pixel header, logo and notebook/button presentation. |
| `NativeLightScreen` | `GuiProgrammableLight` | Shared panel dimensions and value policy; Source 24-pixel header/title at 12,8, logo and exact 138-pixel manual brightness track are bound. Dedicated control definitions remain local. |
| `NativeScreenScreen` | `GuiAnimatedScreenSelector` and related screen dialogs | Shared screen settings and rendered control-surface policies; Source opaque teal shell, 24-pixel header, logo and notebook/button skin are bound. Editor widget geometry remains local. |
| `NativePropulsionScreen` | Propulsion branch of `GuiRedstoneChannel` | Shared engine settings/admission and generic panel dimensions; Source dark body, teal 24-pixel header, logo and notebook/button skin are bound. Particle, shape, join, analog and threshold widget definitions remain local. |
| `NativeTrapdoorScreen` | `GuiProgrammableTrapdoor` | Shared `TrapdoorOptions` value policy and Source opaque teal shell, 24-pixel header, logo and notebook/button skin; dedicated editor widget geometry remains local. |
| `NativeSegmentedScreen` | `GuiSegmentedDoor` | Shared segmented settings/glass policies and Source translucent shell, 26-pixel header/logo positioning and notebook/button skin. Dedicated editor bounds/labels remain local. |
| `NativeCanopyScreen` | `GuiLargeCanopy` | Shared programmable panel geometry with Source translucent dark shell, title/logo and notebook/button presentation. Preview/list positioning and dedicated options still need a complete contract. |
| `NativeRampScreen` | `GuiRampController` | Both adapters consume `RampDialog`, including seven modes, scan/travel choices, offsets, compact branches and control state. Source header/logo, notebook/buttons and the scoped activation control are bound; focused widget checks cover all six faces and both housing variants. |
| `NativeCopifierScreen` | `GuiDuplifier` | Shared `CopifierDialog` controls, Source 22-pixel shell/logo and notebook/button presentation. |
| `NativeCredentialReaderScreen` | `GuiTokenSlot` | Source 28-pixel teal header/logo, notebook and ordinary-button skin are bound. Dimensions, credential fields and receiving-channel/transmission subpage geometry remain locally authored. |
| `NativeWirelessScreen` | `GuiWirelessRedstone` | Source 28-pixel teal shell/logo and notebook/button skin are bound. Input cycling is shared; transmitter/receiver editor dimensions, positions and labels remain independent. |
| `TransmitterScreen` | `GuiTransmitterItem` | Separate transmitter-item adapter retains the Source dark/teal 24-pixel shell, logo and button skin. Compare its actual binding and compact field geometry before retiring or adapting it. |
| `NativeProgrammerScreen` | `programmer.client.GuiProgrammer` | Journal text policy and the Source 24-pixel header/button presentation are shared; retain Source's absence of a header logo. The 228/264-pixel viewport branches, physical inventory slots and journal controls remain locally duplicated. Vehicle-only content follows the stated feature exclusion. |
| `NativeGearScreen` | `GuiModularGear` | Consumes `GearDialog` controls and panel size, Source header-free teal shell/title/logo, notebook tabs and ordinary buttons. Dedicated slider variants still require their Source-specific skin review. |
| `NativeTrackScreen` | `GuiTrack` | Source header-free teal shell/title/logo and ordinary-button skin are bound. Track settings are shared; dedicated coordinates and labels remain local. |
| `NativeLegacyLandingScreen` | `GuiLandingGear` | Source translucent shell/title/logo, notebook and button skin are bound. Dedicated geometry and permitted options still need comparison against the Source landing-gear editor. |
| `NativeSeatScreen` | `GuiProgrammableChair`, `GuiConnectedSeat`, pilot-height dialog | Source Chair/connected-seat shell/title/logo and ordinary-button skin are bound. Dimensions, preview, button/slider placement, height labels and style lists still need a per-role shared contract. |
| `NativePlateScreen` | `GuiPressurePlate` | Source teal shell/logo, notebook and button skin are bound. Dedicated sample-grid, channels and role-dependent option geometry remain local. |
| `NativePortScreen` | `GuiResourcePort` | Both consume `ResourcePortDialog`: Source 308×280 geometry, viewport scale restoration, three notebook pages, six faces, throughput/channels, option labels/order, reverse cycling and horizontal battery gauge. Native legacy widget IDs remain mapped without changing transport. |
| `NativeBufferScreen` | `GuiStorageStatus` | Source and native retain adaptive 354×238 limits and the Source body/title/logo. `NativeBufferBindings.open` correctly routes batteries to `NativePortScreen`; the buffer screen is the fuel-tank path. Picker and gauge geometry remain adapter-local. |
| `NativeRackScreen` | `GuiModuleRack` | Adaptive 348×250 limits, Source translucent `0xEE171B23` shell/logo, notebook tabs and ordinary-button skin match. Route/status and nested editor coordinates remain local. |
| `NativeSurfaceScreen` | `GuiProgrammableSurface` | Both consume `SurfaceDialog` and the Source adaptive programmable panel, 18-pixel header/title/logo, Active/Inactive IDs, layout order, Custom picker, Done control and reverse cycling. |
| `NativeCopyScreen` | `copier.client.GuiCopyController` | Source translucent header-free shell/button skin and absence of a logo are retained. Native 360-pixel width differs from Source's adaptive maximum 400; scan height, channel increment, activation and footer controls remain separately laid out. |
| `NativeBuilderScreen` | `GuiSynthesisTower` | Both adapters consume `SynthesisDialog`: 348×306 normal or 312×240 compact, matching translucent shell, flat design rows, notebook tabs and all 43 inventory slots. Native resize preserves slot identities and the open window; full and compact views have focused actual-client coverage. |
| `NativeInfuserScreen` | `GuiMatterInfuser` | Both adapters consume `InfuserDialog` for the standard mod header, three physical slots, slot frames, progress and sunlight status. Neither uses a vanilla Furnace background or flame gauge. |

## Widget artwork boundary

`NativeDialogButton` uses the exact three Source 200×20 button states in a
200×60 GUI sheet. It retains half-width cropping rather than modern nine-slicing,
normal/disabled text colors and the Source yellow hovered text. The ordinary
buttons in all screen families and the nested transmission widget use this
adapter. No new options or packet operation indices are introduced.

Authored notebook tabs are not vanilla buttons. Their idle/hover/selected fills, two-pixel underline, text colors and text placement are shared explicitly in `DialogStyle` and `NativeDialogTabButton`.

## Current shared control coverage

| Contract level | Families | What the shared code currently proves |
|---|---|---|
| Control IDs, bounds, labels and state-dependent control definitions | Programmable Block/shape variants, Trigger, Glass/porthole variants, ordinary Doors, mounted binary controls, Duplifier, Gear | Portable `DialogControl` families or equivalent typed controls are consumed by adapters. Their Source family shell, logo, notebook and ordinary-button presentation are now bound. |
| Generic panel dimensions plus domain options or input policy | Light, Screen editor, Propulsion, Trapdoor, Segmented Door, Canopy | `ProgrammableDialogLayout`, option/domain policies and input cycling are shared in part. The dedicated adapters still construct their own widgets; this is not a shared family editor layout. |
| Predominantly adapter-local widget geometry | Credential Reader, Wireless/Transmitter, Programmer, Track, legacy Landing Gear, Seat roles, Plate, Buffer, Rack, Copy Controller | Existing domain/runtime sharing does not make their visible coordinates, tabs, labels or option lists common. Extract those Source definitions independently. |
| Shared dedicated inventory/editor geometry in both adapters | Infuser, Synthesis Tower, Ramp, Resource Port/Battery, Surface | `InfuserDialog`, `SynthesisDialog`, `RampDialog`, `ResourcePortDialog` and `SurfaceDialog` define the Source/native layouts and corresponding options. Synthesis resize retains the same 43 slot identities; Port retains legacy native widget identifiers through its adapter map. |

## Remaining concrete scope

The family shells, applicable logos, notebook tabs, ordinary-button artwork and
Light manual track are complete. Focused presentation checks render the actual
27 remaining production screen classes, covering idle, notebook hover and
ordinary-button hover, Source ARGB/header bounds, unoccluded authored-logo
pixels and viewport fit. Existing Infuser, Synthesis and Ramp checks cover the
other three classes. These are presentation checks, not substitutes for
server authorization or durable ownership tests. Source Port/Battery captures
cover all three pages, the shared descriptor bounds and previous-value face,
activation and shape choices. Source Surface captures cover compact/normal
viewports, wheel/drag scrolling, reverse layout cycling and independent
Active/Inactive selection.

Continue extracting dedicated geometry and option definitions for the
adapter-local families in the table. Preserve Buffer's established dimensions
and battery-to-Port routing. Compare role-specific Seat layouts, transmission
subpages, Programmer inventory/journal branches, Rack route editors and Copy
Controller geometry separately. Keep the Source Light manual track distinct
from the remaining Forge-style sliders. Shared domain settings alone do not
establish complete editor-layout parity.

## Extraction and verification order

1. Bind shared logo, family shell and notebook-tab presentation. This fixes a common visible omission without changing feature options or control indices.
2. Extract each remaining family's Source geometry and option definitions into portable contracts. The contracts contain control IDs, widget kinds, labels, bounds, option order, visibility, enabled conditions and compact-layout decisions. The adapters retain their own transport and inventory integration.
3. Match the Source widget family. A native toolkit's default button or slider is not evidence that the 1.12 visual style was preserved. Reuse the exact authored flat tabs and manual Light track; retain the distinct Forge slider behavior where Source uses it.
4. Compare identical family state, viewport and selected page in Source and native captures. Assert panel/logo/tab/track positions and unoccluded artwork pixels; compare control definitions and previous-value right-click behavior independently of font rasterization.
5. Keep menu authorization, pending asynchronous work and source/client inventory synchronization checks alongside visual acceptance. A pending durable operation must not be rendered as a rejected user action.

Focused captures establish affected-family presentation parity; they do not establish a full release gate.
