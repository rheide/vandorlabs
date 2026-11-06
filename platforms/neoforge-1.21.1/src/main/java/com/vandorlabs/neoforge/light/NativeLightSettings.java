package com.vandorlabs.neoforge.light;

import com.vandorlabs.persistence.LightData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.ModernHousingTags;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import net.minecraft.nbt.CompoundTag;

/** Light-owned fields only; hosts compose the existing housing, channel, frame and transmission adapters. */
public record NativeLightSettings(LightData light) {
    public NativeLightSettings {Objects.requireNonNull(light);}
    public static NativeLightSettings fresh(){return new NativeLightSettings(LightData.fresh());}
    /** Channels are already decoded by the shared channel policy; no standalone Screen defaults are installed. */
    public static NativeLightSettings read(CompoundTag root,ChannelList channels,IntUnaryOperator material){return new NativeLightSettings(LightData.read(new ModernHousingTags(root),Objects.requireNonNull(channels).first(),material));}
    public void write(CompoundTag root){light.write(new ModernHousingTags(root));}
    /** Only Light choices; shared item adapters add housing/frame/channel choices independently. */
    public CompoundTag itemFields(){var result=new CompoundTag();write(result);result.remove("JoinedSignalLevel");result.remove("LightGroupPowered");return result;}
    public static final class Saved {
        private final CompoundTag original;
        public final NativeLightSettings settings;
        public Saved(CompoundTag root,ChannelList channels,IntUnaryOperator material){original=root.copy();settings=read(original,channels,material);}
        public CompoundTag unchanged(){return original.copy();}
        public CompoundTag edited(NativeLightSettings next){var result=original.copy();next.write(result);return result;}
    }
}
