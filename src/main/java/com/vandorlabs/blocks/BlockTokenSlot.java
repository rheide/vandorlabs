package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Compact surface-mounted card/chip reader, using the Wall Slider's mount rules. */
public final class BlockTokenSlot extends BlockMountedSwitch {
    public final boolean chip;
    public BlockTokenSlot(String name,boolean chip){super(name,false);this.chip=chip;}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new TileEntityTokenSlot(chip);}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        if(MountedControlGeometry.mount(world,pos)%16!=0)return super.getBoundingBox(state,world,pos);
        state=getActualState(state,world,pos);EnumFacing facing=state.getValue(FACING);int rotation=state.getValue(ROTATION);double half=3D/16,h=(chip?3:4.4)/16;
        Vec3d a=TokenSlotGeometry.point(facing,rotation,-half,-h,0),b=TokenSlotGeometry.point(facing,rotation,half,h,.10);return new AxisAlignedBB(a.x,a.y,a.z,b.x,b.y,b.z);
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return MountedControlGeometry.mount(world,pos)%16==0?NULL_AABB:super.getCollisionBoundingBox(state,world,pos);}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,java.util.List<AxisAlignedBB> boxes,net.minecraft.entity.Entity entity,boolean actual){
        if(MountedControlGeometry.mount(world,pos)%16!=0)super.addCollisionBoxToList(state,world,pos,query,boxes,entity,actual);
    }
    @Override public net.minecraft.util.math.RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        if(MountedControlGeometry.mount(world,pos)%16==0)return super.collisionRayTrace(state,world,pos,start,end);
        net.minecraft.util.math.RayTraceResult closest=null;
        for(AxisAlignedBB box:MountedControlGeometry.boxes(state,world,pos)){net.minecraft.util.math.RayTraceResult hit=rayTrace(pos,start,end,box);if(hit!=null&&(closest==null||start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(closest.hitVec)))closest=hit;}
        return closest;
    }
    @Override public int getLightValue(IBlockState state,IBlockAccess world,BlockPos pos){return 0;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing facing,float x,float y,float z){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityTokenSlot))return false;TileEntityTokenSlot tile=(TileEntityTokenSlot)raw;
        return TokenSlotInteractions.activate(tile,player,hand);
    }
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){TokenSlotInteractions.tick(world,pos);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TokenSlotInteractions.drop(world,pos);super.breakBlock(world,pos,state);
    }
}
