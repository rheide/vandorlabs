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
    static ResourceLocation white(){return reference?new ResourceLocation("vandorlabs","textures/blocks/screen_white.png"):WHITE;}
    static ResourceLocation input(String id,boolean off){if(reference)return legacyInput(id,off);ResourceLocation[] pair=INPUTS.get(id);if(pair==null){pair=new ResourceLocation[]{legacyInput(id,false),legacyInput(id,true)};INPUTS.put(id,pair);}return pair[off?1:0];}
    private static ResourceLocation legacyInput(String id,boolean off){return new ResourceLocation("vandorlabs","textures/blocks/console_inputs/"+id+(off?"_off":"_static")+".png");}
}
