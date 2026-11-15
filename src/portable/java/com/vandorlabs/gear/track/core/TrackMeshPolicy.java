package com.vandorlabs.gear.track.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Finite authored-part templates shared by both render adapters. No world access or per-part allocation. */
public final class TrackMeshPolicy {
    public static final class Part {
        public final String name;public final int quarterTurns,template;
        private Part(String name,int turns,int template){this.name=name;quarterTurns=turns;this.template=template;}
    }
    public interface Sink {void part(Part value);}
    private static final String[] EXTRA={"socket","top_mount","top_mount_high","side_mount","side_mount_low","bridge_left","bridge_right"};
    private static final Part[] TEMPLATES=new Part[(3*16+EXTRA.length)*4];
    static{
        for(TrackTopology.Style style:TrackTopology.Style.values())for(int mask=0;mask<16;mask++)for(int turn=0;turn<4;turn++){int id=(style.ordinal()*16+mask)*4+turn;TEMPLATES[id]=new Part(style.name().toLowerCase(Locale.ROOT)+"_"+mask,turn,id);}
        for(int extra=0;extra<EXTRA.length;extra++)for(int turn=0;turn<4;turn++){int id=(48+extra)*4+turn;TEMPLATES[id]=new Part(EXTRA[extra],turn,id);}
    }
    public static int templateCount(){return TEMPLATES.length;}
    public static Part template(int index){return TEMPLATES[index];}
    private static Part extra(int group,int turn){return TEMPLATES[(48+group)*4+(turn&3)];}
    /** key must come from a validated appearance or the released legacy masking reader. */
    public static void emit(TrackTopology.Style style,int turns,int key,Sink sink){
        if(style==null||sink==null)throw new IllegalArgumentException("Missing track artwork");
        sink.part(TEMPLATES[(style.ordinal()*16+(key&15))*4+(turns&3)]);
        switch(key>>4&3){case 1:sink.part(extra(0,turns));break;case 2:sink.part(extra(1,turns));break;case 3:sink.part(extra(2,turns));break;default:break;}
        int sides=key>>6&15,lower=key>>14&15;for(int side=0;side<4;side++)if((sides&1<<side)!=0)sink.part(extra((lower&1<<side)!=0?4:3,side));
        if((key>>10&8)!=0)sink.part(extra(5,turns));if((key>>10&2)!=0)sink.part(extra(6,turns));
    }
    private static final class Parts extends ArrayList<Part> implements Sink {private static final long serialVersionUID=1L;public void part(Part value){add(value);}}
    public static List<Part> parts(TrackTopology.Style style,int turns,TrackVisualState state){if(state==null)throw new IllegalArgumentException("Missing track appearance");Parts result=new Parts();emit(style,turns,state.key(),result);return Collections.unmodifiableList(result);}
    private TrackMeshPolicy(){}
}
