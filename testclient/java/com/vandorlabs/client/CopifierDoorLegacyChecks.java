package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
/** Actual ordinary Door copied setter ordering, including upper-cell ownership and retained aliases. */
public final class CopifierDoorLegacyChecks {
 private static final BlockPos SOURCE=new BlockPos(4,80,4),TARGET=SOURCE.east(4);
 private static NBTTagCompound profile(int code,BlockPos pos){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());if(code>0){tag.setInteger("SpaceDesign",code%4==0?29:code%4==1?15:code%4==2?16:0);tag.setInteger("SpaceDetail",code%3);tag.setBoolean("SpaceFramed",(code&1)==0);tag.setInteger("SpaceSlideDirection",code%3);tag.setBoolean("SpaceDoorMiddle",(code&2)!=0);tag.setBoolean("SpaceDoorSliding",(code&4)!=0);tag.setBoolean("SpaceDoorHinges",(code&8)!=0);tag.setInteger("SpaceDoorTrigger",code%3);tag.setBoolean("SpaceDoorPanel",(code&1)!=0);tag.setInteger("SpaceDoorPlacementDepth",code%3);tag.setInteger("DoorPanelMode",code%3);tag.setInteger("DoorSecurityMode",code%5);tag.setInteger("DoorSecurityAllowed",code*9);tag.setInteger("DoorFaceTexture",code%4==0?-1:7);tag.setBoolean("DoorTileTexture",(code&1)!=0);tag.setInteger("DoorTextureLayout",code%3);tag.setInteger("RedstoneChannel",13);tag.setIntArray("RedstoneChannels",new int[]{13,17});tag.setInteger("FrameInterior",8);}
  if(code==7){tag.setInteger("SpaceDesign",99);tag.setInteger("SpaceDetail",99);tag.setInteger("DoorFaceTexture",Integer.MAX_VALUE);tag.setInteger("DoorTextureLayout",99);}
  if(code==8){tag.setString("SpaceDoorTrigger","wrong");tag.setString("DoorPanelMode","wrong");}
  if(code>=9){NBTTagCompound glass=new NBTTagCompound();glass.setInteger("Material",7);glass.setInteger("Shade",code%19);glass.setInteger("Tint",code%5);glass.setInteger("Layout",code%3);tag.setTag("DoorGlass",glass);}
  return tag;
 }
 private static TileEntitySpaceDoor put(NonRenderingChecks.MemoryWorld world,Block block,BlockPos pos,EnumFacing facing,NBTTagCompound input){world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER);world.setBlockState(pos,state,2);world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);TileEntitySpaceDoor owner=(TileEntitySpaceDoor)world.getTileEntity(pos);owner.readFromNBT(input.copy());return owner;}
 public static void main(String[] args)throws Exception{net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySpaceDoor.class,new ResourceLocation("minecraft:door_copy_reference"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new ResourceLocation("minecraft:door_reader_copy_reference"));
  net.minecraftforge.fml.common.ModMetadata metadata=new net.minecraftforge.fml.common.ModMetadata();metadata.modId="vandorlabs";metadata.name="Vandor Labs";metadata.version="2.9";java.lang.reflect.Field field=net.minecraftforge.fml.common.Loader.class.getDeclaredField("modController");field.setAccessible(true);field.set(net.minecraftforge.fml.common.Loader.instance(),new net.minecraftforge.fml.common.LoadController(net.minecraftforge.fml.common.Loader.instance()));net.minecraftforge.fml.common.Loader.instance().setActiveModContainer(new net.minecraftforge.fml.common.DummyModContainer(metadata));
  java.util.List<Block> blocks=new java.util.ArrayList<>();for(String id:new String[]{"programmable_door","space_rotating_door","space_sliding_door"}){Block block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=candidate;if(block==null)block=ModBlocks.doorMotionTemplate(id);if(block==null)throw new AssertionError("Missing Source door "+id);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);blocks.add(block);}
  JsonArray rows=new JsonArray();int captures=0;
  for(Block block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int code=0;code<12;code++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);NBTTagCompound input=profile(code,SOURCE);put(world,block,SOURCE,facing,input);NBTTagCompound captured=ProgrammableSettings.capture(world,SOURCE);if(!captured.equals(ProgrammableSettings.capture(world,SOURCE.up())))throw new AssertionError("Upper capture must resolve lower");NBTTagCompound before=profile(11-code,TARGET);TileEntitySpaceDoor target=put(world,block,TARGET,facing.getOpposite(),before);JsonObject row=new JsonObject();row.addProperty("id",block.getRegistryName().getResourcePath());row.addProperty("facing",facing.getIndex());row.addProperty("input",input.toString());row.addProperty("capture",captured.toString());observe(rows,row,world,target,before,captured);captures++;
  }
  for(Block block:blocks)for(int variant=0;variant<32;variant++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);NBTTagCompound before=profile(9+variant%3,TARGET);TileEntitySpaceDoor target=put(world,block,TARGET,EnumFacing.NORTH,before);NBTTagCompound values=new NBTTagCompound();
   if(variant<3)values.setInteger("door_design",new int[]{15,29,99}[variant]);
   else if(variant<6)values.setInteger("door_detail",variant-3);
   else if(variant<9)values.setInteger("door_depth",variant-6);
   else if(variant<12)values.setInteger("door_panel",variant-9);
   else if(variant==12)values.setBoolean("door_panel",false);
   else if(variant==13)values.setInteger("door_slide_direction",3);
   else if(variant==14)values.setBoolean("door_middle",false);
   else if(variant==15)values.setBoolean("door_sliding",false);
   else if(variant==16)values.setBoolean("door_hinges",false);
   else if(variant==17)values.setBoolean("framed",false);
   else if(variant==18)values.setInteger(ProgrammableSettings.TRIGGER,99);
   else if(variant==19){values.setBoolean("door_tile_texture",true);values.setInteger("door_texture_layout",2);}
   else if(variant==20)values.setBoolean("door_tile_texture",false);
   else if(variant==21)values.setInteger("door_texture_layout",1);
   else if(variant==22)values.setInteger("door_face_texture",Integer.MAX_VALUE);
   else if(variant==23){NBTTagCompound security=new NBTTagCompound();security.setInteger("Mode",4);security.setInteger("Allowed",42);values.setTag("door_security",security);values.setInteger("door_panel",0);}
   else if(variant==24){NBTTagCompound security=new NBTTagCompound();security.setInteger("Mode",999);security.setInteger("Allowed",-1);values.setTag("door_security",security);}
   else if(variant==25)values.setTag(ProgrammableSettings.DOOR_GLASS,new NBTTagCompound());
   else if(variant==26){NBTTagCompound root=new NBTTagCompound(),glass=new NBTTagCompound();glass.setInteger("Material",7);glass.setInteger("Shade",3);glass.setInteger("Tint",4);glass.setInteger("Layout",2);root.setTag("DoorGlass",glass);values.setTag(ProgrammableSettings.DOOR_GLASS,root);}
   else if(variant==27)values.setTag("FrameAppearance",new NBTTagCompound());
   else if(variant==28)values.setIntArray("redstone_channel",new int[]{29,31});
   else if(variant==29){NBTTagCompound tx=new NBTTagCompound();tx.setIntArray("Channels",new int[]{37});tx.setInteger("Range",64);tx.setBoolean("Local",true);values.setTag("Transmission",tx);}
   else if(variant==30){NBTTagCompound tx=new NBTTagCompound();tx.setInteger("Range",64);values.setTag("Transmission",tx);values.setInteger("door_design",15);}
   else{values.setString("door_design","wrong");values.setString("door_middle","wrong");}
   JsonObject row=new JsonObject();row.addProperty("id",block.getRegistryName().getResourcePath());row.addProperty("facing",EnumFacing.SOUTH.getIndex());observe(rows,row,world,target,before,values);
  }
  byte[] raw=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);Path out=Paths.get(args[0]);Files.createDirectories(out.getParent());Files.write(out,raw);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(captures!=144||rows.size()!=240||!hash.toString().equals("fd5ddea41ed7d2dfed3bd042a3b1f6df917367447620c531fef38cfcf6e1ec07"))throw new AssertionError("Released Door copy corpus changed: "+hash);System.out.println("Copifier Door Source PASS captures="+captures+" applications="+rows.size()+" sha256="+hash);
 }
 private static void observe(JsonArray rows,JsonObject row,NonRenderingChecks.MemoryWorld world,TileEntitySpaceDoor target,NBTTagCompound before,NBTTagCompound values){NBTTagCompound undo=ProgrammableSettings.capture(world,TARGET);row.addProperty("target",before.toString());row.addProperty("values",values.toString());boolean applied=ProgrammableSettings.apply(world,TARGET.up(),values.copy());row.addProperty("applied",applied);row.addProperty("after",ProgrammableSettings.capture(world,TARGET).toString());if(world.getTileEntity(TARGET)!=target)throw new AssertionError("Copy replaced Door owner");if(applied){if(!ProgrammableSettings.apply(world,TARGET,undo))throw new AssertionError("Source undo refused");row.addProperty("undo",ProgrammableSettings.capture(world,TARGET).toString());}rows.add(row);}
}
