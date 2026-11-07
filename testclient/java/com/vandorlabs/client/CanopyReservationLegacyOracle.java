package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.CanopyReservations;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual open helper release, foreign-cell close refusal and transactional restoration callbacks. */
public final class CanopyReservationLegacyOracle {
    private static final BlockPos CLICKED=new BlockPos(15,79,15);
    private static final class World extends CanopyPlacementLegacyOracle.World {
        int fault,calls;boolean restoring;BlockCanopy block;
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){if(restoring&&state.getBlock()==block&&++calls==2&&fault==1)return false;boolean result=super.setBlockState(pos,state,flags);if(restoring&&state.getBlock()==block&&calls==2&&fault==2)tiles.remove(pos);return result;}
    }
    private static JsonArray position(BlockPos pos){JsonArray a=new JsonArray();a.add(pos.getX());a.add(pos.getY());a.add(pos.getZ());return a;}
    private static JsonObject snapshot(World world,TileEntityCanopy root){JsonObject out=new JsonObject();out.addProperty("progress",root.progress);out.addProperty("waiting",root.waitingForReservations);out.addProperty("target",root.targetOpen);JsonArray cells=new JsonArray();for(BlockPos pos:root.cells()){JsonObject cell=new JsonObject();cell.add("pos",position(pos));cell.addProperty("released",root.isReleasedCell(pos));IBlockState state=world.states.getOrDefault(pos,Blocks.AIR.getDefaultState());cell.addProperty("id",state.getBlock().getRegistryName().toString());TileEntityCanopy tile=world.tiles.get(pos) instanceof TileEntityCanopy?(TileEntityCanopy)world.tiles.get(pos):null;cell.add("anchor",tile==null||tile.anchor==null?JsonNull.INSTANCE:position(tile.anchor));cells.add(cell);}out.add("cells",cells);out.add("saved",CanopySettingsLegacyOracle.tags(root.writeToNBT(new NBTTagCompound())));return out;}
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("CanopyReference");File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,released=0,blocked=0,faults=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")) {
            for(BlockCanopy block:blocks)if(block.opening()||com.vandorlabs.canopy.LargeCanopySettings.supports(block.kind))for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int motion=0;motion<2;motion++)for(boolean slide:new boolean[]{false,true})for(int variant=0;variant<8;variant++) {
                World world=new World();world.block=block;world.time=1;world.setBlockState(CLICKED,Blocks.STONE.getDefaultState(),2);EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("4e79b6e9-5ab8-4336-9436-828135df0252"),"CanopyReservationOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};actor.rotationYaw=(block.opening()?facing.getOpposite():facing).getHorizontalAngle();ItemStack stack=new ItemStack(net.minecraft.item.Item.getItemFromBlock(block),2);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("CanopyOpenable",true);tag.setInteger("CanopyMotion",motion);tag.setBoolean("CanopySlideForward",slide);tag.setInteger("CanopyDepth",17);tag.setInteger("CanopyScale",90);stack.setTagCompound(tag);actor.setHeldItem(EnumHand.MAIN_HAND,stack);
                if(stack.getItem().onItemUse(actor,world,CLICKED,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)!=EnumActionResult.SUCCESS)throw new AssertionError("Source reservation fixture placement");TileEntityCanopy root=null;for(net.minecraft.tileentity.TileEntity tile:world.tiles.values())if(tile instanceof TileEntityCanopy&&((TileEntityCanopy)tile).draws()){if(root!=null)throw new AssertionError("Source reservation duplicate root");root=(TileEntityCanopy)tile;}if(root==null)throw new AssertionError("Source reservation root");root.targetOpen=true;root.progress=root.previous=1;
                CanopyReservations.releaseOpen(root);List<BlockPos> free=new ArrayList<>();for(BlockPos pos:root.cells())if(root.isReleasedCell(pos))free.add(pos);JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("motion",motion);row.addProperty("slide",slide);row.addProperty("variant",variant);row.add("open",snapshot(world,root));released+=free.size();
                if(!free.isEmpty()){BlockPos cell=free.get(0);if(variant==1)world.setBlockState(cell,Blocks.STONE.getDefaultState(),2);if(variant==2)world.setBlockState(cell,Blocks.WATER.getDefaultState(),2);if(variant==3)world.setBlockState(cell,Blocks.LAVA.getDefaultState(),2);if(variant==4){world.setBlockState(cell,world.getBlockState(root.getPos()),2);((TileEntityCanopy)world.getTileEntity(cell)).anchor=cell;}if(variant==5||variant==6){world.fault=variant==5?1:2;world.restoring=true;}}
                boolean cold=variant==7&&(root.getPos().getX()>>4)==0&&(root.getPos().getZ()>>4)==0;world.chunkLimit=cold;row.addProperty("cold",cold);
                root.targetOpen=false;boolean ready=CanopyReservations.prepareClose(root);world.chunkLimit=false;row.addProperty("ready",ready);row.addProperty("writes",world.calls);row.add("close",snapshot(world,root));if(!ready)blocked++;if(world.restoring)faults++;
                world.restoring=false;world.fault=0;for(BlockPos pos:free){IBlockState state=world.states.getOrDefault(pos,Blocks.AIR.getDefaultState());TileEntityCanopy tile=world.tiles.get(pos) instanceof TileEntityCanopy?(TileEntityCanopy)world.tiles.get(pos):null;if(state.getBlock()!=block||tile==null||!root.getPos().equals(tile.anchor))world.setBlockState(pos,Blocks.AIR.getDefaultState(),2);}boolean restored=CanopyReservations.restoreFootprint(root);row.addProperty("retry",restored);row.add("restored",snapshot(world,root));if(!restored)throw new AssertionError("Source reservation retry must restore after obstruction removal");CanopyReservations.removed(root);out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"061dcd199926c7c2c41885c062265d38a30602a5b5bab94d36220263d5817a5c");
        System.out.println("Actual canopy reservations PASS profiles="+profiles+" released="+released+" refused="+blocked+" faultCases="+faults+" file="+file);
    }
}
