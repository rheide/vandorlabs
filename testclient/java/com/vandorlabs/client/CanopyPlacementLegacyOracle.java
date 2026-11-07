package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual specialized canopy item callbacks, including centered and surface-only footprints. */
public final class CanopyPlacementLegacyOracle {
    private static final BlockPos CLICKED=new BlockPos(8,80,8);
    private static net.minecraft.server.MinecraftServer marker;
    static class World extends NonRenderingChecks.MemoryWorld {
        World(){super(false);}
        @Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return marker;}
    }
    private static JsonArray pos(BlockPos p) {
        JsonArray out=new JsonArray(); out.add(p.getX());out.add(p.getY());out.add(p.getZ());return out;
    }
    private static NBTTagCompound input(int profile) {
        if(profile==0)return null;
        NBTTagCompound tag=new NBTTagCompound();
        if(profile==2){tag.setInteger("CanopyDepth",17);tag.setInteger("CanopyScale",90);tag.setInteger("CanopyShade",2);tag.setInteger("CanopyTint",3);tag.setInteger("CanopyTextureLayout",2);tag.setBoolean("CanopyTileMaterial",true);tag.setIntArray("CanopyChannels",new int[]{7,3,7});tag.setBoolean("CanopyOpenable",true);}
        if(profile==3){tag.setInteger("CanopyDepth",0);tag.setByte("CanopyScale",(byte)100);tag.setInteger("CanopyMount",3);tag.setBoolean("CanopyActivateOnPower",false);tag.setBoolean("CanopyTileMaterial",true);}
        return tag;
    }
    private static JsonObject snapshot(World world,BlockCanopy block,EntityPlayer actor) {
        JsonObject out=new JsonObject();JsonArray cells=new JsonArray();
        List<BlockPos> positions=new ArrayList<>();
        for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> entry:world.states.entrySet())if(entry.getValue().getBlock()==block)positions.add(entry.getKey());
        positions.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        TileEntityCanopy root=null;int drops=0;JsonArray owners=new JsonArray();
        for(BlockPos p:positions){TileEntityCanopy tile=TileEntityCanopy.at(world,p);if(tile==null)throw new AssertionError("Source placed canopy tile missing");JsonObject cell=new JsonObject();cell.add("pos",pos(p));cell.addProperty("facing",tile.facing().getIndex());cell.add("anchor",tile.anchor==null?JsonNull.INSTANCE:pos(tile.anchor));cell.addProperty("draws",tile.draws());cells.add(cell);if(tile.draws()){if(root!=null)throw new AssertionError("Duplicate Source drawing owner");root=tile;}for(ItemStack drop:block.getDrops(world,p,world.getBlockState(p),0)){drops+=drop.getCount();JsonObject owner=new JsonObject();owner.add("pos",pos(p));owner.add("item",CanopySettingsLegacyOracle.tags(drop.serializeNBT()));owners.add(owner);}}
        out.add("cells",cells);out.addProperty("dropCount",drops);out.add("dropOwners",owners);
        if(root!=null){out.add("root",pos(root.getPos()));out.add("saved",CanopySettingsLegacyOracle.tags(root.writeToNBT(new NBTTagCompound())));out.add("pick",CanopySettingsLegacyOracle.tags(block.getPickBlock(world.getBlockState(root.getPos()),new RayTraceResult(Vec3d.ZERO,EnumFacing.NORTH,root.getPos()),world,root.getPos(),actor).serializeNBT()));if(drops!=1)throw new AssertionError("Source canopy drop ownership "+drops);}
        return out;
    }
    static List<BlockCanopy> initialize()throws Exception {
        PropulsionPlacementLegacyOracle.initialize();java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);marker=(net.minecraft.server.MinecraftServer)((sun.misc.Unsafe)field.get(null)).allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityCanopy.class,new ResourceLocation("vandorlabs","canopy"));
        List<BlockCanopy> blocks=new ArrayList<>();for(Block block:ModBlocks.BLOCKS)if(block instanceof BlockCanopy){blocks.add((BlockCanopy)block);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(new BlockCanopy.CanopyItem((BlockCanopy)block).setRegistryName(block.getRegistryName()));}
        if(blocks.size()!=11)throw new AssertionError("Source canopy catalog "+blocks.size());
        return blocks;
    }
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=initialize();
        File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int callbacks=0,successes=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")) {
            for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(EnumFacing side:EnumFacing.values())for(int profile=0;profile<4;profile++) {
                World world=new World();world.setBlockState(CLICKED,Blocks.STONE.getDefaultState(),2);
                EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("03c82db1-0b73-4387-87ac-bd268888a549"),"CanopyOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};
                actor.rotationYaw=facing.getHorizontalAngle();ItemStack stack=new ItemStack(Item.getItemFromBlock(block),2);NBTTagCompound tag=input(profile);if(tag!=null)stack.setTagCompound(tag.copy());actor.setHeldItem(EnumHand.MAIN_HAND,stack);
                EnumActionResult result=stack.getItem().onItemUse(actor,world,CLICKED,EnumHand.MAIN_HAND,side,.25F,.75F,.625F);
                JsonObject placed=snapshot(world,block,actor);
                boolean success=result==EnumActionResult.SUCCESS;
                if(stack.getCount()!=(success?1:2)||success==(placed.getAsJsonArray("cells").size()==0))throw new AssertionError("Source item admission/count mismatch");
                JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("width",block.width);row.addProperty("height",block.height);row.addProperty("length",block.length);row.addProperty("opening",block.opening());row.addProperty("playerFacing",facing.getIndex());row.addProperty("side",side.getIndex());row.addProperty("profile",profile);row.add("input",tag==null?JsonNull.INSTANCE:CanopySettingsLegacyOracle.tags(tag));row.addProperty("result",result.name());row.addProperty("remaining",stack.getCount());row.add("placed",placed);out.println(gson.toJson(row));callbacks++;if(success)successes++;
            }
        }
        SegmentedReferenceGolden.check(file,"f529b3c74897abc394749b18ee95874af714d68dd9ce51763cf0b67580e89045");
        System.out.println("Actual standalone canopy item PASS ids="+blocks.size()+" callbacks="+callbacks+" successful="+successes+" file="+file);
    }
}
