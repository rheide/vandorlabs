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
        NBTTagCompound saved=stack.getSubCompound("BlockEntityTag");
        boolean inheritRotation=saved==null||!saved.hasKey("DiagonalRotationFlipped",1);
        ItemStack configured=stack.copy();NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");tag=tag==null?new NBTTagCompound():tag.copy();
        int mode=BlockProgrammableDiagonalTrapdoor.placementMode(world,pos.offset(side.getOpposite()),stack);
        tag.setInteger("TrapdoorPosition",mode);tag.removeTag("DiagonalReverse");configured.setTagInfo("BlockEntityTag",tag);
        if(!super.placeBlockAt(configured,player,world,pos,side,x,y,z,state))return false;
        net.minecraft.tileentity.TileEntity support=world.getTileEntity(pos.offset(side.getOpposite())),placed=world.getTileEntity(pos);
        if(inheritRotation && side.getAxis()==EnumFacing.Axis.Y && support instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor && placed instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)
            ((com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)placed).matchRotation((com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)support);
        return true;
    }
}
