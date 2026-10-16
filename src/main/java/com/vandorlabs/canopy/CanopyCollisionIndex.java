package com.vandorlabs.canopy;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import java.util.*;

/** Linear-build immutable broadphase; exact boxes retain their original order and identity. */
public final class CanopyCollisionIndex {
    private static final double SCALE=2;
    private static final int MAX_CELLS=262144,MAX_REFERENCES=4000000,MAX_BOX_CELLS=512;
    private final List<AxisAlignedBB> boxes;
    private final AxisAlignedBB bounds;
    private final int minX,minY,minZ,nx,ny,nz;
    private final int[] offsets,references,oversized;
    private static final class Matches {
        int[] indices=new int[16];int size,examined;
        void add(int index){if(size==indices.length)indices=Arrays.copyOf(indices,size*2);indices[size++]=index;}
    }
    public CanopyCollisionIndex(List<AxisAlignedBB> boxes){
        this.boxes=boxes;
        if(boxes.isEmpty()){bounds=null;minX=minY=minZ=nx=ny=nz=0;offsets=references=oversized=null;return;}
        AxisAlignedBB first=boxes.get(0);double x0=first.minX,y0=first.minY,z0=first.minZ,x1=first.maxX,y1=first.maxY,z1=first.maxZ;
        for(int i=1;i<boxes.size();i++){AxisAlignedBB b=boxes.get(i);x0=Math.min(x0,b.minX);y0=Math.min(y0,b.minY);z0=Math.min(z0,b.minZ);x1=Math.max(x1,b.maxX);y1=Math.max(y1,b.maxY);z1=Math.max(z1,b.maxZ);}
        bounds=new AxisAlignedBB(x0,y0,z0,x1,y1,z1);minX=cell(x0);minY=cell(y0);minZ=cell(z0);nx=cell(x1)-minX+1;ny=cell(y1)-minY+1;nz=cell(z1)-minZ+1;
        if(nx<=0||ny<=0||nz<=0||nx>MAX_CELLS||ny>MAX_CELLS||nz>MAX_CELLS){offsets=references=oversized=null;return;}
        long total=(long)nx*ny*nz;
        if(total>MAX_CELLS){offsets=references=oversized=null;return;}
        int[] counts=new int[(int)total+1],large=new int[16];int largeCount=0,referenceCount=0;boolean overBudget=false;
        for(int i=0;i<boxes.size();i++){
            AxisAlignedBB b=boxes.get(i);int ax=cell(b.minX)-minX,bx=cell(b.maxX)-minX,ay=cell(b.minY)-minY,by=cell(b.maxY)-minY,az=cell(b.minZ)-minZ,bz=cell(b.maxZ)-minZ;
            long coverage=(long)(bx-ax+1)*(by-ay+1)*(bz-az+1);
            if(coverage>MAX_BOX_CELLS){if(largeCount==large.length)large=Arrays.copyOf(large,large.length*2);large[largeCount++]=i;continue;}
            if((long)referenceCount+coverage>MAX_REFERENCES){overBudget=true;break;}referenceCount+=(int)coverage;
            for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)for(int x=ax;x<=bx;x++)counts[(z*ny+y)*nx+x+1]++;
        }
        if(overBudget){offsets=references=oversized=null;return;}
        for(int i=1;i<counts.length;i++)counts[i]+=counts[i-1];offsets=counts;references=new int[referenceCount];oversized=Arrays.copyOf(large,largeCount);int[] cursor=counts.clone();int nextLarge=0;
        for(int i=0;i<boxes.size();i++){
            if(nextLarge<largeCount&&oversized[nextLarge]==i){nextLarge++;continue;}
            AxisAlignedBB b=boxes.get(i);int ax=cell(b.minX)-minX,bx=cell(b.maxX)-minX,ay=cell(b.minY)-minY,by=cell(b.maxY)-minY,az=cell(b.minZ)-minZ,bz=cell(b.maxZ)-minZ;
            for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)for(int x=ax;x<=bx;x++){int bucket=(z*ny+y)*nx+x;references[cursor[bucket]++]=i;}
        }
    }
    private static int cell(double coordinate){return MathHelper.floor(coordinate*SCALE);}
    public AxisAlignedBB bounds(){return bounds;}
    /** Exact tests returned for deterministic performance checks. Oversized shapes use a bounded fallback. */
    public int addIntersecting(AxisAlignedBB query,List<AxisAlignedBB> output){
        if(bounds==null||!bounds.intersects(query))return 0;
        if(query.minX<=bounds.minX&&query.minY<=bounds.minY&&query.minZ<=bounds.minZ&&query.maxX>=bounds.maxX&&query.maxY>=bounds.maxY&&query.maxZ>=bounds.maxZ){output.addAll(boxes);return 0;}
        if(offsets==null){for(AxisAlignedBB b:boxes)if(b.intersects(query))output.add(b);return boxes.size();}
        int ax=Math.max(0,cell(query.minX)-minX),bx=Math.min(nx-1,cell(query.maxX)-minX),ay=Math.max(0,cell(query.minY)-minY),by=Math.min(ny-1,cell(query.maxY)-minY),az=Math.max(0,cell(query.minZ)-minZ),bz=Math.min(nz-1,cell(query.maxZ)-minZ);
        Matches matches=new Matches();
        for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)for(int x=ax;x<=bx;x++){int bucket=(z*ny+y)*nx+x;for(int at=offsets[bucket];at<offsets[bucket+1];at++){int index=references[at];matches.examined++;if(boxes.get(index).intersects(query))matches.add(index);}}
        for(int index:oversized){matches.examined++;if(boxes.get(index).intersects(query))matches.add(index);}
        Arrays.sort(matches.indices,0,matches.size);int previous=-1;for(int i=0;i<matches.size;i++){int index=matches.indices[i];if(index!=previous)output.add(boxes.get(index));previous=index;}return matches.examined;
    }
}
