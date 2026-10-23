package com.vandorlabs.blocks;

import java.util.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;

/** A stepped height field, rolled around its rise axis before horizontal placement. */
public final class ProgrammableStairGeometry {
    private ProgrammableStairGeometry(){}
    public static int settings(IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityAnimatedScreenSelector?((TileEntityAnimatedScreenSelector)tile).stairGeometry():8;}
    public static Vec3d transform(Vec3d p,IBlockState state,int roll){
        double x=p.x,y=state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP?1-p.y:p.y,z=p.z;
        if(roll==1){double old=y;y=z;z=1-old;}else if(roll==2){double old=y;y=1-z;z=old;}
        switch(state.getValue(BlockStairs.FACING)){case WEST:return new Vec3d(1-x,y,1-z);case NORTH:return new Vec3d(z,y,1-x);case SOUTH:return new Vec3d(1-z,y,x);default:return new Vec3d(x,y,z);}
    }
    /** Resolve a mounted face back to the same stair before its sideways roll. */
    public static EnumFacing unrolledFace(IBlockState state,int roll,EnumFacing face){
        if(roll==0)return face;
        Vec3d center=new Vec3d(.5,.5,.5);
        for(EnumFacing local:EnumFacing.values()){
            Vec3d point=center.add(new Vec3d(local.getDirectionVec()).scale(.5));
            Vec3d mounted=transform(point,state,roll).subtract(center);
            if(EnumFacing.getFacingFromVector((float)mounted.x,(float)mounted.y,(float)mounted.z)==face){
                Vec3d normal=transform(point,state,0).subtract(center);
                return EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z);
            }
        }
        throw new IllegalArgumentException("Invalid stair face");
    }
    public static double height(int x,int z,int count,BlockStairs.EnumShape shape){
        double main=(x+1D)/count;
        if(shape==BlockStairs.EnumShape.STRAIGHT)return main;
        double side=(shape==BlockStairs.EnumShape.INNER_LEFT||shape==BlockStairs.EnumShape.OUTER_LEFT)?(count-z)/(double)count:(z+1D)/count;
        return shape==BlockStairs.EnumShape.INNER_LEFT||shape==BlockStairs.EnumShape.INNER_RIGHT?Math.max(main,side):Math.min(main,side);
    }
    public static List<AxisAlignedBB> boxes(IBlockState state,int settings){
        int count=16/(settings&31),roll=settings>>5;List<AxisAlignedBB> result=new ArrayList<>();
        for(int x=0;x<count;x++)for(int z=0;z<count;z++){
            double top=height(x,z,count,state.getValue(BlockStairs.SHAPE));
            Vec3d a=transform(new Vec3d(x/(double)count,0,z/(double)count),state,roll),b=transform(new Vec3d((x+1D)/count,top,(z+1D)/count),state,roll);
            result.add(new AxisAlignedBB(a.x,a.y,a.z,b.x,b.y,b.z));
        }
        return result;
    }
    public static boolean solidFace(IBlockState state,int settings,EnumFacing side){
        List<AxisAlignedBB> boxes=boxes(state,settings);
        for(int a=0;a<16;a++)for(int b=0;b<16;b++){
            double edge=side.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1-1e-6:1e-6,u=(a+.5)/16,v=(b+.5)/16;
            Vec3d point=side.getAxis()==EnumFacing.Axis.X?new Vec3d(edge,u,v):side.getAxis()==EnumFacing.Axis.Y?new Vec3d(u,edge,v):new Vec3d(u,v,edge);
            boolean filled=false;for(AxisAlignedBB box:boxes)if(box.contains(point)){filled=true;break;}if(!filled)return false;
        }return true;
    }
}
