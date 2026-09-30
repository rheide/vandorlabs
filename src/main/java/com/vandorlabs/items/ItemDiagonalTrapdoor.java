package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Carry the clicked wall's geometry into the newly created tile, on both sides. */
public final class ItemDiagonalTrapdoor extends ItemBlock {
    public ItemDiagonalTrapdoor(BlockProgrammableDiagonalTrapdoor block){super(block);}
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state) {
        ItemStack configured=stack.copy();NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");tag=tag==null?new NBTTagCompound():tag.copy();
        int mode=BlockProgrammableDiagonalTrapdoor.placementMode(world,pos.offset(side.getOpposite()),stack);
        tag.setInteger("TrapdoorPosition",mode);tag.removeTag("DiagonalReverse");configured.setTagInfo("BlockEntityTag",tag);
        return super.placeBlockAt(configured,player,world,pos,side,x,y,z,state);
    }
}
