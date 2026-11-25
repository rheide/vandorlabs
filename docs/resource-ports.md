# Resource Ports

**Power Port**, **Fluid Port** and **Matter Port** transfer resources between
compatible storage and pipes. They do not generate resources. Matter Ports accept
ordinary items as well as Programmable Matter. All three ports are available on
Forge 1.12.2 and NeoForge 1.21.1, using the target platform’s standard capabilities
without mandatory dependencies on pipe mods.

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

The **Shared output limit (blank for unlimited)** field accepts a blank value for unlimited throughput. Enter a
positive whole number to limit RF, millibuckets or items leaving the port per game
tick. The limit applies to all Output faces together, including pipes that extract
from those faces. Accepting outputs share the available throughput; rotating
remainders keep small limits useful across multiple outputs.

On NeoForge, Mekanism 10.7 uses whole Joules. At its default 2.5 J/FE conversion, 2 FE converts exactly to 5 J; an offer of 1 FE cannot make an exact round trip. Very small shared limits can stall or leave budget unused; a shared limit of 4 permits 2 FE on each of two outputs. Leaving the field blank permits larger offers. Unaccepted energy remains in the port.

Incoming resources wait in a bounded buffer. Power Ports hold 1,000,000 RF, Fluid
Ports hold 64,000 mB of one fluid and Matter Ports hold nine item stacks. Full
buffers refuse additional input. Unaccepted output remains in the port.

The **Battery Block** uses the same controls with a 100,000 RF store. Its Housing tab also shows the live charge and Block/Hexagon shape. See the [storage guide](matter-and-storage.md) for existing-battery compatibility.

## Housing

The **Housing** tab selects a material for Disabled faces using the standard texture picker. Choose **Original artwork** to restore the authored port hull. Input and Output faces keep their port artwork. The hotbar icons use red accents for Power, blue for Fluid and yellow for Matter.

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

## Vehicle Mode on Forge 1.12.2

Attach a Battery Block, a standalone RFTools Power Cell, a Mekanism Energy Cube,
a Thermal Expansion Energy Cell, or a Galacticraft Energy Storage Module to the
craft. Set the face touching the RF route to Output where the storage block
offers face settings. Use Thermal Dynamics Fluxducts, Mekanism Universal Cables,
or Galacticraft Aluminum Wire to reach the down-facing Power Port under the
centre of a Survey Dish or Sentinel Array. A direct connection to that face
also works. In Flight, a [Module Rack Battery Module](module-rack.md#battery-module)
on the craft covers whatever this wired supply does not deliver.

Use Thermal Dynamics Fluiducts, Mekanism Mechanical Pipes, or Galacticraft Fluid
Pipes to connect compatible standalone tanks to a Maxwell Drive's inlet. The
three drive inlets accept the same fuels regardless of which pipe is used. Build
each run with one pipe family and include the entire connected network before
assembling the vehicle. Galacticraft Fluid Pipes must share their colour. When
using a pull-only pipe, put it at the storage end and use a regular pipe at the
inlet. Attached Thermal Dynamics servo and retriever faces are inactive in
Vehicle Mode. Saved conduit buffers are retained for parking, while carried
transfer draws from onboard storage.

A [Rotating or Extender Arm](modular-landing-gear.md) can pass its selected
resource through the base and tip at a settled endpoint. Choose RF, fluid, or
items and set the transfer direction in the arm dialog. Wire the storage and
destination to the matching faces. Redstone-channel motion pauses the transfer
until the arm reaches its new endpoint.

RFTools linked cells retain their network configuration when moved and reconnect
when parked. Unlinked cells use their own stored RF in Vehicle Mode. Use
standalone Mekanism Fluid Tanks as onboard supply for now. The native formed
Dynamic Tank continues to preserve its fluids and inventory for parking.

## Moving and copying settings

Mining a port drops one configured port item containing its stored resources.
Place that item to restore its settings and contents. Stored items are not also
dropped separately.

The Duplifier's **Resource Port Settings** option copies all six face choices,
throughput, pull option, Housing material, artwork condition, Redstone mode and channels. It never
copies stored power, fluid or items; the destination keeps its own contents.
