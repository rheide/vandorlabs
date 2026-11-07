package com.vandorlabs.vehicle;

import com.vandorlabs.items.CopiedSettingsMemory;
import java.util.*;
import net.minecraft.nbt.*;

/** Owning cache only. Private immutable entries contain no tile, world or capability instances. */
final class VehicleGearConfigurationCache {
    static final long MAX_BYTES=8L*1024*1024;
    private static final int MAX_ENTRIES=256;
    private static final CopiedSettingsMemory.Tags<NBTBase> MEMORY=new CopiedSettingsMemory.Tags<NBTBase>(){
        public int type(NBTBase tag){return tag.getId();}
        public Iterable<String> keys(NBTBase tag){return ((NBTTagCompound)tag).getKeySet();}
        public NBTBase member(NBTBase tag,String key){return ((NBTTagCompound)tag).getTag(key);}
        public NBTBase element(NBTBase tag,int index){return ((NBTTagList)tag).get(index);}
        public int length(NBTBase tag){switch(tag.getId()){case 9:return ((NBTTagList)tag).tagCount();case 8:return ((NBTTagString)tag).getString().length();case 11:return ((NBTTagIntArray)tag).getIntArray().length;case 7:return ((NBTTagByteArray)tag).getByteArray().length;default:return 0;}}
    };
    private static final class Entry{final NBTTagCompound data;final long bytes;Entry(NBTTagCompound data,long bytes){this.data=data;this.bytes=bytes;}}
    private final LinkedHashMap<Object,Entry> entries=new LinkedHashMap<>(16,.75F,true);
    private long bytes;
    NBTTagCompound configuration(VehicleStructure.Cell cell){
        Object identity=cell.gearConfigurationIdentity();if(identity==null)throw new IllegalArgumentException("Gear configuration identity missing");Entry cached=entries.get(identity);if(cached!=null)return cached.data;
        NBTTagCompound data=cell.gearConfigurationData();long retained=128+2*CopiedSettingsMemory.estimate(data,MEMORY);if(retained>MAX_BYTES)return data;
        while(!entries.isEmpty()&&(entries.size()>=MAX_ENTRIES||bytes+retained>MAX_BYTES)){Iterator<Entry> oldest=entries.values().iterator();bytes-=oldest.next().bytes;oldest.remove();}
        entries.put(identity,new Entry(data,retained));bytes+=retained;return data;
    }
    void clear(){entries.clear();bytes=0;}
    int size(){return entries.size();}
    long retainedBytes(){return bytes;}
}
