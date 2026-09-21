package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockConfigurableSpaceDoor extends BlockSpaceDoor {
    public BlockConfigurableSpaceDoor(String name,boolean sliding,BlockDetailedDoor paired) {
        super(name,sliding,true,paired);
    }
    @Override public TileEntity createTileEntity(World world,IBlockState state) { return new TileEntitySpaceDoor(); }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing face,float x,float y,float z) {
        if (!player.isSneaking()) return super.onBlockActivated(world,pos,state,player,hand,face,x,y,z);
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        if (!world.isRemote) player.openGui(VandorLabs.instance,GuiHandler.GUI_SPACE_DOOR,world,
                lower.getX(),lower.getY(),lower.getZ());
        return true;
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack) {
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        TileEntity raw=world.getTileEntity(pos);
        if (!world.isRemote && raw instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
            BlockPos mate=tile.mate();
            if (mate!=null && world.isBlockLoaded(mate) && world.getTileEntity(mate) instanceof TileEntitySpaceDoor) {
                TileEntitySpaceDoor other=(TileEntitySpaceDoor)world.getTileEntity(mate);
                tile.configure(other.getDesign(),other.getDetail(),other.isFramed(),other.getSlideDirection(),other.isMiddle());
            }
        }
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(IBlockState state,
            net.minecraft.world.IBlockAccess world,BlockPos pos) {
        net.minecraft.util.math.AxisAlignedBB box=super.getBoundingBox(state,world,pos);
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        TileEntity raw=world.getTileEntity(lower);
        if (!isSlidingModel() && raw instanceof TileEntitySpaceDoor && ((TileEntitySpaceDoor)raw).isMiddle()) {
            EnumFacing facing=getActualState(state,world,pos).getValue(FACING);
            return box.offset(facing.getFrontOffsetX()*TileEntitySpaceDoor.MIDDLE_OFFSET,0,
                    facing.getFrontOffsetZ()*TileEntitySpaceDoor.MIDDLE_OFFSET);
        }
        return box;
    }
}
