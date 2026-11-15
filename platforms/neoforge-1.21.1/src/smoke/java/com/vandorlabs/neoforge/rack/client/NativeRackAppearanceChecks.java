package com.vandorlabs.neoforge.rack.client;

import com.vandorlabs.neoforge.rack.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Exact atlas/record proof shared with the two-action Rack capture journey. */
final class NativeRackAppearanceChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Rack appearance: "+why);}
    static void verify(){
        for(var facing:Direction.values()) {
            var model=NativeRackClient.facing(facing);var state=NativeRackBindings.BLOCK.defaultBlockState().setValue(NativeRackBlock.FACING,facing);
            var original=ModelData.builder().with(NativeRackEntity.VISUAL,new NativeRackEntity.Visual(1,2,1,-1,false)).build();
            var selected=ModelData.builder().with(NativeRackEntity.VISUAL,new NativeRackEntity.Visual(1,2,1,0,false)).build();
            var random=RandomSource.create(0);var before=model.getQuads(state,null,random,original,null);var after=model.getQuads(state,null,random,selected,null);
            need(before.size()==after.size(),"identical record count "+facing);need(after==model.getQuads(state,null,random,selected,null),"cached housing variants "+facing);
            int outer=0,front=0;
            for(int index=0;index<before.size();index++) {
                var a=before.get(index);var b=after.get(index);
                if(outer(a,facing)){outer++;need(b.getSprite().contents().name().toString().equals(com.vandorlabs.neoforge.ModernFinishCatalog.option(0).texture()),"selected outside material "+facing);}
                else {if(a.getDirection()==facing)front++;need(a==b&&Arrays.equals(a.getVertices(),b.getVertices()),"exact original front/interior records "+facing+"/"+index);}
                noLetters(b);
            }
            need(outer>0&&front>0,"outside and front both exercised "+facing);
            for(int slot=0;slot<4;slot++)for(var quad:model.moving(slot,2))noLetters(quad);
        }
        var mc=Minecraft.getInstance();var item=mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(NativeRackBindings.AUTOPILOT),mc.level,mc.player,0);
        for(int selector=0;selector<7;selector++)for(var quad:item.getQuads(null,selector==6?null:Direction.from3DDataValue(selector),RandomSource.create(0)))noLetters(quad);
        System.out.println("[vandorlabs][rack-capture] appearance PASS checks="+checks+" all6frontOuter/cachedMaterialRecords/atlasAliases/movingSeatedNoLettering");
    }
    private static boolean outer(BakedQuad quad,Direction front){if(quad.getDirection()==front)return false;int side=quad.getDirection().get3DDataValue(),axis=side<2?1:side<4?2:0;int[] data=quad.getVertices();int stride=data.length/4;float plane=(side&1)==0?0:1;for(int v=0;v<4;v++)if(Math.abs(Float.intBitsToFloat(data[v*stride+axis])-plane)>1e-6)return false;return true;}
    private static void noLetters(BakedQuad quad){
        var sprite=quad.getSprite();if(!sprite.contents().name().toString().equals("vandorlabs:blocks/module_rack/autopilot_module"))return;
        int[] data=quad.getVertices();int stride=data.length/4;double minU=99,minV=99,maxU=-99,maxV=-99;
        for(int vertex=0;vertex<4;vertex++){double u=(Float.intBitsToFloat(data[vertex*stride+4])-sprite.getU0())/(sprite.getU1()-sprite.getU0())*16,v=(Float.intBitsToFloat(data[vertex*stride+5])-sprite.getV0())/(sprite.getV1()-sprite.getV0())*16;minU=Math.min(minU,u);minV=Math.min(minV,v);maxU=Math.max(maxU,u);maxV=Math.max(maxV,v);}
        need(!(minU<8.75&&maxU>4.75&&minV<15&&maxV>13.75),"no emitted moving/seated variant samples archived NAV texels");
    }
}
