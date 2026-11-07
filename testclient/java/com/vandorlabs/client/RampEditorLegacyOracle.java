package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.network.MessageRampController;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityRampController;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Released packet handler/order in an empty memory world; no journal or actor-receipt acceptance claim. */
public final class RampEditorLegacyOracle {
 private static final BlockPos POS=new BlockPos(4,80,4);
 public static final class Player extends EntityPlayerMP {
  boolean spectator,editable=true;private Player(){super(null,null,null,null);}
  @Override public boolean isSpectator(){return spectator;}@Override public boolean isCreative(){return capabilities.isCreativeMode;}
  @Override public boolean canPlayerEdit(BlockPos pos,EnumFacing face,ItemStack item){return editable;}
  @Override public ItemStack getHeldItemMainhand(){return ItemStack.EMPTY;}
  @Override public UUID getUniqueID(){return new UUID(0,77);}
  @Override public void sendStatusMessage(net.minecraft.util.text.ITextComponent text,boolean action){}
 }
 public static final class Connection extends NetHandlerPlayServer {
  int packets;private Connection(){super(null,null,null);}
  @Override public void sendPacket(Packet<?> packet){packets++;}
 }
 public static final class Tile extends TileEntityRampController {
  final List<String> calls=new ArrayList<>();
  @Override public boolean configureHalfOffsets(EntityPlayer p,int a,int b,int pixels,boolean power,boolean slow,boolean lift,EnumFacing direction,int travel,boolean extend,int speed,boolean match,boolean connected,int interpolation,int escalator,boolean moat,boolean vertical){calls.add("configure");boolean result=super.configureHalfOffsets(p,a,b,pixels,power,slow,lift,direction,travel,extend,speed,match,connected,interpolation,escalator,moat,vertical);calls.add("configured:"+result+":"+error);return result;}
  @Override public void setRedstoneChannels(ChannelList next){if(calls!=null)calls.add("channels");super.setRedstoneChannels(next);}
  @Override public void setAnalogDeployment(boolean next){if(calls!=null)calls.add("analog:"+next);super.setAnalogDeployment(next);}
 }
 public static void main(String[] args)throws Exception {
  List<BlockRampController> blocks=RampSettingsLegacyOracle.initialize();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(Tile.class,new net.minecraft.util.ResourceLocation("vandorlabs","ramp_editor_oracle"));
  java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)f.get(null);List<String> lines=new ArrayList<>();int commits=0,errors=0;
  for(BlockRampController block:blocks)for(int meta=0;meta<16;meta++)for(int profile=0;profile<16;profile++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.time=100;world.setBlockState(POS,block.getStateFromMeta(meta),2);world.getTileEntity(POS).invalidate();Tile tile=new Tile();tile.setWorld(world);tile.setPos(POS);world.tiles.put(POS,tile);
   Player player=(Player)unsafe.allocateInstance(Player.class);player.editable=true;player.world=world;player.posX=4.5;player.posY=80;player.posZ=3;player.capabilities=new PlayerCapabilities();player.capabilities.allowEdit=true;player.connection=(Connection)unsafe.allocateInstance(Connection.class);player.openContainer=new ContainerRampController(tile);
   if(profile==10)player.openContainer=new ContainerRampController(new TileEntityRampController());if(profile==11)player.spectator=true;if(profile==12)player.posX=40;if(profile==13)player.editable=false;if(profile==14)player.openContainer=null;
   if(profile==0&&!tile.usable(player))throw new AssertionError("Initial usable context pos="+tile.getPos()+" installed="+(world.getTileEntity(POS)==tile)+" distance="+player.getDistanceSq(POS)+" loaded="+world.isBlockLoaded(POS)+" edit="+com.vandorlabs.items.ConfigurationAccess.canKeepDialogOpen(player,world,POS)+" locked="+com.vandorlabs.gear.ArmTransactions.locked(world,POS));
   int pixels=profile==2?3:4,start=profile==3?33:-3,speed=profile==4?99:2,escalator=profile==5||profile==6?1:0;
   MessageRampController sent=MessageRampController.halfOffsets(POS,start,7,pixels,profile!=1,false,profile==6,EnumFacing.EAST,17,0,false,speed,true).withInterpolation(profile==9?99:2).withEscalator(escalator).withMoat(false).withVerticalLayout(profile==7).withAnalogDeployment(profile%2==0||profile==1).withChannels(ChannelList.of(23,29));
   io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();MessageRampController message=new MessageRampController();int bytes;String wire;try{sent.toBytes(buffer);if(profile==15)buffer.setInt(20,99);bytes=buffer.readableBytes();byte[] raw=new byte[bytes];buffer.getBytes(buffer.readerIndex(),raw);wire=Base64.getEncoder().encodeToString(raw);message.fromBytes(buffer);if(buffer.isReadable())throw new AssertionError("Trailing Source Ramp command");}finally{buffer.release();}
   long revision=tile.configurationRevision();MessageRampController.Handler.apply(player,message);boolean committed=tile.configurationRevision()!=revision;if(committed)commits++;if(tile.error)errors++;
   JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("scan",BlockRampController.scanFacing(world.getBlockState(POS)).getIndex());row.addProperty("profile",profile);row.addProperty("bytes",bytes);row.addProperty("wire",wire);row.addProperty("committed",committed);row.addProperty("error",tile.error);row.addProperty("status",tile.status);row.addProperty("analog",tile.analogDeployment);row.addProperty("start",tile.startHalfSteps());row.addProperty("end",tile.endHalfSteps());row.addProperty("packets",((Connection)player.connection).packets);JsonArray calls=new JsonArray();for(String call:tile.calls)calls.add(call);row.add("calls",calls);JsonArray channels=new JsonArray();for(int channel:tile.getRedstoneChannels().toArray())channels.add(channel);row.add("channels",channels);lines.add(row.toString());
   if(committed&&(!tile.calls.contains("channels")||!tile.calls.contains("analog:"+(profile%2==0||profile==1))))throw new AssertionError("Committed choices must apply channels then analog");if(!committed&&(tile.calls.contains("channels")||tile.calls.toString().contains("analog:")))throw new AssertionError("Refused choices changed channel/analog");RampSettingsLegacyOracle.retire(world);
  }
  if(commits!=148||errors!=288)throw new AssertionError("Actual command oracle never reached configure/commit/error");
  if(lines.size()!=512)throw new AssertionError("Source command coverage");Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());Files.write(path,lines,java.nio.charset.StandardCharsets.UTF_8);SegmentedReferenceGolden.check(path.toFile(),"6018bf41102c0419b1066656eaee0623f1776c73d9178331347ccec932c0f857");System.out.println("Ramp Source editor PASS profiles="+lines.size()+" commits="+commits+" errors="+errors+" actualHandler/order/authorization/full+slab16meta; emptyWorld/noJournalClaim");
 }
}
