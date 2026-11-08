# Captured-contact split recovery

The optional captured-head release fixture uses the actual two-controller scene,
ordinary transmitter input, a fractional captured arm and a Survival empty-hand
interaction packet. It does not manufacture a scope, pose or journal.

`VANDOR_LABS_COUPLER_SCOPED_SPLIT_CAUGHT=1`, together with the existing release
and captured-head flags, cancels the fresh detached vehicle's real join event.
At that boundary the retained output must already have its new generation, and
the detached output must carry a distinct generation. The existing asynchronous
coupling journal handles rollback and retirement.

The first accepted run observed journal and rollback at the same server tick.
It checked the original null scope, exact immutable Source and pose, and one
usable owner after retirement. A bounded outbound FML probe decoded the actual
Source and STATE packets: one Source fragment and two STATE packets, with
neither staged generation exposed. Generation identity is compared directly;
UUIDs have no ordering meaning. The evidence callbacks never serialize a live
vehicle or access a world on a Netty thread.

Validation: actual captured-head client focus (19 assertions), packet decode
round trips, Java 8 dedicated-server linkage and standard assemble. This caught
exception result does not establish abrupt termination or partial-save recovery.
Those phases remain separate acceptance work. A known non-null original scope
requires an actual accepted assembly or attach receipt, rather than fixture
scope injection. General-active split remains intentionally unsupported.

A separate pre-journal case (`VANDOR_LABS_COUPLER_SCOPED_SPLIT_PREJOURNAL=1`)
blocks the existing shared disk worker, waits for the actual prepared record,
and makes its destination parent a file in the disposable world. Releasing the
worker produces a genuine filesystem exception before journal publication. The
accepted live check made 21 assertions: pending I/O remains pending gameplay,
the prepared original Source and null scope remain exact at the failure
callback, and no second usable owner or live recovery record is created.

## Abrupt partial save and cold replay

The accepted `coupling-scoped-split-partial` recovery case uses the same real
player release scene. At the actual journal boundary it saves and reads back the
engine's manual-input data, then checkpoints the original entity chunks. After
both outputs are installed, only the detached output's chunk is checkpointed.
The existing harness kills that process and performs two cold verifications;
artifact retention ends after all three processes.

The first cold verification observes the final actual owner by entity identity,
before motion: exact original Source, null scope, position, rotation, arm phase
and target. All scene vehicles are counted, rather than filtering for only the
journal's two UUIDs. The interrupted detached chunk is explicitly loaded, and
its stale owner must remain unusable.

The original snapshot deliberately retains a pending unlock request. After the
initial one-owner rollback proof, the actual nearby player allows ordinary ticks
to retry that request. This is a new successful transfer, not duplicate recovery:
it retains the parent UUID, creates a fresh detached UUID and fresh independent
generations, and must leave the interrupted detached UUID unusable. Across all
usable scene owners, 48 physical cells, two chests with 18 diamonds and one
captured arm remain exact once. Both outputs retain their released-head proofs.

Two immutable Stable snapshots were recorded after 50 unchanged world ticks.
The accepted recovery scene retained phase 0.375 with target false and the
ordinary blocked flag set; the proposed next phase was 1/3, with articulation
and cache readiness both true. Target completion and the precise obstruction
are not established by that flag alone. The recovery and ownership comparisons
remain valid independently of that motion diagnostic. A stricter test-only
prepass now uses the actual Module geometry/sweep/foreign/world callbacks and
records up to eight rejecting boxes. Its live rerun remains pending; cache
preparation or a stale blocked flag cannot satisfy that stricter boundary. Read-only cached articulation evidence contains the original joint
and two body matrices, then zero joints and one body matrix per independent
output. It never invokes the live entity serializer to collect evidence.

The second cold restart compares both final world-owned instances against those
Stable snapshots, including Source, scope, pose, phase and target; it reloads the
old interrupted chunks and again checks all usable owners and cargo. Accepted
validation: 814 first-cold and 39 second-cold assertions, real SIGKILL and partial
chunk save, identical standard JAR in all three processes, plus Java 8
linkage/assemble. Separate direct-committed-before-kill and rollback-partial
fault routes are implemented but remain unvalidated. A genuine non-null BEFORE
scope also remains pending actual assembly/attach receipt coverage.

All three boundary helpers now compare the actual immutable `Before` generation
with the installed owner, preserving either legacy null or a genuine known
scope. `-Dvandorlabs.scopedSplitKnownBefore=true` additionally refuses a null
original, so a future receipt-binding run cannot accidentally pass through the
legacy path. This flag asserts an existing scope; it never creates one.
Known-before live coverage remains pending that real binding.
