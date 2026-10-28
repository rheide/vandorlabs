package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;

/** Independent frame finish selection; absent fields retain previous-release artwork. */
public final class FrameAppearance {
    public int front=-1, interior=-1;
    public int artworkVersion;
    public boolean tile,displayFit,split;
    public int splitTop=-1,splitBottom=-1;
    public static boolean valid(int choice){return choice==-1 || ScreenHousingTextures.validChoice(choice) && "Frame".equals(ScreenHousingTextures.category(choice));}
    public void write(NBTTagCompound tag){
        if(artworkVersion>0)tag.setInteger("DisplayArtworkVersion",artworkVersion);else tag.removeTag("DisplayArtworkVersion");
        if(artworkVersion>0)tag.setInteger("FrameFront",front);else optional(tag,"FrameFront",front);optional(tag,"FrameInterior",interior);optional(tag,"DisplaySplitTop",splitTop);optional(tag,"DisplaySplitBottom",splitBottom);
        flag(tag,"FrameTile",tile);flag(tag,"DisplayFit",displayFit);flag(tag,"DisplaySplit",split);
    }
    private static void optional(NBTTagCompound tag,String key,int value){if(value<0)tag.removeTag(key);else tag.setInteger(key,value);}
    private static void flag(NBTTagCompound tag,String key,boolean value){if(value)tag.setBoolean(key,true);else tag.removeTag(key);}

