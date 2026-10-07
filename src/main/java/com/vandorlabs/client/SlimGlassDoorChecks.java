package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;

/** Checks native sprites and slot padding after GUI rotation for every new door item variant. */
final class SlimGlassDoorChecks {
    static void run(Minecraft mc){
        int checked=0;
        for(String id:new String[]{"programmable_door","large_programmable_door"})for(int detail=0;detail<2;detail++)for(boolean framed:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})for(boolean hinges:new boolean[]{false,true}){
            if(sliding && !hinges)continue;
            ItemStack stack=new ItemStack(Block.getBlockFromName("vandorlabs:"+id));NBTTagCompound tag=new NBTTagCompound();
            tag.setInteger("SpaceDesign",29);tag.setInteger("SpaceDetail",detail);tag.setBoolean("SpaceFramed",framed);tag.setBoolean("SpaceDoorSliding",sliding);tag.setBoolean("SpaceDoorHinges",hinges);stack.setTagInfo("SpaceDoorSettings",tag);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
            require(model!=mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),"missing Slim Glass item");
            ItemTransformVec3f gui=model.getItemCameraTransforms().gui;int faces=0;boolean nativeTexture=false;
            for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(null,side,0)){
                faces++;nativeTexture|=quad.getSprite().getIconName().equals("vandorlabs:blocks/glass_doors/"+(detail==0?"low":"medium")+"/slim_glass");
                int[] data=quad.getVertexData();int stride=data.length/4;
                for(int v=0;v<4;v++){
                    double x=(Float.intBitsToFloat(data[v*stride])-.5)*gui.scale.x,y=(Float.intBitsToFloat(data[v*stride+1])-.5)*gui.scale.y,z=(Float.intBitsToFloat(data[v*stride+2])-.5)*gui.scale.z;
                    double rz=Math.toRadians(gui.rotation.z),rx=Math.toRadians(gui.rotation.x),ry=Math.toRadians(gui.rotation.y);
                    double xx=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=xx;
                    double yy=y*Math.cos(rx)-z*Math.sin(rx);z=y*Math.sin(rx)+z*Math.cos(rx);y=yy;
                    xx=x*Math.cos(ry)+z*Math.sin(ry);x=xx+.5+gui.translation.x;y+=.5+gui.translation.y;
                    require(x>.035 && x<.965 && y>.035 && y<.965,"Slim Glass icon lacks padding "+id+" "+detail+" "+framed+" "+sliding+" "+hinges+" x="+x+" y="+y);
                }
            }
            require(faces>0 && nativeTexture,"Slim Glass native sprite missing");checked++;
        }
        System.out.println("[vandorlabs][reprolab] slim-glass-door-icons PASS variants="+checked);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
