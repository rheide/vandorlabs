package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;

/** Real connected target admission and mutation, including a target retained before a hold begins. */
public final class NativeCopifierHeldChecks {
    public static void run(ServerPlayer player,BlockPos pos,boolean allowed){
        var level=player.serverLevel();var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);var before=tile.settings();
        var target=NativeCopifierHousingWorld.installed(level,pos);if(target==null)throw new AssertionError("Copifier held fixture installed target");
        if((NativeCopifierHousingWorld.target(level,pos,player,Direction.NORTH,false)!=null)!=allowed)throw new AssertionError("Connected Copifier held admission");
        if(allowed&&com.vandorlabs.neoforge.ramp.NativeRampJournal.locked(level,pos))return;
        var saved=tile.saveWithoutMetadata(level.registryAccess());
        var values=target.capture();values.putInt("wall_texture",before.mainTexture==0?1:0);
        if(NativeCopifierHousingWorld.apply(level,pos,target,values,player)!=allowed)throw new AssertionError("Retained Copifier target held mutation");
        if(!allowed&&tile.settings()!=before)throw new AssertionError("Held Copifier changed appearance");
        if(allowed){if(tile.settings().mainTexture==before.mainTexture)throw new AssertionError("Unheld Copifier did not apply");tile.loadWithComponents(saved,level.registryAccess());}
    }
    private NativeCopifierHeldChecks(){}
}
