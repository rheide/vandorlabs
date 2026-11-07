package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.persistence.*;
import com.vandorlabs.tiles.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Actual mounted/numeric/wireless Copifier getters and setters, including block-type changes. */
public final class CopifierControlLegacyChecks {
 private static boolean supported(Block b){return b instanceof BlockVandorSwitch&&!(b instanceof BlockTokenSlot)||b instanceof BlockIndustrialLever||b instanceof BlockWirelessRedstone;}
 private static String hash(byte[] raw)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneChannel.class,new ResourceLocation("minecraft:copier_binary_reference"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySignalControl.class,new ResourceLocation("minecraft:copier_signal_reference"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityWirelessRedstone.class,new ResourceLocation("minecraft:copier_wireless_reference"));
  net.minecraftforge.fml.common.ModMetadata metadata=new net.minecraftforge.fml.common.ModMetadata();metadata.modId="vandorlabs";metadata.name="Vandor Labs";metadata.version="2.9";java.lang.reflect.Field controller=net.minecraftforge.fml.common.Loader.class.getDeclaredField("modController");controller.setAccessible(true);controller.set(net.minecraftforge.fml.common.Loader.instance(),new net.minecraftforge.fml.common.LoadController(net.minecraftforge.fml.common.Loader.instance()));net.minecraftforge.fml.common.Loader.instance().setActiveModContainer(new net.minecraftforge.fml.common.DummyModContainer(metadata));
  for(Block block:ModBlocks.BLOCKS)if(supported(block))net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
  for(String type:BlockSignalControl.TYPES)if(Block.getBlockFromName("vandorlabs:"+type)==null)throw new AssertionError("Source fixture missing registered control type "+type);
  JsonArray rows=new JsonArray();int blocks=0;BlockPos pos=new BlockPos(4,80,4),target=pos.east(3);
  for(Block block:ModBlocks.BLOCKS){if(!supported(block))continue;blocks++;for(int meta=0;meta<16;meta++)for(int profile=0;profile<8;profile++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(pos,block.getStateFromMeta(meta),2);TileEntityRedstoneChannel source=(TileEntityRedstoneChannel)world.getTileEntity(pos);NBTTagCompound input=new NBTTagCompound();if(profile==1)input=source.writeToNBT(new NBTTagCompound());
   if(profile>=2){input.setInteger("RedstoneChannel",profile==2?0:13);input.setIntArray("RedstoneChannels",profile==2?new int[0]:new int[]{13,17});input.setBoolean("LocalOn",(profile&1)!=0);input.setBoolean("ChannelInitialized",true);input.setInteger("MountRotation",profile+1);input.setInteger("ControlMountVersion",2);input.setInteger("BaseHeight",profile%4);input.setInteger("BaseTilt",profile%3);input.setInteger("TiltDirection",profile%4);input.setInteger("PowerLeverSize",profile%3);input.setInteger(SignalControlData.LOW,profile==6?0:2);input.setInteger(SignalControlData.HIGH,profile==6?99:12);input.setInteger(SignalControlData.STEP,profile%4);input.setBoolean("WirelessAnalog",(profile&1)==0);input.setInteger("WirelessOn",12);input.setInteger("WirelessOff",3);}
   if(profile==4){input.setString("MountRotation","wrong");input.setString("LowLimit","wrong");input.setIntArray("RedstoneChannels",new int[]{-1});}
   if(profile==5){input.setInteger("BaseHeight",999);input.setInteger("BaseTilt",999);input.setInteger("TiltDirection",999);input.setInteger("PowerLeverSize",999);}
   if(profile==7){input.setInteger(SignalControlData.LOW,Integer.MIN_VALUE);input.setInteger(SignalControlData.HIGH,Integer.MAX_VALUE);}
   input.setInteger("x",pos.getX());input.setInteger("y",pos.getY());input.setInteger("z",pos.getZ());source.readFromNBT(input.copy());NBTTagCompound captured=ProgrammableSettings.capture(world,pos);if(captured==null)throw new AssertionError("missing supported capture "+block);world.setBlockState(target,block.getStateFromMeta(meta^3),2);TileEntityRedstoneChannel destination=(TileEntityRedstoneChannel)world.getTileEntity(target);NBTTagCompound before=new NBTTagCompound();before.setInteger("x",target.getX());before.setInteger("y",target.getY());before.setInteger("z",target.getZ());destination.readFromNBT(before.copy());
   JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("input",input.toString());row.addProperty("capture",captured.toString());row.addProperty("target",before.toString());row.addProperty("values",captured.toString());row.addProperty("applied",ProgrammableSettings.apply(world,target,captured.copy()));row.addProperty("afterBlock",world.getBlockState(target).getBlock().getRegistryName().getResourcePath());row.addProperty("after",ProgrammableSettings.capture(world,target).toString());rows.add(row);
  }}
  // Independent partial fields and invalid types use a non-default installed target.
  for(Block block:ModBlocks.BLOCKS)if(supported(block))for(int variant=0;variant<22;variant++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(target,block.getStateFromMeta(3),2);TileEntityRedstoneChannel destination=(TileEntityRedstoneChannel)world.getTileEntity(target);
   NBTTagCompound before=new NBTTagCompound();before.setInteger("x",target.getX());before.setInteger("y",target.getY());before.setInteger("z",target.getZ());before.setBoolean("LocalOn",true);before.setInteger("ControlStep",2);before.setInteger("MountRotation",2);before.setInteger("BaseHeight",2);before.setInteger("BaseTilt",2);before.setInteger("TiltDirection",3);before.setInteger("ControlMountVersion",2);before.setInteger("PowerLeverSize",0);before.setInteger("LowLimit",2);before.setInteger("HighLimit",12);destination.readFromNBT(before.copy());
   NBTTagCompound values=new NBTTagCompound(),signal=new NBTTagCompound();
   switch(variant){
    case 0: values.setInteger("switch_rotation",-5);break;
    case 1: values.setInteger("active",3);break;
    case 2: values.setBoolean("active",false);break;
    case 3: values.setTag("signal_settings",signal);break;
    case 4: signal.setInteger("BaseHeight",1);signal.setInteger("BaseTilt",1);signal.setInteger("TiltDirection",2);values.setTag("signal_settings",signal);break;
    case 5: signal.setInteger("BaseHeight",999);signal.setInteger("BaseTilt",999);signal.setInteger("TiltDirection",999);values.setTag("signal_settings",signal);break;
    case 6: signal.setInteger("LowLimit",2);signal.setInteger("HighLimit",12);values.setTag("signal_settings",signal);break;
    case 7: signal.setInteger("LowLimit",0);signal.setInteger("HighLimit",99);values.setTag("signal_settings",signal);break;
    case 8: signal.setInteger("LowLimit",Integer.MIN_VALUE);signal.setInteger("HighLimit",Integer.MAX_VALUE);values.setTag("signal_settings",signal);break;
    case 9: signal.setInteger("PowerLeverSize",1);values.setTag("signal_settings",signal);break;
    case 10: signal.setInteger("PowerLeverSize",999);values.setTag("signal_settings",signal);break;
    case 11: signal.setInteger("ControlType",2);signal.setInteger("BaseHeight",1);values.setTag("signal_settings",signal);break;
    case 12: signal.setBoolean("WirelessAnalog",false);signal.setInteger("WirelessOn",9);signal.setInteger("WirelessOff",4);values.setTag("signal_settings",signal);break;
    case 13: signal.setString("WirelessAnalog","wrong");signal.setInteger("WirelessOn",99);signal.setInteger("WirelessOff",-1);values.setTag("signal_settings",signal);break;
    case 14: values.setIntArray("redstone_channel",new int[]{13,17});break;
    case 15: values.setString("redstone_channel","wrong");break;
    case 16: {NBTTagCompound transmission=new NBTTagCompound();transmission.setIntArray("Channels",new int[]{19});transmission.setInteger("Range",32);transmission.setBoolean("Local",true);values.setTag("Transmission",transmission);values.setIntArray("redstone_channel",new int[]{13,17});break;}
    case 17: {NBTTagCompound transmission=new NBTTagCompound();transmission.setInteger("Range",32);values.setTag("Transmission",transmission);values.setBoolean("active",false);break;}
    case 18: values.setString("active","wrong");values.setString("switch_rotation","wrong");values.setString("signal_settings","wrong");break;
    case 19: values.setInteger("active",-1);break;
    case 20: values.setInteger("active",4);break;
    case 21: signal.setInteger("ControlType",1);values.setTag("signal_settings",signal);break;
   }
   JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",0);row.addProperty("target",before.toString());row.addProperty("values",values.toString());row.addProperty("applied",ProgrammableSettings.apply(world,target,values.copy()));row.addProperty("afterBlock",world.getBlockState(target).getBlock().getRegistryName().getResourcePath());row.addProperty("after",ProgrammableSettings.capture(world,target).toString());rows.add(row);
  }
  byte[] raw=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);if(blocks!=12||rows.size()!=1800||!hash(raw).equals("0ebe115c8af406bb7ae897923b72528411892e364241e49777826faab9e4f250"))throw new AssertionError("Released control corpus changed: "+hash(raw));Path output=Paths.get(args[0]);Files.createDirectories(output.getParent());Files.write(output,raw);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));System.out.println("Copifier controls Source PASS blocks="+blocks+" cases="+rows.size()+" sha256="+hash);
 }
}
