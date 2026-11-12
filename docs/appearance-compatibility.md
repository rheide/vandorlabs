# Appearance and mod compatibility

These integrations target the main Forge 1.12.2 build.

## Looking-at tooltips

With Waila/Hwyla installed, programmable blocks show a `Texture:` line using the
same name as the texture picker. Wawla uses this HUD API, so no separate Wawla
integration or mandatory dependency is required. The `Texture and door movement`
option in the HUD's Vandor Labs settings controls the additional information.

Blocks, slabs, stairs, walls, Portholes and Storage show their primary housing
finish. Screens, inputs and consoles show their primary display artwork; special
Redstone, Data Chip and Vehicle Status surfaces have descriptive names. Lights
and Triggers show the active artwork. Glass and canopies show the selected glass
shade or replacement material. Ordinary, Large and Segmented Doors and both
Trapdoor families also report `Movement: Rotating` or `Movement: Sliding`.

Looking at either half of an ordinary Door or any of the nine Large Door cells
uses the owning tile's settings. Missing or unloaded owners are ignored. The
provider reads the appearance data already synchronized for rendering; it does
not send inventory contents, credentials or additional tile NBT over the network.

## Picking configured blocks

Creative middle-click preserves the selected block's existing settings. Minecraft
already distinguishes configured stacks by their item NBT, including a different
configuration of the same item already in the hotbar. The configured inventory
models now consume the chosen Door face texture, frame and glazing, the Trapdoor
surface and shape, and the Porthole housing, rim and glass selections.

The new item meshes reuse the existing surface emitters and retire cached results
when resources or vertex formats change. Existing pick-block settings, placement
settings and world rendering keep their established data formats.

## Industrial Renewal catwalks

Industrial Renewal 1.12.2 recognizes adjacent doorways by checking Minecraft's
[`BlockDoor` type](https://github.com/Cassiobsk8/Industrial_Renewal/blob/f9389105d6d9c4e223d1c1785a4b14ff23b35192/src/main/java/cassiokf/industrialrenewal/blocks/BlockCatWalk.java).
Ordinary and Large Programmable Doors, along with the legacy
Vandor Door variants, now expose that native type while retaining their own
placement, animation, support, security, harvesting and assembly behavior.
Catwalk railings and collision openings can therefore update using Industrial
Renewal's own connection logic, including after a Door is removed. No manual
catwalk side settings are rewritten, and Industrial Renewal is optional.

Segmented Doors use a different tile-owned assembly and are outside this native
Door integration. The preserved Vandor metadata layout also remains authoritative;
other integrations should use the block's state methods instead of interpreting
its metadata with vanilla Door static helpers.

## Gold glass

`Gold` is an amber-gold finish (`#D89B2B`) in the shared glass palette, with the
same Mild, Normal, Strong and Opaque controls as other shades. It is available in
the existing glass selectors, including flat and diagonal Glass, Portholes,
canopies, supported Door glazing and Segmented Door windows, as well as configured
item previews and Dynmap.

Gold is appended as shade ID 19; IDs 0 through 18 retain their previous meanings.
The existing shimmer pattern, texture sizes and tint opacity rules are preserved.
Dynmap generators read the shared palette so future regeneration retains Gold.

## Focused verification

Use a Java 8 JDK for the Forge Gradle build:

```sh
./gradlew testWailaTooltips testNativeDoorCompatibility testDedicatedServerLinkage testDynmapRenderers --no-daemon
```

The `appearance-compat` client focus requires the actual Hwyla and Industrial
Renewal JARs, supplied through `VANDOR_LABS_TEST_HWYLA` and
`VANDOR_LABS_TEST_INDUSTRIAL_RENEWAL` when running
`testclient/test_viewscreen.sh --focus appearance-compat`. It checks configured
middle-clicks, displayed item appearances and placement, installed HUD provider
discovery, and the actual catwalk connection and collision behavior. The
`glass-dialog` and `glass-appearance` focuses cover Gold selection and the existing
glass appearance paths. See the client harness for its other runtime prerequisites.
