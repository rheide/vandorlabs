package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.render.TextureLayoutMode;
import net.minecraft.nbt.*;

/** Immutable user settings only. Reader inventories, ownership, fluids and motion timelines stay outside it. */
public record NativeDoorSettings(SpaceDoorData door,int faceTexture,int rawLayout,boolean tileTexture,int depth,
                                 int rawPanelMode,int securityMode,int securityAllowed,FrameSettings frames,NativeDoorGlass glass) {
    public static final String ORDINARY_ID="programmable_door",LARGE_ID="large_programmable_door",
            LEGACY_ROTATING_ID="space_rotating_door",LEGACY_SLIDING_ID="space_sliding_door";
    public static NativeDoorSettings fresh(boolean large,boolean slidingModel){
        var door=new SpaceDoorData(large?23:2,1,true,0,slidingModel,slidingModel,true,0,true);
        return new NativeDoorSettings(door,-1,-1,false,slidingModel?0:1,1,0,127,new FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false),NativeDoorGlass.DEFAULT);
    }
    public int textureLayout(){return rawLayout<0?(tileTexture?1:0):rawLayout;}
    public int panelMode(){return !door.panel?0:rawPanelMode==2&&depth==0?2:1;}
    public static int singleCompany(int mask){mask&=127;return (mask&1)!=0?127:Integer.lowestOneBit(mask);}
    public static int readerCompany(CompoundTag reader){return singleCompany(!reader.contains("AnyToken")||reader.getBoolean("AnyToken")?127:reader.contains("AllowedTokens")?reader.getInt("AllowedTokens"):127);}
    public static int securityBehavior(int mode){return mode==1?3:mode==3?1:mode==4?2:0;}
    public static boolean frame(int value){return value==-1||ModernFinishCatalog.validChoice(value)&&ModernFinishCatalog.option(value).category().equals("Frame");}
    public static boolean surface(int value){return frame(value)||ModernFinishCatalog.validChoice(value)&&ModernFinishCatalog.option(value).general();}
    public static NativeDoorSettings read(CompoundTag tag){
        var access=new ModernHousingTags(tag);var data=SpaceDoorData.read(access);
        int layout=tag.contains("DoorTextureLayout",Tag.TAG_INT)&&TextureLayoutMode.valid(tag.getInt("DoorTextureLayout"))?tag.getInt("DoorTextureLayout"):-1;
        int face=tag.contains("DoorFaceTexture",Tag.TAG_INT)&&tag.getInt("DoorFaceTexture")>=0?ModernFinishCatalog.storedChoice(tag.getInt("DoorFaceTexture")):-1;
        int depth=tag.contains("SpaceDoorPlacementDepth",Tag.TAG_INT)?Math.max(0,Math.min(2,tag.getInt("SpaceDoorPlacementDepth"))):data.middle?0:1;
        int panel=tag.contains("DoorPanelMode",Tag.TAG_INT)?Math.max(0,Math.min(2,tag.getInt("DoorPanelMode"))):data.panel?1:0;
        int mode=Math.max(0,Math.min(4,tag.getInt("DoorSecurityMode"))),allowed;
        if(tag.contains("DoorKeyReader",Tag.TAG_COMPOUND)){allowed=readerCompany(tag.getCompound("DoorKeyReader"));}
        else allowed=tag.contains("DoorSecurityAllowed",Tag.TAG_INT)?tag.getInt("DoorSecurityAllowed")&127:127;
        return new NativeDoorSettings(data,face,layout,tag.getBoolean("DoorTileTexture"),depth,panel,mode,singleCompany(allowed),FrameSettings.read(access,NativeDoorSettings::frame,NativeDoorSettings::surface,ModernFinishCatalog::validChoice),NativeDoorGlass.read(tag));
    }
    /** Writes choices only; intentionally never serializes retained runtime/reader state. */
    public void write(CompoundTag tag){var access=new ModernHousingTags(tag);door.write(access);frames.write(access);glass.write(tag);tag.putInt("DoorFaceTexture",faceTexture);tag.putBoolean("DoorTileTexture",tileTexture);tag.putInt("DoorTextureLayout",textureLayout());tag.putInt("SpaceDoorPlacementDepth",depth);tag.putInt("DoorPanelMode",panelMode());tag.putInt("DoorSecurityMode",securityMode);tag.putInt("DoorSecurityAllowed",securityAllowed);}
    public boolean same(NativeDoorSettings b){if(b==null)return false;var a=door;var d=b.door;var f=frames;var g=b.frames;return a.design==d.design&&a.detail==d.detail&&a.framed==d.framed&&a.direction==d.direction&&a.middle==d.middle&&a.sliding==d.sliding&&a.hinges==d.hinges&&a.trigger==d.trigger&&a.panel==d.panel&&faceTexture==b.faceTexture&&rawLayout==b.rawLayout&&tileTexture==b.tileTexture&&depth==b.depth&&rawPanelMode==b.rawPanelMode&&securityMode==b.securityMode&&securityAllowed==b.securityAllowed&&glass.same(b.glass)&&f.front==g.front&&f.interior==g.interior&&f.exterior==g.exterior&&f.artworkVersion==g.artworkVersion&&f.splitTop==g.splitTop&&f.splitBottom==g.splitBottom&&f.tile==g.tile&&f.displayFit==g.displayFit&&f.split==g.split&&f.bare==g.bare;}
}
