# Redstone channel input range

Both adapters use the existing `ChannelList` policy: enabled channel IDs are **1 through 2,147,483,647**. Zero and an empty bank mean disconnected; individual dialogs may use an empty bank to select their documented local-redstone behavior. A bank accepts at most 64 entries and canonicalizes ordering, duplicate IDs and zero entries. Negative IDs, integer overflow and incomplete comma lists cannot be applied.

A complete 64-entry bank of ten-digit IDs needs 766 characters including comma/space separators. The shared text limit remains 768 characters. Forge Coupler and Segmented Door fields now use that limit; their former 256-character limit could truncate a bank that the shared parser and packet already supported. Existing converted native editors already use the full shared limit and int-backed wire fields.

Copy Controller channel translation now uses the same address space. Its signed increment is bounded to −2,147,483,646 through +2,147,483,646, the largest shift that can leave any enabled ID within the address space. The GUI, packet, tile settings and saved increment use this same policy. Each translated enabled ID is checked with a widened addition before publication or material consumption. Overflow and translation below channel 1 refuse the complete plan; zero remains disconnected. Inventories, opaque capabilities, artwork, levels and pulse deadlines are not translated.

The saved single-channel cockpit display gate and coupling recovery signal IDs also retain the full int-backed range. Their previous 65,535-ID checks disagreed with normal channel editing. Coupling's separate 65,535-entry frozen-bank memory budget, signal levels, radio ranges, identity checks, complete-bank validation and recovery ownership guards remain intact. No coupling geometry or movement behavior changes.

The audit covers Forge channel dialogs and converted native receivers, transmitters, mounted controls, readers, Doors, Trapdoors, Segmented Doors, Lights, propulsion and Screen/control-row editing. The native Copy Controller and native Vehicle/Coupler families remain unconverted; their later adapters must reuse this policy rather than copy the former 16-bit ceiling.

Focused validation includes shared boundary and full-bank parsing, actual Forge Copy Controller packet/settings/save round trips, immutable copy-schema translation, cockpit gate saves and old/new coupling signal guards. Native checks round-trip full-width IDs and 64-entry banks through ten actual editor codec routes. Runtime and live-dialog evidence is recorded separately from those world-free checks; this is not a release gate.

## Focused acceptance

The Forge boundary/save/packet corpus passes 50 assertions plus the existing 34 copy-schema checks. Converted native editors pass six channel-bank profiles across ten actual codec routes (126 checks), including the complete 766-character bank. Dedicated-server linkage passes independently.

The exact standard-JAR client focus exercises actual queued Copy Controller transactions at 65,535, 67,002 and the upper int boundary (24 assertions), plus coupling holds at 67,002 and 2,147,483,647 with asynchronous durable preparation, cold journal loading, signal ownership and malformed-journal quarantine (48 checks). Cold loading here is an in-process unload/reload of the persisted journal, not an abrupt process-restart test. The existing 3,773-case copy-plan matrix also passes. Actual Copy Controller, Coupler and Segmented Door widgets accept full-width values and complete banks; compact and normal Copy Controller dialog captures retain the established layout.

The former Copy Controller backend rejects 67,002 before the fix; the same focused path now passes. This validation does not enable native Vehicle/Coupler or native Copy Controller implementations and does not constitute a release gate.
