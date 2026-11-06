package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.MessageRedstoneChannel;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import com.vandorlabs.redstone.ChannelList;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
/** Actual released packet handler and real container authorization; no copied editor operation. */
public final class PropulsionEditorLegacyOracle {
 public static final class Player extends EntityPlayerMP {
  private Player(){super(null,null,null,null);}
  @Override public boolean isSpectator(){return false;}
  @Override public boolean isCreative(){return capabilities.isCreativeMode;}
  @Override public boolean canPlayerEdit(BlockPos p,EnumFacing side,ItemStack stack){return true;}
  @Override public ItemStack getHeldItemMainhand(){return ItemStack.EMPTY;}
  @Override public void sendStatusMessage(net.minecraft.util.text.ITextComponent text,boolean action){}
 }
 public static void main(String[] args)throws Exception {
  List<BlockPropulsionLight> blocks=PropulsionPlacementLegacyOracle.initialize();java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)f.get(null);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int rows=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(BlockPropulsionLight block:blocks)for(EnumFacing face:EnumFacing.values())for(int profile=0;profile<12;profile++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(8,90,8);PanelPlane plane=PanelPlane.of(face);List<BlockPos> cells=new ArrayList<>();int size=block instanceof BlockConnectedPropulsionLight?2:1;
   for(int x=0;x<size;x++)for(int y=0;y<size;y++){BlockPos p=base.offset(plane.right,x).offset(plane.up,y);world.setBlockState(p,block.getDefaultState().withProperty(BlockPropulsionLight.FACING,face).withProperty(BlockPropulsionLight.POWERED,true),2);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("LightInitialized",true);tag.setBoolean("ManualOn",true);tag.setInteger("ParticleLevel",1+(x+y)%3);tag.setInteger("PropulsionSideTexture",9);tag.setBoolean("PropulsionJoin",true);tag.setIntArray("RedstoneChannels",new int[]{17});TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(p);tile.readFromNBT(tag);tile.setPos(p);cells.add(p);}
   BlockPos selected=cells.get(cells.size()-1);Player player=(Player)unsafe.allocateInstance(Player.class);player.world=world;player.posX=selected.getX()+.5;player.posY=selected.getY()+.5;player.posZ=selected.getZ()+.5;player.capabilities=new PlayerCapabilities();player.capabilities.isCreativeMode=profile!=10;TileEntityRedstoneLight owner=(TileEntityRedstoneLight)world.getTileEntity(selected);player.openContainer=profile==11?new ContainerRedstoneChannel((TileEntityRedstoneLight)world.getTileEntity(base)):new ContainerRedstoneChannel(owner);if(profile==11&&size==1)player.posX+=20;
   boolean updateShape=profile>=6;int target=profile==6?block.shape():profile==7?0:profile==8?1:2;
   MessageRedstoneChannel message=new MessageRedstoneChannel(selected,37,profile!=0,true,profile!=1,profile%2==0,profile!=2,64,updateShape,target).withChannels(ChannelList.of(37,41)).withParticleLevel(3).withRedstoneInverted(profile%3==0).withSignalBrightness(profile%3!=1,5);
   JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("face",face.getIndex());row.addProperty("profile",profile);row.addProperty("target",target);row.add("before",snapshot(world,cells));MessageRedstoneChannel.Handler.apply(player,message);row.add("after",snapshot(world,cells));out.println(gson.toJson(row));rows++;
  }}SegmentedReferenceGolden.check(file,"376a1aa47e65f3f9ed58b695c9bf59f6514d0462dd78b7d3968ebd6664281271");System.out.println("Actual propulsion editor PASS rows="+rows+" realHandler/container/shapeShortCircuit/groupPhaseOrder/all27/sixFaces/creative+identityGuards");
 }
 private static JsonArray snapshot(NonRenderingChecks.MemoryWorld world,List<BlockPos> cells){JsonArray result=new JsonArray();for(BlockPos p:cells){TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(p);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());JsonObject c=new JsonObject();c.addProperty("block",world.getBlockState(p).getBlock().getRegistryName().getResourcePath());c.addProperty("powered",world.getBlockState(p).getValue(BlockPropulsionLight.POWERED));for(String key:new String[]{"ChannelLevel","ParticleLevel","ParticleThreshold","PropulsionSideTexture"})c.addProperty(key,saved.getInteger(key));for(String key:new String[]{"ManualOn","ChannelSignal","SignalBrightness","RedstoneInverted","PropulsionJoin","LightInitialized"})c.addProperty(key,saved.getBoolean(key));JsonArray channels=new JsonArray();for(int channel:saved.getIntArray("RedstoneChannels"))channels.add(channel);c.add("channels",channels);result.add(c);}return result;}
}
