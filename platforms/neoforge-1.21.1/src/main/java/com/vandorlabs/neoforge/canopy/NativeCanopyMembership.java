package com.vandorlabs.neoforge.canopy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Loaded-only twenty-tick repair of explicitly owned standalone footprints. */
public final class NativeCanopyMembership {
    private NativeCanopyMembership(){}
    private static boolean loaded(ServerLevel world,BlockPos pos){return world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    private static NativeCanopyEntity member(NativeCanopyEntity owner,BlockPos pos){
        var found=NativeCanopyLifecycle.at(owner.getLevel(),pos);
        return found!=null&&found.getBlockState().is(owner.getBlockState().getBlock())&&owner.getLevel().getBlockState(pos).is(owner.getBlockState().getBlock())?found:null;
    }
    public static boolean repair(NativeCanopyEntity host){
        if(!(host.getLevel() instanceof ServerLevel world)||!world.getServer().isSameThread()||!host.available()||host.removing()||!NativeCanopyLifecycle.installed(host))return false;
        var links=host.links();var pos=host.getBlockPos();var anchor=links.anchor();
        if(anchor!=null&&!anchor.equals(pos)){
            if(!loaded(world,anchor))return false;
            var parent=member(host,anchor);
            // Opaque failed loads cannot establish that their retained ownership is absent.
            if(parent!=null&&(!parent.available()||parent.links().anchor()==null))return false;
            if(parent==null||!anchor.equals(parent.links().anchor())||!NativeCanopyFootprint.cells(parent.kind(),anchor,parent.facing(),parent.settings(),parent.links()).contains(pos)){
                host.removing(true);world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);return true;
            }
            return false;
        }
        var pair=links.pair();
        if(pair!=null){
            var owner=member(host,pair);boolean broken=loaded(world,pair)&&(owner==null||owner.available()&&(owner.facing()!=host.facing()||!pair.equals(owner.links().pair())));
            if(pair.equals(pos))for(int i=1;i<links.joinedWidth();i++){
                var cell=pos.relative(host.facing().getClockWise(),i);if(!loaded(world,cell))continue;var other=member(host,cell);
                if(other==null||other.available()&&(other.facing()!=host.facing()||!pos.equals(other.links().pair())))broken=true;
            }
            if(broken){var members=NativeCanopyLifecycle.members(host);for(var other:members)other.stageLinks(other.links().unpairedClosed());for(var other:members)other.notifyChanged();}
        }
        // Valid pre-ownership legacy roots remain accepted. An absent Anchor alone
        // must never authorize destruction or invention of another cell's owner.
        if(anchor==null&&!links.savedFootprint())return false;
        var cells=NativeCanopyFootprint.cells(host.kind(),pos,host.facing(),host.settings(),host.links());
        for(int i=0;i<cells.size();i++){
            var cell=cells.get(i);if(!host.links().released().isEmpty()&&host.links().released().contains(cells.indexOf(cell)))continue;if(!loaded(world,cell))continue;var other=member(host,cell);
            if(other!=null&&!other.available())continue;
            boolean rootLegacy=other==host&&anchor==null;
            if(other==null||!rootLegacy&&!pos.equals(other.links().anchor())){
                NativeCanopyRemoval.remove(host,host.getBlockState(),pos,(level,target)->level.setBlock(target,Blocks.AIR.defaultBlockState(),3));
                if(NativeCanopyLifecycle.at(world,pos)==host){host.removing(true);world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}return true;
            }
        }
        return false;
    }
}
