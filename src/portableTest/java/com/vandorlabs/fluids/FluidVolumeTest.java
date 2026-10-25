package com.vandorlabs.fluids;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public final class FluidVolumeTest {
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void close(double a,double b){need(Math.abs(a-b)<1e-10,a+" != "+b);}
    private static double volume(FluidVolume v){double n=0;for(FluidVolume.Cell c:v.cells)n+=(c.box.maxX-c.box.minX)*(c.box.maxY-c.box.minY)*(c.box.maxZ-c.box.minZ);return n;}
    public static void main(String[] args){
        FluidVolume empty=FluidVolume.build(Collections.emptyList(),null,(x,y,z)->0);
        close(volume(empty),1);need(empty.cells.size()==1&&empty.faces.size()==6&&empty.hasSide(0)&&!empty.hasSide(1),"full air cube");
        need(empty.sideAt(0,0,0)==0&&empty.sideAt(1,.5,.5)==-1&&empty.sideAt(-.0001,.5,.5)==-1,"half-open outer boundaries");
        FluidVolume full=FluidVolume.build(Arrays.asList(new FluidVolume.Box(0,0,0,1,1,1)),null,(x,y,z)->0);
        need(full.cells.isEmpty()&&full.faces.isEmpty(),"full solid holds no bucket space");
        FluidVolume half=FluidVolume.build(Arrays.asList(new FluidVolume.Box(0,0,0,1,.5,1)),null,(x,y,z)->0);
        close(volume(half),.5);need(half.sideAt(.5,.5,.5)==0&&half.sideAt(.5,.4999,.5)==-1,"slab material/fluid boundary belongs to open half");
        FluidVolume divided=FluidVolume.build(Collections.emptyList(),new double[][]{{.5},null,null},(x,y,z)->x<.5?0:1);
        close(volume(divided),1);need(divided.cells.size()==2&&divided.faces.size()==12&&divided.sideAt(.5,.5,.5)==1&&divided.sideAt(.499999,.5,.5)==0,"two independent sides without air seam");
        int boundaries=0;for(FluidVolume.Face face:divided.faces)if(face.vertices[0].x==.5&&face.axis==0)boundaries++;
        need(boundaries==2,"both fluid sides retain independent interface faces");
        // Extra partition cuts do not create internal fluid faces for a single owner.
        FluidVolume cut=FluidVolume.build(Collections.emptyList(),new double[][]{{.25,.5,.75},{.5},{.5}},(x,y,z)->0);
        close(volume(cut),1);need(cut.cells.size()==16&&cut.faces.size()==40,"partition face cancellation");
        for(FluidVolume.Face face:empty.faces){FluidVolume.Point a=face.vertices[0],b=face.vertices[1],c=face.vertices[2];double nx=(b.y-a.y)*(c.z-a.z)-(b.z-a.z)*(c.y-a.y),ny=(b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z),nz=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);double[] normal={nx,ny,nz};need(normal[face.axis]!=0,"nondegenerate authored face winding");}
        AtomicInteger probes=new AtomicInteger();need(!FluidVolume.canFill(999,false,false,()->{probes.incrementAndGet();return true;}),"partial bucket rejected");need(probes.get()==0,"invalid amount must not sample geometry");
        for(boolean gas:new boolean[]{false,true})for(boolean occupied:new boolean[]{false,true})for(boolean available:new boolean[]{false,true})need(FluidVolume.canFill(1000,gas,occupied,available,false)==(!gas&&!occupied&&available),"bucket policy");
        need(FluidVolume.canFill(1000,false,false,false,true)&&!FluidVolume.canDrain(false,1000)&&!FluidVolume.canDrain(true,999)&&FluidVolume.canDrain(true,1000),"opening/drain semantics");
        System.out.println("FluidVolume PASS analytic full/empty/slab/dual ownership, half-open boundaries, face cancellation/winding and lazy bucket eligibility");
    }
}
