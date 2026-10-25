# Machine display settings

Use the **Configurizer** on a formed Core-based multiblock or one of its reserved cells to open its shared dialog. The following new models have configurable surfaces:

| Model | Surface tabs | Authored Data Chip Mount |
| --- | --- | --- |
| Seyr Meridian Drive | Half Screen, Keyboard | Yes |
| Pinnacle Vector Fold Array | Half Screen, Keyboard | Yes |
| Maxwell Drive | Screen, Keyboard | Yes |
| Paladyne Navigation Computer | Half Screen | No |
| Seyr Navigation Computer | Half Screen | No |
| Pinnacle Oculus Drive | Half Screen, Keyboard | Yes |
| Survey Dish | Half Screen, Keyboard | Yes |
| Sentinel Array | Half Screen, Keyboard | Yes |
| Standalone Dish / Antenna | No display | Yes |

![Machine display dialog with independent screen and keyboard tabs](multiblocks/machine-display-dialog.png)

Each surface keeps its own artwork selection. **Model artwork** preserves the supplied design. **Black** and the standard Programmable Block material and screen/panel catalogs are also available, including half-screen and keyboard designs. **Custom...** samples a block material. Static/Animated playback and Slow/Normal/Fast speed apply to the fitted displays.

The original Constructor frames have no authored display surfaces or chip reader. Standalone radar models provide redstone activation and their always-visible chip holder without display surfaces.

## Activation

Machines consume physical redstone or configured Vandor Labs input channels. Apply physical power to any reserved cell or the Core. `0` means disconnected from channels. No Core or reserved cell emits physical or channel power, including with a chip inserted.

Redstone is disabled by default. Except for the three drives’ configurable ON/OFF polarity below, the activation rule matches [navigation-table activation](navigation-displays.md):

| Redstone | Active appearance and selected artwork |
| --- | --- |
| Disabled | Always active |
| Enabled | Active while physical or channel power is present |

Data Chips never gate activation. The integrated dialog has no Security, company-filter or return-mode controls. Legacy saved values cannot re-enable those gates.

Inactive screens and keyboards are black. Drives use their idle casing artwork while inactive. Navigation computers use their Off appearance; when activated, their saved Off/Idle/Computing appearance remains available through ordinary right-click cycling.

Oculus shutters use a one-second stroke with continuous reversals. Dish models sweep through ±55° every eight seconds, fading movement when Scanning starts or stops. Oculus/radar assemblies default to Active/Scanning; ordinary right-click cycles their three appearances. Their chip readers, input gates and channels use the same dialog and persistence as the existing drives.

These settings control appearances; they do not add propulsion, dimension travel, crafting or navigation calculations.

## Native chip mount

Models with an authored holder always show it. It uses the standalone Data Chip Mount's native frame, size, insertion animation and physical item retention. Right-click with any Data Chip to insert one item; empty-hand right-click removes it. The chip stays inserted until removed, accepts every company, and does not control the machine's activity.

![Always-visible native mount with retained chip](multiblocks/machine-reader-visible.png)

Existing inserted items retain their full data when old Security settings are removed. Chips already awaiting return keep their original return owner and finish that return; new insertions use Stay Inserted. Standalone Data Chip Mounts and Access Card readers keep their own configuration and behavior, described in [Data Chip Mounts](data-mounts.md).

## Drive Engine controls

Maxwell Drive, Seyr Meridian Drive and Pinnacle Oculus Drive have an **Engine** tab alongside their surface tabs. Particle intensity and threshold controls apply only to Maxwell. **Seyr Meridian and Pinnacle Oculus have no particles**, including with legacy saved particle settings.

| Control | Behavior |
| --- | --- |
| Redstone: Disabled | Keeps the drive powered at level 15. |
| Redstone: Enabled | Uses the strongest level, 0–15, from physical power anywhere on the assembly and its configured input channels. Channel 0 means no channel. |
| Activate on: ON / OFF | ON uses the input level. OFF uses 15 minus that level. A resulting level of zero switches the drive off. Polarity has no effect while Redstone is disabled. |
| Brightness: On/Off / Signal | On/Off gives active effects full brightness. Signal scales brightness by the resulting level divided by 15. |
| Particles: Off / Light / Medium / Heavy | Selects exhaust intensity independently of brightness; Off disables particles. |
| Particles at level: 0–15 | In Signal mode, exhaust starts at or above this resulting level while the drive is active. On/Off brightness ignores this threshold. |

Defaults are Redstone Disabled, Activate on ON and On/Off brightness. Maxwell additionally defaults to Light particles and threshold 8. Right-click any cycling option to select its previous value.

Maxwell has yellow/white exhaust. Seyr Meridian’s active front cyan lights pulse using a pause-aware cosmetic clock. Oculus retains its native colors, energized field and shutter movement. Signal brightness scales these active visual effects. An activity selected by ordinary right-click remains subject to the drive’s power controls.

Placed and carried Maxwell drives emit cosmetic particles at their authored outlet, following the machine’s facing and the vehicle’s pose. These machines provide **no vehicle thrust** and do not qualify a vehicle for Taxi or Hover. Engine settings, channels and retained chip data survive saving and movement.

Screen choices, redstone activation, channels and the chip's full item data persist through saving and reloading. Manual dismantling retains a chip in its Core; reassembly restores access. Breaking the Core releases the held chip once. Mining a reserved cell dismantles the machine and removes the selected cell; rapid removal safely skips stale renderers.

[Step-by-step multiblock build guides](multiblocks/README.md)

## Dynmap

Core seeds and formed machinery have native map geometry and artwork, including the Oculus Drive and all four radar variants. Reserved cells render their own clipped portion of the assembly, using the saved facing and detected Core corner. Maps show a static saved activity pose; the Oculus Active pose has open shutters, while scanning dishes use their resting heading. Selected screen artwork, inserted chip items and continuous mechanism animation remain in-game features.
