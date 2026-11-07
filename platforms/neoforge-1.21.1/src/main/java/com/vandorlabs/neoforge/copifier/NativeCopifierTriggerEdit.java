package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.Arrays;
import net.minecraft.nbt.*;

/** Trigger-only validation followed by the shared frame/channel/transmission metadata policy. */
public final class NativeCopifierTriggerEdit {
    public record Plan(boolean applicable,int off,TriggerState trigger,NativeCopifierHousingEdit.Plan metadata) {
        public CompoundTag capture(){return NativeCopifierTriggerEdit.capture(off,trigger,metadata);}
    }
    private static int number(CompoundTag tag,String key,int fallback){return tag.contains(key,Tag.TAG_INT)?tag.getInt(key):fallback;}
    private static boolean flag(CompoundTag tag,String key,boolean fallback){return tag.contains(key,Tag.TAG_BYTE)?tag.getBoolean(key):fallback;}
    private static int[] fill(int value){var array=new int[4];Arrays.fill(array,value);return array;}
    private static NativeCopifierHousingEdit.Plan metadata(CompoundTag current,CompoundTag values){var tail=new CompoundTag();for(String key:new String[]{"FrameAppearance","redstone_channel","Transmission"})if(values.contains(key))tail.put(key,values.get(key).copy());return NativeCopifierHousingEdit.prepare(NativeCopifierHousingCapture.Family.CUBE,current,tail);}
    public static Plan prepare(CompoundTag current,CompoundTag values){
        if(values==null)values=new CompoundTag();if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Invalid copied Trigger settings");
        var base=metadata(current,new CompoundTag());var old=TriggerState.read(new ModernHousingTags(current),ModernFinishCatalog::storedChoice);int off=base.appearance().housing().mainTexture;var next=old;
        boolean branch=values.contains("signal_settings",Tag.TAG_COMPOUND)||values.contains("wall_texture")||values.contains("trigger_on_texture")||values.contains("redstone_channel");
        if(branch){var signal=values.getCompound("signal_settings");int on=number(values,"trigger_on_texture",old.on),low=number(signal,"Low",old.low),medium=number(signal,"Medium",old.medium),sides=number(signal,"Sides",old.sides),exact=number(signal,"Exact",old.exact);off=number(values,"wall_texture",off);int[] modes=signal.contains("Modes",Tag.TAG_INT_ARRAY)?signal.getIntArray("Modes"):fill(ScreenBehavior.STATIC),speeds=signal.contains("Speeds",Tag.TAG_INT_ARRAY)?signal.getIntArray("Speeds"):fill(1);if(exact< -1||exact>15||!TriggerState.validArtwork(modes,speeds))throw new IllegalArgumentException("Invalid Trigger artwork");for(int choice:new int[]{off,on,low,medium,sides})if(!ModernFinishCatalog.validChoice(choice))throw new IllegalArgumentException("Invalid Trigger finish");next=new TriggerState(on,flag(signal,"States",old.bands),exact,low,medium,flag(signal,"FrontOnly",old.frontOnly),sides,modes,speeds);off=ModernFinishCatalog.storedChoice(off);}
        var tail=metadata(current,values);return new Plan(branch||tail.applicable(),off,next,tail);
    }
    public static CompoundTag capture(CompoundTag current){var base=metadata(current,new CompoundTag());return capture(base.appearance().housing().mainTexture,TriggerState.read(new ModernHousingTags(current),ModernFinishCatalog::storedChoice),base);}
    private static CompoundTag capture(int off,TriggerState trigger,NativeCopifierHousingEdit.Plan metadata){var out=new CompoundTag();out.putInt("wall_texture",off);out.putInt("trigger_on_texture",trigger.on);var signal=new CompoundTag();signal.putBoolean("FrontOnly",trigger.frontOnly);signal.putInt("Sides",trigger.sides);signal.putBoolean("States",trigger.bands);signal.putInt("Exact",trigger.exact);signal.putInt("Low",trigger.low);signal.putInt("Medium",trigger.medium);signal.putIntArray("Modes",fill(ScreenBehavior.STATIC));signal.putIntArray("Speeds",fill(1));out.put("signal_settings",signal);ChannelList channels=metadata.channels();if(channels.size()<=1)out.putInt("redstone_channel",channels.first());else out.putIntArray("redstone_channel",channels.toArray());var frames=new CompoundTag();metadata.frames().write(new ModernHousingTags(frames));out.put("FrameAppearance",frames);var tx=new CompoundTag();tx.putIntArray("Channels",metadata.transmittedChannels().toArray());tx.putInt("Range",metadata.transmissionRange());tx.putBoolean("Local",metadata.localRedstone());out.put("Transmission",tx);return out;}
    private NativeCopifierTriggerEdit(){}
}
