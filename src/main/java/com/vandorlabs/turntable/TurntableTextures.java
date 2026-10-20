package com.vandorlabs.turntable;

/** Stable surface settings, independent of the shared material catalog's numeric choices. */
public final class TurntableTextures {
    public static final String[] IDS={"original_off","original_on","stone_iron","rotary_deck","basalt","graphite_ivory"};
    public static final String[] LABELS={"Dormant","Active","Iron","Rotary","Basalt","Ivory"};
    private static final String[] FILES={"turntable_block_top","turntable_block_top_on","turntable_block_top_stone_iron","turntable_block_top_rotary_deck","turntable_block_top_basalt","turntable_block_top_graphite_ivory"};
    private TurntableTextures(){}
    public static int material(String id){if(id!=null&&id.startsWith("material:")){String key=id.substring(9);String[] ids=com.vandorlabs.tiles.ScreenHousingTextures.IDS;for(int i=0;i<ids.length;i++)if(ids[i].equals(key))return i;}return -1;}
    public static int choice(String id){int i=index(id);return i>=0?i:IDS.length+material(id);}
    public static String fromChoice(int choice){return choice<IDS.length?IDS[choice]:"material:"+com.vandorlabs.tiles.ScreenHousingTextures.IDS[choice-IDS.length];}
    public static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
    public static String normalize(String id,boolean on){return index(id)<0&&material(id)<0?IDS[on?1:0]:id;}
    public static String texture(String id,boolean high){int material=material(id);if(material>=0)return com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(material));int i=index(id);return "vandorlabs:blocks/turntable/"+FILES[i<0?0:i]+(high?"":"_low");}
    public static String label(String id){int material=material(id);if(material>=0){String label=com.vandorlabs.tiles.ScreenHousingTextures.label(com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(material));return label==null?com.vandorlabs.tiles.ScreenHousingTextures.IDS[material].replace('_',' '):label;}int i=index(id);return LABELS[i<0?0:i];}
}
