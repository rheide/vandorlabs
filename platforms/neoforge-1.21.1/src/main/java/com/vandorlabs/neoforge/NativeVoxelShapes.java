package com.vandorlabs.neoforge;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Exact-coordinate native unions. Geometry caches own their retained-byte budget. */
public final class NativeVoxelShapes {
    // SliceShape/SubShape share parent occupancy; reserve the full bounded view closure.
    private static final int FACE_VIEWS=80*160;
    public static int retainedCost(VoxelShape shape,int boxes){
        long x=shape.getCoords(Direction.Axis.X).size(),y=shape.getCoords(Direction.Axis.Y).size(),z=shape.getCoords(Direction.Axis.Z).size();
        return (int)Math.min(Integer.MAX_VALUE,512+FACE_VIEWS+boxes*112L+128+16*(x+y+z)+(x*y*z+7)/8);
    }
    /** Build the exact component union once, without repeatedly decomposing intermediate unions. */
    public static VoxelShape union(List<AABB> boxes,int retainedBudget){
        List<AABB> active=new ArrayList<>();for(AABB b:boxes)if(b.maxX-b.minX>=1e-7&&b.maxY-b.minY>=1e-7&&b.maxZ-b.minZ>=1e-7)active.add(b);if(active.isEmpty())return Shapes.empty();
        double[][] axes=new double[3][];for(int axis=0;axis<3;axis++){double[] values=new double[active.size()*2];int n=0;for(AABB b:active){double min=axis==0?b.minX:axis==1?b.minY:b.minZ,max=axis==0?b.maxX:axis==1?b.maxY:b.maxZ;values[n++]=min==0?0:min;values[n++]=max==0?0:max;}Arrays.sort(values);int unique=0;for(double value:values)if(unique==0||value!=values[unique-1])values[unique++]=value;axes[axis]=Arrays.copyOf(values,unique);}
        int nx=axes[0].length-1,ny=axes[1].length-1,nz=axes[2].length-1;long cells=(long)nx*ny*nz,estimated=512+FACE_VIEWS+boxes.size()*112L+128+16L*(axes[0].length+axes[1].length+axes[2].length)+((long)axes[0].length*axes[1].length*axes[2].length+7)/8;if(cells>Integer.MAX_VALUE||estimated>retainedBudget)throw new IllegalStateException("Voxel grid exceeds checked retained-size budget");
        var bits=new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(nx,ny,nz);
        for(AABB b:active){int x0=index(axes[0],b.minX),x1=index(axes[0],b.maxX),y0=index(axes[1],b.minY),y1=index(axes[1],b.maxY),z0=index(axes[2],b.minZ),z1=index(axes[2],b.maxZ);for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)bits.fill(x,y,z);}
        return new ExactUnion(bits,axes[0],axes[1],axes[2]);
    }
    private static int index(double[] axis,double value){int index=Arrays.binarySearch(axis,value==0?0:value);if(index<0)throw new IllegalStateException("Missing voxel coordinate");return index;}
    private static final class ExactUnion extends net.minecraft.world.phys.shapes.ArrayVoxelShape {
        ExactUnion(net.minecraft.world.phys.shapes.DiscreteVoxelShape shape,double[] x,double[] y,double[] z){super(shape,x,y,z);}
    }
    private NativeVoxelShapes(){}
}
