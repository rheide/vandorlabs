# Native Ramp saved-state recovery

Native Ramp Controllers keep a durable journal while they own a generated
platform. Disk preparation can leave an action pending for several ticks. The
original platform and supported actors remain owned throughout preparation,
publication and return; a pending disk operation does not reject the action.

Initial capture and return to native blocks support connected players, native
item entities and adult, unsaddled pigs. Other actor roles must be moved off the
platform before capture or return. The controller reports this refusal before
transferring terrain or actor ownership. Removing the unsupported actor and
configuring the controller again retries the action.

Healthy saves record the supported actor's physical support separately from its
inventory or item payload. On restart, an authenticated support witness can map
an item or pig from an earlier or later saved platform pose to the saved
controller pose. This changes its position and known Ramp support metadata;
private item components and unrelated native records remain unchanged. Actor
moves between native entity chunks require a separate ownership protocol and
remain refused.

Returning actors have a durable original-space intent before physical alignment.
The current generated-side actor records are checkpointed under the same fence
before the return intent becomes durable. The journal remains until both native
terrain and actor records have completed
their checkpoints. Interrupted return recovery runs before ordinary gameplay.
The conservative cold repair admits one operation, at most 128 actors (including at most eight players), one
native actor chunk and empty neighbouring actor chunks. Unknown records,
external ownership envelopes, passengers, changed dimensions and unavailable
factories remain retained for recovery instead of being guessed or discarded.
Healthy support inspection is bounded to 64 chunks, 8192 native records and
128 matching supported actors. Rewriting a queued or already live entity record
is refused.

Known older private native `ACTIVE` journals can retain an obsolete initial actor
roster. Their typed recipe and current actor support must be proven before that
roster is durably retired. An unproven old actor/platform pose remains refused.
This migration does not restore an old item or inventory snapshot.

Source 1.12 active platforms, `Sources`, original-block snapshots and generated
`controlled_ramp` tile data are not a full-world conversion format for NeoForge.
Their canonical registry identities are preserved, but importing those active
snapshots leaves the native tile inert and retains its original NBT exactly.
Configure or dismantle the original Source platform before moving its settings
to a native world.

Independent saves with different escalator tread phases still require a proven
belt-phase mapping. Such phase changes remain refused during support adoption;
ordinary Ramp pose recovery does not establish generic mob or escalator
recovery parity.
