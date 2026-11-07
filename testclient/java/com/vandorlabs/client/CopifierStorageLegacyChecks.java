package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityProgrammableStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
/** Actual Storage appearance copy preserves physical inventory and inherited dormant configuration. */
public final class CopifierStorageLegacyChecks {
 private static final BlockPos SOURCE=new BlockPos(4,80,4),TARGET=SOURCE.east(3);
 private static NBTTagCompound profile(int code,BlockPos pos){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());
  if(code>0){tag.setInteger("housingTexture",code==1?14:37);if(code>2)tag.setInteger("HousingTextureVersion",1);tag.setBoolean("SlabTileSides",(code&1)==0);tag.setInteger("MainTextureLayout",code%3);tag.setBoolean("FaceTexturesEnabled",(code&1)!=0);tag.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});tag.setInteger("RedstoneChannel",13);tag.setIntArray("RedstoneChannels",new int[]{13,17});tag.setInteger("FrameFront",7);tag.setInteger("FrameInterior",8);tag.setBoolean("FrameTile",true);}
  if(code==4){tag.setString("housingTexture","wrong");tag.setIntArray("FaceTextures",new int[]{1,2});tag.setString("MainTextureLayout","wrong");}
  if(code==5){tag.setInteger("housingTexture",Integer.MAX_VALUE);tag.setIntArray("RedstoneChannels",new int[]{-1});tag.setInteger("FrameFront",Integer.MAX_VALUE);}
  if(code==6){tag.setInteger("MainTextureLayout",99);tag.setBoolean("FaceTexturesEnabled",false);}
  if(code>=7){NBTTagCompound setting=new NBTTagCompound();setting.setIntArray("Channels",new int[]{19,23});setting.setInteger("Range",32);setting.setBoolean("Local",true);NBTTagCompound all=new NBTTagCompound();all.setTag("main",setting);NBTTagCompound forge=new NBTTagCompound();forge.setTag("VandorTransmission",all);tag.setTag("ForgeData",forge);}
  return tag;
 }
 private static TileEntityProgrammableStorage put(NonRenderingChecks.MemoryWorld world,BlockPos pos,int meta,NBTTagCompound data){world.setBlockState(pos,net.minecraft.init.Blocks.AIR.getDefaultState(),2);world.setBlockState(pos,ModBlocks.PROGRAMMABLE_STORAGE.getStateFromMeta(meta),2);TileEntityProgrammableStorage tile=(TileEntityProgrammableStorage)world.getTileEntity(pos);tile.readFromNBT(data.copy());tile.setInventorySlotContents(26,new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND,7));return tile;}
 public static void main(String[] args)throws Exception{net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableStorage.class,new net.minecraft.util.ResourceLocation("minecraft:storage_copy_reference"));JsonArray rows=new JsonArray();int captures=0;
  for(int meta=0;meta<4;meta++)for(int code=0;code<9;code++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);NBTTagCompound input=profile(code,SOURCE);put(world,SOURCE,meta,input);NBTTagCompound captured=ProgrammableSettings.capture(world,SOURCE);NBTTagCompound before=profile(8-code,TARGET);TileEntityProgrammableStorage target=put(world,TARGET,meta^3,before);JsonObject row=new JsonObject();row.addProperty("meta",meta);row.addProperty("input",input.toString());row.addProperty("capture",captured.toString());observe(rows,row,world,target,before,captured);captures++;
  }
  for(int variant=0;variant<18;variant++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);NBTTagCompound before=profile(7,TARGET),values=new NBTTagCompound();TileEntityProgrammableStorage target=put(world,TARGET,3,before);
   if(variant<3)values.setInteger("wall_texture",new int[]{7,-1,Integer.MAX_VALUE}[variant]);
   else if(variant<6)values.setInteger("texture_layout",variant-3);
   else if(variant<8)values.setBoolean("slab_tile_sides",variant==7);
   else if(variant<12){NBTTagCompound faces=new NBTTagCompound();faces.setBoolean("enabled",variant!=8);if(variant>=10)faces.setIntArray("choices",variant==10?new int[]{-1,1,-1,3,-1,5}:new int[]{1,2});values.setTag("face_textures",faces);}
   else if(variant==12)values.setTag("FrameAppearance",new NBTTagCompound());
   else if(variant==13)values.setIntArray("redstone_channel",new int[]{29,31});
   else if(variant==14){NBTTagCompound tx=new NBTTagCompound();tx.setIntArray("Channels",new int[]{37});tx.setInteger("Range",64);tx.setBoolean("Local",false);values.setTag("Transmission",tx);}
   else if(variant==15){NBTTagCompound tx=new NBTTagCompound();tx.setInteger("Range",64);values.setTag("Transmission",tx);values.setInteger("wall_texture",7);}
   else if(variant==16){values.setString("wall_texture","wrong");values.setString("face_textures","wrong");values.setString("texture_layout","wrong");}
   else values.setString("redstone_channel","wrong");
   JsonObject row=new JsonObject();row.addProperty("meta",0);observe(rows,row,world,target,before,values);
  }
  byte[] raw=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);Path out=Paths.get(args[0]);Files.createDirectories(out.getParent());Files.write(out,raw);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(captures!=36||rows.size()!=54||!hash.toString().equals("d04ddbe889a26244478f167737afe4f44baeb935013a8df6e5dca3c76847f3de"))throw new AssertionError("Released Storage copy corpus changed: "+hash);System.out.println("Copifier Storage Source PASS captures="+captures+" applications="+rows.size()+" sha256="+hash);
 }
 private static void observe(JsonArray rows,JsonObject row,NonRenderingChecks.MemoryWorld world,TileEntityProgrammableStorage target,NBTTagCompound before,NBTTagCompound values){
  NBTTagCompound undo=ProgrammableSettings.capture(world,TARGET);undo.getCompoundTag("face_textures").setIntArray("choices",target.getFaceTextures().choices());row.addProperty("target",before.toString());row.addProperty("values",values.toString());boolean applied=ProgrammableSettings.apply(world,TARGET,values.copy());row.addProperty("applied",applied);row.addProperty("after",ProgrammableSettings.capture(world,TARGET).toString());
  if(world.getTileEntity(TARGET)!=target||target.getStackInSlot(26).getCount()!=7)throw new AssertionError("Copy changed Storage inventory owner");
  if(applied){if(!ProgrammableSettings.apply(world,TARGET,undo))throw new AssertionError("Source settings undo refused");row.addProperty("undo",ProgrammableSettings.capture(world,TARGET).toString());if(target.getStackInSlot(26).getCount()!=7)throw new AssertionError("Undo changed inventory");}rows.add(row);
 }
}
