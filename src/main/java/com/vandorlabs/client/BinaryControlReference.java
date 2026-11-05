package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import net.minecraft.block.*;
import net.minecraft.block.state.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Optional actual legacy renderer reference for the independent native binary-control adapter. */
final class BinaryControlReference {
    static void export(Minecraft mc){
        String output=System.getenv("VANDOR_BINARY_REFERENCE");if(output==null||output.isEmpty())return;JsonObject root=new JsonObject(),world=new JsonObject(),items=new JsonObject();root.addProperty("format",1);root.add("world",world);root.add("items",items);
        for(String id:new String[]{"push_button","rocker_switch"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);
            for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<(face.getAxis()==EnumFacing.Axis.Y?4:1);rotation++)for(boolean on:new boolean[]{false,true}){
                IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,face).withProperty(BlockVandorSwitch.ROTATION,rotation).withProperty(BlockVandorSwitch.ON,on);IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                for(int mount=0;mount<64;mount++){IBlockState extended=((IExtendedBlockState)state).withProperty(MountedControlGeometry.MOUNT,mount);world.add(id+"/"+face.getName()+"/"+rotation+"/"+on+"/"+mount,quads(model,extended));}
            }
            for(int mount=0;mount<64;mount++){
                ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",mount%4);tag.setInteger("BaseTilt",mount/4%4);tag.setInteger("TiltDirection",mount/16);stack.setTagInfo("RedstoneChannelSettings",tag);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
                JsonObject row=new JsonObject();row.add("quads",quads(model,null));ItemTransformVec3f gui=model.getItemCameraTransforms().gui;JsonArray transform=new JsonArray();for(float value:new float[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(value);row.add("gui",transform);items.add(id+"/"+mount,row);
            }
        }
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(output),StandardCharsets.UTF_8)){new Gson().toJson(root,writer);}catch(IOException e){throw new IllegalStateException("Cannot export binary reference",e);}System.out.println("[vandorlabs][reprolab] binary-control-reference PASS world="+world.entrySet().size()+" items="+items.entrySet().size());
    }
    private static JsonArray quads(IBakedModel model,IBlockState state){JsonArray result=new JsonArray();for(EnumFacing face:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad q:model.getQuads(state,face,0)){
        JsonObject row=new JsonObject();row.addProperty("sprite",q.getSprite().getIconName());row.addProperty("face",q.getFace().getName());row.addProperty("shade",q.shouldApplyDiffuseLighting());JsonArray points=new JsonArray();int[] raw=q.getVertexData();int stride=raw.length/4;for(int i=0;i<4;i++){JsonArray p=new JsonArray();for(int axis=0;axis<3;axis++)p.add(Float.intBitsToFloat(raw[i*stride+axis]));p.add((Float.intBitsToFloat(raw[i*stride+4])-q.getSprite().getMinU())/(q.getSprite().getMaxU()-q.getSprite().getMinU()));p.add((Float.intBitsToFloat(raw[i*stride+5])-q.getSprite().getMinV())/(q.getSprite().getMaxV()-q.getSprite().getMinV()));points.add(p);}row.add("points",points);result.add(row);
    }return result;}
    private BinaryControlReference(){}
}
