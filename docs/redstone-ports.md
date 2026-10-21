# Redstone Transmitter and Receiver

Both blocks use the existing redstone channel bank in their current dimension. Right-click to edit one or more channels. Channel zero means no channel. The port face identifies the only wired connection; the opposite face identifies the transmitter or receiver. Arrows follow signal flow: away from the transmitter's wired input, and toward the receiver's wired output.

The transmitter reads only the block next to its input port. Analog mode sends its exact strength, from 0 to 15, on each selected channel. Digital mode sends 15 for any positive input and zero otherwise. It never emits wired redstone power.

The receiver listens to the strongest signal on any selected channel. It emits weak and strong power only through its output port. Analog mode follows a positive received level. Digital mode uses the On slider instead. The Off slider selects the output when all selected channels are off, in either mode. Defaults are Analog, On 15 and Off 0. A nonzero Off value deliberately keeps a weak output present without an active channel signal.

Use the port selector to change direction, including up and down. Right-click option buttons to select their previous value. Level sliders cover 0 through 15. Connections to other faces cannot supply a transmitter or receive power from a receiver. Like other channel members, only loaded endpoints participate.
