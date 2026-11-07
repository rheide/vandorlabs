package com.vandorlabs.segmented;

/** Stable catalog identities; model choices reuse the existing whole-door/per-cell texture fields. */
public final class SegmentedArtworkPolicy {
    public static final String CATEGORY="Doors/Segmented";
    private static final String[] GATES={"segmented_gate_picket","segmented_gate_industrial","segmented_gate_lattice"};
    private static final String[] TILES={"segmented_tile_raised_black_panels","segmented_tile_ribbed_blue_steel","segmented_tile_spiral_aluminum_glazing"};
    private SegmentedArtworkPolicy(){}
    public static String gateId(int kind){if(kind<0||kind>=GATES.length)throw new IllegalArgumentException("Segmented gate kind");return GATES[kind];}
    public static String tileId(int kind){if(kind<0||kind>=TILES.length)throw new IllegalArgumentException("Segmented tile kind");return TILES[kind];}
    public static int gate(String id){for(int kind=0;kind<GATES.length;kind++)if(GATES[kind].equals(id))return kind;return SegmentedGateJoin.NONE;}
    public static boolean translucent(String id){return TILES[2].equals(id);}
    public static boolean segmentedOnly(String id){if(gate(id)>=0)return true;for(String tile:TILES)if(tile.equals(id))return true;return false;}
    public static boolean complex(String id){return gate(id)>=0||translucent(id);}
    /** Category moves must not narrow the established general-material eligibility of these saved IDs. */
    public static boolean legacyGeneral(String id){return "segmented_door_panel".equals(id)||"segmented_door_panel_light".equals(id);}
}
