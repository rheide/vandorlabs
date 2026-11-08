# Passive native arm capture recipes

`VehicleArmCouplerPeers` is an unbound, read-only preparation helper for the
continuous Coupler adapter. It supplies native recipes for generic operational
arm roots which have no payload yet, or whose payload is still materialized.
Already captured nonmaterialized payloads need no capture recipe.

Preparation requires an actual installed server-owned vehicle, a ready external
scope and supplied closed pairs whose addresses and identities still exist.
Native discovery uses the existing `ArmPayload.discover(root, null)` callback on
the installed `VehicleWorld`; materialized refresh uses `snapshotPlaced` against
the exact authoritative Source. Neither path moves the actor or invokes input,
configuration, phase or payload setters. No generation is minted or published.

A selected native recipe must have a current-phase `gridMapping` whose ordered
cells and block states match its saved native correspondence and the Source.
There is no endpoint-only restriction on materialized recipes: an intermediate
phase is admissible if the existing Source mapping actually proves it. Nominal
full-height aliases cannot substitute for a different current phase.

`Prepared.peers()` is immutable. Each `Peer` exposes its source-local root and
matched native head cells. `payload()` returns a defensive metadata-only decode
using `readForCouplerReceipt`, never ordinary payload deserialization with nested
tile/capability factories. The result remains a materialized recipe; the future
caller must use its existing chosen capture and publication path.

Rechecks require the same actual owner, ownership epoch, Source, view, scope,
root/tile/assembly/payload identities, membership, channels, primitive settings,
phase, target, passive role, motion revision and observed head scalars. They do not serialize payloads or rely
on carried `motionRevision`, which need not change for every setting edit.
Current head points are compared with the frozen observations; bent links are
not subjected to an invented opposing-world-normal test. Owner/view/Source and
live root/assembly/payload references are weak, with no global cache.

Limits are 64 generic roots, 65,536 aggregate membership and examined payload
rows, and 64 MiB conservative retained metadata. Discovery reserves a bounded
worst case before invoking the existing callback. Frozen byte budgets include
cell and decode overhead; rejection occurs before retaining the next recipe.
Independent payload overlap, nested gear frames, captured rigid-arm links,
pending release or recovery, held roots and stored sections refuse explicitly.
Joined members share their existing controller root and are not independent
captures.

The caller must recheck and install every returned peer in one existing batch,
or prepare again after the first Source change. This helper grants no mutation
authority and provides no runtime motion, collision, journal or public-activation
acceptance. The optional `PEERS_ONLY` fixture reuses the actual native two-arm
placement scene before motion. The accepted focus made 49 assertions: two
separate tip-only recipes, six examined rows and 39,280 conservative retained
bytes; exact Source/scope/phase/target retained; defensive metadata-only getter;
worker rejection; and a public carried channel edit invalidating the prepared
result despite unchanged Source identity and motion revision. Java 8 dedicated
linkage and standard assembly also passed. Materialized-peer live coverage,
accepted-passive-to-same-target command coverage, large-budget stress and the
actual chosen capture batch remain pending. No allocation-free recheck claim is
made; it deliberately observes current Source heads before the one-off install.
