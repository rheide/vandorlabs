package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-local stationary sources, preserving the Forge Sources/Position/Channel/Range schema. */
public final class ManualSavedData extends SavedData {
    public static final String NAME="vandorlabs_manual_redstone";
    public static final class Entry implements ChannelSourceIndex.SourceSnapshot {
        public final BlockPos pos;public final int channel,range;
        Entry(BlockPos pos,int channel,int range){this.pos=pos.immutable();this.channel=channel;this.range=range;}
        public int x(){return pos.getX();}public int y(){return pos.getY();}public int z(){return pos.getZ();}public int range(){return range;}public int level(){return 15;}
    }
    private final List<Entry> entries=new ArrayList<>();
    private final List<Entry> view=java.util.Collections.unmodifiableList(entries);
    public List<Entry> entries(){return view;}
    public static boolean validRange(int range){return range==0||range==16||range==64||range==256;}
    public static ManualSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ManualSavedData::new,ManualSavedData::load,null),NAME);}
    public void apply(BlockPos pos,ChannelList channels,int range,boolean on){
        if(!validRange(range))throw new IllegalArgumentException("Invalid transmitter range");
        clearCovered(pos,channels,range);
        if(on)for(int i=0;i<channels.size();i++)entries.add(new Entry(pos,channels.get(i),range));
        if(on)setDirty();
    }
    /** Commands clear centers in their own coverage, including built-in ranges not stored by handhelds. */
    public boolean clearCovered(BlockPos pos,ChannelList channels,int range){
        if(range<0)throw new IllegalArgumentException("Invalid clearing range");
        int count=entries.size();entries.removeIf(e->channels.contains(e.channel)&&(range==0||ChannelSpatialPolicy.finiteContains(e.x(),e.y(),e.z(),pos.getX(),pos.getY(),pos.getZ(),range)));
        boolean changed=count!=entries.size();if(changed)setDirty();return changed;
    }
    public static ManualSavedData load(CompoundTag tag,HolderLookup.Provider registries){
        ManualSavedData result=new ManualSavedData();ListTag list=tag.getList("Sources",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){CompoundTag entry=list.getCompound(i);int channel=entry.getInt("Channel"),range=entry.getInt("Range");if(channel>0&&validRange(range))result.entries.add(new Entry(BlockPos.of(entry.getLong("Position")),channel,range));}
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        ListTag list=new ListTag();for(Entry entry:entries){CompoundTag value=new CompoundTag();value.putLong("Position",entry.pos.asLong());value.putInt("Channel",entry.channel);value.putInt("Range",entry.range);list.add(value);}tag.put("Sources",list);return tag;
    }
}
