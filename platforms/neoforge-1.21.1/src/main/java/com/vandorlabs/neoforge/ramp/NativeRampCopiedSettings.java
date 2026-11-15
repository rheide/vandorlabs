package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Settings-only synchronous copier path. Pending physical transfers never masquerade as applied choices. */
public final class NativeRampCopiedSettings {
    public record Plan(NativeRampSettings before,NativeRampConfiguration.Plan choices,boolean geometry,boolean applicable){}
    public static CompoundTag capture(NativeRampEntity owner){return owner.settings().capture(owner.horizontalFacing().get3DDataValue());}
    public static Plan prepare(NativeRampEntity owner,CompoundTag values){
        var before=owner.settings();var choices=NativeRampConfiguration.copied(before,owner.horizontalFacing().get3DDataValue(),owner.scanFacing().get3DDataValue(),values,true,true,!owner.inert(),false);
        boolean geometry=choices.configurationRequested()&&choices.admission()!=null&&choices.admission().accepted();
        boolean ready=choices.valid()&&!owner.inert()&&owner.controller()!=null&&(!geometry||!owner.controller().attached());
        return new Plan(before,choices,geometry,ready);
    }
    public static boolean available(NativeRampEntity owner,ServerPlayer actor){return owner.getLevel()!=null&&!owner.inert()&&NativeConfigurationAccess.canEditDialog(actor,owner);}
    public static boolean apply(NativeRampEntity owner,ServerPlayer actor,Plan plan){
        if(!plan.applicable||!owner.settings().equals(plan.before)||!available(owner,actor)||owner.controller()==null||plan.geometry&&owner.controller().attached())return false;
        if(plan.geometry){var result=owner.controller().configure(actor,plan.choices.result());return result.admitted()&&result.choicesCommitted();}
        if(plan.choices.channelFooter())owner.copiedChannelFooter(plan.choices.result().channels());
        return true;
    }
    private NativeRampCopiedSettings(){}
}
