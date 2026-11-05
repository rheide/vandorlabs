package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Streaming actual Forge lever reference; no copied geometry or UV formulas. */
final class PowerLeverReference {
    static void export(Minecraft mc){
        String output=System.getenv("VANDOR_POWER_REFERENCE");if(output==null||output.isEmpty())return;int worlds=0,items=0;Gson gson=new Gson();
        try(Writer writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output),StandardCharsets.UTF_8))){
            writer.write("{\"format\":1,\"source\":\"Forge 1.12.2 actual power lever baked models and component collision\"}\n");
            for(String id:new String[]{"compact_power_lever","industrial_power_lever","small_power_lever","large_power_lever"}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);
                for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)for(boolean floor:new boolean[]{false,true})for(boolean on:new boolean[]{false,true})for(int size=0;size<2;size++)for(int mount=0;mount<64;mount++){
                    IBlockState state=block.getDefaultState().withProperty(BlockIndustrialLever.FACING,face).withProperty(BlockIndustrialLever.FLOOR,floor).withProperty(BlockIndustrialLever.POWERED,on);
                    IBlockState extended=((IExtendedBlockState)state).withProperty(BlockIndustrialLever.SIZE,size).withProperty(MountedControlGeometry.MOUNT,mount);IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                    JsonObject row=new JsonObject();row.addProperty("kind","world");row.addProperty("key",id+"/"+face.getName()+"/"+floor+"/"+on+"/"+size+"/"+mount);row.add("quads",BinaryControlReference.quads(model,extended));JsonArray boxes=new JsonArray();for(AxisAlignedBB box:MountedControlGeometry.boxes(state,size,mount)){JsonArray value=new JsonArray();for(double v:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})value.add(v);boxes.add(value);}row.add("boxes",boxes);gson.toJson(row,writer);writer.write('\n');worlds++;
                }
                for(String kind:new String[]{"absent","int0","int1","int-1","int2","intMIN","intMAX","byte0","byte1","long0","long1","float0","float1","string0"})for(int mount=0;mount<64;mount++){
                    ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",mount%4);tag.setInteger("BaseTilt",mount/4%4);tag.setInteger("TiltDirection",mount/16);if(kind.startsWith("int"))tag.setInteger("PowerLeverSize",kind.equals("intMIN")?Integer.MIN_VALUE:kind.equals("intMAX")?Integer.MAX_VALUE:Integer.parseInt(kind.substring(3)));else if(kind.startsWith("byte"))tag.setByte("PowerLeverSize",Byte.parseByte(kind.substring(4)));else if(kind.startsWith("long"))tag.setLong("PowerLeverSize",Long.parseLong(kind.substring(4)));else if(kind.startsWith("float"))tag.setFloat("PowerLeverSize",Float.parseFloat(kind.substring(5)));else if(kind.startsWith("string"))tag.setString("PowerLeverSize",kind.substring(6));stack.setTagInfo("RedstoneChannelSettings",tag);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
                    JsonObject row=new JsonObject();row.addProperty("kind","item");row.addProperty("key",id+"/"+kind+"/"+mount);row.add("quads",BinaryControlReference.quads(model,null));ItemTransformVec3f gui=model.getItemCameraTransforms().gui;JsonArray transform=new JsonArray();for(float v:new float[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(v);row.add("gui",transform);gson.toJson(row,writer);writer.write('\n');items++;
                }
            }
        }catch(IOException e){throw new IllegalStateException("Cannot export power lever reference",e);}System.out.println("[vandorlabs][reprolab] power-lever-reference PASS world="+worlds+" items="+items+" actual emitted quads/supportboxes/itemtransforms");
    }
    private PowerLeverReference(){}
}
