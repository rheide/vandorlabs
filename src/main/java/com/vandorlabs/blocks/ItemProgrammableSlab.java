package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Combining keeps the placed slab's settings, including dormant face choices. */
public final class ItemProgrammableSlab extends ItemBlock {
    public ItemProgrammableSlab(net.minecraft.block.Block slab) { super(slab); }
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos pos,EnumHand hand,
            EnumFacing side,float x,float y,float z) {
        IBlockState state=world.getBlockState(pos);
        if(state.getBlock()==block) {
            boolean upper=state.getValue(BlockProgrammableSlab.HALF)==BlockSlab.EnumBlockHalf.TOP;
            if(side==(upper?EnumFacing.DOWN:EnumFacing.UP))return combine(player,world,pos,hand,side);
        }
        BlockPos adjacent=pos.offset(side);
        if(world.getBlockState(adjacent).getBlock()==block)return combine(player,world,adjacent,hand,side);
        return super.onItemUse(player,world,pos,hand,side,x,y,z);
    }
    private EnumActionResult combine(EntityPlayer player,World world,BlockPos pos,EnumHand hand,EnumFacing side) {
        ItemStack stack=player.getHeldItem(hand);
        if(stack.isEmpty()||!player.canPlayerEdit(pos,side,stack)||!world.isBlockModifiable(player,pos)
                ||!world.checkNoEntityCollision(new AxisAlignedBB(pos)))return EnumActionResult.FAIL;
        TileEntity raw=world.getTileEntity(pos);
        if(!(raw instanceof TileEntityAnimatedScreenSelector))return EnumActionResult.FAIL;
        NBTTagCompound saved=raw.writeToNBT(new NBTTagCompound());
        IBlockState full=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,
                world.getBlockState(pos).getValue(BlockAnimatedScreenSelector.FACING));
        if(!world.setBlockState(pos,full,3))return EnumActionResult.FAIL;
        raw=world.getTileEntity(pos);if(raw!=null){raw.readFromNBT(saved);raw.markDirty();}
        world.notifyBlockUpdate(pos,full,full,3);
        net.minecraft.block.SoundType sound=full.getBlock().getSoundType(full,world,pos,player);
        world.playSound(player,pos,sound.getPlaceSound(),SoundCategory.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);
        if(!player.capabilities.isCreativeMode)stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
    @Override public boolean canPlaceBlockOnSide(World world,BlockPos pos,EnumFacing side,EntityPlayer player,ItemStack stack) {
        IBlockState state=world.getBlockState(pos);
        if(state.getBlock()==block && side==(state.getValue(BlockProgrammableSlab.HALF)==BlockSlab.EnumBlockHalf.TOP?EnumFacing.DOWN:EnumFacing.UP))return true;
        return world.getBlockState(pos.offset(side)).getBlock()==block || super.canPlaceBlockOnSide(world,pos,side,player,stack);
    }
}
