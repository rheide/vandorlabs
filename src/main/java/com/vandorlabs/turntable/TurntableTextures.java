package com.vandorlabs.turntable;

/** Stable surface settings, independent of the shared material catalog's numeric choices. */
public final class TurntableTextures {
    public static final String[] IDS={"original_off","original_on","stone_iron","rotary_deck","basalt","graphite_ivory"};
    public static final String[] LABELS={"Original Off","Original On","Stone and Iron","Rotary Deck","Embossed Basalt","Graphite and Ivory"};
    private static final String[] FILES={"turntable_block_top","turntable_block_top_on","turntable_block_top_stone_iron","turntable_block_top_rotary_deck","turntable_block_top_basalt","turntable_block_top_graphite_ivory"};
    private TurntableTextures(){}
    public static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
    public static String normalize(String id,boolean on){return index(id)<0?IDS[on?1:0]:id;}
    public static String texture(String id,boolean high){int i=index(id);return "vandorlabs:blocks/turntable/"+FILES[i<0?0:i]+(high?"":"_low");}
    public static String label(String id){int i=index(id);return LABELS[i<0?0:i];}
}
