package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;

/** Pure selected-section choices; the installed lifecycle retains linked-door admission/publication. */
public final class NativeCopifierSegmentedEdit {
    public record State(NativeSegmentedDoorSettings settings,ChannelList channels,int ticks,boolean local,boolean opens,int texture){}
    public record Plan(boolean applicable,boolean configuration,State state){}
    public static State read(CompoundTag saved){
        var channels=ChannelList.of(Math.max(0,saved.getInt("RedstoneChannel")));
        if(saved.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(saved.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Released scalar fallback. */}
        int texture=saved.contains("SegmentedTexture",Tag.TAG_INT)&&NativeSegmentedDoorEntity.validTexture(saved.getInt("SegmentedTexture"))?saved.getInt("SegmentedTexture"):-1;
        return new State(NativeSegmentedDoorSettings.read(saved),channels,Math.max(5,Math.min(30,saved.contains("SegmentedSpeed")?saved.getInt("SegmentedSpeed"):10)),!saved.contains("SegmentedLocalRedstone")||saved.getBoolean("SegmentedLocalRedstone"),!saved.contains("SegmentedSignalOpens")||saved.getBoolean("SegmentedSignalOpens"),texture);
    }
    public static CompoundTag capture(State state){
        var out=new CompoundTag();var config=new CompoundTag();state.settings().write(config);config.putInt("SegmentedTexture",state.texture());config.putInt("SegmentedSpeed",state.ticks());config.putBoolean("SegmentedLocalRedstone",state.local());config.putBoolean("SegmentedSignalOpens",state.opens());config.putBoolean("SegmentedConfigured",true);out.put("segmented_door",config);
        if(state.channels().size()<=1)out.putInt("redstone_channel",state.channels().first());else out.putIntArray("redstone_channel",state.channels().toArray());return out;
    }
    public static Plan prepare(State old,CompoundTag values){
        if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Malformed copied Segmented Door settings");
        var channels=old.channels();if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){/* Source retains the current selection. */}else if(values.contains("redstone_channel",Tag.TAG_INT))channels=ChannelList.of(Math.max(0,values.getInt("redstone_channel")));
        if(values.contains("segmented_door",Tag.TAG_COMPOUND)){
            var config=values.getCompound("segmented_door");int texture=config.contains("SegmentedTexture",Tag.TAG_INT)&&NativeSegmentedDoorEntity.validTexture(config.getInt("SegmentedTexture"))?config.getInt("SegmentedTexture"):-1;
            return new Plan(true,true,new State(NativeSegmentedDoorSettings.read(config),channels,Math.max(5,Math.min(30,config.getInt("SegmentedSpeed"))),config.getBoolean("SegmentedLocalRedstone"),config.getBoolean("SegmentedSignalOpens"),texture));
        }
        return new Plan(values.contains("redstone_channel"),false,new State(old.settings(),channels,old.ticks(),old.local(),old.opens(),old.texture()));
    }
    /** Only motion/layout fields are shared with other sections by Source configurePart. */
    public static State peer(State old,Plan plan){var next=plan.state();return new State(old.settings().shared(next.settings()),next.channels(),next.ticks(),next.local(),next.opens(),next.texture());}
    private NativeCopifierSegmentedEdit(){}
}
