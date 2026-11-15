package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.neoforge.gear.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;

/** Source semantic Track style plus the existing arm clipboard; never copies physical ownership. */
public final class NativeTrackCopiedSettings {
    public record Plan(boolean applicable,NativeGearSettings settings,Integer style){}
    public static CompoundTag capture(NativeGearEntity owner){var values=NativeGearCopiedSettings.capture(owner.settings());int style=NativeTracks.style(owner);if(style>=0)values.getCompound("modular_arm_settings").putInt("TrackStyle",style);return values;}
    public static Plan prepare(NativeGearEntity owner,CompoundTag values){if(values==null||values.sizeInBytes()>256*1024||!com.vandorlabs.neoforge.copifier.NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Malformed Track clipboard");var sanitized=values.copy();Integer style=null;if(sanitized.contains("modular_arm_settings")){if(!sanitized.contains("modular_arm_settings",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Track clipboard type");var choices=sanitized.getCompound("modular_arm_settings");if(choices.contains("TrackStyle")){if(!choices.contains("TrackStyle",Tag.TAG_INT)||choices.getInt("TrackStyle")<0||choices.getInt("TrackStyle")>2)throw new IllegalArgumentException("Track style range");style=choices.getInt("TrackStyle");choices.remove("TrackStyle");}}var arm=NativeGearCopiedSettings.prepare(owner.settings(),sanitized);return new Plan(arm.applicable()||style!=null,arm.settings(),style);}
    public static boolean available(NativeGearEntity owner,ServerPlayer actor){if(owner instanceof NativeTrackEntity track&&track.controllerEntity()==null)return !track.inert()&&track.getLevel()==actor.level()&&actor.getServer().isSameThread()&&track.visual().owner()==null&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,actor.level(),track.getBlockPos());return owner.trackDescriptor()!=null&&NativeGearCopiedSettings.available(owner,actor);}
    public static boolean apply(NativeGearEntity owner,ServerPlayer actor,Plan plan){if(!plan.applicable()||!available(owner,actor)||owner.progress()!=1||plan.settings().pushBlocks()||plan.settings().carryLoad())return false;if(plan.style()!=null&&!NativeTracks.canStyle(actor,owner,plan.style()))return false;boolean configured=owner instanceof NativeTrackEntity track&&track.controllerEntity()==null?owner.configureStandaloneTrack(plan.settings()):owner.configure(plan.settings(),owner.target());if(!configured)return false;return plan.style()==null||NativeTracks.style(actor,owner,plan.style());}
    private NativeTrackCopiedSettings(){}
}
