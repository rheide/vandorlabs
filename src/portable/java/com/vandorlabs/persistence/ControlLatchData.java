package com.vandorlabs.persistence;

import com.vandorlabs.redstone.ChannelList;
import java.util.Map;

/** Current per-channel mounted-control bank; the host retains its map, transitions and world callbacks. */
public final class ControlLatchData {
    public interface Tag {
        boolean containsIntArray(String key);
        int[] getIntArray(String key);
        void putIntArray(String key,int[] value);
    }
    /** Implemented by the existing owner, preserving virtual level lookup without allocating a callback. */
    public interface LevelLookup {int latchedLevel(int channel);}
    private ControlLatchData(){}
    public static int level(Map<Integer,Integer> levels,ChannelList latched,int channel){return levels.getOrDefault(channel,latched.contains(channel)?15:0);}
    public static int localLevel(boolean isLatch,boolean localOn,LevelLookup lookup,int channel){return isLatch?lookup.latchedLevel(channel):localOn?15:0;}
    public static int outputLevel(boolean localOn,ChannelList channels,LevelLookup lookup){
        if(!localOn)return 0;int level=15;for(int i=0;i<channels.size();i++)level=Math.min(level,lookup.latchedLevel(channels.get(i)));return level;
    }
    /** Mutate the existing level map; absent tail values keep the independent LatchedChannels fallback. */
    public static ChannelList read(Tag tag,ChannelList channels,boolean localOn,Map<Integer,Integer> levels){
        ChannelList latched=localOn?channels:ChannelList.EMPTY;
        if(tag.containsIntArray("LatchedChannels"))try{latched=ChannelList.of(tag.getIntArray("LatchedChannels"));}catch(IllegalArgumentException malformed){/* Keep the legacy LocalOn fallback. */}
        latched=latched.intersect(channels);
        levels.clear();int[] saved=tag.getIntArray("LatchedLevels");for(int i=0;i<Math.min(saved.length,channels.size());i++)levels.put(channels.get(i),Math.max(0,Math.min(15,saved[i])));
        return latched;
    }
    /** Exactly the two legacy ownership arrays; no intermediate state snapshot or second clone. */
    public static void write(Tag tag,ChannelList channels,ChannelList latched,LevelLookup lookup){
        int[] saved=new int[channels.size()];for(int i=0;i<saved.length;i++)saved[i]=lookup.latchedLevel(channels.get(i));
        tag.putIntArray("LatchedLevels",saved);tag.putIntArray("LatchedChannels",latched.toArray());
    }
}
