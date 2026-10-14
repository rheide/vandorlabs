# Screens and partial blocks

Half-height consoles and input panels occupy their own Minecraft block cell, even where their visible housing fills only part of it. Placing them on a slab uses the adjacent cell; they cannot share the slab's occupied cell or embed in another partial block.

For half-input panels, click a slab's side to align the wall panel with its lower or upper half. Vanilla slabs, modded `BlockSlab` implementations and Programmable Slabs are recognized. Sneak-click a side for a horizontal keyboard shelf. Click an underside to use ceiling mounting; the click position selects its ceiling position. These options also work without a slab.

Same-cell embedding would require a composite block that preserves both original block states and tile entities, combines their rendering and collision, and handles interactions, redstone, breaking, copying and vehicle rotation. Modded partial blocks can also require their own ticking and special rendering. Arbitrary same-cell embedding is therefore unsupported; a smaller collision box alone cannot provide it without replacing the supporting block.
