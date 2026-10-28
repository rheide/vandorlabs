package com.vandorlabs.redstone;

import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Handheld toggles are stationary saved sources at the point of use, never per-player tick scans. */
public final class ManualRedstoneData extends WorldSavedData {
    private static final String NAME="vandorlabs_manual_redstone";
    public static final class Entry {public final BlockPos pos;public final int channel,range;Entry(BlockPos pos,int channel,int range){this.pos=pos.toImmutable();this.channel=channel;this.range=range;}}
    public final List<Entry> entries=new ArrayList<>();
    public ManualRedstoneData(){super(NAME);}
    public ManualRedstoneData(String name){super(name);}
    public static ManualRedstoneData get(World world){ManualRedstoneData data=(ManualRedstoneData)world.getPerWorldStorage().getOrLoadData(ManualRedstoneData.class,NAME);if(data==null){data=new ManualRedstoneData();world.getPerWorldStorage().setData(NAME,data);}return data;}
    public void apply(BlockPos pos,ChannelList channels,int range,boolean on){int count=entries.size();entries.removeIf(e->channels.contains(e.channel)&&(range==0||e.pos.distanceSq(pos)<=(double)range*range));if(on)for(int i=0;i<channels.size();i++)entries.add(new Entry(pos,channels.get(i),range));if(on||entries.size()!=count)markDirty();}
    @Override public void readFromNBT(NBTTagCompound tag){entries.clear();NBTTagList list=tag.getTagList("Sources",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound t=list.getCompoundTagAt(i);int channel=t.getInteger("Channel"),range=t.getInteger("Range");if(channel>0&&(range==0||range==16||range==64||range==256))entries.add(new Entry(BlockPos.fromLong(t.getLong("Position")),channel,range));}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){NBTTagList list=new NBTTagList();for(Entry e:entries){NBTTagCompound t=new NBTTagCompound();t.setLong("Position",e.pos.toLong());t.setInteger("Channel",e.channel);t.setInteger("Range",e.range);list.appendTag(t);}tag.setTag("Sources",list);return tag;}
}
