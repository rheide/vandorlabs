package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.RedstoneLightData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;

/** Inactive scalar adapter. Module/ownership installation belongs to the future host, not this value. */
public record NativeRedstoneLightSettings(RedstoneLightData data,ChannelList channels) {
    public NativeRedstoneLightSettings {if(data==null||channels==null||data.channel!=channels.first())throw new IllegalArgumentException("Redstone-light primary channel mismatch");}
    public static NativeRedstoneLightSettings read(CompoundTag root){var channels=ChannelList.of(Math.max(0,root.getInt("RedstoneChannel")));if(root.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(root.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Released malformed-list fallback retains scalar channel. */}return new NativeRedstoneLightSettings(RedstoneLightData.read(new ModernHousingTags(root),channels.first(),ModernFinishCatalog::storedChoice),channels);}
    public NativeRedstoneLightSettings capture(boolean powered){var next=data.capture(powered);return next==data?this:new NativeRedstoneLightSettings(next,channels);}
    public void write(CompoundTag target){data.write(new ModernHousingTags(target));target.putIntArray("RedstoneChannels",channels.toArray());}
    public static final class Saved {
        private final CompoundTag original;public final NativeRedstoneLightSettings settings;
        public Saved(CompoundTag root){original=root.copy();settings=read(root);}
        public CompoundTag unchanged(){return original.copy();}
        public CompoundTag edited(NativeRedstoneLightSettings next){var result=original.copy();next.write(result);return result;}
    }
}
