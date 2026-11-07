package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Helper ownership transaction only. Ramp sweep admission and close scheduling are separate. */
public final class NativeCanopyReservations {
    private NativeCanopyReservations(){}
    /** Mutation seams also allow actual-world refusal/missing-factory tests without replacing Level. */
    public interface Access {
        default boolean loaded(NativeCanopyEntity owner,BlockPos pos){var world=owner.getLevel();return world instanceof ServerLevel server&&server.getServer().isSameThread()&&server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
        default NativeCanopyEntity entity(NativeCanopyEntity owner,BlockPos pos){return loaded(owner,pos)?NativeCanopyLifecycle.at(owner.getLevel(),pos):null;}
        default BlockState state(NativeCanopyEntity owner,BlockPos pos){if(!loaded(owner,pos))throw new IllegalStateException("Cold canopy reservation read");return owner.getLevel().getBlockState(pos);}
        default boolean write(NativeCanopyEntity owner,BlockPos pos,BlockState state){return loaded(owner,pos)&&owner.getLevel().setBlock(pos,state,2);}
        /** Physical seat/cockpit adapters may retain their own cell; this never means replaceable material. */
        default boolean shared(NativeCanopyEntity owner,BlockPos pos){return false;}
    }
    public static final Access WORLD=new Access(){};
    private static List<BlockPos> cells(NativeCanopyEntity member){return NativeCanopyFootprint.cells(member.kind(),member.getBlockPos(),member.facing(),member.settings(),member.links());}
    private static boolean released(NativeCanopyEntity member,List<BlockPos> cells,BlockPos pos){return member.links().released().contains(cells.indexOf(pos));}
    private static void release(NativeCanopyEntity member,List<BlockPos> cells,BlockPos pos,boolean value){var indices=new HashSet<>(member.links().released());int index=cells.indexOf(pos);if(value)indices.add(index);else indices.remove(index);member.stageLinks(member.links().withReleased(indices));}
    private static boolean own(Access access,NativeCanopyEntity member,BlockPos pos){var helper=access.entity(member,pos);return helper!=null&&helper.getBlockState().is(member.getBlockState().getBlock())&&member.getBlockPos().equals(helper.links().anchor());}
    private static boolean available(Access access,NativeCanopyEntity member,BlockPos pos){return access.loaded(member,pos)&&(access.state(member,pos).isAir()||own(access,member,pos)||access.shared(member,pos));}
    private static boolean real(NativeCanopyEntity owner){return owner.getLevel() instanceof ServerLevel&&owner.available()&&NativeCanopyLifecycle.installed(owner);}
    public static void releaseOpen(NativeCanopyEntity owner,Access access){
        if(!real(owner)||!owner.publication().draws()||owner.links().progress()!=1||!owner.links().open()&&!owner.links().waiting())return;
        NativeCanopyLifecycle.prepare(owner);var geometry=owner.publication().geometry();if(geometry==null)return;
        for(var member:NativeCanopyLifecycle.members(owner)){var cells=cells(member);for(var cell:cells){
            if(cell.equals(member.getBlockPos())||released(member,cells,cell)||!access.loaded(member,cell)||!own(access,member,cell))continue;
            var volume=new AABB(cell).deflate(.00001);boolean intersects=false;for(var box:geometry.boxes())if(box.intersects(volume)){intersects=true;break;}if(intersects)continue;
            var helper=access.entity(member,cell);release(member,cells,cell,true);helper.removing(true);
            if(!access.write(member,cell,Blocks.AIR.defaultBlockState())){helper.removing(false);release(member,cells,cell,false);}
        }member.notifyChanged();}
    }
    /** Restores only AIR, exact owned helpers or an explicit shared housing; water/lava are refused. */
    public static boolean restoreFootprint(NativeCanopyEntity owner,Access access){
        if(!real(owner))return false;var members=NativeCanopyLifecycle.members(owner);
        for(var member:members){var cells=cells(member);for(var cell:cells)if(released(member,cells,cell)&&!available(access,member,cell))return false;}
        if(members.stream().noneMatch(member->!member.links().released().isEmpty()))return true;
        List<BlockPos> added=new ArrayList<>();List<NativeCanopyEntity> parents=new ArrayList<>();
        try{
            for(var member:members){var cells=cells(member);for(var cell:cells)if(released(member,cells,cell)&&!own(access,member,cell)&&!access.shared(member,cell)){
                if(!access.write(member,cell,member.getBlockState()))throw new IllegalStateException("Canopy helper placement refused");added.add(cell);parents.add(member);
                var helper=access.entity(member,cell);if(helper==null)throw new IllegalStateException("Canopy helper tile missing");helper.helperOf(member);helper.notifyChanged();
            }}
            for(var member:members){var cells=cells(member);for(var cell:cells)if(released(member,cells,cell)&&!access.shared(member,cell))release(member,cells,cell,false);member.notifyChanged();}return true;
        }catch(RuntimeException failed){
            for(int i=0;i<added.size();i++){var cell=added.get(i);var parent=parents.get(i);if(!access.loaded(parent,cell))continue;var helper=access.entity(parent,cell);
                if(access.state(parent,cell)==parent.getBlockState()&&(helper==null||parent.getBlockPos().equals(helper.links().anchor())||helper.links().anchor()==null&&!helper.links().savedFootprint())){if(helper!=null)helper.removing(true);access.write(parent,cell,Blocks.AIR.defaultBlockState());}
            }return false;
        }
    }
}
