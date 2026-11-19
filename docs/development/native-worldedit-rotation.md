# Native WorldEdit rotation

WorldEdit 7.3.8 on NeoForge 1.21.1 already rotates block-state directions on ordinary programmable Doors, diagonal walls and wall-mounted propulsion wedges. Horizontal rotation preserves the Door hinge and upper/lower state and the wall's inverted state.

Floor- and ceiling-mounted propulsion wedges encode their triangular corner in the block ID. The optional compatibility hook applies the native block's rotation for quarter-turns around the vertical axis and passes through the clipboard's existing tile data. It covers all four corner variants of the Rocket Thruster, Ion Drive, Plasma Vent and Impulse Engine. WorldEdit handles other blocks and transforms through its own rules.

From the NeoForge platform directory, run `python3 test_worldedit_rotation.py --worldedit-jar <path>` with the [WorldEdit 7.3.8 Modrinth distribution](https://modrinth.com/plugin/worldedit/version/WTAFvuRx). The isolated server copies actual world blocks through WorldEdit's clipboard and pastes them after 90°, 180° and 270° turns. Running the same test without the JAR checks dedicated startup with the optional integration absent.
