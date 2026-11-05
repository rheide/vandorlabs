package com.vandorlabs.persistence;

import com.vandorlabs.redstone.ChannelList;
import java.util.*;

public final class ControlLatchDataTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Tags implements ControlLatchData.Tag {
        final Map<String,Object> fields=new HashMap<>();final List<String> writes=new ArrayList<>();
        public boolean containsIntArray(String key){return fields.get(key) instanceof int[];}
        public int[] getIntArray(String key){Object value=fields.get(key);return value instanceof int[]?(int[])value:new int[0];}
        public void putIntArray(String key,int[] value){writes.add(key);fields.put(key,value);}
    }
    public static void main(String[] ignored){
        ChannelList channels=ChannelList.of(3,7,9);Map<Integer,Integer> levels=new HashMap<>();Tags tag=new Tags();
        for(boolean local:new boolean[]{false,true})for(Object malformed:new Object[]{null,"wrong",new int[]{-1},new int[65]}){
            tag.fields.clear();if(malformed!=null)tag.fields.put("LatchedChannels",malformed);levels.put(99,11);ChannelList latched=ControlLatchData.read(tag,channels,local,levels);check(latched.equals(local?channels:ChannelList.EMPTY)&&levels.isEmpty(),"malformed/missing latched array keeps LocalOn fallback and clears existing map");
        }
        tag.fields.clear();tag.fields.put("LatchedChannels",new int[]{9,7,7,12});tag.fields.put("LatchedLevels",new int[]{0});ChannelList latched=ControlLatchData.read(tag,channels,true,levels);
        check(latched.equals(ChannelList.of(7,9))&&levels.size()==1&&levels.get(3)==0,"canonical intersection and partial levels prefix");check(ControlLatchData.level(levels,latched,3)==0&&ControlLatchData.level(levels,latched,7)==15&&ControlLatchData.level(levels,latched,11)==0,"explicit zero versus missing fallback");
        tag.fields.put("LatchedLevels",new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,8,11});ControlLatchData.read(tag,channels,false,levels);check(levels.size()==3&&levels.get(3)==0&&levels.get(7)==15&&levels.get(9)==8,"clamp only persisted prefix, ignore extras");
        tag.fields.put("LatchedChannels",new int[0]);tag.fields.remove("LatchedLevels");check(ControlLatchData.read(tag,channels,true,levels).isEmpty()&&levels.isEmpty(),"explicit empty suppresses LocalOn fallback");
        final List<Integer> calls=new ArrayList<>();ControlLatchData.LevelLookup lookup=channel->{calls.add(channel);return channel==3?6:9;};
        check(ControlLatchData.localLevel(false,true,lookup,3)==15&&ControlLatchData.localLevel(false,false,lookup,3)==0&&calls.isEmpty(),"nonlatch binary output avoids lookup");check(ControlLatchData.localLevel(true,false,lookup,3)==6,"latch numeric level independent of aggregate LocalOn");calls.clear();
        check(ControlLatchData.outputLevel(false,null,lookup)==0&&calls.isEmpty(),"OFF short circuit");check(ControlLatchData.outputLevel(true,ChannelList.EMPTY,lookup)==15&&calls.isEmpty(),"ON empty bank retains physical15");check(ControlLatchData.outputLevel(true,channels,lookup)==6&&calls.equals(Arrays.asList(3,7,9)),"minimum bank and exact virtual lookup order");
        Tags written=new Tags();ControlLatchData.write(written,channels,ChannelList.of(3,9),lookup);check(written.writes.equals(Arrays.asList("LatchedLevels","LatchedChannels")),"exact write order and two target arrays");check(Arrays.equals(written.getIntArray("LatchedLevels"),new int[]{6,9,9})&&Arrays.equals(written.getIntArray("LatchedChannels"),new int[]{3,9}),"writer uses virtual lookup and retains configured order");
        levels.put(3,-8);check(ControlLatchData.level(levels,channels,3)==-8,"runtime level lookup must not silently clamp");written=new Tags();ControlLatchData.write(written,ChannelList.of(3),channels,channel->-8);check(written.getIntArray("LatchedLevels")[0]==-8,"runtime writer must not silently clamp");
        Tags empty=new Tags();ControlLatchData.write(empty,ChannelList.EMPTY,ChannelList.EMPTY,lookup);check(empty.getIntArray("LatchedLevels").length==0&&empty.getIntArray("LatchedChannels").length==0,"empty arrays still written");
        System.out.println("ControlLatchData PASS checks="+checks+" malformed legacy fallback prefix explicit-zero min empty-bank virtual-dispatch ownership arrays");
    }
}
