# Moving optional machines

Arms support standalone Thermal Expansion machines and dynamos, Energy Cells,
Fluid Tanks and Caches. Supported standalone Mekanism machines include the
processing machines and factories, chemical processors, Electrolytic Separator,
Rotary Condensentrator, Pressurized Reaction Chamber, Formulaic Assemblicator,
Oredictionificator, heaters and Ambient Accumulator.

Place the machine on the arm's endpoint or on its selected platform, configure
the arm, and activate it normally. Inventories, stored resources, security and
machine settings travel in the attachment snapshot. Thermal machine facing and
side configuration rotate together when the structure is parked at a cardinal
heading. Captured machines resume their normal processing after they return to
native world blocks; the moving snapshot does not run a second machine.

A Mekanism Fluid Tank keeps its visible fluid level while carried by a Rotating
Arm. With the arm's resource mode set to Fluid, its mounting-face connection can
fill the tank at a settled rotated endpoint. Simulated fills leave the saved
contents unchanged. Committed fills update the arm's saved attachment, so the
same contents return when the arm reverses or reloads.

Linked structures and shared networks require their complete native structure
and a dedicated ownership adapter. A standalone machine adapter does not permit
moving only part of a multiblock or copying a remote shared inventory.
