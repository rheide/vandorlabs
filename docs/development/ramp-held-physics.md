# Ramp held-source piston publication

Ramp journal preparation does not freeze source gameplay. Arming establishes a hold while its durable ownership record is being written. A block-write refusal alone is insufficient during that hold: vanilla piston movement can publish its destination even when the source replacement is denied.

The piston pre-event now refuses the complete move if its base, head, resolved pushed source, pushed destination or destroyed cell belongs to a held Ramp operation. Worlds without active holds return before resolving a piston structure. Discovery uses vanilla's bounded piston resolver, rather than scanning loaded blocks. The operation's existing scoped mutation bypass remains authoritative.

The dedicated regression prepares a real journal, waits for preparation, arms it and holds the asynchronous ARM write pending. The actual vanilla piston block-event path must leave both a pushed Stone source and a separately held empty-extension piston base untouched, with no moving destination or head. An unheld powered piston must still publish its moving block. Before this guard, the held Stone case fails because the destination is published despite the source-write refusal.

After the same fixed probe passes, the operation retires and the server saves normally. A fresh server process explicitly loads the journal directory and checks the original Stone and piston base, no phantom destination/head, no dropped duplicate and no remaining operation. This is focused piston ownership acceptance, not a full Ramp generation or release gate. Falling-block and TNT source admission is a separate capture-policy boundary.
