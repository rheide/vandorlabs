# Experimental NeoForge Dynmap textures

The native mod JAR includes static Dynmap textures for Programmable Surfaces and Copy Controllers. The resource generator reads the same native housing models and artwork used by the game. Surfaces show their default artwork on the configured facing and housing artwork on the other faces. Copy Controllers show the housing textures in their placed orientation. The map uses a full-cube housing and static default artwork. Configured Surface artwork, joined drawing UVs, activity and other controller families require separate integration.

The focused renderer uses Dynmap's experimental NeoForge 1.21.1 implementation from upstream commit `23728ba`. The released Dynmap platforms currently exclude NeoForge. Normal Vandor Labs builds require neither a Dynmap JAR nor its internal renderer classes. [Upstream NeoForge pull request](https://github.com/webbukkit/dynmap/pull/4183)

Run `python3 test_dynmap_surface.py --jar <native Vandor Labs JAR> --dynmap-jar <experimental Dynmap JAR>` from the NeoForge platform directory. The isolated server places up- and down-facing Surface pads and Copy Controllers, renders a flat map through Dynmap, and compares the resulting PNG pixels with the native artwork in the supplied Vandor Labs JAR. The fixture records both hashes before launch and checks them again after the render.
