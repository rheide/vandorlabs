# Vandor Labs project guidance

- Keep finished build artifacts in `build/libs/`. Copy a build elsewhere only when the user explicitly requests a destination.
- Use Java 8 for Gradle 4.9 builds. Run the live client suite for client-visible or gameplay changes.
- Build only the standard mod JAR. Never produce original-textures JARs; the original texture directory is an archive and validation reference only.
- Keep documentation technical and user-facing. Do not commit task logs, chat transcripts, authoring instructions, personal paths or machine-specific details.
- Item icons must be slightly smaller than their inventory/hotbar slots, with visible padding on every side. Account for the GUI rotation and depth, and check every generated variant; fitting the unrotated model into 16×16 coordinates does not guarantee that its icon fits.
