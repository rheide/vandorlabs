package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import com.vandorlabs.persistence.SpaceDoorData;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Closed separating plane plus current physical leaf cells; immutable, level-free published data. */
public final class NativeDoorFluidGeometry {
    public record Plane(int x,int z,double near,double far) {
        public int side(double px,double py,double pz){return px*x+py*0D+pz*z<(near+far)/2?0:1;}
        private double[] cuts(int normal){List<Double> cuts=new ArrayList<>();cuts.add(.5);if(normal!=0)for(double edge:new double[]{near,far}){double cut=edge/normal;if(cut>0&&cut<1)cuts.add(cut);}return cuts.stream().mapToDouble(Double::doubleValue).toArray();}
        public double[][] cuts(){return new double[][]{cuts(x),new double[]{.5},cuts(z)};}
    }
    public record Snapshot(Plane plane,NativeFluidVolume volume){}
    private NativeDoorFluidGeometry(){}
    public static Plane plane(NativeDoorSettings settings,Direction face){
        if(face.getAxis()==Direction.Axis.Y)throw new IllegalArgumentException("Ordinary opening facing");var door=settings.door();int x=face.getStepX(),z=face.getStepZ();
        double center=x*.5+0D*.5+z*.5+(door.sliding?0:5.24/16D)+SpaceDoorData.positionOffset(door.sliding,door.framed,door.hinges,settings.depth());
        return new Plane(x,z,center-1D/16,center+1D/16);
    }
    /** World addition/subtraction retains the current Forge cut rounding at large and negative coordinates. */
    public static Snapshot build(NativeDoorSettings settings,Direction face,boolean halfOpen,boolean hingeLeft,boolean paired,int worldX,int worldY,int worldZ){
        Plane plane=plane(settings,face);List<AABB> solids=new ArrayList<>();
        for(AABB local:NativeDoorGeometry.shape(settings,face,halfOpen,hingeLeft,paired,true).toAabbs()){
            AABB world=local.move(worldX,worldY,worldZ);
            double x0=Math.max(0,world.minX-worldX),y0=Math.max(0,world.minY-worldY),z0=Math.max(0,world.minZ-worldZ),x1=Math.min(1,world.maxX-worldX),y1=Math.min(1,world.maxY-worldY),z1=Math.min(1,world.maxZ-worldZ);
            if(x1>x0&&y1>y0&&z1>z0)solids.add(new AABB(x0,y0,z0,x1,y1,z1));
        }
        return new Snapshot(plane,NativeFluidVolume.build(solids,plane::side,plane.cuts()));
    }
}
