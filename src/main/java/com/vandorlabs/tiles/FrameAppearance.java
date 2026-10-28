package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;

/** Independent frame finish selection; absent fields retain previous-release artwork. */
public final class FrameAppearance {
    public int front=-1, interior=-1;
    public boolean tile,displayFit,split;
    public int splitTop=-1,splitBottom=-1;
    public static boolean valid(int choice){return choice==-1 || ScreenHousingTextures.validChoice(choice) && "Frame".equals(ScreenHousingTextures.category(choice));}
    public void write(NBTTagCompound tag){tag.setInteger("FrameFront",front);tag.setInteger("FrameInterior",interior);tag.setBoolean("FrameTile",tile);tag.setBoolean("DisplayFit",displayFit);tag.setBoolean("DisplaySplit",split);tag.setInteger("DisplaySplitTop",splitTop);tag.setInteger("DisplaySplitBottom",splitBottom);}
    public void read(NBTTagCompound tag){front=tag.hasKey("FrameFront",3)&&valid(tag.getInteger("FrameFront"))?tag.getInteger("FrameFront"):-1;interior=tag.hasKey("FrameInterior",3)&&valid(tag.getInteger("FrameInterior"))?tag.getInteger("FrameInterior"):-1;tile=tag.getBoolean("FrameTile");displayFit=tag.getBoolean("DisplayFit");split=tag.getBoolean("DisplaySplit");splitTop=tag.hasKey("DisplaySplitTop",3)?tag.getInteger("DisplaySplitTop"):-1;splitBottom=tag.hasKey("DisplaySplitBottom",3)?tag.getInteger("DisplaySplitBottom"):-1;if(splitTop!=-1&&!ScreenHousingTextures.validChoice(splitTop))splitTop=-1;if(splitBottom!=-1&&!ScreenHousingTextures.validChoice(splitBottom))splitBottom=-1;}
    public static String bare(String id){
        String result=id.replace("_framed_","_bare_");if(result.startsWith("framed_"))result="bare_"+result.substring(7);return result;
    }
    public static boolean framed(String id){return id!=null&&(id.contains("_framed_")||id.startsWith("framed_"));}
    public static int bareChoice(int choice){if(choice<0)return choice;com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);if(e==null)return choice;String source=e.get("source").getAsString();String next=bare(source);if(next.equals(source))return choice;int result=ScreenHousingTextures.screenIndex(next);return result==0?choice:result;}
    public void migrateLegacyFrame(){int choice=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");if(choice>0)front=choice;}
    public String rail(String fallback){if(front<0)return fallback;com.google.gson.JsonObject e=ScreenHousingTextures.entry(front);return e!=null&&e.has("frameRail")?"vandorlabs:blocks/"+e.get("frameRail").getAsString():front(fallback);}
    public String front(String fallback){return front<0?fallback:ScreenHousingTextures.texture(front);}
    public String interior(String fallback){return interior<0?fallback:ScreenHousingTextures.texture(interior);}
    public int light(){if(front<0)return 0;com.google.gson.JsonObject e=ScreenHousingTextures.entry(front);return e!=null&&e.has("frameLight")?Math.max(0,Math.min(5,e.get("frameLight").getAsInt())):0;}
}
