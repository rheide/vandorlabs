package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.persistence.CanopySettingsData;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Owning-thread configuration admission and reversible footprint mutation. No renderer/world-worker access. */
public final class NativeCanopyConfiguration {
    private NativeCanopyConfiguration(){}
    public interface Access extends NativeCanopyReservations.Access {
        default boolean editable(NativeCanopyEntity owner,Player player,BlockPos pos){return loaded(owner,pos)&&(player==null||owner.getLevel().mayInteract(player,pos)&&player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem()));}
        default boolean replaceable(NativeCanopyEntity owner,BlockPos pos){return loaded(owner,pos)&&!owner.getLevel().isOutsideBuildHeight(pos)&&owner.getLevel().getWorldBorder().isWithinBounds(pos)&&(state(owner,pos).canBeReplaced()||shared(owner,pos));}
    }
    public static final Access WORLD=new Access(){};
    private static List<BlockPos> cells(NativeCanopyEntity owner){return NativeCanopyFootprint.cells(owner.kind(),owner.getBlockPos(),owner.facing(),owner.settings(),owner.links());}
    private static boolean own(Access access,NativeCanopyEntity owner,BlockPos cell){var member=access.entity(owner,cell);return member!=null&&access.state(owner,cell).is(owner.getBlockState().getBlock())&&owner.getBlockPos().equals(member.links().anchor());}
    private static boolean sameShape(CanopySettingsData a,CanopySettingsData b){return a.depth==b.depth&&a.scale==b.scale&&a.mount==b.mount&&a.hinge==b.hinge&&a.openable==b.openable&&a.motion==b.motion&&a.slideForward==b.slideForward&&a.angle==b.angle;}
    private static boolean legal(NativeCanopyEntity owner,NativeCanopySettings next){var a=owner.settings().data();var b=next.data();return b.valid()&&b.mount==a.mount&&(!owner.kind().equals("canopy")||b.angle<=2)&&(!owner.kind().equals("long_canopy")||b.angle<=3)&&(owner.links().progress()==0||b.motion==a.motion&&b.slideForward==a.slideForward&&b.angle==a.angle&&b.hinge==a.hinge&&b.openable==a.openable);}
    private static boolean paint(NativeCanopyEntity owner,Player player,NativeCanopySettings next,Access access,boolean joined){
        var members=new ArrayList<NativeCanopyEntity>();var columns=joined?NativeCanopyLifecycle.members(owner):List.of(owner);if(joined&&columns.size()!=owner.width()/NativeCanopyModels.builtin().dimensions(owner.kind()).width())return false;
        for(var column:columns)for(var cell:NativeCanopyFootprint.reserved(cells(column),column.links().released())){var member=access.entity(owner,cell);if(member==null||member.inert()||!access.editable(owner,player,cell)||(joined?NativeCanopyLifecycle.owner(member)!=owner:NativeCanopyLifecycle.constituent(member)!=owner))return false;members.add(member);}
        for(var member:members)member.stageSettings(next);for(var member:members)member.notifyChanged();NativeCanopyControl.settingsChanged(owner);return true;
    }
    public static boolean apply(NativeCanopyEntity owner,Player player,NativeCanopySettings next){return apply(owner,player,next,WORLD);}
    public static boolean apply(NativeCanopyEntity owner,Player player,NativeCanopySettings next,Access access){
        if(player!=null&&player.isSpectator())return false;
        if(!(owner.getLevel() instanceof ServerLevel server)||!server.getServer().isSameThread()||!owner.available()||!NativeCanopyLifecycle.installed(owner)||!owner.publication().draws()||!legal(owner,next))return false;
        if(!CanopySettingsData.supports(owner.kind()))return paint(owner,player,next,access,true);
        var currentCells=cells(owner);var old=new LinkedHashSet<>(currentCells);
        var desired=NativeCanopyFootprint.shell(owner.kind(),owner.getBlockPos(),owner.facing(),next,owner.links().centered());
        if(!owner.links().released().isEmpty()){
            if(sameShape(owner.settings().data(),next.data()))return paint(owner,player,next,access,false);
            for(var cell:old)if(!access.editable(owner,player,cell)||!owner.links().released().contains(currentCells.indexOf(cell))&&!own(access,owner,cell))return false;
            for(var cell:desired)if(!access.editable(owner,player,cell)||!old.contains(cell)&&!access.replaceable(owner,cell))return false;
            if(!NativeCanopyReservations.restoreFootprint(owner,access))return false;
        }
        for(var cell:old)if(!access.editable(owner,player,cell)||!own(access,owner,cell))return false;
        for(var cell:desired)if(!access.editable(owner,player,cell)||!old.contains(cell)&&!access.replaceable(owner,cell))return false;
        var affected=new LinkedHashSet<>(old);affected.addAll(desired);var before=new LinkedHashMap<BlockPos,BlockState>();var data=new HashMap<BlockPos,CompoundTag>();var originals=new HashMap<BlockPos,NativeCanopyEntity>();var staged=new HashMap<BlockPos,NativeCanopyEntity>();
        for(var cell:affected){before.put(cell,access.state(owner,cell));var member=access.entity(owner,cell);if(member!=null){originals.put(cell,member);data.put(cell,member.saveWithoutMetadata(server.registryAccess()));}}
        owner.removing(true);
        try{
            for(var cell:desired){if(!old.contains(cell)&&!access.write(owner,cell,owner.getBlockState()))throw new IllegalStateException("Canopy expansion refused");var member=access.entity(owner,cell);if(member==null)throw new IllegalStateException("Canopy expansion factory missing");if(old.contains(cell)){if(member!=originals.get(cell))throw new IllegalStateException("Canopy owner identity changed");}else{if(member.initialized())throw new IllegalStateException("Foreign canopy expansion owner");staged.put(cell,member);}member.configurationRole(owner);}
            for(var cell:old)if(!desired.contains(cell)){var member=access.entity(owner,cell);if(member!=originals.get(cell))throw new IllegalStateException("Canopy contraction owner changed");member.removing(true);if(!access.write(owner,cell,Blocks.AIR.defaultBlockState()))throw new IllegalStateException("Canopy contraction refused");}
            owner.configurationFootprint(desired);owner.stageSettings(next);for(var cell:desired){var member=access.entity(owner,cell);member.configurationSettings(next);}
            owner.removing(false);for(var cell:desired)access.entity(owner,cell).notifyChanged();NativeCanopyControl.settingsChanged(owner);server.updateNeighborsAt(owner.getBlockPos(),owner.getBlockState().getBlock());return true;
        }catch(RuntimeException failed){
            // Retain exact prior owner components; do not erase a foreign replacement during rollback.
            for(var cell:affected){var member=access.entity(owner,cell);if(member!=null&&(member==originals.get(cell)||member==staged.get(cell)))member.removing(true);}
            boolean restored=true;for(var entry:before.entrySet()){var cell=entry.getKey();try{var current=access.state(owner,cell);var currentOwner=access.entity(owner,cell);if(currentOwner!=null&&currentOwner!=originals.get(cell)&&currentOwner!=staged.get(cell)||!current.isAir()&&!current.is(owner.getBlockState().getBlock())&&current!=entry.getValue()){restored=false;continue;}access.write(owner,cell,entry.getValue());var member=access.entity(owner,cell);if(data.containsKey(cell)){if(member==null){restored=false;continue;}member.loadWithComponents(data.get(cell),server.registryAccess());member.removing(false);member.notifyChanged();}else if(access.state(owner,cell)!=entry.getValue())restored=false;}catch(RuntimeException rollback){restored=false;failed.addSuppressed(rollback);}}
            if(!restored){var retained=access.entity(owner,owner.getBlockPos());if(retained==owner)retained.retainConfigurationRecovery(data.get(owner.getBlockPos()));}
            com.mojang.logging.LogUtils.getLogger().warn("Canopy configuration rolled back{}",restored?"":" with retained recovery owner",failed);return false;
        }finally{owner.removing(false);}
    }
}
