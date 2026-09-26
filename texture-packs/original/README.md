# Archived original textures

This directory preserves the original artwork for all 441 retained runtime
textures: 332 PNG textures and 109 compact VLTA animations. Resources used
only by retired blocks have been removed from both texture trees.

Build a one-off jar with this archived set using
`./gradlew clean buildOriginalTextures --no-daemon`. The resulting
`vandorlabs-1.1-original-textures.jar` is separate from the normal jar, which uses
the selected default textures under `texture-packs/default`.
