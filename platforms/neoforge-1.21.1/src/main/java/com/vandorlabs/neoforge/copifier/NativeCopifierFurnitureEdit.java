package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.plate.NativePlateEntity;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;

/** Source-compatible static furniture settings; physical sensing/runtime resources are never copied. */
public final class NativeCopifierFurnitureEdit {
    public record PlatePlan(ChannelList channels,int range,boolean local,int rotation,boolean applicable){}
    public static CompoundTag capture(NativePlateEntity tile){
        var out=new CompoundTag();var channels=tile.getRedstoneChannels();if(channels.size()<=1)out.putInt("redstone_channel",channels.first());else out.putIntArray("redstone_channel",channels.toArray());
        var transmission=new CompoundTag();transmission.putIntArray("Channels",channels.toArray());transmission.putInt("Range",tile.transmissionRange());transmission.putBoolean("Local",tile.localRedstone());out.put("Transmission",transmission);
        out.putInt("switch_rotation",tile.mountRotation());out.putBoolean("active",tile.output()>0);return out;
    }
    public static PlatePlan prepare(NativePlateEntity tile,CompoundTag values){
        if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Invalid copied furniture");
        var channels=tile.getRedstoneChannels();int range=tile.transmissionRange(),rotation=tile.mountRotation();boolean local=tile.localRedstone(),applicable=false;
        if(values.contains("redstone_channel")){if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))channels=ChannelList.of(values.getIntArray("redstone_channel"));else if(values.contains("redstone_channel",Tag.TAG_INT))channels=ChannelList.of(Math.max(0,values.getInt("redstone_channel")));else throw new IllegalArgumentException("Invalid plate channels");applicable=true;}
        if(values.contains("Transmission")){if(!values.contains("Transmission",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid transmission");var tag=values.getCompound("Transmission");channels=ChannelList.of(tag.getIntArray("Channels"));range=tag.getInt("Range");local=tag.getBoolean("Local");applicable=true;}
        if(values.contains("switch_rotation")){if(!values.contains("switch_rotation",Tag.TAG_INT))throw new IllegalArgumentException("Invalid stored plate rotation");rotation=Math.floorMod(values.getInt("switch_rotation"),4);applicable=true;}
        // Source exposes this generic source flag, but it cannot replace the plate's derived physical output.
        if(values.contains("active")){if(!values.contains("active",Tag.TAG_BYTE))throw new IllegalArgumentException("Invalid source flag");applicable=true;}
        if(!TransmissionSettings.validConfiguration(channels,range))throw new IllegalArgumentException("Invalid plate transmission");
        return new PlatePlan(channels,range,local,rotation,applicable);
    }
    public static boolean apply(NativePlateEntity tile,PlatePlan plan){return plan.applicable&&tile.configureCopiedTransmission(plan.channels,plan.range,plan.local,plan.rotation);}
    private NativeCopifierFurnitureEdit(){}
}
