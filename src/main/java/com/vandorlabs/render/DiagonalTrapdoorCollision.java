package com.vandorlabs.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;
import java.util.List;

/** Bounded, world-independent collision meshes keyed by their exact rigid corners. */
public final class DiagonalTrapdoorCollision {
    private static final Cache<Key,Mesh> MESHES=CacheBuilder.newBuilder().maximumSize(128).build();
    private DiagonalTrapdoorCollision() { }

    public static void add(double[][] vertices,int axis,int across,BlockPos pos,
            AxisAlignedBB entityBox,List<AxisAlignedBB> boxes) {
        Key probe=new Key(vertices,axis,across);
        Mesh mesh=MESHES.getIfPresent(probe);
        if(mesh==null) {
            mesh=build(vertices,axis,across);
            // Caller-owned corners may be reused or edited after this query.
            double[][] copied=new double[8][];
            for(int i=0;i<8;i++)copied[i]=vertices[i].clone();
            Mesh raced=MESHES.asMap().putIfAbsent(new Key(copied,axis,across),mesh);
            if(raced!=null)mesh=raced;
        }
        if(!intersects(entityBox,mesh.bounds,pos))return;
        for(AxisAlignedBB box:mesh.boxes)
            if(intersects(entityBox,box,pos))boxes.add(box.offset(pos));
    }

    private static boolean intersects(AxisAlignedBB query,AxisAlignedBB box,BlockPos pos) {
        // Same arithmetic and strict comparisons as query.intersects(box.offset(pos)).
        return query.minX<box.maxX+pos.getX() && query.maxX>box.minX+pos.getX()
                && query.minY<box.maxY+pos.getY() && query.maxY>box.minY+pos.getY()
                && query.minZ<box.maxZ+pos.getZ() && query.maxZ>box.minZ+pos.getZ();
    }

    private static Mesh build(double[][] vertices,int axis,int across) {
        AxisAlignedBB[] boxes=new AxisAlignedBB[16*across];
        double[][] cell=new double[8][3];
        double[][] split=across>1?new double[8][3]:cell;
        for(int slice=0;slice<16;slice++) {
            for(int i=0;i<8;i++)for(int a=0;a<3;a++)
                cell[i][a]=vertices[i&~axis][a]+(vertices[i|axis][a]-vertices[i&~axis][a])*(slice+((i&axis)==0?0:1))/16D;
            for(int column=0;column<across;column++) {
                if(across>1)for(int i=0;i<8;i++)for(int a=0;a<3;a++)
                    split[i][a]=cell[i&~1][a]+(cell[i|1][a]-cell[i&~1][a])*(column+((i&1)==0?0:1))/across;
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
                double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
                for(double[] point:split) {
                    minX=Math.min(minX,point[0]);minY=Math.min(minY,point[1]);minZ=Math.min(minZ,point[2]);
                    maxX=Math.max(maxX,point[0]);maxY=Math.max(maxY,point[1]);maxZ=Math.max(maxZ,point[2]);
                }
                boxes[slice*across+column]=new AxisAlignedBB(minX,minY,minZ,maxX,maxY,maxZ);
            }
        }
        return new Mesh(boxes);
    }
    private static final class Mesh {
        final AxisAlignedBB[] boxes;
        final AxisAlignedBB bounds;
        Mesh(AxisAlignedBB[] boxes) {
            this.boxes=boxes;AxisAlignedBB union=boxes[0];
            for(int i=1;i<boxes.length;i++)union=union.union(boxes[i]);
            bounds=union;
        }
    }
    private static final class Key {
        final double[][] vertices;
        final int axis,across,hash;
        Key(double[][] vertices,int axis,int across) {
            this.vertices=vertices;this.axis=axis;this.across=across;
            hash=(Arrays.deepHashCode(vertices)*31+axis)*31+across;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            if(!(other instanceof Key))return false;
            Key key=(Key)other;
            return axis==key.axis && across==key.across && Arrays.deepEquals(vertices,key.vertices);
        }
    }
}
