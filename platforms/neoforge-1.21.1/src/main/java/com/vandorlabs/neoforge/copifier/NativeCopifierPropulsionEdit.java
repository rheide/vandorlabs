package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.propulsion.NativeRedstoneLightSettings;
import com.vandorlabs.persistence.RedstoneLightData;
import com.vandorlabs.propulsion.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;

/** Placed fixture copier policy. Shape transaction and actor/claim checks stay in the installed adapter. */
public final class NativeCopifierPropulsionEdit {
    public record Plan(boolean applicable,PropulsionDefinition definition,NativeRedstoneLightSettings settings) {
        public CompoundTag capture(){return NativeCopifierPropulsionEdit.capture(definition,settings);}
    }
    public static CompoundTag capture(com.vandorlabs.neoforge.propulsion.NativePropulsionEntity owner){return capture(((com.vandorlabs.neoforge.propulsion.NativePropulsionBlock)owner.getBlockState().getBlock()).definition(),owner.settings());}
    public static CompoundTag capture(PropulsionDefinition definition,NativeRedstoneLightSettings settings){var data=settings.data();var out=new CompoundTag();out.putInt("wall_texture",data.sideTexture);out.putBoolean("join",data.join);out.putBoolean("active",data.manualMode()!=0);out.putInt("particles",data.particleLevel);var signal=new CompoundTag();signal.putBoolean("Brightness",data.analog);signal.putInt("Threshold",data.particleThreshold);signal.putBoolean("Inverted",data.inverted);out.put("signal_settings",signal);if(!definition.family.isEmpty())out.putInt("propulsion_shape",definition.shape);var channels=settings.channels();if(channels.size()<=1)out.putInt("redstone_channel",channels.first());else out.putIntArray("redstone_channel",channels.toArray());return out;}
    public static Plan prepare(PropulsionDefinition definition,int facing,NativeRedstoneLightSettings settings,CompoundTag values){
        if(values==null)values=new CompoundTag();if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Invalid copied placed-engine settings");boolean applicable=false;var target=definition;
        if(values.contains("propulsion_shape",Tag.TAG_INT)&&!definition.family.isEmpty()){int shape=values.getInt("propulsion_shape");if(shape<0||shape>2)throw new IllegalArgumentException("Invalid engine shape");if(shape!=definition.shape)target=PropulsionPlacement.select(PropulsionDefinition.of(definition.family),shape,facing,.25F,.25F,.25F);applicable=true;}
        var old=settings.data();int side=old.sideTexture,particles=old.particleLevel,threshold=old.particleThreshold;boolean join=old.join,analog=old.analog,inverted=old.inverted,manual=old.manualOn,initialized=old.initialized;
        if(values.contains("wall_texture",Tag.TAG_INT)){side=ModernFinishCatalog.storedChoice(values.getInt("wall_texture"));applicable=true;}
        if(values.contains("join",Tag.TAG_BYTE)&&target.maxConnected>0){join=values.getBoolean("join");applicable=true;}
        if(values.contains("signal_settings",Tag.TAG_COMPOUND)){var signal=values.getCompound("signal_settings");inverted=signal.getBoolean("Inverted");analog=signal.getBoolean("Brightness");threshold=Math.max(0,Math.min(15,signal.getInt("Threshold")));applicable=true;}
        if(values.contains("active",Tag.TAG_BYTE)||values.contains("particles",Tag.TAG_BYTE)||values.contains("particles",Tag.TAG_INT)){manual=values.contains("active",Tag.TAG_BYTE)?values.getBoolean("active"):old.manualMode()!=0;particles=values.contains("particles",Tag.TAG_INT)?Math.max(0,Math.min(3,values.getInt("particles"))):values.contains("particles",Tag.TAG_BYTE)?values.getBoolean("particles")?1:0:old.particleLevel;initialized=true;applicable=true;}
        ChannelList channels=settings.channels();if(values.contains("redstone_channel")){if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){/* Source keeps the current bank. */}else if(values.contains("redstone_channel",Tag.TAG_INT))channels=ChannelList.of(Math.max(0,values.getInt("redstone_channel")));applicable=true;}
        var next=new RedstoneLightData(channels.first(),old.channelSignal,old.channelLevel,manual,particles,initialized,inverted,analog,threshold,join,side);return new Plan(applicable,target,new NativeRedstoneLightSettings(next,channels));
    }
    private NativeCopifierPropulsionEdit(){}
}
