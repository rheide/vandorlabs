package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual ItemBlock generic-NBT merge followed by the specialized canopy placement callback. */
public final class CanopyConfiguredItemLegacyOracle {
    private static final BlockPos CLICKED=new BlockPos(8,80,8);
    private static final class World extends CanopyPlacementLegacyOracle.World {
        BlockPos first;
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){if(first==null&&state.getBlock() instanceof BlockCanopy)first=pos.toImmutable();return super.setBlockState(pos,state,flags);}
    }
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static NBTTagCompound input(int profile){
        if(profile==0)return null;NBTTagCompound tag=new NBTTagCompound(),generic=new NBTTagCompound();
        switch(profile){
            case 1:break;case 2:tag.setInteger("CanopyDepth",17);break;
            case 3:generic.setInteger("CanopyDepth",42);break;
            case 4:generic.setInteger("CanopyDepth",42);tag.setInteger("CanopyDepth",17);break;
            case 5:generic.setBoolean("CenteredPlacement",false);generic.setBoolean("HollowPlacement",false);break;
            case 6:generic.setFloat("Progress",.5F);generic.setBoolean("Open",true);break;
            case 7:generic.setLong("AnchorOffset",new BlockPos(4,0,0).toLong());break;
            case 8:generic.setLong("PairOffset",new BlockPos(4,0,0).toLong());generic.setInteger("CanopyWidth",3);break;
            case 9:case 10:generic.setIntArray("CanopyFootprint",new int[]{0,0,0,0,0,2});break;
            case 11:generic.setIntArray("CanopyFootprint",new int[]{0,0,2});break;
            case 12:generic.setString("ForeignCanopyData","opaque-original");break;
            case 13:tag.setByte("BlockEntityTag",(byte)7);tag.setByte("CanopyScale",(byte)100);break;
            case 14:generic.setIntArray("CanopyFootprint",new int[3003]);break;
            case 15:generic.setIntArray("CanopyReleasedHelpers",new int[]{1,2});generic.setBoolean("CanopyWaitingForReservations",true);generic.setBoolean("Open",true);generic.setFloat("Progress",1);break;
            default:throw new AssertionError(profile);
        }
        if(!generic.hasNoTags())tag.setTag("BlockEntityTag",generic);return tag;
    }
    private static EnumActionResult place(World world,BlockCanopy block,EnumFacing facing,EnumFacing side,NBTTagCompound input){
        world.setBlockState(CLICKED,Blocks.STONE.getDefaultState(),2);EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("f171c385-e263-462a-898d-3eccde2e8737"),"ConfiguredCanopyOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};actor.rotationYaw=facing.getHorizontalAngle();ItemStack held=new ItemStack(Item.getItemFromBlock(block),2);if(input!=null)held.setTagCompound(input.copy());actor.setHeldItem(EnumHand.MAIN_HAND,held);NBTTagCompound before=held.serializeNBT();Map<BlockPos,IBlockState> states=new HashMap<>(world.states);EnumActionResult result=held.getItem().onItemUse(actor,world,CLICKED,EnumHand.MAIN_HAND,side,.25F,.75F,.625F);if(held.getCount()!=(result==EnumActionResult.SUCCESS?1:2))throw new AssertionError("Configured held count");if(result!=EnumActionResult.SUCCESS&&(!before.equals(held.serializeNBT())||!states.equals(world.states)))throw new AssertionError("Refused configured placement changed its owner or scene");return result;
    }
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,successes=0,overwrites=0,clickedOverwrites=0,invisible=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH})for(int profile=0;profile<16;profile++){
                NBTTagCompound input=input(profile);World world=new World();BlockPos foreign=null;if(profile==10){World reference=new World();if(place(reference,block,facing,side,input)==EnumActionResult.SUCCESS&&reference.first!=null){foreign=reference.first.add(0,0,2);world.setBlockState(foreign,Blocks.STONE.getDefaultState(),2);}}
                EnumActionResult action=place(world,block,facing,side,input);JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("playerFacing",facing.getIndex());row.addProperty("side",side.getIndex());row.addProperty("profile",profile);row.addProperty("result",action.name());boolean clickedRetained=world.getBlockState(CLICKED).getBlock()==Blocks.STONE;row.addProperty("clickedRetained",clickedRetained);if(!clickedRetained)clickedOverwrites++;if(Boolean.getBoolean("vandorlabs.canopyItemSafety")&&!clickedRetained)throw new AssertionError("Configured item replaced clicked support "+block.kind+"/"+facing+"/"+side+"/"+profile);row.add("input",input==null?JsonNull.INSTANCE:CanopySettingsLegacyOracle.tags(input));if(foreign!=null){row.add("foreign",pos(foreign));boolean retained=world.getBlockState(foreign).getBlock()==Blocks.STONE;row.addProperty("foreignRetained",retained);if(!retained)overwrites++;if(Boolean.getBoolean("vandorlabs.canopyItemSafety")&&(!retained||action==EnumActionResult.SUCCESS||world.first!=null))throw new AssertionError("Configured item replaced foreign STONE "+block.kind+"/"+facing+"/"+side+"/"+profile);}
                if(world.first!=null){TileEntityCanopy root=TileEntityCanopy.at(world,world.first);if(root==null)throw new AssertionError("Configured factory missing");row.add("root",pos(world.first));row.addProperty("draws",root.draws());if(!root.draws())invisible++;row.addProperty("width",root.width());NBTTagCompound saved=new NBTTagCompound();root.writeToNBT(saved);row.add("saved",CanopySettingsLegacyOracle.tags(saved));JsonArray cells=new JsonArray();for(BlockPos p:root.cells()){JsonObject cell=new JsonObject();cell.add("pos",pos(p));cell.addProperty("id",world.getBlockState(p).getBlock().getRegistryName().toString());TileEntityCanopy tile=TileEntityCanopy.at(world,p);cell.add("anchor",tile==null||tile.anchor==null?JsonNull.INSTANCE:pos(tile.anchor));cells.add(cell);}row.add("cells",cells);}
                if(action==EnumActionResult.SUCCESS)successes++;out.println(gson.toJson(row));profiles++;
            }
        }
        if(!Boolean.getBoolean("vandorlabs.canopyItemSafety"))SegmentedReferenceGolden.check(file,"2a7869e22458d5189d16b943cb7d425744e0e7d5d16216dfa3de0946a0509a81");
        System.out.println("Actual configured canopy item callbacks PASS profiles="+profiles+" successes="+successes+" foreignOverwrites="+overwrites+" clickedOverwrites="+clickedOverwrites+" invisible="+invisible+" file="+file);
    }
}
