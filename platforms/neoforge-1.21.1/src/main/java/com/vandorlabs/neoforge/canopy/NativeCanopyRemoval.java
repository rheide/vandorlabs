package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Released constituent teardown: unpair before post-AIR harvest and erase only exact loaded helpers. */
public final class NativeCanopyRemoval {
    private NativeCanopyRemoval(){}
    public static void remove(NativeCanopyEntity hit,BlockState previous,BlockPos mined,java.util.function.BiPredicate<ServerLevel,BlockPos> erase){
        if(!(hit.getLevel() instanceof ServerLevel world)||!world.getServer().isSameThread()||hit.removing())return;
        // onRemove may run after the state palette changed; retain the actual removed facing/block.
        hit.setBlockState(previous);var base=NativeCanopyLifecycle.constituent(hit);if(base.removing()||base.links().anchor()!=null&&!base.links().anchor().equals(base.getBlockPos()))return;
        var rootPos=base.getBlockPos();var block=previous.getBlock();var snapshot=base.saveWithoutMetadata(world.registryAccess());
        if(base.inert())base.removing(true);else NativeCanopyLifecycle.unpairForRemoval(hit);
        var footprint=new LinkedHashSet<>(NativeCanopyFootprint.cells(base.kind(),rootPos,base.facing(),base.settings(),base.links()));
        var size=NativeCanopyModels.builtin().dimensions(base.kind());int radius=Math.max(8,size.width()+size.length()+size.height());
        for(int x=(rootPos.getX()-radius)>>4;x<=(rootPos.getX()+radius)>>4;x++)for(int z=(rootPos.getZ()-radius)>>4;z<=(rootPos.getZ()+radius)>>4;z++){
            var chunk=world.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;for(var raw:new ArrayList<>(chunk.getBlockEntities().values()))if(raw instanceof NativeCanopyEntity member&&member.getBlockState().is(block)&&rootPos.equals(member.links().anchor()))footprint.add(member.getBlockPos());
        }
        var owned=new LinkedHashMap<BlockPos,NativeCanopyEntity>();for(var cell:footprint){var member=NativeCanopyLifecycle.at(world,cell);if(member!=null&&member.getBlockState().is(block)&&rootPos.equals(member.links().anchor())){owned.put(cell,member);member.removing(true);}}
        for(var entry:owned.entrySet()){var cell=entry.getKey();if(!cell.equals(mined)&&NativeCanopyLifecycle.at(world,cell)==entry.getValue()&&world.getBlockState(cell).is(block))erase.test(world,cell);}
        // Refused helper deletion must not revive another usable owner after the mined item is emitted.
        NativeCanopyEntity recovery=null;for(var entry:owned.entrySet())if(!entry.getKey().equals(mined)&&NativeCanopyLifecycle.at(world,entry.getKey())==entry.getValue()&&world.getBlockState(entry.getKey()).is(block)){if(recovery==null){recovery=entry.getValue();recovery.retainConfigurationRecovery(snapshot);recovery.notifyChanged();}else {entry.getValue().uninitializedRemnant();entry.getValue().notifyChanged();}}
        if(recovery!=null)hit.suppressHarvest=true;
    }
}
