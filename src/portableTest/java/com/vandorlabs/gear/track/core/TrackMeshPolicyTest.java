package com.vandorlabs.gear.track.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Independent released selection oracle, including every valid lower-side subset and exact legacy bit layout. */
public final class TrackMeshPolicyTest {
    private static int cases,checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Sink implements TrackMeshPolicy.Sink {final List<String> parts=new ArrayList<String>();public void part(TrackMeshPolicy.Part p){need(TrackMeshPolicy.template(p.template)==p,"finite immutable template identity");parts.add(p.name+":"+p.quarterTurns);}}
    public static void main(String[] args){
        Sink actual=new Sink();List<String> expected=new ArrayList<String>();
        for(TrackTopology.Style style:TrackTopology.Style.values())for(int facing=0;facing<4;facing++)for(int belt=0;belt<16;belt++)for(int mount=0;mount<4;mount++)for(int side=0;side<16;side++)for(int bridge:new int[]{0,2,8,10})for(int lower=0;lower<16;lower++)if((lower&~side)==0){
            actual.parts.clear();expected.clear();int key=belt|mount<<4|side<<6|bridge<<10|lower<<14;
            TrackVisualState value=new TrackVisualState(belt,TrackVisualState.Mount.values()[mount],side,bridge,lower);
            need(value.key()==key&&TrackVisualState.strict(key).equals(value),"exact packed appearance");need(TrackVisualState.legacy(key|1<<25).equals(value),"released legacy masking");
            TrackMeshPolicy.emit(style,facing,key,actual);expected.add(style.name().toLowerCase(Locale.ROOT)+"_"+belt+":"+facing);
            if(mount!=0)expected.add(new String[]{"","socket","top_mount","top_mount_high"}[mount]+":"+facing);
            for(int i=0;i<4;i++)if((side&1<<i)!=0)expected.add(((lower&1<<i)!=0?"side_mount_low":"side_mount")+":"+i);
            if((bridge&8)!=0)expected.add("bridge_left:"+facing);if((bridge&2)!=0)expected.add("bridge_right:"+facing);
            need(actual.parts.equals(expected),"released part ordering/identity/quarter turns");need(value.onArm().key()==(mount==1?key&~48:key),"arm socket replacement only");cases++;
        }
        for(int key:new int[]{-1,1<<10,1<<12,1<<25,1<<14}){boolean refused=false;try{TrackVisualState.strict(key);}catch(IllegalArgumentException invalid){refused=true;}need(refused,"strict new data refuses unknown/noncanonical bits");}
        need(TrackMeshPolicy.templateCount()==220,"bounded retained templates");System.out.println("Track mesh policy PASS cases="+cases+" checks="+checks+" legacyParts/packedBits/strictNewData/armSocket/220templates");
    }
    private TrackMeshPolicyTest(){}
}
