package com.vandorlabs.ramp;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Clipboard tools relocate tile x/y/z but leave the ramp's private journal intact. */
public final class RampCopyData {
    public static final String ORIGIN="RampJournalOrigin";
    private RampCopyData(){}
    /** These two lists represent sets. Coordinate translation changes HashSet iteration order,
     * but must not make an otherwise lossless vehicle/copy snapshot fail its NBT check.
     * Preserve every entry and field, including duplicates, so real data loss is still rejected. */
    public static NBTTagCompound canonicalizeSets(NBTTagCompound tag){
        if(!"vandorlabs:programmable_ramp".equals(tag.getString("id")))return tag;
        for(String key:new String[]{"Cells","RampCanopyBlockers"}){
            if(!tag.hasKey(key,9))continue;
            net.minecraft.nbt.NBTTagList list=tag.getTagList(key,10);
            if(list.tagCount()<2)continue;
            java.util.List<NBTTagCompound> entries=new java.util.ArrayList<>();
            for(int i=0;i<list.tagCount();i++)entries.add(list.getCompoundTagAt(i));
            entries.sort((a,b)->{
                BlockPos p=position(a),q=position(b);
                int order=Integer.compare(p.getX(),q.getX());
                if(order==0)order=Integer.compare(p.getY(),q.getY());
                return order==0?Integer.compare(p.getZ(),q.getZ()):order;
            });
            net.minecraft.nbt.NBTTagList sorted=new net.minecraft.nbt.NBTTagList();
            for(NBTTagCompound entry:entries)sorted.appendTag(entry);
            tag.setTag(key,sorted);
        }
        return tag;
    }
    private static BlockPos position(NBTTagCompound tag){
        return tag.hasKey("X")?new BlockPos(tag.getInteger("X"),tag.getInteger("Y"),tag.getInteger("Z")):BlockPos.fromLong(tag.getLong("Pos"));
    }
    public static NBTTagCompound relocated(NBTTagCompound source){
        if(!source.hasKey(ORIGIN,4))return source;
        BlockPos at=new BlockPos(source.getInteger("x"),source.getInteger("y"),source.getInteger("z"));
        BlockPos saved=BlockPos.fromLong(source.getLong(ORIGIN));
        if(at.equals(saved))return source;
        NBTTagCompound result=com.vandorlabs.vehicle.VehicleTileData.translated(source,at.subtract(saved));
        result.setInteger("x",at.getX());result.setInteger("y",at.getY());result.setInteger("z",at.getZ());
        return result;
    }
}
