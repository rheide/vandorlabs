# Block category settings

Vandor Labs generates `config/vandorlabs-blocks.cfg` on first startup. All categories default to `true`. Set a category to `false` and restart Minecraft or the server to hide its blocks from creative menus and remove their crafting recipes. Existing placed blocks and inventory items retain their settings and behavior; registry IDs stay available so loading a world does not delete blocks.

For example:

```text
blocks {
    B:decoration=false
    B:ramps=true
    B:screens=true
}
```

Edit the generated file rather than replacing it with this partial example. The available keys are:

| Key | Blocks |
| --- | --- |
| `screens` | Programmable Viewscreen |
| `consoles_inputs` | Regular programmable consoles and inputs |
| `programmable_solids` | Programmable blocks, walls, slabs, stairs, storage and portholes |
| `diagonals` | All diagonal variants, including diagonal glass, screens, consoles and trapdoors |
| `glass` | Regular programmable glass |
| `doors` | Programmable doors and regular trapdoors |
| `ramps` | Programmable Ramp and its generated segments |
| `lighting` | Programmable lights, frames and light slabs |
| `seating` | Programmable chairs and connected seats |
| `vehicle_controls` | Pilot Seat and landing gear |
| `propulsion` | Thrusters, drives and hover propulsion |
| `canopies` | Canopies and fixed visors |
| `redstone_controls` | Buttons, switches, levers, throttles and Programmable Trigger Block |
| `decoration` | Remaining decorative blocks, including the industrial table |

Each block belongs to one category. Diagonal variants belong to `diagonals` regardless of their material or function. Items such as the Configurizer, Duplifier, armor and crafting ingots are unaffected.

Use the same file on server and clients for matching creative menus. The server's settings determine which recipes are available. These switches control acquisition; commands and already-owned items can still place the registered blocks.
