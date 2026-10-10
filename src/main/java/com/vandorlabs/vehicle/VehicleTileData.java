package com.vandorlabs.vehicle;

import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Translate the absolute links used by supported Vandor tiles; relative links remain unchanged. */
public final class VehicleTileData {
    private VehicleTileData() { }
    public static NBTTagCompound translated(NBTTagCompound source, BlockPos offset) {
        NBTTagCompound result=source.copy();
        result.setInteger("x",source.getInteger("x")+offset.getX());
        result.setInteger("y",source.getInteger("y")+offset.getY());
        result.setInteger("z",source.getInteger("z")+offset.getZ());
        if(!source.getString("id").startsWith("vandorlabs:"))return result;
        for(String key:new String[]{"Anchor","Pair","TrapdoorPartner","TrapdoorSquare"})
            if(result.hasKey(key,4)) result.setLong(key,BlockPos.fromLong(result.getLong(key)).add(offset).toLong());
        if(result.hasKey("TrapdoorAssembly",9)) {
            NBTTagList before=result.getTagList("TrapdoorAssembly",4), after=new NBTTagList();
            for(int i=0;i<before.tagCount();i++)
                after.appendTag(new NBTTagLong(BlockPos.fromLong(((NBTTagLong)before.get(i)).getLong()).add(offset).toLong()));
            result.setTag("TrapdoorAssembly",after);
        }
        if(result.hasKey("ControllerVersion") || result.hasKey("Controller")) {
            for(String key:new String[]{"Sources","Cells","Origins","RampSourceTiles"}) {
                NBTTagList list=result.getTagList(key,10);
                for(int n=0;n<list.tagCount();n++) {
                    NBTTagCompound entry=list.getCompoundTagAt(n);
                    BlockPos before=entry.hasKey("X")?new BlockPos(entry.getInteger("X"),entry.getInteger("Y"),entry.getInteger("Z")):BlockPos.fromLong(entry.getLong("Pos"));
                    BlockPos after=before.add(offset);
                    entry.setInteger("X",after.getX());entry.setInteger("Y",after.getY());entry.setInteger("Z",after.getZ());
                    if(entry.hasKey("Pos"))entry.setLong("Pos",after.toLong());
                    if(entry.hasKey("Tile",10))entry.setTag("Tile",translated(entry.getCompoundTag("Tile"),offset));
                }
            }
            if(result.hasKey("SourceY"))result.setInteger("SourceY",result.getInteger("SourceY")+offset.getY());
            if(result.hasKey("Controller")) {
                BlockPos owner=result.hasKey("ControllerX")?new BlockPos(result.getInteger("ControllerX"),result.getInteger("ControllerY"),result.getInteger("ControllerZ")):BlockPos.fromLong(result.getLong("Controller"));
                owner=owner.add(offset);result.setLong("Controller",owner.toLong());result.setInteger("ControllerX",owner.getX());result.setInteger("ControllerY",owner.getY());result.setInteger("ControllerZ",owner.getZ());
            }
            if(result.hasKey("MinAlong")) {
                net.minecraft.util.EnumFacing direction=net.minecraft.util.EnumFacing.getHorizontal(result.getInteger("Facing"));
                result.setInteger("MinAlong",result.getInteger("MinAlong")+offset.getX()*direction.getFrontOffsetX()+offset.getZ()*direction.getFrontOffsetZ());
            }
        }
        return result;
    }
}
