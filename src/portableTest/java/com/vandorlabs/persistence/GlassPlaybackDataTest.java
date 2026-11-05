package com.vandorlabs.persistence;

import java.util.*;

public final class GlassPlaybackDataTest {
    private static final GlassPlaybackData.ByteTag<Map<String,Byte>> TAG=new GlassPlaybackData.ByteTag<Map<String,Byte>>(){
        @Override public void putByte(Map<String,Byte> tag,String key,byte value){tag.put(key,value);}
        @Override public void remove(Map<String,Byte> tag,String key){tag.remove(key);}
    };
    public static void main(String[] args){
        for(int value=-128;value<=127;value++){if(GlassPlaybackData.read(true,(byte)value)!=(value>=0&&value<=2?value:1)||GlassPlaybackData.read(false,(byte)value)!=1)throw new AssertionError("Strict byte/default");}
        for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){
            Map<String,Byte> tag=new HashMap<String,Byte>();tag.put("Opaque",(byte)99);tag.put(GlassPlaybackData.MODE,(byte)-12);tag.put(GlassPlaybackData.SPEED,(byte)-13);GlassPlaybackData.write(TAG,tag,mode,speed);
            if(tag.size()!=(mode==1&&speed==1?1:3)||tag.get("Opaque")!=99)throw new AssertionError("Sparse overwrite/removal");
            GlassPlaybackData.writeSettings(TAG,tag,mode,speed);if(tag.get(GlassPlaybackData.MODE)!=mode||tag.get(GlassPlaybackData.SPEED)!=speed)throw new AssertionError("Explicit settings");
        }
        System.out.println("Glass playback byte codec PASS 512 read boundaries nine sparse/explicit writes");
    }
}
