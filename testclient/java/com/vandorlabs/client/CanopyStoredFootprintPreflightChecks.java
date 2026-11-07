package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual source callbacks prove loaded, editable admission before any stored helper cell is written. */
public final class CanopyStoredFootprintPreflightChecks {
    private static final class World extends CanopyPlacementLegacyOracle.World {
        BlockPos denied;int canopyWrites,coldWrites;
        @Override public boolean isBlockModifiable(EntityPlayer player,BlockPos pos){return !pos.equals(denied);}
        @Override public IBlockState getBlockState(BlockPos pos){if(!isBlockLoaded(pos))throw new AssertionError("Cold preflight read "+pos);return super.getBlockState(pos);}
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){if(!isBlockLoaded(pos)){coldWrites++;throw new AssertionError("Cold helper write "+pos);}if(state.getBlock() instanceof BlockCanopy)canopyWrites++;return super.setBlockState(pos,state,flags);}
    }
    public static void main(String[] args)throws Exception {
        BlockCanopy block=null;for(BlockCanopy candidate:CanopyPlacementLegacyOracle.initialize())if(candidate.kind.equals("regular_canopy_glass"))block=candidate;if(block==null)throw new AssertionError("Source regular canopy");
        for(int test=0;test<4;test++){
            World world=new World();BlockPos clicked=new BlockPos(test==2?15:8,80,test==2?15:8),root=clicked.up(),extra=root.east(2);world.setBlockState(clicked,Blocks.STONE.getDefaultState(),2);if(test==0)world.setBlockState(extra,Blocks.STONE.getDefaultState(),2);if(test==1)world.denied=extra;if(test==2)world.chunkLimit=true;
            EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("678c58d2-bba5-4f6b-9628-3862116cfad1"),"StoredFootprintPreflight")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};player.rotationYaw=180;ItemStack held=new ItemStack(Item.getItemFromBlock(block),2);NBTTagCompound outer=new NBTTagCompound(),generic=new NBTTagCompound();generic.setIntArray("CanopyFootprint",new int[]{0,0,0,2,0,0});outer.setTag("BlockEntityTag",generic);outer.setString("OpaqueMarker","unchanged");held.setTagCompound(outer);player.setHeldItem(EnumHand.MAIN_HAND,held);NBTTagCompound before=held.serializeNBT();Map<BlockPos,IBlockState> states=new HashMap<>(world.states);
            EnumActionResult result=held.getItem().onItemUse(player,world,clicked,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F);
            if(test<3){if(result!=EnumActionResult.FAIL||held.getCount()!=2||!held.serializeNBT().equals(before)||!states.equals(world.states)||world.canopyWrites!=0||world.coldWrites!=0)throw new AssertionError("Stored footprint refusal mutated owner/scene case="+test);}
            else{TileEntityCanopy owner=TileEntityCanopy.at(world,root),helper=TileEntityCanopy.at(world,extra);if(result!=EnumActionResult.SUCCESS||held.getCount()!=1||owner==null||helper==null||!root.equals(owner.anchor)||!root.equals(helper.anchor)||owner.cells().size()!=2)throw new AssertionError("Valid stored AIR footprint refused");}
        }
        System.out.println("[vandorlabs][canopy-stored-preflight] PASS cases=4 foreign editable cold valid-air raw-stack-preserved");
    }
}
