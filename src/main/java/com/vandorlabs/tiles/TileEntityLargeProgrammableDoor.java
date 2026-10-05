package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

/** Nine cells address one configuration and one renderer, without loading neighbors. */
public class TileEntityLargeProgrammableDoor extends TileEntitySpaceDoor {
    private int column,row;
    private boolean assigned;
    public void assign(BlockPos anchor){column=Math.abs(pos.getX()-anchor.getX())+Math.abs(pos.getZ()-anchor.getZ());row=pos.getY()-anchor.getY();assigned=true;markDirty();}
    public boolean isAnchor(){return assigned && column==0 && row==0;}
    public BlockPos anchorPos(){if(world==null)return pos;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor))return pos;
        return pos.offset(state.getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW(),-column).down(row);}
    @Override protected boolean isLowerDoor(){return isAnchor() && world!=null && world.getBlockState(pos).getBlock() instanceof BlockLargeProgrammableDoor;}
    @Override public BlockPos mate(){return null;}
    @Override public boolean hasLocalRedstoneSignal(){
        if(!isLowerDoor())return false;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        net.minecraft.util.EnumFacing width=state.getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)
            if(com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,pos.offset(width,x).up(y)))return true;
        return false;
    }
    @Override public boolean usable(net.minecraft.entity.player.EntityPlayer player){
        if(!isAnchor() || !super.usable(player))return false;
        net.minecraft.util.EnumFacing width=world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=pos.offset(width,x).up(y);
            if(!world.isBlockLoaded(cell) || !player.canPlayerEdit(cell,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand()) || !world.isBlockModifiable(player,cell))return false;
        }
        return true;
    }
    @Override public AxisAlignedBB getRenderBoundingBox(){return new AxisAlignedBB(pos.add(-4,-3,-4),pos.add(5,7,5));}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("LargeDoorAssigned",assigned);tag.setInteger("LargeDoorColumn",column);tag.setInteger("LargeDoorRow",row);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){
        // Assign role before the base class schedules redstone registration.
        assigned=tag.getBoolean("LargeDoorAssigned");column=tag.getInteger("LargeDoorColumn");row=tag.getInteger("LargeDoorRow");
        if(column<0 || column>2 || row<0 || row>2)assigned=false;
        super.readFromNBT(tag);
    }
}
