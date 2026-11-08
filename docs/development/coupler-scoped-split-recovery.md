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
