package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import java.util.Map;

/** Persistent native control bank with the current legacy per-channel latch contract. */
public interface NativeChannelLatch extends NativeChannelMember {
    boolean isChannelLatch();
    boolean latchOn();
    void applyLinkedLatch(boolean on);
    default ChannelList latchedChannels(){return latchOn()?getRedstoneChannels():ChannelList.EMPTY;}
    default int latchedLevel(int channel){return latchedChannels().contains(channel)?15:0;}
    default void applyLinkedChannels(ChannelList active){applyLinkedLatch(!active.isEmpty());}
    default void applyLinkedLevels(Map<Integer,Integer> levels){
        int[] active=new int[levels.size()];int count=0;
        for(var entry:levels.entrySet())if(entry.getValue()>0)active[count++]=entry.getKey();
        applyLinkedChannels(ChannelList.of(java.util.Arrays.copyOf(active,count)));
    }
}
