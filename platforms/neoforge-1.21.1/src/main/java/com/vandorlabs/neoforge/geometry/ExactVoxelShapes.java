package com.vandorlabs.neoforge.geometry;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Exact cached AABB unions without Shapes.or's coordinate-epsilon merging. */
public final class ExactVoxelShapes {
    private static final long MAX_CELLS=1_048_576;
    private ExactVoxelShapes(){}
    /** For bounded authored geometry, constructed once and reused by the block. */
    public static VoxelShape boxes(List<AABB> input){
        List<AABB> boxes=new ArrayList<>();for(AABB box:input){
            if(!Double.isFinite(box.minX)||!Double.isFinite(box.minY)||!Double.isFinite(box.minZ)||!Double.isFinite(box.maxX)||!Double.isFinite(box.maxY)||!Double.isFinite(box.maxZ))throw new IllegalArgumentException("Non-finite collision box");
            if(box.minX<box.maxX&&box.minY<box.maxY&&box.minZ<box.maxZ)boxes.add(box);
        }
        if(boxes.isEmpty())return Shapes.empty();
        double[][] coordinates=new double[3][];
        for(int axis=0;axis<3;axis++){TreeSet<Double> cuts=new TreeSet<>();for(AABB box:boxes){cuts.add(min(box,axis));cuts.add(max(box,axis));}coordinates[axis]=cuts.stream().mapToDouble(Double::doubleValue).toArray();}
        long cells=(long)(coordinates[0].length-1)*(coordinates[1].length-1)*(coordinates[2].length-1);
        if(cells>MAX_CELLS)throw new IllegalArgumentException("Exact collision grid exceeds "+MAX_CELLS+" cells: "+cells);
        BitSetDiscreteVoxelShape grid=new BitSetDiscreteVoxelShape(coordinates[0].length-1,coordinates[1].length-1,coordinates[2].length-1);
        for(AABB box:boxes){
            int x0=Arrays.binarySearch(coordinates[0],box.minX),x1=Arrays.binarySearch(coordinates[0],box.maxX),y0=Arrays.binarySearch(coordinates[1],box.minY),y1=Arrays.binarySearch(coordinates[1],box.maxY),z0=Arrays.binarySearch(coordinates[2],box.minZ),z1=Arrays.binarySearch(coordinates[2],box.maxZ);
            for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)grid.fill(x,y,z);
        }
        return new ExactShape(grid,coordinates);
    }
    private static double min(AABB box,int axis){return axis==0?box.minX:axis==1?box.minY:box.minZ;}
    private static double max(AABB box,int axis){return axis==0?box.maxX:axis==1?box.maxY:box.maxZ;}
    private static final class ExactShape extends VoxelShape {
        private final DoubleList[] coordinates=new DoubleList[3];
        ExactShape(BitSetDiscreteVoxelShape grid,double[][] points){super(grid);for(int i=0;i<3;i++)coordinates[i]=DoubleLists.unmodifiable(DoubleArrayList.wrap(points[i]));}
        @Override public DoubleList getCoords(Direction.Axis axis){return coordinates[axis.ordinal()];}
    }
}
