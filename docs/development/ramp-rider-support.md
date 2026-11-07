# Ramp rider support

The released support helper distinguishes availability from downward/slow vertical motion. Only moving Player and Item support collision is clipped to the feet, with a maximum half-block shallow overlap. Recent proven floors remain eligible for correction for twenty ticks within three quarters of a block horizontally and one thousandth of a block vertically. Ordinary side, ceiling and foreign collisions remain authoritative.

The independent Forge reference captures 18,400 support profiles, 28,800 recent-floor event profiles and 2,000 exact vanilla axis-clipping queries. It includes both logical sides, moving/stationary surfaces, eligibility boundaries, stale/future ticks and dimension changes. The expanded reference SHA-256 is `79dfbdeaefef0ed5eb70e523a04afe51b59ff6cd4ea1dcb613ba9ba1b7040ae7`. The shared scalar policy and native axis-clipping adapter match all 49,200 cases exactly. Native world transport and correction still need separate acceptance.

The private native obstacle adapter consumes the complete lazy collision iterator inside the own-root carrying scope, restores an enclosing scope even after failure, and refuses oversized or cold queries before traversal. Source selection and original ownership remain the controller's responsibility. These APIs are not public Ramp activation or a completed rider implementation.

The dedicated world focus checks actual lazy block-shape calls, nested carrying roots, retained foreign collisions, exception cleanup, cold queries without loading, worker refusal, real Player/Item shallow support and vanilla Boat/Minecart eligibility. Transport packets, recent-floor integration after materialization and moving-world acceptance remain pending.

Transport admission has an independent 14,483-case actual Forge packet-handler reference, expanded SHA-256 `8651842fa51549993a15cca2fb2085eefbb232e101157f7089253af8f52b9ca3`. The shared correction policy matches every position, momentum, grounding and ordering result, including consuming a newer sequence before rejecting a physical correction. The ordinary released packet is sixty bytes. Native wire encoding, real transport and client interpolation are separate work; this reference alone does not establish a network implementation.

Ramp controller queries use broad Entity selection; vanilla boats and minecarts are not blanket-excluded. The separate arm vehicle predicate must not be substituted for those controller-specific filters. Native Vandor Vehicle Mode remains deferred.
