package com.vandorlabs.render;

/** Existing authored door families, detail aliases and numeric item-model keys. */
public final class SpaceDoorModels {
    public static final String[] DESIGNS={"observation","airlock","standard","security","reactor","viewport","laboratory","cargo","ventilation",
            "cargo_lift","blast_shield","glazed_hangar","quarantine_seal","reactor_barrier","modular_shutter","white_glass","dark_glass","plain_cargo","stepped_freight","observation_leaf","reinforced_leaf","warehouse_shutter","slotted_bay","cross_braced_bay","split_view_bay","offset_cargo","twin_observation","armored_biparting","service_freight","slim_glass"};
    public static final String[] DETAILS={"low","low"};
    public static final int FIRST_DOUBLE_DESIGN=21,DEFAULT_LARGE_DESIGN=23,DEFAULT_DESIGN=2;
    private SpaceDoorModels(){}
    public static String modelId(int design,boolean sliding,boolean framed){
        String family=DESIGNS[design];String prefix="space_"+("reactor".equals(family)?"reactor_service":family)+(sliding?"_sliding":"_rotating");
        return prefix+(design<5?"_door_":"_")+(framed?"framed":"bare");
    }
    /** Actual configured-item selector; detail1/2 intentionally share the low artwork. */
    public static String itemModel(com.vandorlabs.persistence.SpaceDoorData data,boolean large){
        return "detailed_doors/"+DETAILS[data.detail]+"/"+(large?"large_":"")+modelId(data.design,data.sliding,data.framed)+"_left_leaf"+(!data.sliding&&!data.hinges?"_no_hinges":"");
    }
    public static int metadata(int design,int detail,boolean framed,boolean paired,boolean right,int part){return (design<15?1+design*72:4321+(design-15)*72)+(detail*2+(framed?1:0))*12+(paired?6:0)+part*2+(right?1:0);}
    public static int metadata(int design,int detail,boolean framed,boolean paired,boolean right,int part,boolean sliding){return metadata(design,detail,framed,paired,right,part)+(sliding?1080:0);}
}
