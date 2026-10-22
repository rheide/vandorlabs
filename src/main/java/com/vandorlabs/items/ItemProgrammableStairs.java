package com.vandorlabs.items;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Side mounting chooses the solid wing edge from the clicked support face. */
public final class ItemProgrammableStairs extends ItemBlock {
    public ItemProgrammableStairs(BlockProgrammableStairs block){super(block);}
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state){
        if(!super.placeBlockAt(stack,player,world,pos,side,x,y,z,state))return false;
        if(side.getAxis().isHorizontal()&&world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector){
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
            net.minecraft.util.math.Vec3d support=ProgrammableStairGeometry.transform(new net.minecraft.util.math.Vec3d(.5,0,.5),state,1).subtract(new net.minecraft.util.math.Vec3d(.5,.5,.5));
            EnumFacing fullFace=EnumFacing.getFacingFromVector((float)support.x,(float)support.y,(float)support.z);
            tile.setStairGeometry(tile.stairTread(),fullFace==side.getOpposite()?1:2);
        }
        return true;
    }
}
