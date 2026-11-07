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
    public void write(CompoundTag tag){var tags=new ModernHousingTags(tag);glass.write(tags);frames.write(tags);playback.write(tag);}
}
