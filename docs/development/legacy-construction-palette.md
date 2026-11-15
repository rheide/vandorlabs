# Legacy construction state schema

`exportLegacyConstructionStateSchema` exports the actual Minecraft 1.12.2 vanilla block-state schema without loading a world or assigning modded numeric registry identifiers. It records each canonical block identifier, property name and allowed value, with Source data version 1343. The baseline has 254 blocks, 386 properties and 1,593 allowed values.

The default output is `build/legacy-construction-states.json`. `-PconstructionSchemaOutput=<path>` selects an explicit destination. `testLegacyConstructionStateSchema` compares that file with the actual initialized Source registry. Neither task assembles a JAR or changes production resources by default.

A construction importer must validate the complete old block identity and property schema before applying vanilla data fixing. Defaults for properties introduced after 1.12 may be completed only for this recognized Source schema. Unknown identifiers, properties, values and future schemas must remain refused; registry lookup or AIR fallback cannot establish compatibility. A converted private snapshot must retain opaque legacy data separately from current known tile fields. Original failed-load data and journal before-images remain unchanged.

This export describes vanilla states. It neither registers missing mod blocks nor authorizes native Vehicle Mode. Construction designs containing unavailable native identifiers must be retained and refused rather than silently replacing them.
