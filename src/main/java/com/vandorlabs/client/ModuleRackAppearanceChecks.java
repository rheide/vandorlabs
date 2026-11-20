package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockModuleRack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Exact packaged-record regression; no sprite-name assumption decides which faces may change. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class ModuleRackAppearanceChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Rack appearance: "+why);}
    static void verify(Minecraft mc) {
        BlockModuleRack block=(BlockModuleRack)net.minecraft.block.Block.getBlockFromName("vandorlabs:module_rack");
        for(EnumFacing facing:EnumFacing.values()) {
            net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockModuleRack.FACING,facing);
            IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
            IExtendedBlockState original=((IExtendedBlockState)state).withProperty(BlockModuleRack.HOUSING,-1)
                    .withProperty(BlockModuleRack.OCCUPIED_MASK,1).withProperty(BlockModuleRack.MODULE_MASK,1).withProperty(BlockModuleRack.MODULE_KINDS,2);
            IExtendedBlockState selected=original.withProperty(BlockModuleRack.HOUSING,0);
            int outer=0,front=0;
            for(int pass=0;pass<7;pass++) {
                int selector=(pass+6)%7;EnumFacing side=selector==6?null:EnumFacing.getFront(selector);
                List<BakedQuad> before=model.getQuads(original,side,0),after=model.getQuads(selected,side,0);
                need(before.size()==after.size(),"same record count "+facing+"/"+selector);
                need(after==model.getQuads(selected,side,0),"cached material records "+facing+"/"+selector);
                for(int index=0;index<before.size();index++) {
                    BakedQuad a=before.get(index),b=after.get(index);boolean outside=outer(a.getVertexData(),a.getFace().getIndex(),facing.getIndex());
                    if(outside){outer++;need(b.getSprite().getIconName().equals(b.getFace()==facing.getOpposite()?"vandorlabs:blocks/resource_ports/power_in_off":com.vandorlabs.tiles.ScreenHousingTextures.texture(0)),"outer plane gets chosen material "+facing+"/"+index);}
                    else {if(a.getFace()==facing)front++;need(a==b&&Arrays.equals(a.getVertexData(),b.getVertexData()),"front/interior exact authored record retained "+facing+"/"+selector+"/"+index);}
                    noLetters(b);
                }
            }
            for(int slot=0;slot<4;slot++)for(int charge=0;charge<5;charge++){
                List<BakedQuad> battery=ModuleRackModel.batteryQuads(facing,slot,charge);int red=0,green=0;
                for(BakedQuad quad:battery){String sprite=quad.getSprite().getIconName();need(!sprite.contains("missingno"),"battery sprite present");if(sprite.equals("vandorlabs:blocks/resource_ports/power_in_off"))red++;if(sprite.equals("vandorlabs:blocks/resource_ports/power_in_on"))green++;}
                int axis=facing.getAxis()==EnumFacing.Axis.X?0:facing.getAxis()==EnumFacing.Axis.Y?1:2;
                int sign=facing.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:-1;
                float bodyFront=-Float.MAX_VALUE;
                for(BakedQuad quad:battery)if(quad.getFace()==facing&&quad.getSprite().getIconName().equals("vandorlabs:blocks/module_rack/dark")){
                    int[] data=quad.getVertexData();int stride=data.length/4;
                    for(int vertex=0;vertex<4;vertex++)bodyFront=Math.max(bodyFront,sign*Float.intBitsToFloat(data[vertex*stride+axis]));
                }
                need(bodyFront>-Float.MAX_VALUE,"battery body front emitted");
                for(BakedQuad quad:battery)if(quad.getSprite().getIconName().equals("vandorlabs:blocks/resource_ports/power_in_off")||quad.getSprite().getIconName().equals("vandorlabs:blocks/resource_ports/power_in_on")){
                    need(quad.getFace()==facing,"battery artwork faces out of rack");int[] data=quad.getVertexData();int stride=data.length/4;
                    for(int vertex=0;vertex<4;vertex++)need(sign*Float.intBitsToFloat(data[vertex*stride+axis])>bodyFront+1e-5F,"battery artwork lies in front of opaque cassette");
                }
                need(red==1&&green==(charge==0?0:1),"battery emitted Port bar/charge geometry "+facing+"/"+slot+"/"+charge);
            }
            need(outer>0&&front>0,"both outer and front records exercised "+facing);
            for(int slot=0;slot<4;slot++) {
                // Source moving meshes retain decoded records; inspect the same typed baked source independently.
                try {
                    java.lang.reflect.Field field=ModuleRackModel.class.getDeclaredField("autopilotModules");field.setAccessible(true);
                    IBakedModel moving=((IBakedModel[])field.get(model))[slot];
                    for(int selector=0;selector<7;selector++)for(BakedQuad quad:moving.getQuads(null,selector==6?null:EnumFacing.getFront(selector),0))noLetters(quad);
                } catch(Exception failure){throw new AssertionError("Autopilot moving model",failure);}

            }
        }
        System.out.println("[vandorlabs][reprolab] module-rack appearance PASS checks="+checks+" all6frontOuter/exactPackagedAliases/movingSeatedNoLettering/cache");
    }
    private static boolean outer(int[] data,int side,int front) {
        if(side==front)return false;int stride=data.length/4,axis=side<2?1:side<4?2:0;float plane=(side&1)==0?0:1;
        for(int vertex=0;vertex<4;vertex++)if(Math.abs(Float.intBitsToFloat(data[vertex*stride+axis])-plane)>1e-6)return false;
        return true;
    }
    private static void noLetters(BakedQuad quad) {
        if(!quad.getSprite().getIconName().equals("vandorlabs:blocks/module_rack/autopilot_module"))return;
        int[] data=quad.getVertexData();int stride=data.length/4;double minU=99,minV=99,maxU=-99,maxV=-99;
        for(int vertex=0;vertex<4;vertex++){double u=quad.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[vertex*stride+4])),v=quad.getSprite().getUnInterpolatedV(Float.intBitsToFloat(data[vertex*stride+5]));minU=Math.min(minU,u);minV=Math.min(minV,v);maxU=Math.max(maxU,u);maxV=Math.max(maxV,v);}
        need(!(minU<8.75&&maxU>4.75&&minV<15&&maxV>13.75),"no emitted variant samples archived NAV texels");
    }
}
