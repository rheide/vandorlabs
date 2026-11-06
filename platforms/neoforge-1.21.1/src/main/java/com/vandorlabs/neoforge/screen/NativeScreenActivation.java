package com.vandorlabs.neoforge.screen;
import com.vandorlabs.persistence.DisplayActivationData;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import net.minecraft.nbt.*;
/** World-free immutable screen activation. Runtime power and cosmetic clocks remain caller-owned. */
public record NativeScreenActivation(DisplayActivationData data) {
 public static final NativeScreenActivation LEGACY=new NativeScreenActivation(DisplayActivationData.LEGACY);
 public NativeScreenActivation{java.util.Objects.requireNonNull(data);}
 public static NativeScreenActivation read(CompoundTag root){CompoundTag tag=root.getCompound("DisplayActivation");DisplayActivationData value=DisplayActivationData.read(root.contains("DisplayActivation",Tag.TAG_COMPOUND),tag.getInt("Mode"),tag.getIntArray("Textures"),tag.getIntArray("Playback"),tag.getIntArray("Speeds"),ModernFinishCatalog::validChoice,ModernFinishCatalog::storedChoice);return value==DisplayActivationData.LEGACY?LEGACY:new NativeScreenActivation(value);}
 public NativeScreenActivation configure(int mode,int state,int texture,int playback,int speed){return new NativeScreenActivation(data.configure(mode,state,texture,playback,speed,ModernFinishCatalog::validChoice));}
 public NativeScreenActivation inheritUnset(int texture,int playback,int speed){DisplayActivationData next=data.inheritUnset(data.mode,texture,playback,speed);return next==data?this:new NativeScreenActivation(next);}
 public void write(CompoundTag root){if(data.mode<0){root.remove("DisplayActivation");return;}CompoundTag saved=root.getCompound("DisplayActivation").copy();saved.putInt("Mode",data.mode);saved.putIntArray("Textures",data.textures());saved.putIntArray("Playback",data.playback());saved.putIntArray("Speeds",data.speeds());root.put("DisplayActivation",saved);}
}
