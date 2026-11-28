package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Item NBT must never override the face of the support that was just clicked. */
public final class ItemDisplayArm extends ItemBlock {
    public ItemDisplayArm(Block block) { super(block); }
    @Override public CreativeTabs[] getCreativeTabs() {
        return getCreativeTab() == null ? new CreativeTabs[0] : super.getCreativeTabs();
    }
    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
            EnumFacing side, float x, float y, float z, IBlockState state) {
        if (!super.placeBlockAt(stack, player, world, pos, side, x, y, z, state)) return false;
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityDisplayArm) {
            ((TileEntityDisplayArm) raw).setMountFace(side);
            NBTTagCompound data = stack.getSubCompound("BlockEntityTag");
            if (data != null && data.hasKey(TileEntityDisplayArm.ITEM_FACING, 3)) {
                int facing = data.getInteger(TileEntityDisplayArm.ITEM_FACING);
                if (facing >= 2 && facing < 6) world.setBlockState(pos,
                        world.getBlockState(pos).withProperty(BlockAnimatedScreenSelector.FACING,
                                EnumFacing.getFront(facing)), 3);
            }
        }
        return true;
    }
}
