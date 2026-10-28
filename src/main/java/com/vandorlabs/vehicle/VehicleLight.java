package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Local, bounded block lighting for a moving snapshot, independent of parent chunk mutation. */
final class VehicleLight {
    private final VehicleWorld world;
    private Map<BlockPos,Integer> sources=Collections.emptyMap();
    private Map<BlockPos,Integer> opacity=Collections.emptyMap();
    private int appearance=Integer.MIN_VALUE,minX,minY,minZ,width,height,depth;
    private byte[] levels=new byte[0];
    private boolean dirty=true;
    VehicleLight(VehicleWorld world){this.world=world;}
    void invalidate(){dirty=true;}
    Map<BlockPos,Integer> sources(){at(BlockPos.ORIGIN);return Collections.unmodifiableMap(sources);}
    int at(BlockPos pos){
        int current=world.propulsionAppearance();
        if(dirty||appearance!=current){
            dirty=false;appearance=current;Map<BlockPos,Integer> next=new HashMap<>(),nextOpacity=new HashMap<>();
            for(VehicleStructure.Cell cell:world.structure.cells){int light=world.getBlockState(cell.pos).getLightValue(world,cell.pos);if(light>0)next.put(cell.pos,Math.min(15,light));int blocking=world.getBlockState(cell.pos).getLightOpacity(world,cell.pos);if(blocking>1)nextOpacity.put(cell.pos,blocking);}
            // Changed opaque geometry also changes propagation even with the same lamps.
            if(!sources.equals(next)||!opacity.equals(nextOpacity)){sources=next;opacity=nextOpacity;build();}
        }
        int x=pos.getX()-minX,y=pos.getY()-minY,z=pos.getZ()-minZ;
        return x<0||y<0||z<0||x>=width||y>=height||z>=depth?0:levels[(y*depth+z)*width+x]&15;
    }
    private void build(){
        if(sources.isEmpty()){levels=new byte[0];width=height=depth=0;return;}
        int maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;minX=minY=minZ=Integer.MAX_VALUE;
        for(BlockPos pos:sources.keySet()){minX=Math.min(minX,pos.getX()-14);minY=Math.min(minY,pos.getY()-14);minZ=Math.min(minZ,pos.getZ()-14);maxX=Math.max(maxX,pos.getX()+14);maxY=Math.max(maxY,pos.getY()+14);maxZ=Math.max(maxZ,pos.getZ()+14);}
        width=maxX-minX+1;height=maxY-minY+1;depth=maxZ-minZ+1;
        long volume=(long)width*height*depth;
        if(volume>1000000L)throw new IllegalArgumentException("Vehicle light bounds exceed snapshot limit");
        levels=new byte[(int)volume];int[] queue=new int[levels.length];int head=0,tail=0,count=0;
        for(Map.Entry<BlockPos,Integer> entry:sources.entrySet()){
            BlockPos pos=entry.getKey();int index=((pos.getY()-minY)*depth+pos.getZ()-minZ)*width+pos.getX()-minX;
            levels[index]=(byte)(entry.getValue()|128);queue[tail]=index;tail=(tail+1)%queue.length;count++;
        }
        while(count>0){
            int index=queue[head];head=(head+1)%queue.length;count--;int light=levels[index]&15;levels[index]=(byte)light;
            if(light<=1)continue;
            int x=index%width,z=(index/width)%depth,y=index/(width*depth);
            for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.VALUES){
                int nx=x+side.getFrontOffsetX(),ny=y+side.getFrontOffsetY(),nz=z+side.getFrontOffsetZ();
                if(nx<0||ny<0||nz<0||nx>=width||ny>=height||nz>=depth)continue;
                BlockPos at=new BlockPos(minX+nx,minY+ny,minZ+nz);int next=(ny*depth+nz)*width+nx;
                int value=light-Math.max(1,opacity.getOrDefault(at,1));
                if(value<=(levels[next]&15))continue;
                boolean queued=(levels[next]&128)!=0;levels[next]=(byte)(value|128);
                if(!queued){queue[tail]=next;tail=(tail+1)%queue.length;count++;}
            }
        }
    }
}
