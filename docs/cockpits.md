# Cockpits and peripheral controls

Cockpit, Advanced Pilot Chair and Yoke and Screen are available in the creative menu.

Cockpit combines a Pilot Seat, vehicle mode controls, independent
screens and touch panels, and physical throttle controls. Advanced Pilot Chair provides a smaller seated arrangement with red upholstery. Yoke and Screen is a standalone
peripheral and does not add a passenger seat to a vehicle.

The cockpit console has a continuous shell, a lowered screen bank and three angled
keyboard touch panels. Existing display and output settings follow their surface
identities when the console geometry is updated; new panels start with defaults.

Place the complete assembly with its item. Placement checks every rotated cell
before creating the root and its reservations. Breaking any reserved part removes
the assembly. Collision follows the component boxes and narrow slices of angled console
solids, leaving the cockpit interior open. Configured item drops preserve displays, channels and controls.

Use the Configurizer or sneak-right-click any part to open the combined dialog.
Use the arrows to select the Pilot page, the sidewall and armrest casing picker, individual surface pages and individual output pages. Each screen or touch panel has its own artwork, animation speed,
animation toggle and optional ON/OFF channel gate. Slow, Normal and Fast use the same playback intervals as Programmable Screen; Normal advances one frame every ten ticks. Channel zero leaves a surface
enabled continuously.

Touch areas default to animated keyboard artwork and use a 2:1 aspect ratio. Selecting Blank makes a surface black and inactive. The right armrest has one rectangular touch area facing the seat; the cockpit has no left-armrest touch panel, and its throttle assemblies sit forward. Keyboard artwork has no numeric output overlay.

The center cockpit screen and the Yoke and Screen display default to Vehicle Status. The left and right screens default to Bridge Bare Pilot and Bridge Bare Engineering. The left keyboard uses Console Controls Navigation Controls, the right uses Console Controls Sensor Controls, and the angled center deck uses Bare Personal Terminal. Surface pickers include Redstone and Vehicle Status modes and support wheel scrolling and scrollbar dragging. Screen backs use Door Interior (Dark), horizontal casing surfaces use Glass Frame Interior, and the casing selector controls the remaining sidewalls. Casing artwork tiles at one texture square per block rather than stretching across broad faces.

Each surface starts with three independent input rows. Selecting **Redstone?** opens the shared redstone screen editor for labels, channels, row order and control type. Add or remove rows up to three per surface; choose toggle, four-position slider with configurable low/high levels, or a one-second pulse. Other artwork remains unobstructed. The output pages also expose each independent control. Operate a row directly on the model. Physical throttles cycle
through the same four levels, and their output channels can operate separately
placed thrusters or other channel-controlled blocks. Throttles do not replace the
craft's physical propulsion or lift requirements.

The seated assemblies participate in normal craft discovery. Their reserved cells
and saved controls survive conversion, movement and parking. A cockpit supplies
vehicle controls when the craft has no separate Vehicle Controller; a separate
Vehicle Controller takes precedence. The Pilot page includes boarding behavior,
trigger channels, independent Block/Taxi/Hover channels and mode buttons. Vehicles
with plates or landing struts retain their Hover-only mode restrictions. Ski-only
vehicles can Taxi while all runners remain above ice, packed ice or frosted ice.

## Moving screens and exit clearance

Press the small button between the right keyboard and right armrest to retract the three-screen bank into the console housing. Press again to raise it. The displays move with their frame and retain their individual settings.

![Retracting cockpit screens](images/gallery/version-2.3/cockpit-screens.gif)

The button behind the two thrust levers slides the seat and both armrests one block backwards, leaving the console fixed. It checks the full rear sweep before moving and reserves the occupied rear cells until the seat has returned. The seated player and camera follow the same animation timeline as the cushion. A blocked path leaves the mechanism in its current state. Vehicle snapshots preserve the mechanism settings and rear reservations.

![Cockpit seat clearance](images/gallery/version-2.3/cockpit-seat.gif)

## Advanced Pilot Chair height

The casing page has a Height option for Advanced Pilot Chair: +0, +2, +4 or +6px. The new +0 default lowers the seat and side rests by 6px; +6 restores their former height. The lower support compresses above the floor while the upper chair, controls and seated player change height together. Left-click advances; right-click goes back. Height survives configured item drops and vehicle conversion.

![Advanced Pilot Chair height](images/gallery/version-2.3/advanced-chair-height.gif)
