package com.vandorlabs.gear;

import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Group topology is relative to its mount; rotations transform every local link together. */
public final class GearData {
    private GearData(){}
    private static BlockPos vector(BlockPos p,int turns){switch(Math.floorMod(turns,4)){case 1:return new BlockPos(-p.getZ(),p.getY(),p.getX());case 2:return new BlockPos(-p.getX(),p.getY(),-p.getZ());case 3:return new BlockPos(p.getZ(),p.getY(),-p.getX());default:return p;}}
    public static void rotate(NBTTagCompound tag,int turns){
        if(tag.hasKey("GearOwnerOffset",4))tag.setLong("GearOwnerOffset",vector(BlockPos.fromLong(tag.getLong("GearOwnerOffset")),turns).toLong());
        for(String key:new String[]{"GearFeet","GearColumns"}){NBTTagList old=tag.getTagList(key,11),list=new NBTTagList();for(int i=0;i<old.tagCount();i++){int[] xyz=old.getIntArrayAt(i);if(xyz.length!=3)throw new IllegalArgumentException("Invalid modular gear topology");BlockPos p=vector(new BlockPos(xyz[0],xyz[1],xyz[2]),turns);list.appendTag(new NBTTagIntArray(new int[]{p.getX(),p.getY(),p.getZ()}));}if(tag.hasKey(key,9))tag.setTag(key,list);}
        try{EnumFacing facing=EnumFacing.valueOf(tag.getString("GearDirection"));for(int i=0;i<Math.floorMod(turns,4);i++)facing=facing.rotateY();tag.setString("GearDirection",facing.name());}catch(IllegalArgumentException ignored){}
        if(tag.hasKey("GearFootFacing",3)){EnumFacing foot=EnumFacing.getHorizontal(tag.getInteger("GearFootFacing"));for(int i=0;i<Math.floorMod(turns,4);i++)foot=foot.rotateY();tag.setInteger("GearFootFacing",foot.getHorizontalIndex());}
    }
}
