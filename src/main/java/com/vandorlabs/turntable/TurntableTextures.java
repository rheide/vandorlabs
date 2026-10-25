package com.vandorlabs.turntable;

/** Stable surface settings, independent of the shared material catalog's numeric choices. */
public final class TurntableTextures {
    public static final String[] IDS={"original_off","original_on","stone_iron","rotary_deck","basalt","graphite_ivory","landing","landing-grey","maintenance","maintenance-grey","docking","docking-grey","copier-input","copier-output"};
    public static final String[] LABELS={"Dormant","Active","Iron","Rotary","Basalt","Ivory","Landing","Landing Grey","Maintenance","Maintenance Grey","Docking","Docking Grey","Copier Input","Copier Output"};
    private static final String[] FILES={"turntable/turntable_block_top","turntable/turntable_block_top_on","turntable/turntable_block_top_stone_iron","turntable/turntable_block_top_rotary_deck","turntable/turntable_block_top_basalt","turntable/turntable_block_top_graphite_ivory","surface/landing","surface/landing-grey","surface/maintenance","surface/maintenance-grey","surface/docking","surface/docking-grey","surface/copier-input","surface/copier-output"};
    private TurntableTextures(){}
    public static int material(String id){if(id!=null&&id.startsWith("choice:"))try{int value=Integer.parseInt(id.substring(7));return value>=com.vandorlabs.tiles.CustomBlockMaterials.ID_BASE&&com.vandorlabs.tiles.ScreenHousingTextures.validChoice(value)?value:-1;}catch(NumberFormatException invalid){return -1;}if(id!=null&&id.startsWith("material:")){String key=id.substring(9);String[] ids=com.vandorlabs.tiles.ScreenHousingTextures.IDS;for(int i=0;i<ids.length;i++)if(ids[i].equals(key))return i;}return -1;}
    public static int choice(String id){int i=index(id);return i>=0?i:IDS.length+material(id);}
    public static String fromChoice(int choice){return choice<IDS.length?IDS[choice]:"material:"+com.vandorlabs.tiles.ScreenHousingTextures.IDS[choice-IDS.length];}
    public static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
    public static String fromMaterialChoice(int choice){if(!com.vandorlabs.tiles.ScreenHousingTextures.validChoice(choice))throw new IllegalArgumentException("Unknown surface texture");for(int i=0;i<com.vandorlabs.tiles.ScreenHousingTextures.IDS.length;i++)if(com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(i)==choice)return "material:"+com.vandorlabs.tiles.ScreenHousingTextures.IDS[i];return "choice:"+choice;}
    public static int materialChoice(String id){int existing=material(id);if(existing>=0)return existing>=com.vandorlabs.tiles.ScreenHousingTextures.IDS.length?existing:com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(existing);int index=index(id);if(index<0)return 0;String key=index<6?"turntable_"+IDS[index]:"surface_"+IDS[index].replace('-','_');for(int i=0;i<com.vandorlabs.tiles.ScreenHousingTextures.IDS.length;i++)if(key.equals(com.vandorlabs.tiles.ScreenHousingTextures.IDS[i]))return com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(i);return 0;}
    public static boolean valid(String id){return index(id)>=0||material(id)>=0;}
    public static String normalize(String id,boolean on){return index(id)<0&&material(id)<0?IDS[on?1:0]:id;}
    public static String texture(String id,boolean high){
        int material=material(id);
        if(material>=0){int choice=material>=com.vandorlabs.tiles.ScreenHousingTextures.IDS.length?material:com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(material);String texture=com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice);return !high&&"Surfaces".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice))?texture+"_low":texture;}
        int index=index(id);return "vandorlabs:blocks/"+FILES[index<0?0:index]+(high?"":"_low");
    }
    public static String label(String id){int material=material(id);if(material>=0){String label=com.vandorlabs.tiles.ScreenHousingTextures.label(material>=com.vandorlabs.tiles.ScreenHousingTextures.IDS.length?material:com.vandorlabs.tiles.ScreenHousingTextures.choiceAt(material));return label==null?(material>=com.vandorlabs.tiles.ScreenHousingTextures.IDS.length?"Custom":com.vandorlabs.tiles.ScreenHousingTextures.IDS[material].replace('_',' ')):label;}int i=index(id);return LABELS[i<0?0:i];}
}
