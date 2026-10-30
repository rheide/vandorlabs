# Redstone Transmitter and Receiver

Both blocks use the existing redstone channel bank in their current dimension. Right-click to edit one or more channels. Channel zero means no channel. The marked port identifies the transmitter input or receiver output; the opposite face identifies the transmitter or receiver. The side arrows point toward the physical port on both blocks.

The transmitter reads only the block next to its input port. Analog mode sends its exact strength, from 0 to 15, on each selected channel. Digital mode sends 15 for any positive input and zero otherwise. Its built-in Range slider selects 4 through 256 blocks. Local Redstone: On emits the sampled signal from the side opposite its input port; Off sends only on channels.

The receiver listens to the strongest signal on any selected channel. It emits weak and strong power only through its output port. Analog mode follows a positive received level. Digital mode uses the On slider instead. The Off slider selects the output when all selected channels are off, in either mode. Defaults are Analog, On 15 and Off 0. A nonzero Off value deliberately keeps a weak output present without an active channel signal.

Use the port selector to change direction, including up and down. Right-click option buttons to select their previous value. Level sliders cover 0 through 15. Only the selected input face supplies a transmitter; its optional local output is on the opposite face. Receivers output only on their selected port. Like other channel members, only loaded endpoints participate.
