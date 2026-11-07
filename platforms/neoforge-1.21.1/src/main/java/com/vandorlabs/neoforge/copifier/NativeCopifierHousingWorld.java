package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;

/** Server adapter. The caller separately authenticates the exact held Copifier session. */
public final class NativeCopifierHousingWorld {
    record Target(ProgrammableBlockEntity tile,NativeCopifierHousingCapture.Family family){}
    private static Target target(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face){return target(level,pos,actor,face,true);}
    static Target target(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face,boolean requireReach){
        if(!level.getServer().isSameThread()||actor==null||face==null||!(requireReach?NativeConfigurationAccess.canEditDialog(actor,level,pos,face):actor.isAlive()&&!actor.isSpectator()&&actor.level()==level&&level.hasChunkAt(pos)&&level.mayInteract(actor,pos)&&actor.mayUseItemAt(pos,face,actor.getMainHandItem())))return null;
        if(!(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity tile)||tile.isRemoved()||tile.getLevel()!=level||tile.getBlockState()!=level.getBlockState(pos))return null;
        var id=BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock());if(!id.getNamespace().equals("vandorlabs"))return null;
        try{return new Target(tile,NativeCopifierHousingCapture.Family.of(id.getPath()));}catch(IllegalArgumentException unsupported){return null;}
    }
    public static CompoundTag capture(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face){
        var target=target(level,pos,actor,face);if(target==null)return null;
        try{return NativeCopifierHousingCapture.capture(target.family,target.tile.saveWithoutMetadata(level.registryAccess()));}catch(IllegalArgumentException unsupported){return null;}
    }
    /** Single installed target only. Caller authenticates its held tool; connected/undo routes are separate. */
    public static boolean applySingle(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face,CompoundTag values){
        if(values==null||values.isEmpty())return false;var target=target(level,pos,actor,face);if(target==null)return false;
        return apply(level,pos,target,values);
    }
    static boolean apply(ServerLevel level,BlockPos pos,Target target,CompoundTag values){
        if(values==null||values.isEmpty())return false;
        var tile=target.tile;final NativeCopifierHousingEdit.Plan plan;
        try{plan=NativeCopifierHousingEdit.prepare(target.family,tile.saveWithoutMetadata(level.registryAccess()),values);}catch(IllegalArgumentException unsupported){return false;}
        if(!plan.applicable()||!plan.appearance().valid()||!com.vandorlabs.neoforge.shape.NativeWallEntity.valid(plan.frames())||!com.vandorlabs.neoforge.shape.NativeWallEntity.valid(plan.walls())||!com.vandorlabs.neoforge.shape.NativeWallEntity.validPanelGlass(plan.glass()))return false;
        // All target field validation completes before the existing installed-host setters publish.
        if(tile instanceof com.vandorlabs.neoforge.shape.NativeWallEntity wall){
            if(!wall.configure(plan.appearance(),plan.walls(),plan.frames()))return false;
            if((wall.isPorthole()||wall.isGlass())&&!wall.configurePanelGlass(plan.glass(),plan.playback(),plan.walls().joinPortholes,plan.walls().portholeShape))throw new IllegalStateException("Validated copied panel refused");
        }else if(tile instanceof com.vandorlabs.neoforge.shape.ProgrammableShapeEntity shape){if(!shape.applyShape(plan.appearance()))return false;}
        else tile.apply(plan.appearance().housing());
        if(!tile.configureCopiedMetadata(plan.frames(),plan.channels(),plan.transmissionRange(),plan.localRedstone(),plan.transmittedChannels()))throw new IllegalStateException("Validated copied metadata refused");
        level.sendBlockUpdated(pos,tile.getBlockState(),tile.getBlockState(),2);return true;
    }
    private NativeCopifierHousingWorld(){}
}
