package com.vandorlabs.segmented;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Click depth is item-placement context, not another persisted metadata bit. */
public final class SegmentedDoorItem extends ItemBlock {
    public SegmentedDoorItem(SegmentedDoorBlock block){super(block);}
    @Override public net.minecraft.creativetab.CreativeTabs[] getCreativeTabs(){return getCreativeTab()==null?new net.minecraft.creativetab.CreativeTabs[0]:super.getCreativeTabs();}
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state){
        SegmentedDoorTile support=SegmentedDoorTile.at(world,pos.offset(side.getOpposite()));
        int position=support!=null&&support.facing()==state.getValue(SegmentedDoorBlock.FACING)
                ?support.settings.position:com.vandorlabs.render.PanelDepthPolicy.fromPlacement(state.getValue(SegmentedDoorBlock.FACING).getIndex(),side.getIndex(),x,z);
        if(!super.placeBlockAt(stack,player,world,pos,side,x,y,z,state))return false;
        SegmentedDoorTile placed=SegmentedDoorTile.at(world,pos);
        if(placed!=null&&stack.getSubCompound("BlockEntityTag")==null){placed.settings=placed.settings.position(position);placed.markDirty();}
        return true;
    }
}
