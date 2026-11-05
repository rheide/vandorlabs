package com.vandorlabs.persistence;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class TriggerStateTest {
    private static final class Tag implements TriggerState.Tag {
        final Map<String,Object> values=new HashMap<>();
        public boolean containsInt(String key){return values.get(key) instanceof Integer;}
        public int getInt(String key){Object v=values.get(key);return v instanceof Number?((Number)v).intValue():0;}
        public boolean getBoolean(String key){return Boolean.TRUE.equals(values.get(key));}
        public int[] getIntArray(String key){Object v=values.get(key);return v instanceof int[]?(int[])v:new int[0];}
        public void putInt(String key,int value){values.put(key,value);}
        public void putBoolean(String key,boolean value){values.put(key,value);}
        public void putIntArray(String key,int[] value){values.put(key,value);}
        public void remove(String key){values.remove(key);}
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static TriggerState read(Tag tag){return TriggerState.read(tag,v->Math.max(0,v));}
    public static void main(String[] args){
        Tag old=new Tag();old.putInt("TriggerOnTexture",37);
        TriggerState saved=read(old);
        check(saved.on==37&&saved.low==37&&saved.medium==37&&saved.exact==-1,"old missing fields use ON fallback and any-level selection");
        check(!saved.frontOnly&&saved.sides==0,"old housing defaults");
        old.values.put("TriggerExactLevel",Long.valueOf(4));old.values.put("TriggerLowTexture",Byte.valueOf((byte)9));
        old.values.put("TriggerMediumTexture","wrong");
        saved=read(old);check(saved.exact==-1&&saved.low==37&&saved.medium==37,"optional fields require exact int tag type");
        old.putInt("TriggerExactLevel",100);old.putInt("TriggerLowTexture",-8);old.putInt("TriggerMediumTexture",90000);
        old.putBoolean("TriggerFrontOnly",true);old.putInt("TriggerSidesTexture",120000);
        old.putIntArray("TriggerArtworkModes",new int[]{-1,0,1,9});old.putIntArray("TriggerArtworkSpeeds",new int[]{-1,0,1,9});
        saved=read(old);check(saved.exact==15&&saved.low==0&&saved.medium==90000&&saved.frontOnly&&saved.sides==120000,"normalization and high external material IDs");
        check(Arrays.equals(saved.artworkModes(),new int[]{0,0,1,2})&&Arrays.equals(saved.artworkSpeeds(),new int[]{0,0,1,2}),"legacy playback clamps");
        int[] copy=saved.artworkModes();copy[0]=99;check(saved.artworkModes()[0]==0,"saved arrays immutable");
        Tag output=new Tag();saved.write(output);TriggerState again=read(output);
        check(again.frontOnly&&again.sides==120000&&Arrays.equals(again.artworkModes(),saved.artworkModes())&&Arrays.equals(again.artworkSpeeds(),saved.artworkSpeeds()),"front-only, side and dormant playback round trip");
        old.putIntArray("TriggerArtworkModes",new int[]{2});old.putIntArray("TriggerArtworkSpeeds",new int[5]);
        saved=read(old);check(Arrays.equals(saved.artworkModes(),new int[]{1,1,1,1})&&Arrays.equals(saved.artworkSpeeds(),new int[]{1,1,1,1}),"malformed arrays reset wholly to legacy defaults");
        new TriggerState(0,false,-1,0,0,false,0,new int[]{1,1,1,1},new int[]{1,1,1,1}).write(output);
        check(!output.values.containsKey("TriggerFrontOnly")&&!output.values.containsKey("TriggerSidesTexture")&&!output.values.containsKey("TriggerArtworkModes")&&!output.values.containsKey("TriggerArtworkSpeeds"),"default writes remove stale optional fields");
        int[] callerModes={0,2,1,2},callerSpeeds={2,0,1,2};
        TriggerState.write(output,5,true,0,6,7,true,8,callerModes,callerSpeeds);
        callerModes[0]=2;callerSpeeds[0]=0;
        check(output.getIntArray("TriggerArtworkModes")[0]==0&&output.getIntArray("TriggerArtworkSpeeds")[0]==2,"static writer copies only published arrays without retaining caller ownership");
        for(int exact=-1;exact<=15;exact++)for(int signal=0;signal<=15;signal++)for(int band=0;band<2;band++){
            int expected=exact>=0&&signal!=exact?0:band==0?(exact>=0||signal>0?3:0):(signal==0?0:signal<6?1:signal<11?2:3);
            check(TriggerState.visibleSlot(band==1,exact,signal)==expected,"selection exact="+exact+" signal="+signal+" bands="+band);
        }
        System.out.println("TriggerState contracts PASS");
    }
}
