package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.DuplifierConnectedApply;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.block.Block;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Freeze the actual released connected-selection projection, including hidden saved face choices. */
final class CopifierConnectedLegacyChecks {
 static void run(){try{
  CopifierHousingLegacyChecks.run();
  Block[] blocks={ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS,ModBlocks.PROGRAMMABLE_WALL,ModBlocks.PROGRAMMABLE_PORTHOLE_WALL,ModBlocks.PROGRAMMABLE_PORTHOLE_BLOCK,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,new BlockProgrammableWall("programmable_diagonal_porthole",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE),ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS};
  java.lang.reflect.Method method=DuplifierConnectedApply.class.getDeclaredMethod("configuration",net.minecraft.world.World.class,BlockPos.class,boolean.class);method.setAccessible(true);
  JsonArray original=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(System.getProperty("vandorlabs.copifierHousingReference"))),StandardCharsets.UTF_8)).getAsJsonArray(),rows=new JsonArray();BlockPos pos=new BlockPos(4,80,4);int cases=0;
  for(JsonElement element:original){JsonObject row=element.getAsJsonObject();Block block=null;for(Block candidate:blocks)if(candidate.getRegistryName().getResourcePath().equals(row.get("block").getAsString()))block=candidate;if(block==null)throw new AssertionError("missing Source family");
   for(int disabled=0;disabled<=(row.get("profile").getAsInt()==2?1:0);disabled++){
    NBTTagCompound input=JsonToNBT.getTagFromJson(row.get("input").getAsString());if(disabled==1)input.setBoolean("FaceTexturesEnabled",false);
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(pos,block.getStateFromMeta(row.get("meta").getAsInt()),2);world.getTileEntity(pos).readFromNBT(input.copy());
    JsonObject out=new JsonObject();out.addProperty("block",row.get("block").getAsString());out.addProperty("meta",row.get("meta").getAsInt());out.addProperty("input",input.toString());out.addProperty("strict",((NBTTagCompound)method.invoke(null,world,pos,false)).toString());out.addProperty("ignore",((NBTTagCompound)method.invoke(null,world,pos,true)).toString());rows.add(out);cases++;
   }
  }
  byte[] bytes=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);Path path=Paths.get(System.getProperty("vandorlabs.copifierConnectedReference"));Files.createDirectories(path.getParent());Files.write(path,bytes);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("3cc19480eda4799d01c96f54711527e914d566af17cff8fb3c4b87ccb4c3fe7a"))throw new AssertionError("Source connected projection changed: "+hash);System.out.println("Copifier connected Source PASS cases="+cases+" sha256="+hash);
 }catch(Exception failure){throw new AssertionError(failure);}}
}
