package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;

/** Mount/output clipboard only. Credential/filter/return state is deliberately not represented. */
public final class NativeCopifierReaderEdit {
    public record Settings(ControlMountSettings mount,int rotation,ChannelList channels,ChannelList outputs,int range,boolean local,boolean active){
        public Settings{if(mount==null||!ControlMountSettings.valid(mount.height,mount.tilt,mount.direction)||channels==null||outputs==null||!TransmissionSettings.validRange(range))throw new IllegalArgumentException("Invalid reader clipboard settings");rotation=Math.floorMod(rotation,4);}
    }
    public record Plan(boolean applicable,Settings settings){public CompoundTag capture(){return NativeCopifierReaderEdit.capture(settings);}}
    /** The active scalar is supplied by the owning thread's existing reader policy, without copying its stack. */
    public static Settings read(CompoundTag fields,boolean active){var tags=new ModernHousingTags(fields);var source=RedstoneData.Source.read(tags);ChannelList channels=ChannelList.of(source.channel);if(fields.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(fields.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Released scalar fallback. */}var t=fields.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");return new Settings(ControlMountSettings.read(tags,0,0,0),source.mountRotation,channels,TransmissionSettings.readChannels(t.contains("Channels",Tag.TAG_INT_ARRAY),t.getIntArray("Channels"),channels),TransmissionSettings.readRange(t.contains("Range",Tag.TAG_INT),t.getInt("Range"),true),TransmissionSettings.readLocal(t.contains("Local",Tag.TAG_BYTE),t.getBoolean("Local"),true),active);}
    public static CompoundTag capture(Settings current){var out=new CompoundTag();var bank=current.channels;if(bank.size()<=1)out.putInt("redstone_channel",bank.first());else out.putIntArray("redstone_channel",bank.toArray());var t=new CompoundTag();t.putIntArray("Channels",current.outputs.toArray());t.putInt("Range",current.range);t.putBoolean("Local",current.local);out.put("Transmission",t);out.putBoolean("active",current.active);out.putInt("switch_rotation",current.rotation);var signal=new CompoundTag();signal.putIntArray("RedstoneChannels",bank.toArray());signal.putInt("Channel",bank.first());ControlMountSettings.write(new ModernHousingTags(signal),current.mount.height,current.mount.tilt,current.mount.direction);out.put("signal_settings",signal);return out;}
    public static Plan prepare(Settings current,CompoundTag values){if(values==null)values=new CompoundTag();if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Invalid copied reader settings");boolean applicable=false;var mount=current.mount;int rotation=current.rotation,range=current.range;boolean local=current.local;var channels=current.channels;var outputs=current.outputs;
        if(values.contains("signal_settings",Tag.TAG_COMPOUND)){mount=ControlMountSettings.read(new ModernHousingTags(values.getCompound("signal_settings")),mount.height,mount.tilt,mount.direction);applicable=true;}
        if(values.contains("active",Tag.TAG_BYTE))applicable=true; // Source reader overrides setLocalOn: refresh only.
        if(values.contains("switch_rotation",Tag.TAG_INT)){rotation=Math.floorMod(values.getInt("switch_rotation"),4);applicable=true;}
        if(values.contains("redstone_channel")){channels=values.contains("Transmission",Tag.TAG_COMPOUND)?ChannelList.of(values.getCompound("Transmission").getIntArray("Channels")):outputs;applicable=true;}
        if(values.contains("Transmission",Tag.TAG_COMPOUND)){var t=values.getCompound("Transmission");channels=outputs=ChannelList.of(t.getIntArray("Channels"));range=t.getInt("Range");local=t.getBoolean("Local");applicable=true;}
        return new Plan(applicable,new Settings(mount,rotation,channels,outputs,range,local,current.active));
    }
    private NativeCopifierReaderEdit(){}
}
