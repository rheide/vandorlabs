package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityMatterInfuser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockMatterInfuser extends BlockVandorDirectional {
    public BlockMatterInfuser(){super("programmable_matter_infuser");setHardness(3.5F);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityMatterInfuser();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_MATTER_INFUSER,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntityMatterInfuser)InventoryHelper.dropInventoryItems(world,pos,(TileEntityMatterInfuser)tile);
        super.breakBlock(world,pos,state);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state){return true;}
    @Override public int getComparatorInputOverride(IBlockState state,World world,BlockPos pos){return net.minecraft.inventory.Container.calcRedstone(world.getTileEntity(pos));}
}