    public void read(NBTTagCompound tag){artworkVersion=tag.getInteger("DisplayArtworkVersion")==1?1:0;front=tag.hasKey("FrameFront",3)&&valid(tag.getInteger("FrameFront"))?tag.getInteger("FrameFront"):-1;interior=tag.hasKey("FrameInterior",3)&&valid(tag.getInteger("FrameInterior"))?tag.getInteger("FrameInterior"):-1;tile=tag.getBoolean("FrameTile");displayFit=tag.getBoolean("DisplayFit");split=tag.getBoolean("DisplaySplit");splitTop=tag.hasKey("DisplaySplitTop",3)?tag.getInteger("DisplaySplitTop"):-1;splitBottom=tag.hasKey("DisplaySplitBottom",3)?tag.getInteger("DisplaySplitBottom"):-1;if(splitTop!=-1&&!ScreenHousingTextures.validChoice(splitTop))splitTop=-1;if(splitBottom!=-1&&!ScreenHousingTextures.validChoice(splitBottom))splitBottom=-1;}
    public static boolean displayBlock(net.minecraft.block.Block block){
        return block.getClass()==com.vandorlabs.blocks.BlockAnimatedScreenSelector.class
            ||block instanceof com.vandorlabs.blocks.BlockProgrammableFullInput
            ||block instanceof com.vandorlabs.blocks.BlockProgrammableConsole
            ||block instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
    }
    /** The unpaired engineering image also contains a border; renderer samples its body only. */
    public static boolean initializeDisplay(net.minecraft.block.Block block,NBTTagCompound tag){
        if(!displayBlock(block))return false;
        if(tag.getInteger("DisplayArtworkVersion")>=1){if(tag.getInteger("DisplayArtworkVersion")==1&&!tag.hasKey("FrameFront",3)){tag.setInteger("FrameFront",-1);return true;}return false;}
        tag.setInteger("DisplayArtworkVersion",1);
        boolean overridden=tag.hasKey("PrimarySurfaceTexture",3)&&tag.getInteger("PrimarySurfaceTexture")!=-1;
        if(!overridden&&"engineering_screen".equals(tag.getString("selectedScreen"))&&!tag.hasKey("FrameFront",3)){
            int frame=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");
            if(frame<=0)throw new IllegalStateException("Missing default engineering frame");tag.setInteger("FrameFront",frame);tag.setBoolean("framed",false);
        }
        if(!tag.hasKey("FrameFront",3))tag.setInteger("FrameFront",-1);
        return true;
    }
    public static String bare(String id){
        String result=id.replace("_framed_","_bare_").replace("/framed_","/bare_");if(result.startsWith("framed_"))result="bare_"+result.substring(7);return result;
    }
    public static boolean framed(String id){return id!=null&&(id.contains("_framed_")||id.contains("/framed_")||id.startsWith("framed_"));}
    public static String bareInput(String id){String next=bare(id);return TileEntityAnimatedScreenSelector.isValidInputPanel(next)?next:id;}
    public static int bareChoice(int choice){if(choice<0||choice>=ScreenHousingTextures.BUILTIN_COUNT)return choice;com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);if(e==null)return choice;String source=e.get("source").getAsString();String next=bare(source);if(next.equals(source))return choice;int result=ScreenHousingTextures.screenIndex(next);return result==0?choice:result;}
    private static boolean framedChoice(int choice){if(choice<0||choice>=ScreenHousingTextures.BUILTIN_COUNT)return false;com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);return e!=null&&framed(e.get("source").getAsString());}
    /** Explicit artwork migration; the legacy framed flag alone also exists on plain blocks. */
    public static boolean needsLegacyScreen(NBTTagCompound tag){
        if(tag.hasKey("FrameFront",3))return false;
        String selected=tag.getString("selectedScreen");int primary=tag.hasKey("PrimarySurfaceTexture",3)?tag.getInteger("PrimarySurfaceTexture"):-1,second=tag.hasKey("SecondarySurfaceTexture",3)?tag.getInteger("SecondarySurfaceTexture"):-1;
        return framed(selected)||!"engineering_screen".equals(selected)&&com.vandorlabs.blocks.ModBlocks.DISPLAY_FRAMED_IDS.contains(selected)||framed(tag.getString("inputPanel"))||framed(tag.getString("secondaryInputPanel"))||framedChoice(primary)||framedChoice(second);
    }
    public static boolean migrateLegacyScreen(NBTTagCompound tag){
        if(!needsLegacyScreen(tag))return false;
        String selected=tag.getString("selectedScreen"),input=tag.getString("inputPanel"),secondary=tag.getString("secondaryInputPanel");
        int primary=tag.hasKey("PrimarySurfaceTexture",3)?tag.getInteger("PrimarySurfaceTexture"):-1,second=tag.hasKey("SecondarySurfaceTexture",3)?tag.getInteger("SecondarySurfaceTexture"):-1;
        boolean explicit=framed(selected)||framed(input)||framed(secondary)||framedChoice(primary)||framedChoice(second);
        if(com.vandorlabs.blocks.ModBlocks.DISPLAY_FRAMED_IDS.contains(selected))for(com.vandorlabs.blocks.ModBlocks.ScreenOption option:com.vandorlabs.blocks.ModBlocks.SCREEN_OPTIONS)if(option.hasPair()&&option.framedId.equals(selected)){selected=option.bareId;explicit=true;break;}
        if(!explicit)return false;
        int frame=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");if(frame<=0)throw new IllegalStateException("Missing legacy screen frame");
        tag.setInteger("FrameFront",frame);tag.setBoolean("framed",false);
        if(tag.hasKey("selectedScreen",8))tag.setString("selectedScreen",bare(selected));if(tag.hasKey("inputPanel",8))tag.setString("inputPanel",bareInput(input));if(tag.hasKey("secondaryInputPanel",8))tag.setString("secondaryInputPanel",bareInput(secondary));
        if(tag.hasKey("PrimarySurfaceTexture",3))tag.setInteger("PrimarySurfaceTexture",bareChoice(primary));if(tag.hasKey("SecondarySurfaceTexture",3))tag.setInteger("SecondarySurfaceTexture",bareChoice(second));return true;
    }
    public void migrateLegacyFrame(){int choice=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");if(choice>0)front=choice;}
    public static double aspect(int choice){com.google.gson.JsonObject e=choice<0?null:ScreenHousingTextures.entry(choice);return e!=null&&e.has("frameAspect")?Math.max(1,e.get("frameAspect").getAsDouble()):1;}
    public static boolean border(int choice){com.google.gson.JsonObject e=choice<0?null:ScreenHousingTextures.entry(choice);return e==null||!e.has("frameBorder")||e.get("frameBorder").getAsBoolean();}
    public String rail(String fallback){if(front<0)return fallback;com.google.gson.JsonObject e=ScreenHousingTextures.entry(front);return e!=null&&e.has("frameRail")&&border(front)?"vandorlabs:blocks/"+e.get("frameRail").getAsString():front(fallback);}
    public String front(String fallback){return front<0?fallback:ScreenHousingTextures.texture(front);}
    public static String interiorTexture(int choice,String fallback){if(choice<0)return fallback;com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);return e!=null&&e.has("frameRail")&&border(choice)?"vandorlabs:blocks/"+e.get("frameRail").getAsString():ScreenHousingTextures.texture(choice);}
    public String interior(String fallback){return interiorTexture(interior,fallback);}
    private static int light(int choice){if(choice<0)return 0;com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);return e!=null&&e.has("frameLight")?Math.max(0,Math.min(5,e.get("frameLight").getAsInt())):0;}
    public int light(){return Math.max(light(front),light(interior));}
}
