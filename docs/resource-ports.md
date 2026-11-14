# Resource Ports

**Power Port**, **Fluid Port** and **Matter Port** transfer resources between
compatible storage and pipes. They do not generate resources. Matter Ports accept
ordinary items as well as Programmable Matter.

Each face can be **Disabled**, **Input** or **Output**. A newly placed port has an
Input facing the player and an Output on the opposite face. This also works when
placing from above or below. The remaining four faces start Disabled.

## Build and connect

1. Craft a port using four Iron Ingots in the corners, four Redstone Dust on the
   edges and the appropriate centre ingredient: a Redstone Block for Power, a
   Bucket for Fluid or a Hopper for Matter.
2. Place it between compatible source and destination storage or pipes.
3. Right-click it, or use the Configurizer, to open its dedicated dialog.
4. On **General**, set the six named world faces to Input, Output or Disabled.
   Connect source storage to an Input and destination storage to an Output.
5. Leave **Pull from inputs: Off** when the source or its pipe pushes resources
   into the port. Enable it to let the port extract from compatible input storage.

Power Ports connect to Forge Energy storage, including the Battery Block. Fluid
Ports connect to compatible fluid handlers, including the Hexagonal Fuel Tank.
Matter Ports connect to item handlers, including ordinary Chests. The selected
source and destination faces must permit the corresponding transfer.

## Limits and balancing

The blank **Shared output limit** field means unlimited throughput. Enter a
positive whole number to limit RF, millibuckets or items leaving the port per game
tick. The limit applies to all Output faces together, including pipes that extract
from those faces. Accepting outputs share the available throughput; rotating
remainders keep small limits useful across multiple outputs.

On NeoForge, Mekanism 10.7 uses whole Joules. At its default 2.5 J/FE conversion, 2 FE converts exactly to 5 J; an offer of 1 FE cannot make an exact round trip. Very small shared limits can stall or leave budget unused; a shared limit of 4 permits 2 FE on each of two outputs. Leaving the field blank permits larger offers. Unaccepted energy remains in the port.

Incoming resources wait in a bounded buffer. Power Ports hold 1,000,000 RF, Fluid
Ports hold 64,000 mB of one fluid and Matter Ports hold nine item stacks. Full
buffers refuse additional input. Unaccepted output remains in the port.

## Redstone and artwork

The **Redstone** tab offers Always active, ON activates and OFF activates, with
the usual channel list. An empty channel list uses adjacent redstone. The selected
condition gates both incoming and outgoing transfers.

**On texture** selects when each configured face uses its On artwork:

- **Redstone active:** its configured ON/OFF condition matches. Always active
  has no configured Redstone condition and keeps this artwork Off.
- **Connected:** a compatible pipe or storage handler is present, even when the
  buffer is empty.
- **Transferring:** that face has recently moved resources. This is the default.

Right-click a cycling dialog option to choose its previous value.

## Moving and copying settings

Mining a port drops one configured port item containing its stored resources.
Place that item to restore its settings and contents. Stored items are not also
dropped separately.

The Duplifier's **Resource Port Settings** option copies all six face choices,
throughput, pull option, artwork condition, Redstone mode and channels. It never
copies stored power, fluid or items; the destination keeps its own contents.
