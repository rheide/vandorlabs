package com.vandorlabs.canopy;

import com.vandorlabs.canopy.CanopyPhysics.Box;
import java.util.*;

/** Linear-build immutable broadphase; exact boxes retain their original order and identity. */
public final class CanopyBoundsIndex {
    private static final double SCALE=2;
    private static final int MAX_CELLS=262144,MAX_REFERENCES=4000000,MAX_BOX_CELLS=512;
    private final List<Box> boxes;
    private final Box bounds;
    private final int minX,minY,minZ,nx,ny,nz;
    private final int[] offsets,references,oversized;
    public CanopyBoundsIndex(List<Box> boxes){
        this.boxes=Collections.unmodifiableList(new ArrayList<Box>(boxes));
        if(boxes.isEmpty()){bounds=null;minX=minY=minZ=nx=ny=nz=0;offsets=references=oversized=null;return;}
        Box first=boxes.get(0);double x0=first.minX,y0=first.minY,z0=first.minZ,x1=first.maxX,y1=first.maxY,z1=first.maxZ;
        for(int i=1;i<boxes.size();i++){Box b=boxes.get(i);x0=Math.min(x0,b.minX);y0=Math.min(y0,b.minY);z0=Math.min(z0,b.minZ);x1=Math.max(x1,b.maxX);y1=Math.max(y1,b.maxY);z1=Math.max(z1,b.maxZ);}
        bounds=new Box(x0,y0,z0,x1,y1,z1);minX=cell(x0);minY=cell(y0);minZ=cell(z0);nx=cell(x1)-minX+1;ny=cell(y1)-minY+1;nz=cell(z1)-minZ+1;
        if(nx<=0||ny<=0||nz<=0||nx>MAX_CELLS||ny>MAX_CELLS||nz>MAX_CELLS){offsets=references=oversized=null;return;}
        long total=(long)nx*ny*nz;
        if(total>MAX_CELLS){offsets=references=oversized=null;return;}
        int[] counts=new int[(int)total+1],large=new int[16];int largeCount=0,referenceCount=0;boolean overBudget=false;
        for(int i=0;i<boxes.size();i++){
            Box b=boxes.get(i);int ax=cell(b.minX)-minX,bx=cell(b.maxX)-minX,ay=cell(b.minY)-minY,by=cell(b.maxY)-minY,az=cell(b.minZ)-minZ,bz=cell(b.maxZ)-minZ;
            long coverage=(long)(bx-ax+1)*(by-ay+1)*(bz-az+1);
            if(coverage>MAX_BOX_CELLS){if(largeCount==large.length)large=Arrays.copyOf(large,large.length*2);large[largeCount++]=i;continue;}
            if((long)referenceCount+coverage>MAX_REFERENCES){overBudget=true;break;}referenceCount+=(int)coverage;
            for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)for(int x=ax;x<=bx;x++)counts[(z*ny+y)*nx+x+1]++;
        }
        if(overBudget){offsets=references=oversized=null;return;}
        for(int i=1;i<counts.length;i++)counts[i]+=counts[i-1];offsets=counts;references=new int[referenceCount];oversized=Arrays.copyOf(large,largeCount);int[] cursor=counts.clone();int nextLarge=0;
        for(int i=0;i<boxes.size();i++){
            if(nextLarge<largeCount&&oversized[nextLarge]==i){nextLarge++;continue;}
            Box b=boxes.get(i);int ax=cell(b.minX)-minX,bx=cell(b.maxX)-minX,ay=cell(b.minY)-minY,by=cell(b.maxY)-minY,az=cell(b.minZ)-minZ,bz=cell(b.maxZ)-minZ;
            for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)for(int x=ax;x<=bx;x++){int bucket=(z*ny+y)*nx+x;references[cursor[bucket]++]=i;}
        }
    }
    private static int cell(double coordinate){return (int)Math.floor(coordinate*SCALE);}
    public Box bounds(){return bounds;}
    /** Conservative retained-memory accounting for the immutable index and owned box list. */
    public long retainedBytes(){return 128L+72L*boxes.size()+(offsets==null?0:16L+4L*offsets.length)+(references==null?0:16L+4L*references.length)+(oversized==null?0:16L+4L*oversized.length);}
    /** Allocation-free existence query for canopy sweeps; touching faces remain clear. */
    public boolean intersects(Box query){return intersects(query.minX,query.minY,query.minZ,query.maxX,query.maxY,query.maxZ);}
    public boolean intersects(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        if(bounds==null||!intersects(bounds,minX,minY,minZ,maxX,maxY,maxZ))return false;
        if(offsets==null){for(int i=0;i<boxes.size();i++)if(intersects(boxes.get(i),minX,minY,minZ,maxX,maxY,maxZ))return true;return false;}
        int ax=Math.max(0,cell(minX)-this.minX),bx=Math.min(nx-1,cell(maxX)-this.minX),ay=Math.max(0,cell(minY)-this.minY),by=Math.min(ny-1,cell(maxY)-this.minY),az=Math.max(0,cell(minZ)-this.minZ),bz=Math.min(nz-1,cell(maxZ)-this.minZ);
        for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)for(int x=ax;x<=bx;x++){
            int bucket=(z*ny+y)*nx+x;
            for(int at=offsets[bucket];at<offsets[bucket+1];at++)if(intersects(boxes.get(references[at]),minX,minY,minZ,maxX,maxY,maxZ))return true;
        }
        for(int index:oversized)if(intersects(boxes.get(index),minX,minY,minZ,maxX,maxY,maxZ))return true;
        return false;
    }
    private static boolean intersects(Box a,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){return a.minX<maxX&&a.maxX>minX&&a.minY<maxY&&a.maxY>minY&&a.minZ<maxZ&&a.maxZ>minZ;}
    public static boolean intersects(Box a,Box b){return a.minX<b.maxX&&a.maxX>b.minX&&a.minY<b.maxY&&a.maxY>b.minY&&a.minZ<b.maxZ&&a.maxZ>b.minZ;}
}
