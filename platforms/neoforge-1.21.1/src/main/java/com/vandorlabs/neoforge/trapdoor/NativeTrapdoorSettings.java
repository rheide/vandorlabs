package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.persistence.TrapdoorData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable choices; pairing, assembly ownership and redstone timelines belong to the native host. */
public record NativeTrapdoorSettings(TrapdoorData choices, ChannelList channels, int facing, int hingeSide,
                                    boolean rotationFlipped, boolean diagonal) {
    private static final String DEFAULT_ART="imported_trapdoors_cyan_lit_armored_sci_fi_hatch_4";
    public NativeTrapdoorSettings {
        if(choices==null || channels==null || facing<0 || facing>3 || hingeSide<0 || hingeSide>2)
            throw new IllegalArgumentException("Invalid trapdoor choices");
    }
    private static final class Defaults { static final int TEXTURE=resolveDefaultTexture(); }
    public static int defaultTexture(){return Defaults.TEXTURE;}
    private static int resolveDefaultTexture() {
        for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)
            if(ModernFinishCatalog.ENTRIES.get(i).id().equals(DEFAULT_ART))return ModernFinishCatalog.storedChoice(i);
        throw new IllegalStateException("Default trapdoor artwork missing");
    }
    public static NativeTrapdoorSettings read(CompoundTag tag, boolean diagonal) {
        var data=TrapdoorData.read(new ModernHousingTags(tag),diagonal,defaultTexture(),ModernFinishCatalog::storedChoice);
        ChannelList channels=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        if(tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(tag.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Retain the released scalar fallback. */}
        return new NativeTrapdoorSettings(data,channels,Math.abs(tag.getInt("TrapdoorCoverFacing")%4),
                diagonal?Math.max(0,Math.min(2,tag.getInt("DiagonalHingeSide"))):0,
                diagonal && tag.getBoolean("DiagonalRotationFlipped"),diagonal);
    }
    public CompoundTag item(boolean offsetEligible) {
        var tag=new CompoundTag();choices.write(new ModernHousingTags(tag));
        if(!diagonal && offsetEligible)tag.putInt("TrapdoorCoverFacing",facing);
        tag.putInt("RedstoneChannel",channels.first());tag.putIntArray("RedstoneChannels",channels.toArray());
        if(diagonal){tag.putInt("DiagonalHingeSide",hingeSide);tag.putBoolean("DiagonalRotationFlipped",rotationFlipped);
            tag.putBoolean("DiagonalHalfHeight",choices.position==2);tag.putBoolean("DiagonalFullWidth",choices.position==1);}
        return tag;
    }
    /** Saving without edits retains opaque fields and the original malformed tag types exactly. */
    public static final class Saved {
        private final CompoundTag retained;
        public final NativeTrapdoorSettings settings;
        public Saved(CompoundTag tag,boolean diagonal){retained=tag.copy();settings=read(tag,diagonal);}
        public CompoundTag unchanged(){return retained.copy();}
        public CompoundTag edited(NativeTrapdoorSettings next,boolean offsetEligible){
            if(next.diagonal()!=settings.diagonal())throw new IllegalArgumentException("Trapdoor family cannot change");
            CompoundTag tag=retained.copy();tag.merge(next.item(offsetEligible));return tag;
        }
    }
}
