# Vandor Labs project guidance

- Keep finished build artifacts in `build/libs/`. Copy a build elsewhere only when the user explicitly requests a destination.
- Use Java 8 for Gradle 4.9 builds. Run the live client suite for client-visible or gameplay changes.
- Build only the standard mod JAR. Never produce original-textures JARs; the original texture directory is an archive and validation reference only.
