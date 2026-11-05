package com.vandorlabs.persistence;

import java.util.function.IntPredicate;

/** Independent frame choices. Negative exterior inherits the interior only at the legacy -2 sentinel. */
public final class FrameSettings {
    public interface Tag extends HousingTags {void remove(String key);}
    public final int front,interior,exterior,artworkVersion,splitTop,splitBottom;
    public final boolean tile,displayFit,split,bare;
    public FrameSettings(int front,int interior,int exterior,int artworkVersion,int splitTop,int splitBottom,
            boolean tile,boolean displayFit,boolean split,boolean bare){
        this.front=front;this.interior=interior;this.exterior=exterior;this.artworkVersion=artworkVersion;
        this.splitTop=splitTop;this.splitBottom=splitBottom;this.tile=tile;this.displayFit=displayFit;this.split=split;this.bare=bare;
    }
    public static int choice(boolean typed,int saved,int fallback,IntPredicate valid){return typed&&valid.test(saved)?saved:fallback;}
    public static FrameSettings read(HousingTags tag,IntPredicate frame,IntPredicate surface,IntPredicate material){
        return new FrameSettings(choice(tag.containsInt("FrameFront"),tag.getInt("FrameFront"),-1,frame),
                choice(tag.containsInt("FrameInterior"),tag.getInt("FrameInterior"),-1,surface),
                choice(tag.containsInt("FrameExterior"),tag.getInt("FrameExterior"),-2,surface),
                tag.getInt("DisplayArtworkVersion")==1?1:0,
                splitChoice(tag.containsInt("DisplaySplitTop"),tag.getInt("DisplaySplitTop"),material),
                splitChoice(tag.containsInt("DisplaySplitBottom"),tag.getInt("DisplaySplitBottom"),material),
                tag.getBoolean("FrameTile"),tag.getBoolean("DisplayFit"),tag.getBoolean("DisplaySplit"),tag.getBoolean("FrameBare"));
    }
    private static int splitChoice(boolean typed,int value,IntPredicate material){return typed&&(value==-1||material.test(value))?value:-1;}
    public int exteriorChoice(){return exterior==-2?interior:exterior;}
    public void write(Tag tag){write(tag,front,interior,exterior,artworkVersion,splitTop,splitBottom,tile,displayFit,split,bare);}
    public static void write(Tag tag,int front,int interior,int exterior,int artworkVersion,int splitTop,int splitBottom,
            boolean tile,boolean displayFit,boolean split,boolean bare){
        if(artworkVersion>0)tag.putInt("DisplayArtworkVersion",artworkVersion);else tag.remove("DisplayArtworkVersion");
        if(artworkVersion>0)tag.putInt("FrameFront",front);else optional(tag,"FrameFront",front);
        optional(tag,"FrameInterior",interior);if(exterior>=-1)tag.putInt("FrameExterior",exterior);else tag.remove("FrameExterior");
        optional(tag,"DisplaySplitTop",splitTop);optional(tag,"DisplaySplitBottom",splitBottom);
        flag(tag,"FrameBare",bare);flag(tag,"FrameTile",tile);flag(tag,"DisplayFit",displayFit);flag(tag,"DisplaySplit",split);
    }
    private static void optional(Tag tag,String key,int value){if(value<0)tag.remove(key);else tag.putInt(key,value);}
    private static void flag(Tag tag,String key,boolean value){if(value)tag.putBoolean(key,true);else tag.remove(key);}
}
