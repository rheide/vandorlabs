package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual specialized item callbacks on old root/helper cells, including out-of-cell float hits. */
public final class CanopyExistingItemLegacyOracle {
    private static final BlockPos CLICKED=new BlockPos(8,80,8);
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static BlockCanopy kind(List<BlockCanopy> blocks,String name){for(BlockCanopy block:blocks)if(block.kind.equals(name))return block;throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,successes=0;
        float[][] hits={{.5F,.5F,.5F},{-.75F,1.25F,2.25F},{.0001F,1F,.9999F},{-Float.MIN_VALUE,.5F,Float.MIN_VALUE}};
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(BlockCanopy heldBlock:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mount=0;mount<4;mount++)for(boolean helper:new boolean[]{false,true})for(EnumFacing side:EnumFacing.values())for(int profile=0;profile<hits.length;profile++){
                CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();BlockCanopy oldBlock=kind(blocks,mount<2?"canopy":"front_canopy_glass");BlockPos ownerPos=helper?(mount<2?CLICKED.offset(facing,oldBlock.length-1):CLICKED.down()):CLICKED;List<BlockPos> occupied=oldBlock.cells(ownerPos,facing);
                for(BlockPos p:occupied){world.setBlockState(p,oldBlock.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);TileEntityCanopy tile=TileEntityCanopy.at(world,p);tile.anchor=ownerPos;tile.large.mount=mount;}
                TileEntityCanopy owner=TileEntityCanopy.at(world,ownerPos);owner.setFootprint(occupied);if(TileEntityCanopy.at(world,CLICKED)==null)throw new AssertionError("Existing canopy target missing");
                EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("4db888fc-79c1-46f8-8bbd-e994ee639cdb"),"ExistingCanopyOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};actor.rotationYaw=EnumFacing.NORTH.getHorizontalAngle();ItemStack stack=new ItemStack(Item.getItemFromBlock(heldBlock),2);NBTTagCompound input=CanopyPlacementLegacyOracle.input(profile);if(input!=null)stack.setTagCompound(input.copy());actor.setHeldItem(EnumHand.MAIN_HAND,stack);
                float[] hit=hits[profile];EnumActionResult action=stack.getItem().onItemUse(actor,world,CLICKED,EnumHand.MAIN_HAND,side,hit[0],hit[1],hit[2]);boolean success=action==EnumActionResult.SUCCESS;if(stack.getCount()!=(success?1:2))throw new AssertionError("Source existing item count");
                TileEntityCanopy placed=null;for(net.minecraft.tileentity.TileEntity tile:world.tiles.values())if(tile instanceof TileEntityCanopy){TileEntityCanopy candidate=(TileEntityCanopy)tile;if(!occupied.contains(candidate.getPos())&&candidate.block()==heldBlock&&candidate.getPos().equals(candidate.anchor)){if(placed!=null)throw new AssertionError("Multiple new constituents");placed=candidate;}}
                if(success!=(placed!=null))throw new AssertionError("Existing item placement root/action mismatch");JsonObject row=new JsonObject();row.addProperty("id",heldBlock.kind);row.addProperty("width",heldBlock.width);row.addProperty("length",heldBlock.length);row.addProperty("height",heldBlock.height);row.addProperty("facing",facing.getIndex());row.addProperty("mount",mount);row.addProperty("helper",helper);row.addProperty("side",side.getIndex());row.addProperty("profile",profile);row.add("input",input==null?JsonNull.INSTANCE:CanopySettingsLegacyOracle.tags(input));JsonArray rawHit=new JsonArray();for(float value:hit)rawHit.add(Float.floatToRawIntBits(value));row.add("hit",rawHit);JsonArray old=new JsonArray();for(BlockPos p:occupied)old.add(pos(p));row.add("occupied",old);row.addProperty("result",action.name());row.addProperty("remaining",stack.getCount());
                if(placed!=null){JsonObject result=new JsonObject();result.add("pos",pos(placed.getPos()));result.addProperty("facing",placed.facing().getIndex());NBTTagCompound settings=new NBTTagCompound();placed.large.write(settings);result.add("settings",CanopySettingsLegacyOracle.tags(settings));JsonArray cells=new JsonArray();for(BlockPos p:placed.cells())cells.add(pos(p));result.add("cells",cells);row.add("placed",result);successes++;}out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"9e2292cd359030b93905fccbf4ef60f08ce1438338c456c2711cef8068dbb719");
        System.out.println("Actual existing canopy item callbacks PASS profiles="+profiles+" successes="+successes+" file="+file);
    }
}
