package com.vandorlabs.client;

import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import java.util.*;

/** Identifiers only: model/texture objects are still resolved after resource reloads. */
final class RenderIdentifierCache {
    static boolean reference;
    private static final ResourceLocation WHITE=new ResourceLocation("vandorlabs","textures/blocks/screen_white.png");
    private static final ModelResourceLocation[][] GEARS=new ModelResourceLocation[4][2];
    private static final Map<String,ResourceLocation[]> INPUTS=new LinkedHashMap<String,ResourceLocation[]>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<String,ResourceLocation[]> entry){return size()>256;}};
    static {for(int size=0;size<4;size++)for(int wheel=0;wheel<2;wheel++)GEARS[size][wheel]=legacyGear(size,wheel!=0);}
    private RenderIdentifierCache(){}
    static ModelResourceLocation gear(int size,boolean wheel){size=Math.max(0,Math.min(3,size));return reference?legacyGear(size,wheel):GEARS[size][wheel?1:0];}
    private static ModelResourceLocation legacyGear(int size,boolean wheel){return new ModelResourceLocation("vandorlabs:landing_gear_"+com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES[size]+(wheel?"_wheel":"_fixed"),"inventory");}
    private static final Map<String,ModelResourceLocation[][]> NON_WHEELED=new HashMap<>();
    static ModelResourceLocation gear(String name,int size,boolean moving){
        if("landing_gear".equals(name))return gear(size,moving);
        ModelResourceLocation[][] ids=NON_WHEELED.computeIfAbsent(name,key->{ModelResourceLocation[][] values=new ModelResourceLocation[4][2];for(int s=0;s<4;s++)for(int p=0;p<2;p++)values[s][p]=new ModelResourceLocation("vandorlabs:"+key+"_"+com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES[s]+(p==1?"_wheel":"_fixed"),"inventory");return values;});
        return ids[Math.max(0,Math.min(3,size))][moving?1:0];
    }
    private static final Map<String,ModelResourceLocation> GEAR_TIPS=new HashMap<>();
    static ModelResourceLocation gearTip(String family,int size,boolean front){String key=family+"_"+com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES[Math.min(3,size)]+(front?"_nose_front":"_nose_rear");return GEAR_TIPS.computeIfAbsent(key,name->new ModelResourceLocation("vandorlabs:"+name,"inventory"));}
    static ResourceLocation white(){return reference?new ResourceLocation("vandorlabs","textures/blocks/screen_white.png"):WHITE;}
    static ResourceLocation input(String id,boolean off){id=com.vandorlabs.tiles.ScreenHousingTextures.canonicalScreen(id);if(reference)return legacyInput(id,off);ResourceLocation[] pair=INPUTS.get(id);if(pair==null){pair=new ResourceLocation[]{legacyInput(id,false),legacyInput(id,true)};INPUTS.put(id,pair);}return pair[off?1:0];}
    private static ResourceLocation legacyInput(String id,boolean off){return new ResourceLocation("vandorlabs","textures/blocks/console_inputs/"+id+(off?"_off":"_static")+".png");}
}
