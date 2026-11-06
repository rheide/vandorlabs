package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ItemCatalogBlock;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual released seven item callbacks and all27 registered engine/hover shapes, including vanilla partial-NBT merge. */
public final class PropulsionPlacementLegacyOracle {
 private static final BlockPos POS=new BlockPos(8,80,8);private static net.minecraft.server.MinecraftServer marker;
 private static final class World extends NonRenderingChecks.MemoryWorld {World(boolean remote){super(remote);}@Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return isRemote?null:marker;}}
 private static NBTTagCompound input(int kind){if(kind==0)return null;NBTTagCompound tag=new NBTTagCompound();if(kind>=2&&kind<=5)tag.setInteger("PropulsionShape",kind==2?1:kind==3?2:kind==4?3:-1);if(kind==6)tag.setByte("PropulsionShape",(byte)2);if(kind==7)tag.setString("PropulsionShape","2");if(kind>=8&&kind<=10){tag.setInteger("PropulsionShape",kind-8);tag.setIntArray("RedstoneChannels",new int[]{17,19});tag.setInteger("ParticleLevel",3);tag.setInteger("PropulsionSideTexture",64);tag.setBoolean("ManualOn",false);tag.setBoolean("LightInitialized",true);tag.setBoolean("SignalBrightness",true);tag.setInteger("ParticleThreshold",7);tag.setBoolean("RedstoneInverted",true);tag.setBoolean("PropulsionJoin",false);}if(kind==11)tag.setInteger("RedstoneChannel",37);if(kind==12)tag.setBoolean("ParticleStream",true);if(kind==13)tag.setInteger("PropulsionSideTexture",64);if(kind==14)tag.setBoolean("PropulsionJoin",false);if(kind==15){tag.setBoolean("SignalBrightness",true);tag.setInteger("ChannelLevel",7);tag.setInteger("ParticleLevel",3);tag.setInteger("ParticleThreshold",8);}return tag;}
 private static JsonArray box(AxisAlignedBB b){JsonArray out=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})out.add(v);return out;}
 private static JsonObject state(IBlockState state){JsonObject out=new JsonObject();out.addProperty("id",state.getBlock().getRegistryName().getResourcePath());out.addProperty("facing",state.getValue(BlockPropulsionLight.FACING).getIndex());out.addProperty("powered",state.getValue(BlockPropulsionLight.POWERED));out.addProperty("particles",state.getValue(BlockPropulsionLight.PARTICLES));return out;}
 private static JsonObject snapshot(World world,EntityPlayer actor){IBlockState s=world.states.get(POS);BlockPropulsionLight block=(BlockPropulsionLight)s.getBlock();TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(POS);JsonObject out=state(s);out.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());out.addProperty("pick",block.getPickBlock(s,new RayTraceResult(Vec3d.ZERO,EnumFacing.NORTH,POS),world,POS,actor).serializeNBT().toString());JsonArray drops=new JsonArray();for(ItemStack stack:block.getDrops(world,POS,s,0))drops.add(stack.serializeNBT().toString());out.add("drops",drops);out.add("bounds",box(block.getBoundingBox(s,world,POS)));return out;}
 private static List<BlockPropulsionLight> initialize()throws Exception {
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);marker=(net.minecraft.server.MinecraftServer)((sun.misc.Unsafe)f.get(null)).allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);
  net.minecraftforge.fml.common.ModMetadata metadata=new net.minecraftforge.fml.common.ModMetadata();metadata.modId="vandorlabs";metadata.name="SourceOracle";net.minecraftforge.fml.common.Loader loader=net.minecraftforge.fml.common.Loader.instance();java.lang.reflect.Field controller=net.minecraftforge.fml.common.Loader.class.getDeclaredField("modController");controller.setAccessible(true);if(controller.get(loader)==null)controller.set(loader,new net.minecraftforge.fml.common.LoadController(loader));loader.setActiveModContainer(new net.minecraftforge.fml.common.DummyModContainer(metadata));
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneLight.class,new ResourceLocation("vandorlabs","redstone_light"));List<BlockPropulsionLight> blocks=new ArrayList<>();for(Block block:ModBlocks.BLOCKS)if(block instanceof BlockPropulsionLight){blocks.add((BlockPropulsionLight)block);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);}for(BlockPropulsionLight block:blocks)if(block.shape()==0){ItemCatalogBlock item=new ItemCatalogBlock(block);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(item.setRegistryName(block.getRegistryName()));}if(blocks.size()!=27)throw new AssertionError("Source catalog engine coverage "+blocks.size());return blocks;
 }
 public static void main(String[] args)throws Exception {
  List<BlockPropulsionLight> blocks=initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int poses=0,placements=0;float[] hit={0,Math.nextDown(.5F),.5F,Math.nextUp(.5F),1};
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(BlockPropulsionLight block:blocks)for(int meta=-1;meta<17;meta++){
    IBlockState s=block.getStateFromMeta(meta);World world=new World(false);world.states.put(POS,s);JsonObject row=new JsonObject();row.addProperty("kind","pose");row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("class",block.getClass().getSimpleName());row.addProperty("meta",meta);row.addProperty("defaultFacing",block.getDefaultState().getValue(BlockPropulsionLight.FACING).getIndex());row.addProperty("shape",block.shape());row.addProperty("family",block.familyId());row.addProperty("join",block.hasJoinMode());row.add("state",state(s));row.addProperty("encoded",block.getMetaFromState(s));row.add("bounds",box(block.getBoundingBox(s,world,POS)));JsonArray rotations=new JsonArray();for(Rotation rotation:Rotation.values())rotations.add(state(block.withRotation(s,rotation)));row.add("rotations",rotations);JsonArray mirrors=new JsonArray();for(Mirror mirror:Mirror.values())mirrors.add(state(block.withMirror(s,mirror)));row.add("mirrors",mirrors);out.println(gson.toJson(row));poses++;
   }
   for(BlockPropulsionLight block:blocks)if(block.shape()==0)for(boolean remote:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.values())for(float x:hit)for(float y:hit)for(int kind=0;kind<16;kind++){
    float hx=x,hy=y,hz=.625F;switch(facing){case SOUTH:hx=1-x;break;case EAST:hz=x;break;case WEST:hz=1-x;break;case UP:hy=.625F;hz=y;break;case DOWN:hy=.625F;hz=1-y;break;default:break;}
    World world=new World(remote);EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("d28f2da8-e07c-4219-a15b-e389d9ef9e09"),"PropulsionOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};ItemBlock item=(ItemBlock)Item.getItemFromBlock(block);ItemStack held=new ItemStack(item,2);NBTTagCompound input=input(kind);if(input!=null)held.setTagInfo("BlockEntityTag",input);actor.setHeldItem(EnumHand.MAIN_HAND,held);String before=held.serializeNBT().toString();IBlockState initial=block.getStateForPlacement(world,POS,facing,hx,hy,hz,0,actor,EnumHand.MAIN_HAND);if(!item.placeBlockAt(held,actor,world,POS,facing,hx,hy,hz,initial)||!held.serializeNBT().toString().equals(before))throw new AssertionError("Actual generic engine item callback");
    JsonObject row=new JsonObject();row.addProperty("kind","placement");row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("remote",remote);row.addProperty("face",facing.getIndex());JsonArray coords=new JsonArray();coords.add(hx);coords.add(hy);coords.add(hz);row.add("hit",coords);row.addProperty("tagKind",kind);row.addProperty("input",input==null?"":input.toString());row.add("initial",state(initial));row.add("placed",snapshot(world,actor));out.println(gson.toJson(row));placements++;
   }
  }
  SegmentedReferenceGolden.check(file,"dc2e19b95ffb4b3277542e172f1b93a2d6d5d40a30647e0efbb881100b11fe84");
  System.out.println("Actual propulsion placement/pose PASS poses="+poses+" itemCallbacks="+placements+" 27Sourceblocks/7items/server+client/6faces/float ties/shape fallback/partialNBT/pick-vs-drop/rotations");
 }
}
