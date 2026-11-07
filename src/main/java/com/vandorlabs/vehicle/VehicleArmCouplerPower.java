package com.vandorlabs.vehicle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;

/** Bounded snapshot power queries. Unknown producers and tile-dependent callbacks stay pending. */
public final class VehicleArmCouplerPower implements IBlockAccess {
    private static final class Pending extends RuntimeException {private Pending(){super(null,null,false,false);}}
    private static final Pending PENDING=new Pending();
    private final VehicleStructure snapshot;
    private final java.util.Map<BlockPos,Byte> prepared=new java.util.HashMap<>();
    public VehicleArmCouplerPower(VehicleStructure snapshot){this.snapshot=snapshot;}
    public Boolean powered(BlockPos coupler){
        Byte cached=prepared.get(coupler);if(cached!=null)return cached<0?null:cached!=0;boolean unknown=false;
        for(EnumFacing side:EnumFacing.values())try{if(redstone(coupler.offset(side),side)>0){prepared.put(coupler,(byte)1);return true;}}catch(Pending pending){unknown=true;}
        prepared.put(coupler,unknown?(byte)-1:(byte)0);return unknown?null:false;
    }
    private IBlockState known(BlockPos pos){return getBlockState(pos);}
    private int redstone(BlockPos pos,EnumFacing side){IBlockState state=known(pos);if(!state.getBlock().shouldCheckWeakPower(state,this,pos,side))return state.getWeakPower(this,pos,side);int value=0;boolean unknown=false;for(EnumFacing neighbor:EnumFacing.values())try{value=Math.max(value,getStrongPower(pos.offset(neighbor),neighbor));if(value>=15)return value;}catch(Pending pending){unknown=true;}if(value>0)return value;if(unknown)throw PENDING;return value;}
    @Override public IBlockState getBlockState(BlockPos pos){VehicleStructure.Cell cell=snapshot.at(pos);IBlockState state=cell==null?Blocks.AIR.getDefaultState():cell.state;String type=state.getBlock().getClass().getName();if(!type.startsWith("net.minecraft.")&&!type.startsWith("com.vandorlabs."))throw PENDING;return state;}
    @Override public TileEntity getTileEntity(BlockPos pos){throw PENDING;}
    @Override public int getCombinedLight(BlockPos pos,int value){throw PENDING;}
    @Override public Biome getBiome(BlockPos pos){throw PENDING;}
    @Override public WorldType getWorldType(){throw PENDING;}
    @Override public boolean isAirBlock(BlockPos pos){return getBlockState(pos).getBlock()==Blocks.AIR;}
    @Override public int getStrongPower(BlockPos pos,EnumFacing side){return known(pos).getStrongPower(this,pos,side);}
    @Override public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean fallback){return known(pos).isSideSolid(this,pos,side);}
}
