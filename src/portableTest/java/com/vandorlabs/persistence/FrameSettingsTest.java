package com.vandorlabs.persistence;

public final class FrameSettingsTest {
    private static void require(boolean ok){if(!ok)throw new AssertionError("frame saved contract");}
    public static void main(String[] args){
        java.util.function.IntPredicate frame=v->v==-1||v==2,surface=v->frame.test(v)||v==5,material=v->v>=0&&v<10;
        MemoryPrimitiveData tag=new MemoryPrimitiveData();FrameSettings missing=FrameSettings.read(tag,frame,surface,material);
        require(missing.front==-1&&missing.interior==-1&&missing.exterior==-2&&missing.exteriorChoice()==-1&&!missing.tile&&!missing.bare);
        int count=0;
        for(int v:new int[]{Integer.MIN_VALUE,-2,-1,0,2,5,9,10,Integer.MAX_VALUE})for(int flags=0;flags<16;flags++){
            FrameSettings raw=new FrameSettings(v,v,v,flags%3,v,v,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);
            raw.write(tag);FrameSettings saved=FrameSettings.read(tag,frame,surface,material);
            require(saved.front==(frame.test(v)&&(v>=0||flags%3>0)?v:-1));require(saved.interior==(v>=0&&surface.test(v)?v:-1));
            require(saved.exterior==(v>=-1&&surface.test(v)?v:-2));require(saved.splitTop==(v>=0&&material.test(v)?v:-1)&&saved.splitBottom==saved.splitTop);
            require(saved.artworkVersion==(flags%3==1?1:0)&&saved.tile==raw.tile&&saved.displayFit==raw.displayFit&&saved.split==raw.split&&saved.bare==raw.bare);count++;
        }
        tag.putString("FrameFront","2");require(FrameSettings.read(tag,frame,surface,material).front==-1);
        System.out.println("FrameSettings PASS "+count+" sparse writes/typed choices/sentinels/flags and missing previous-release fields");
    }
}
