package com.vandorlabs.blocks;

import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.entity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;

/** Standalone half-height, half-depth console incline. */
public final class BlockDiagonalHalfConsole extends BlockAnimatedScreenSelector {
    public static final PropertyBool UPPER=PropertyBool.create("upper");
    public BlockDiagonalHalfConsole(String name) {
        super(name);
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(UPPER,false));
    }
    protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,FACING,UPPER); }
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING,EnumFacing.getFront((meta&7)<6?meta&7:2)).withProperty(UPPER,(meta&8)!=0);
    }
    public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getIndex() | (s.getValue(UPPER)?8:0); }
    public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase player) {
        boolean upper=side==EnumFacing.DOWN || side.getAxis().isHorizontal() && y>.5F;
        Boolean slab=SlabPlacement.upperHalf(world.getBlockState(pos.offset(side.getOpposite())));
        if (side.getAxis().isHorizontal() && slab!=null) upper=slab;
        return getDefaultState().withProperty(FACING,side.getAxis().isHorizontal()?side:placementFacing(world,pos,side,player)).withProperty(UPPER,upper);
    }
    public boolean isOpaqueCube(IBlockState s) { return false; }
    public boolean isFullCube(IBlockState s) { return false; }
    public int getLightValue(IBlockState s,IBlockAccess world,BlockPos pos) { return 0; }
    public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess world,BlockPos pos) {
        return PanelPlacement.rotateFromNorth(new AxisAlignedBB(0,s.getValue(UPPER)?.5:0,.5,1,s.getValue(UPPER)?1:.5,1),s.getValue(FACING));
    }
    public void addCollisionBoxToList(IBlockState s,World world,BlockPos pos,AxisAlignedBB entityBox,List<AxisAlignedBB> boxes,Entity entity,boolean actual) {
        for (int i=0;i<8;i++) {
            double h=(i+1)/16D;
            AxisAlignedBB box=new AxisAlignedBB(0,s.getValue(UPPER)?1-h:0,(8+i)/16D,1,s.getValue(UPPER)?1:h,(9+i)/16D);
            addCollisionBoxToList(pos,entityBox,boxes,PanelPlacement.rotateFromNorth(box,s.getValue(FACING)));
        }
    }
    public RayTraceResult collisionRayTrace(IBlockState s,World world,BlockPos pos,Vec3d start,Vec3d end) {
        java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
        addCollisionBoxToList(s,world,pos,new AxisAlignedBB(pos),boxes,null,false);
        RayTraceResult closest=null;
        for (AxisAlignedBB box:boxes) {
            RayTraceResult hit=rayTrace(pos,start,end,box.offset(-pos.getX(),-pos.getY(),-pos.getZ()));
            if (hit!=null && (closest==null || start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(closest.hitVec))) closest=hit;
        }
        return closest;
    }
}
