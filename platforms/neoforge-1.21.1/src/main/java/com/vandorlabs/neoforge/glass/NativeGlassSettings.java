package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import com.vandorlabs.neoforge.shape.PanelPlayback;
import com.vandorlabs.persistence.*;
import net.minecraft.nbt.CompoundTag;

/** Ordinary glass owns its legacy-size contract; diagonal selector-panel glass keeps its existing adapter. */
public record NativeGlassSettings(ProgrammableGlassSettings glass,FrameSettings frames,PanelPlayback playback) {
    public NativeGlassSettings {java.util.Objects.requireNonNull(glass);java.util.Objects.requireNonNull(frames);java.util.Objects.requireNonNull(playback);}
    public static NativeGlassSettings read(CompoundTag tag){var tags=new ModernHousingTags(tag);return new NativeGlassSettings(ProgrammableGlassSettings.read(tags,value->value==-1||ModernFinishCatalog.validChoice(value)?value:-1),FrameSettings.read(tags,NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice),PanelPlayback.read(tag));}
    /** Resolve only after the installed block state is available; onLoad itself remains local-only. */
    public NativeGlassSettings resolveLegacySize(int metadataSize){if(!glass.legacySize)return this;var g=glass;return new NativeGlassSettings(new ProgrammableGlassSettings(g.shade,g.material,g.layout,g.stretch,g.tint,g.framed,Math.min(metadataSize,1),g.join,g.panelFacing,false),frames,playback);}
    public boolean same(NativeGlassSettings other){if(other==null)return false;var a=glass;var b=other.glass;var f=frames;var g=other.frames;return a.shade==b.shade&&a.material==b.material&&a.layout==b.layout&&a.stretch==b.stretch&&a.tint==b.tint&&a.framed==b.framed&&a.size==b.size&&a.join==b.join&&a.panelFacing==b.panelFacing&&a.legacySize==b.legacySize&&f.front==g.front&&f.interior==g.interior&&f.exterior==g.exterior&&f.artworkVersion==g.artworkVersion&&f.splitTop==g.splitTop&&f.splitBottom==g.splitBottom&&f.tile==g.tile&&f.displayFit==g.displayFit&&f.split==g.split&&f.bare==g.bare&&playback.equals(other.playback);}
    private static int light(int choice){if(choice<0)return 0;var frame=ModernFinishCatalog.option(choice).frame();return frame==null?0:Math.max(0,Math.min(5,frame.light()));}
    /** FrameBare suppresses the front's light independently of GlassFramed in older snapshots. */
    public int frameLight(){return Math.max(frames.bare?0:light(frames.front),Math.max(light(frames.interior),light(frames.exteriorChoice())));}
    public int intrinsicFrameLight(){return glass.framed?frameLight():0;}
    public int paneBlockLight(int ambient){return glass.material>=0&&glass.tint==3&&glass.framed&&frameLight()>0?240:ambient;}
    public void write(CompoundTag tag){var tags=new ModernHousingTags(tag);glass.write(tags);frames.write(tags);playback.write(tag);}
}
