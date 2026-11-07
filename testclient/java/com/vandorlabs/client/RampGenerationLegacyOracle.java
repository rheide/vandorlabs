package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
/** Actual stationary capture/scheduled movement/recovery callbacks in a fixed loaded memory world. */
public final class RampGenerationLegacyOracle {
 private static final BlockPos POS=new BlockPos(4,80,4);
 private static JsonArray position(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
 private static JsonObject snapshot(NonRenderingChecks.MemoryWorld world,TileEntityRampController tile){
  JsonObject out=new JsonObject();out.addProperty("area",tile.area());out.addProperty("pose",tile.pose(0));out.addProperty("status",tile.status);out.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());JsonArray parts=new JsonArray();List<BlockPos> sorted=new ArrayList<>(tile.vehicleCells());sorted.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
  for(BlockPos p:sorted){TileEntity found=world.getTileEntity(p);if(!(found instanceof TileEntityControlledRamp))throw new AssertionError("Missing actual generated part "+p);TileEntityControlledRamp part=(TileEntityControlledRamp)found;JsonObject row=new JsonObject();row.add("position",position(p));row.addProperty("saved",part.writeToNBT(new NBTTagCompound()).toString());JsonArray boxes=new JsonArray();for(AxisAlignedBB box:part.boxes(.25)){JsonArray b=new JsonArray();for(double d:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})b.add(d);boxes.add(b);}row.add("boxes",boxes);parts.add(row);}
  out.add("parts",parts);return out;
 }
 public static void main(String[] args)throws Exception{
  List<BlockRampController> controllers=RampSettingsLegacyOracle.initialize();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(ModBlocks.CONTROLLED_RAMP);net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityControlledRamp.class,new ResourceLocation("vandorlabs","controlled_ramp"));
  List<String> lines=new ArrayList<>();int admitted=0,restored=0;
  for(BlockRampController block:controllers)for(int meta=0;meta<16;meta++)for(int mode=0;mode<6;mode++)for(int material=0;material<4;material++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.time=100;world.setBlockState(POS,block.getStateFromMeta(meta),2);TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(POS);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("ControllerVersion",2);settings.setInteger("StartOffsetHalfSteps",0);settings.setInteger("EndOffsetHalfSteps",-4);settings.setInteger("TreadPixels",8);settings.setInteger("SpeedMode",0);settings.setBoolean("Elevator",mode==1);settings.setInteger("TravelAxis",mode==2?2:0);settings.setBoolean("ExtendSegments",mode==3);settings.setBoolean("MoatMode",mode==4);settings.setBoolean("RampVerticalLayout",mode==5);settings.setInteger("x",POS.getX());settings.setInteger("y",POS.getY());settings.setInteger("z",POS.getZ());tile.readFromNBT(settings);tile.scheduledTick();
   EnumFacing scan=BlockRampController.scanFacing(world.getBlockState(POS));EnumFacing facing=world.getBlockState(POS).getValue(BlockVandorDirectional.FACING);BlockPos seed=POS.offset(scan),second=mode>=4?seed.up():seed.offset(facing);if(second.equals(POS))second=seed.down();if(second.equals(POS))throw new AssertionError("Source setup overwrites owner");IBlockState source=material==0?Blocks.STONE.getDefaultState():material==3?Blocks.STONE_STAIRS.getDefaultState():Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,material==1?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);world.setBlockState(seed,source,2);world.setBlockState(second,source,2);
   JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("mode",mode);row.addProperty("material",material);row.add("seed",position(seed));row.add("second",position(second));boolean accepted=tile.request(true);row.addProperty("accepted",accepted);if(accepted)admitted++;JsonArray states=new JsonArray();states.add(snapshot(world,tile));for(int delta:new int[]{1,3,4}){world.time+=delta;tile.scheduledTick();states.add(snapshot(world,tile));}row.add("states",states);row.addProperty("retractAccepted",tile.request(false));for(int i=0;i<20&&tile.attached();i++){world.time++;tile.scheduledTick();}row.add("recovered",snapshot(world,tile));boolean exact=world.getBlockState(seed).equals(source)&&world.getBlockState(second).equals(source);row.addProperty("exactOriginal",exact);if(exact)restored++;if(world.getTileEntity(POS)!=tile||!tile.getPos().equals(POS))throw new AssertionError("Lost exact installed owner");lines.add(row.toString());RampSettingsLegacyOracle.retire(world);
  }
  if(lines.size()!=768||admitted!=612||restored!=768)throw new AssertionError("Actual generation coverage");Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());Files.write(path,lines,java.nio.charset.StandardCharsets.UTF_8);StringBuilder sha=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))sha.append(String.format(Locale.ROOT,"%02x",b&255));if(Files.size(path)>32L*1024*1024||!sha.toString().equals("a5cb9cb58e2dd878ca97e8c63293c6072cc125df4bed1b5d2821d077d95c87f9"))throw new AssertionError("Changed generation golden "+sha);System.out.println("Ramp Source actual generation PASS cases="+lines.size()+" admitted="+admitted+" exactOriginal="+restored+" sha256="+sha+"; fixed loaded memory world, not durable/server-player proof");
 }
}
