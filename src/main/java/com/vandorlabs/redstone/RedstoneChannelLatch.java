package com.vandorlabs.redstone;

/** A loaded, persistent control whose handle mirrors linked controls. */
public interface RedstoneChannelLatch extends RedstoneChannelMember {
    default int latchedLevel(int channel){return latchedChannels().contains(channel)?15:0;}
    default void applyLinkedLevels(java.util.Map<Integer,Integer> levels){
        int[] active=new int[levels.size()];int count=0;
        for(java.util.Map.Entry<Integer,Integer> entry:levels.entrySet())if(entry.getValue()>0)active[count++]=entry.getKey();
        applyLinkedChannels(ChannelList.of(java.util.Arrays.copyOf(active,count)));
    }
    boolean isChannelLatch();
    boolean latchOn();
    void applyLinkedLatch(boolean on);
    default ChannelList latchedChannels(){return latchOn()?getRedstoneChannels():ChannelList.EMPTY;}
    default void applyLinkedChannels(ChannelList active){applyLinkedLatch(!active.isEmpty());}
}
