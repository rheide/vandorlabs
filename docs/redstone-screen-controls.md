# Redstone screen controls

Choose the Redstone display on a Programmable Screen, Diagonal Screen, Input,
Full Input, Console, Half Console or Diagonal Half Console. Configure each row's
label, channel list and control type. Console surfaces have separate row lists.

- Toggle changes the linked channel bank between off and on.
- Slider selects one of four configured output levels.
- Pulse sends level 15 to every listed channel for 20 game ticks, normally one
  second. Pressing it again restarts the timer from that press. Its idle button
  reads GO and its active button reads ON.

Left-click Control to select the next type; right-click selects the previous
type. Pulse uses the same channel list as the other controls. When a pulse ends,
other active sources and latched controls keep their own channels powered.

Pulse controls work on placed blocks and captured vehicles. The server keeps the
deadline when saving or parking, so loading a screen resumes only the remaining
time. An elapsed deadline cannot restore a permanent on state. Unloaded screens
do not contribute a channel source. Configuration copies retain the Pulse type
and channel list without copying an active press. Idle pulse controls perform no
timer work.
