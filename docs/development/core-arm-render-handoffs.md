# Core arm render handoffs

Forge 1.12.2 keeps render correspondence separate from terrain ownership. Each actual arm capture publishes a bounded cosmetic address before its source cells become journal proxies. The address identifies the transient transfer, original Core cell and native tile identity. Native restoration and the exact journal-proxy publication hook attach the destination identity to the installed Core. These fields appear only in update tags; saved Core, arm payload, capability and journal NBT stay unchanged.

The client retains the captured renderer while native layout data or the cosmetic arm pose is pending. It chooses the owner once per main frame and switches on the following frame, so renderer ordering cannot produce a blank or duplicate body. A factory tile cannot draw a formed machine before its appearance data arrives. After handoff, rendering uses the current native tile, including subsequent activity and settings edits. Missing or removed parent addresses do not hide a complete native machine.

Correspondence is limited to 64 Cores per packet, 8,192 indexed identities and 2,048 weak native-tile entries. Completed owner decisions live on their existing arm tile; loading another arm cannot evict them. World unload clears the indices. Native tile fallback uses cached bounds, physical tile identity, actual distance and frustum tests, and ignores shader-shadow draws when recording main-view admission. There are no world-wide tile scans or per-frame NBT comparisons.

For one joined 27-cell Oculus construction, the raw arm update grew from 8,275 to 8,474 bytes and the native Core update from 1,026 to 1,220 bytes. Capture additionally sends one tracking-player arm update before source freezing. The bounded address codec round-trips independently of the saved snapshot.

The opt-in `arm-core-handoff` client focus observes actual submitted Core vertices during native-NBT delay, inverse arm-header delay and ordinary publication. It checks one visible main-view body, retained supports and chip ownership, repeated updates, current native activity and unchanged saved NBT. It does not run a release gate.
