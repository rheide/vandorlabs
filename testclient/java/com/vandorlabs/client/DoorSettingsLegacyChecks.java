package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.*;
import java.nio.file.*;

/** Actual user-choice projection, deliberately excluding reader inventory/timeline ownership and world callbacks. */
public final class DoorSettingsLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySpaceDoor.class,new net.minecraft.util.ResourceLocation("vandorlabs","door_profile_fixture"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new net.minecraft.util.ResourceLocation("vandorlabs","large_door_profile_fixture"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new net.minecraft.util.ResourceLocation("vandorlabs","door_reader_profile_fixture"));
  JsonArray rows=new JsonArray();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
  for(boolean large:new boolean[]{false,true})for(int code=0;code<256;code++){
   NBTTagCompound input=new NBTTagCompound();if(code!=0){input.setInteger("SpaceDesign",code%34-2);input.setInteger("SpaceDetail",code%5-1);input.setBoolean("SpaceFramed",(code&1)==0);input.setInteger("SpaceSlideDirection",code%6-1);input.setBoolean("SpaceDoorMiddle",(code&2)!=0);input.setBoolean("SpaceDoorSliding",(code&4)!=0);input.setBoolean("SpaceDoorHinges",(code&8)!=0);input.setBoolean("SpaceDoorPanel",(code&16)!=0);input.setInteger("SpaceDoorTrigger",code%5-1);input.setBoolean("DoorTileTexture",(code&32)!=0);if((code&1)==0)input.setInteger("DoorTextureLayout",code%5-1);else input.setByte("DoorTextureLayout",(byte)1);if((code&2)==0)input.setInteger("SpaceDoorPlacementDepth",code%5-1);else input.setByte("SpaceDoorPlacementDepth",(byte)0);input.setInteger("DoorPanelMode",code%5-1);input.setInteger("DoorSecurityMode",code%7-1);input.setInteger("DoorSecurityAllowed",code);input.setInteger("DoorFaceTexture",code%4==0?7:code%4==1?-1:code%4==2?Integer.MAX_VALUE:0);input.setInteger("FrameFront",code%3==0?-1:7);input.setBoolean("FrameTile",(code&64)!=0);
    NBTTagCompound glass=new NBTTagCompound();glass.setInteger("Material",code%3-2);glass.setInteger("Shade",code%21-1);glass.setInteger("Tint",code%6-1);glass.setInteger("Layout",code%5-1);input.setTag("DoorGlass",glass);
    if((code&128)!=0){NBTTagCompound reader=new NBTTagCompound();reader.setBoolean("AnyToken",(code&32)!=0);reader.setInteger("AllowedTokens",code);reader.setString("FutureReader","opaque");input.setTag("DoorKeyReader",reader);}
   }
   TileEntitySpaceDoor tile=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();tile.readFromNBT(input.copy());NBTTagCompound expected=tile.itemSettings();JsonObject row=new JsonObject();row.addProperty("large",large);row.addProperty("input",input.toString());row.addProperty("choices",expected.toString());rows.add(row);hash.update(row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  for(int count:new int[]{0,1,64}){int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=i+1;io.netty.buffer.ByteBuf b=io.netty.buffer.Unpooled.buffer();try{new com.vandorlabs.network.MessageSpaceDoor(net.minecraft.util.math.BlockPos.ORIGIN,2,1,true,0,0,0,false,true,0,true,-1,false).withChannels(com.vandorlabs.redstone.ChannelList.of(channels)).withTextureLayout(0).withSecurity(1,0,127).toBytes(b);if(b.readableBytes()!=46+4*count)throw new AssertionError("Legacy door wire size changed");System.out.println("Actual legacy door wire channels="+count+" bytes="+b.readableBytes());}finally{b.release();}}
  Path file=Paths.get(System.getProperty("vandorlabs.doorSettingsExport","build/test-artifacts/door-settings-forge.json"));Files.createDirectories(file.toAbsolutePath().getParent());Files.write(file,new Gson().toJson(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format("%02x",b&255));if(!text.toString().equals("fa370dc09d3599db8bab05255fc9bbdeb8a44d892dc243f2a9845dc9939ca6e1"))throw new AssertionError("Actual door profile changed "+text);System.out.println("Actual Door settings projection PASS cases="+rows.size()+" golden="+text+" userchoices only; reader/runtime lifecycle excluded");
 }
}
