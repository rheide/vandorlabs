package com.vandorlabs.vehicle;

import net.minecraft.nbt.NBTTagCompound;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Inert material preview. Never retains inventories, capabilities or nested vehicle snapshots. */
public final class VehiclePreview {
    public static final int MAX_BYTES=12000,MAX_RAW=1024*1024;
    private VehiclePreview(){}
    public static NBTTagCompound of(VehicleStructure structure){
        NBTTagCompound result=new NBTTagCompound();VehicleShape.of(structure).write(result);
        // Bound both packet size and client sampling work; retain the legacy silhouette as fallback.
        for(int stride=Math.max(1,(structure.cells.size()+4095)/4096);stride<=structure.cells.size();stride*=2){
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int i=0;i<structure.cells.size();i+=stride){VehicleStructure.Cell c=structure.cells.get(i);cells.add(new VehicleStructure.Cell(c.pos,c.state,null));}
            try{byte[] raw=VehicleStructure.display(cells).encode();ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(GZIPOutputStream zip=new GZIPOutputStream(bytes)){zip.write(raw);}if(bytes.size()<=MAX_BYTES){result.setByteArray("VehiclePreview",bytes.toByteArray());break;}}
            catch(IOException invalid){throw new IllegalStateException("Cannot encode vehicle preview",invalid);}
        }
        return result;
    }
    public static VehicleStructure read(NBTTagCompound tag)throws IOException{
        byte[] bytes=tag.getByteArray("VehiclePreview");if(bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("Invalid preview size");
        ByteArrayOutputStream raw=new ByteArrayOutputStream();try(InputStream zip=new GZIPInputStream(new ByteArrayInputStream(bytes))){byte[] buffer=new byte[4096];int count;while((count=zip.read(buffer))!=-1){if(raw.size()+count>MAX_RAW)throw new IOException("Preview exceeds decoded budget");raw.write(buffer,0,count);}}
        VehicleStructure result=VehicleStructure.readDisplay(net.minecraft.nbt.CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(raw.toByteArray())),new net.minecraft.nbt.NBTSizeTracker(MAX_RAW*4L)));if(result.cells.size()>4096)throw new IOException("Preview exceeds cell budget");for(VehicleStructure.Cell cell:result.cells)if(cell.hasTileData())throw new IOException("Tile data in inert preview");return result;
    }
}
