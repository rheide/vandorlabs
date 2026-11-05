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

/** Streaming actual current Forge detent meshes, component/wedge collision and item previews. */
final class SignalControlReference {
    static void export(Minecraft mc){
        String output=System.getenv("VANDOR_SIGNAL_MODEL_REFERENCE");if(output==null||output.isEmpty())return;int worlds=0,items=0;Gson gson=new Gson();
        try(Writer writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output),StandardCharsets.UTF_8))){
            writer.write("{\"format\":1,\"source\":\"Forge 1.12.2 actual four-detent meshes, collisions and items\"}\n");
            for(String id:new String[]{"thruster_lever","airliner_throttle","fighter_throttle","wall_slider"}){
                BlockSignalControl block=(BlockSignalControl)Block.getBlockFromName("vandorlabs:"+id);
                for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<(face.getAxis()==EnumFacing.Axis.Y?4:1);rotation++)for(int level=0;level<4;level++)for(int mount=0;mount<64;mount++){
                    IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,face).withProperty(BlockVandorSwitch.ROTATION,rotation).withProperty(BlockSignalControl.LEVEL,level);IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);IBlockState extended=((IExtendedBlockState)state).withProperty(BlockSignalControl.MOUNT,mount);
                    JsonObject row=new JsonObject();row.addProperty("kind","world");row.addProperty("key",id+"/"+face.getName()+"/"+rotation+"/"+level+"/"+mount);row.add("quads",BinaryControlReference.quads(model,extended));JsonArray boxes=new JsonArray();for(AxisAlignedBB box:SignalControlShape.boxes(block,state,mount%4,mount/4%4,mount/16)){JsonArray value=new JsonArray();for(double v:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})value.add(v);boxes.add(value);}row.add("boxes",boxes);gson.toJson(row,writer);writer.write('\n');worlds++;
                }
                for(String kind:new String[]{"absent","int0","int1","int2","int-1","int3","intMIN","intMAX","byte1","short2","long1","float1","string2","badheight-int1","baddirection-int2","unversioned-int2","root-int1","wrongwrapper-int2"})for(int mount=0;mount<64;mount++){
                    ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",mount%4);tag.setInteger("BaseTilt",mount/4%4);tag.setInteger("TiltDirection",mount/16);String type=kind.substring(kind.lastIndexOf('-')+1);
                    if(kind.equals("int-1"))tag.setInteger("ControlType",-1);else if(type.startsWith("int"))tag.setInteger("ControlType",type.equals("intMIN")?Integer.MIN_VALUE:type.equals("intMAX")?Integer.MAX_VALUE:Integer.parseInt(type.substring(3)));else if(type.startsWith("byte"))tag.setByte("ControlType",(byte)1);else if(type.startsWith("short"))tag.setShort("ControlType",(short)2);else if(type.startsWith("long"))tag.setLong("ControlType",1);else if(type.startsWith("float"))tag.setFloat("ControlType",1.75F);else if(type.startsWith("string"))tag.setString("ControlType","2");
                    if(kind.startsWith("badheight"))tag.setInteger("BaseHeight",4);if(kind.startsWith("baddirection")){tag.setInteger("BaseHeight",0);tag.setInteger("BaseTilt",0);tag.setInteger("TiltDirection",7);}if(kind.startsWith("unversioned"))tag.removeTag("ControlMountVersion");tag.setInteger("PowerLeverSize",2);tag.setInteger("ControlStep",3);tag.setInteger("LowLimit",6);tag.setInteger("HighLimit",8);tag.setString("UnknownItemField","retained");
                    if(kind.startsWith("root"))stack.setTagCompound(tag);else if(kind.startsWith("wrongwrapper")){NBTTagCompound root=new NBTTagCompound();root.setString("RedstoneChannelSettings","invalid");stack.setTagCompound(root);}else stack.setTagInfo("RedstoneChannelSettings",tag);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
                    JsonObject row=new JsonObject();row.addProperty("kind","item");row.addProperty("key",id+"/"+kind+"/"+mount);row.add("quads",BinaryControlReference.quads(model,null));ItemTransformVec3f gui=model.getItemCameraTransforms().gui;JsonArray transform=new JsonArray();for(float v:new float[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(v);row.add("gui",transform);gson.toJson(row,writer);writer.write('\n');items++;
                }
            }
        }catch(IOException e){throw new IllegalStateException("Cannot export signal control reference",e);}System.out.println("[vandorlabs][reprolab] signal-control-reference PASS world="+worlds+" items="+items+" actual detent meshes/componentcollision/type-aware item transforms");
    }
    private SignalControlReference(){}
}
