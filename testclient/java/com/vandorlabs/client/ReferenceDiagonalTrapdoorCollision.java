package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Frozen 1.3 subdivision arithmetic and collision filtering. */
final class ReferenceDiagonalTrapdoorCollision {
    static void add(double[][] v,int axis,int across,BlockPos pos,AxisAlignedBB entityBox,List<AxisAlignedBB> boxes) {
        for(int slice=0;slice<16;slice++)for(int column=0;column<across;column++) {
            double[][] cell=new double[8][3];
            for(int i=0;i<8;i++)for(int a=0;a<3;a++)cell[i][a]=v[i&~axis][a]+(v[i|axis][a]-v[i&~axis][a])*(slice+((i&axis)==0?0:1))/16D;
            if(across>1) {
                double[][] split=new double[8][3];
                for(int i=0;i<8;i++)for(int a=0;a<3;a++)split[i][a]=cell[i&~1][a]+(cell[i|1][a]-cell[i&~1][a])*(column+((i&1)==0?0:1))/across;
                cell=split;
            }
            double[] b=DiagonalTrapdoorGeometry.bounds(cell);
            AxisAlignedBB box=new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]).offset(pos);
            if(entityBox.intersects(box))boxes.add(box);
        }
    }
}
