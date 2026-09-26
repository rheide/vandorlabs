# Default mixed-resolution textures

This directory contains the default runtime textures imported from the selected
source archive (SHA-256
`4099f7ddc5fc1b5a689d58b2ca3c685a2812b9c2b1462016e256a857f57c68e2`).

The 109 legacy animated PNG strips were converted losslessly to Vandor Labs' VLTA
format. The four vertically stretched single detailed-door atlases were
restored to 128x128 square textures during import; detailed double-door
atlases are 256x256. After retired door resources are removed, the directory
has exactly the same 441 runtime texture paths as the generated build: 332 PNG
textures and 109 compact animations.
Models, blockstates, catalogs, and code continue to come from the generated
canonical build. The normal `./gradlew clean build --no-daemon` command
packages this texture tree into `vandorlabs-1.1.jar`.
