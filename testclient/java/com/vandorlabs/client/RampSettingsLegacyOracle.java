package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.TileEntityRampController;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
/** Released standalone controller callbacks; empty platforms never transfer block ownership. */
public final class RampSettingsLegacyOracle {
 private static final BlockPos POS=new BlockPos(4,80,4);
 private static final String[] INPUTS={"{}","{ControllerVersion:2}","{ControllerVersion:2,StartOffset:-3,EndOffset:5,TreadPixels:4,TravelAxis:1,SpeedMode:2}","{ControllerVersion:2,StartOffsetHalfSteps:-31,EndOffsetHalfSteps:29,TreadPixels:1,TravelAxis:2,RampDirection:1,RedstoneChannels:[I;13,17]}","{ControllerVersion:2,StartOffsetHalfSteps:99,EndOffsetHalfSteps:-99,TreadPixels:3,TravelAxis:99,SpeedMode:99,RampInterpolation:99,EscalatorMode:99,MatchTextures:0b}","{ControllerVersion:2,RampVerticalLayout:1b,MoatMode:1b,StartOffsetHalfSteps:1,EndOffsetHalfSteps:-3,ChannelSignal:1b}","{ControllerVersion:2,StartOffsetHalfSteps:1b,EndOffsetHalfSteps:\"wrong\",RedstoneChannels:[I;-1],RedstoneChannel:17}"};

 static JsonObject snapshot(NonRenderingChecks.MemoryWorld w,BlockRampController block,TileEntityRampController tile,EntityPlayer player){if(w.getTileEntity(POS)!=tile||!tile.getPos().equals(POS))throw new AssertionError("Snapshot requires exact installed owner");JsonObject out=new JsonObject();varState(out,w,block);out.addProperty("capture",ProgrammableSettings.capture(w,POS).toString());out.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());out.addProperty("pick",block.getPickBlock(w.getBlockState(POS),new RayTraceResult(Vec3d.ZERO,EnumFacing.NORTH,POS),w,POS,player).serializeNBT().toString());return out;}
 private static void varState(JsonObject out,NonRenderingChecks.MemoryWorld w,BlockRampController block){net.minecraft.block.state.IBlockState state=w.getBlockState(POS);out.addProperty("meta",block.getMetaFromState(state));out.addProperty("scan",BlockRampController.scanFacing(state).getIndex());out.addProperty("travelFacing",state.getValue(BlockVandorDirectional.FACING).getIndex());AxisAlignedBB b=block.getBoundingBox(state,w,POS);JsonArray box=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})box.add(v);out.add("bounds",box);}
 static List<BlockRampController> initialize()throws Exception{PropulsionPlacementLegacyOracle.initialize();List<BlockRampController> blocks=new ArrayList<>();for(Block b:ModBlocks.BLOCKS)if(b instanceof BlockRampController){blocks.add((BlockRampController)b);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(b);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(new ItemCatalogBlock(b).setRegistryName(b.getRegistryName()));}if(blocks.size()!=2)throw new AssertionError("two Source controller roles");net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRampController.class,new ResourceLocation("vandorlabs","programmable_ramp"));return blocks;}
 static void retire(NonRenderingChecks.MemoryWorld w)throws Exception{for(net.minecraft.tileentity.TileEntity tile:new ArrayList<>(w.tiles.values()))tile.invalidate();net.minecraftforge.event.world.WorldEvent.Unload event=new net.minecraftforge.event.world.WorldEvent.Unload(w);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(event);com.vandorlabs.tiles.WorldActionAuthorization.unload(event);java.lang.reflect.Constructor<com.vandorlabs.tiles.DeferredTileLoad> ctor=com.vandorlabs.tiles.DeferredTileLoad.class.getDeclaredConstructor();ctor.setAccessible(true);ctor.newInstance().worldUnload(event);w.clear();}
 public static void main(String[] args)throws Exception{
  List<BlockRampController> blocks=initialize();
  List<String> lines=new ArrayList<>();for(BlockRampController block:blocks)for(int meta=0;meta<16;meta++)for(int profile=-1;profile<INPUTS.length;profile++){
   NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(false);w.time=100;w.setBlockState(POS,block.getStateFromMeta(meta),2);TileEntityRampController tile=(TileEntityRampController)w.getTileEntity(POS);if(profile>=0){NBTTagCompound raw=JsonToNBT.getTagFromJson(INPUTS[profile]);raw.setInteger("x",POS.getX());raw.setInteger("y",POS.getY());raw.setInteger("z",POS.getZ());tile.readFromNBT(raw);}
   EntityPlayer player=new EntityPlayer(w,new com.mojang.authlib.GameProfile(new UUID(0,77),"RampOracle")){public boolean isCreative(){return true;}public boolean isSpectator(){return false;}};player.setPosition(4.5,80,3);player.capabilities.allowEdit=true;
   JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("inputMeta",meta);row.addProperty("fresh",profile<0);row.addProperty("input",profile<0?"{}":INPUTS[profile]);row.add("snapshot",snapshot(w,block,tile,player));lines.add(row.toString());retire(w);
  }
  if(lines.size()!=256)throw new AssertionError("Source role/meta/profile count");Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());Files.write(path,lines,java.nio.charset.StandardCharsets.UTF_8);byte[] raw=Files.readAllBytes(path);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("4b3b9c540e4aab2ecadcb3137024bba3c424bac1ddd9a8edbb7b8f98c97412e7"))throw new AssertionError("Changed Source settings/item golden "+hash);System.out.println("Ramp Source settings/item PASS cases="+lines.size()+" actual full/slab read+write/capture/pick/meta/bounds; no platform ownership transfer");
 }
}
