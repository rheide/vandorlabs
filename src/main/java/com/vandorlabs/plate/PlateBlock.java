package com.vandorlabs.plate;

import com.vandorlabs.blocks.ProgrammableHousingState;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.property.*;

/** Vanilla plate support/travel, with per-tile wooden or weighted sensing. */
public final class PlateBlock extends BlockBasePressurePlate {
    public static final PropertyInteger POWER=BlockPressurePlateWeighted.POWER;
    private static final ThreadLocal<Integer> SENSING=new ThreadLocal<>();
    public PlateBlock(){super(Material.WOOD);setDefaultState(blockState.getBaseState().withProperty(POWER,0));setRegistryName("vandorlabs","programmable_pressure_plate");setUnlocalizedName("vandorlabs.programmable_pressure_plate");setCreativeTab(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB);setHardness(.5F);setSoundType(SoundType.WOOD);}
    @Override protected BlockStateContainer createBlockState(){return new ExtendedBlockState(this,new net.minecraft.block.properties.IProperty[]{POWER},new IUnlistedProperty[]{ProgrammableHousingState.FINISH});}
    @Override public int getWeakPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return com.vandorlabs.redstone.TransmitterSlots.localRedstone(world,pos)?super.getWeakPower(state,world,pos,side):0;}
    @Override public int getStrongPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return com.vandorlabs.redstone.TransmitterSlots.localRedstone(world,pos)?super.getStrongPower(state,world,pos,side):0;}
    @Override protected int getRedstoneStrength(IBlockState state){return state.getValue(POWER);}
    @Override protected IBlockState setRedstoneStrength(IBlockState state,int strength){return state.withProperty(POWER,Math.max(0,Math.min(15,strength)));}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(POWER,meta&15);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(POWER);}
    private static int sensitivity(World world,BlockPos pos){TileEntity tile=world.getTileEntity(pos);return tile instanceof PlateTile?((PlateTile)tile).sensitivity():0;}
    @Override protected int computeRedstoneStrength(World world,BlockPos pos){int kind=sensitivity(world,pos);return PlateTile.measure(kind,kind==0?world.getEntitiesWithinAABBExcludingEntity(null,PRESSURE_AABB.offset(pos)):world.getEntitiesWithinAABB(Entity.class,PRESSURE_AABB.offset(pos)));}
    public int releaseTicks(World world,BlockPos pos){return sensitivity(world,pos)==0?20:10;}
    @Override public int tickRate(World world){Integer sensitivity=SENSING.get();return sensitivity==null||sensitivity==0?20:10;}
    public void resample(World world,BlockPos pos){IBlockState state=world.getBlockState(pos);if(state.getBlock()==this)updateState(world,pos,state,state.getValue(POWER));}
    @Override protected void updateState(World world,BlockPos pos,IBlockState state,int previous){
        Integer before=SENSING.get();SENSING.set(sensitivity(world,pos));try{super.updateState(world,pos,state,previous);}finally{if(before==null)SENSING.remove();else SENSING.set(before);}
        TileEntity tile=world.getTileEntity(pos);if(tile instanceof PlateTile)((PlateTile)tile).sample();
    }
    @Override protected void playClickOnSound(World world,BlockPos pos){boolean wood=sensitivity(world,pos)==0;world.playSound(null,pos,wood?SoundEvents.BLOCK_WOOD_PRESSPLATE_CLICK_ON:SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_ON,SoundCategory.BLOCKS,.3F,wood?.8F:.90000004F);}
    @Override protected void playClickOffSound(World world,BlockPos pos){boolean wood=sensitivity(world,pos)==0;world.playSound(null,pos,wood?SoundEvents.BLOCK_WOOD_PRESSPLATE_CLICK_OFF:SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_OFF,SoundCategory.BLOCKS,.3F,wood?.7F:.75F);}
    @Override public net.minecraft.util.BlockRenderLayer getBlockLayer(){return net.minecraft.util.BlockRenderLayer.CUTOUT;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new PlateTile();}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity tile=world.getTileEntity(pos);return ((IExtendedBlockState)state).withProperty(ProgrammableHousingState.FINISH,tile instanceof PlateTile?((PlateTile)tile).texture():0);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND||!player.isSneaking()&&player.getHeldItem(hand).getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER)return false;
        if(!world.isRemote&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))player.openGui(com.vandorlabs.VandorLabs.instance,27,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
}
