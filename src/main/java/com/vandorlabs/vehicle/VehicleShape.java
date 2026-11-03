package com.vandorlabs.vehicle;

import net.minecraft.nbt.NBTTagCompound;

/** Fixed 12-cube occupancy grid: 216 bytes, independent of hull size and tile contents. */
public final class VehicleShape {
    public static final int SIZE=12,WORDS=54;
    public final int[] bits;
    public final float centerX,centerY,centerZ;
    private VehicleShape(int[] bits){this.bits=bits;int minX=12,minY=12,minZ=12,maxX=-1,maxY=-1,maxZ=-1;for(int y=0;y<12;y++)for(int z=0;z<12;z++)for(int x=0;x<12;x++)if(occupied(x,y,z)){minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}centerX=maxX<0?6:(minX+maxX+1)/2F;centerY=maxY<0?6:(minY+maxY+1)/2F;centerZ=maxZ<0?6:(minZ+maxZ+1)/2F;}
    public static VehicleShape read(NBTTagCompound tag){int[] bits=tag.getIntArray("VehicleShape");return bits.length==WORDS?new VehicleShape(bits):null;}
    public void write(NBTTagCompound tag){tag.setIntArray("VehicleShape",bits);}
    public boolean occupied(int x,int y,int z){int i=(y*SIZE+z)*SIZE+x;return (bits[i>>>5]&(1<<(i&31)))!=0;}
    public static VehicleShape of(VehicleStructure structure){
        int[] bits=new int[WORDS];double scale=SIZE/Math.max(structure.bounds.maxX,Math.max(structure.bounds.maxY,structure.bounds.maxZ));
        for(VehicleStructure.Cell cell:structure.cells){
            int minX=(int)(cell.pos.getX()*scale),minY=(int)(cell.pos.getY()*scale),minZ=(int)(cell.pos.getZ()*scale);
            int maxX=Math.min(12,(int)Math.ceil((cell.pos.getX()+1)*scale)),maxY=Math.min(12,(int)Math.ceil((cell.pos.getY()+1)*scale)),maxZ=Math.min(12,(int)Math.ceil((cell.pos.getZ()+1)*scale));
            for(int y=minY;y<maxY;y++)for(int z=minZ;z<maxZ;z++)for(int x=minX;x<maxX;x++){int i=(y*SIZE+z)*SIZE+x;bits[i>>>5]|=1<<(i&31);}
        }
        return new VehicleShape(bits);
    }
}
