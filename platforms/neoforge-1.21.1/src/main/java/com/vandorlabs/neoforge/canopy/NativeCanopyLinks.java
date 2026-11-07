package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.persistence.LegacyBlockPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Immutable saved ownership facts. Coordinates intentionally keep the released X26/Y12/Z26 encoding. */
public record NativeCanopyLinks(BlockPos anchor,BlockPos pair,int joinedWidth,boolean centered,boolean hollow,
        List<BlockPos> footprint,Set<Integer> released,boolean waiting,boolean open,float progress,
        boolean powerKnown,boolean lastPower,boolean lastPolarity) {
    public NativeCanopyLinks {if(anchor!=null)anchor=anchor.immutable();if(pair!=null)pair=pair.immutable();if(footprint!=null)footprint=footprint.stream().map(BlockPos::immutable).toList();released=Set.copyOf(released);}
    private static BlockPos decode(long value){return new BlockPos(LegacyBlockPosition.x(value),LegacyBlockPosition.y(value),LegacyBlockPosition.z(value));}
    private static long encode(BlockPos pos){return LegacyBlockPosition.pack(pos.getX(),pos.getY(),pos.getZ());}
    public static NativeCanopyLinks read(CompoundTag tag,BlockPos pos) {
        BlockPos anchor=tag.contains("AnchorOffset")?pos.offset(decode(tag.getLong("AnchorOffset"))):tag.contains("Anchor")?decode(tag.getLong("Anchor")):null;
        BlockPos pair=tag.contains("PairOffset")?pos.offset(decode(tag.getLong("PairOffset"))):tag.contains("Pair")?decode(tag.getLong("Pair")):null;
        int width=tag.contains("CanopyWidth",Tag.TAG_INT)?Math.max(1,Math.min(9,tag.getInt("CanopyWidth"))):pair==null?1:2;
        List<BlockPos> footprint=null;int[] raw=tag.getIntArray("CanopyFootprint");if(raw.length>0&&raw.length<=3000&&raw.length%3==0){footprint=new ArrayList<>();for(int i=0;i<raw.length;i+=3)if(Math.abs((long)raw[i])<=8&&Math.abs((long)raw[i+1])<=8&&Math.abs((long)raw[i+2])<=8)footprint.add(pos.offset(raw[i],raw[i+1],raw[i+2]));}
        Set<Integer> released=new HashSet<>();for(int index:tag.getIntArray("CanopyReleasedHelpers"))if(index>=0&&index<1000)released.add(index);
        float progress=tag.getFloat("Progress");progress=Float.isFinite(progress)?Math.max(0,Math.min(1,progress)):0;
        return new NativeCanopyLinks(anchor,pair,width,tag.getBoolean("CenteredPlacement"),tag.getBoolean("HollowPlacement"),footprint,released,tag.getBoolean("CanopyWaitingForReservations"),tag.getBoolean("Open"),progress,tag.getBoolean("CanopyPowerKnown"),tag.getBoolean("CanopyLastPower"),tag.getBoolean("CanopyLastPolarity"));
    }
    public void write(CompoundTag tag,BlockPos pos,int mask,int roof,int actualWidth) {
        if(anchor!=null){tag.putLong("Anchor",encode(anchor));tag.putLong("AnchorOffset",encode(anchor.subtract(pos)));}
        if(pair!=null){tag.putLong("Pair",encode(pair));tag.putLong("PairOffset",encode(pair.subtract(pos)));}
        tag.putInt("CanopyMask",mask);tag.putInt("CanopyRoofMask",roof);tag.putInt("CanopyWidth",actualWidth);
        if(footprint!=null){int[] offsets=new int[footprint.size()*3];int i=0;for(BlockPos p:footprint){offsets[i++]=p.getX()-pos.getX();offsets[i++]=p.getY()-pos.getY();offsets[i++]=p.getZ()-pos.getZ();}tag.putIntArray("CanopyFootprint",offsets);}
        tag.putIntArray("CanopyReleasedHelpers",new HashSet<>(released).stream().mapToInt(Integer::intValue).toArray());
        tag.putBoolean("CenteredPlacement",centered);tag.putBoolean("HollowPlacement",hollow);tag.putBoolean("CanopyWaitingForReservations",waiting);tag.putBoolean("Open",open);tag.putFloat("Progress",progress);tag.putBoolean("CanopyPowerKnown",powerKnown);tag.putBoolean("CanopyLastPower",lastPower);tag.putBoolean("CanopyLastPolarity",lastPolarity);
    }
    public boolean savedFootprint(){return footprint!=null&&!footprint.isEmpty();}
}
