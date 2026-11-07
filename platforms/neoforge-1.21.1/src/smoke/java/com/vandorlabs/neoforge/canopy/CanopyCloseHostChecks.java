package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.ramp.NativeRampReservations;
import java.util.Set;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Actual reservation/block-change admission; moving Ramp physical decisions are covered by the independent corpus. */
final class CanopyCloseHostChecks {
    private static int checks;
    private static void need(boolean value,String text){checks++;if(!value)throw new AssertionError("Canopy close service "+text);}
    static void run(ServerLevel world){
        var pos=new BlockPos(8,120,8);var tag=new CompoundTag();tag.putLong("AnchorOffset",0);var owner=CanopyHostChecks.put(world,pos,"canopy",Direction.NORTH,tag);NativeCanopyLifecycle.prepare(owner);
        var initial=NativeCanopyCloseService.prepare(owner);need(initial==NativeCanopyControl.Close.READY,"actual loaded-empty Ramp index permits ordinary close status="+initial+" available="+owner.available()+" installed="+NativeCanopyLifecycle.installed(owner)+" publication="+owner.publication()+" links="+owner.links());need(NativeCanopySweeps.query(owner).readiness()==NativeRampReservations.Status.PENDING,"empty Ramp path did not eagerly build a sweep");
        var cells=NativeCanopyFootprint.cells(owner.kind(),pos,owner.facing(),owner.settings(),owner.links());need(cells.size()>1,"real opening helper footprint");var blocked=cells.get(1);world.setBlock(blocked,Blocks.STONE.defaultBlockState(),3);owner.stageLinks(owner.links().withReleased(Set.of(1)));
        need(NativeCanopyCloseService.prepare(owner)==NativeCanopyControl.Close.PENDING,"foreign reservation blocks close without gameplay refusal");owner.control.retry=false;world.setBlock(blocked,Blocks.AIR.defaultBlockState(),3);NativeCanopyCloseService.tick(new LevelTickEvent.Post(()->true,world));need(owner.control.retry,"actual block notification queues close retry");need(NativeCanopyCloseService.prepare(owner)==NativeCanopyControl.Close.READY,"released reservation restores after notification");need(owner.links().released().isEmpty(),"restoration retires released marker");need(NativeCanopyLifecycle.at(world,blocked)!=null,"restored helper installed before admission");
        // The Ramp listener is a queue-only retry bridge, never a command to retract a Ramp.
        NativeCanopyLifecycle.at(world,blocked).removing(true);world.setBlock(blocked,Blocks.STONE.defaultBlockState(),3);owner.stageLinks(owner.links().withReleased(Set.of(1)));need(NativeCanopyCloseService.prepare(owner)==NativeCanopyControl.Close.PENDING,"second blocked admission");owner.control.retry=false;var callback=NativeRampReservations.bindChanged(null);try{need(callback!=null,"real Ramp changed callback bound");callback.accept(world,blocked);need(!owner.control.retry,"Ramp callback only queues work");NativeCanopyCloseService.tick(new LevelTickEvent.Post(()->true,world));need(owner.control.retry,"Ramp completion retry drains on world thread");}finally{NativeRampReservations.bindChanged(callback);}
        NativeCanopyCloseService.retire(owner);owner.removing(true);for(var cell:cells){var helper=NativeCanopyLifecycle.at(world,cell);if(helper!=null)helper.removing(true);world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);}System.out.println("[vandorlabs][canopy-host] actual close admission PASS checks="+checks);
    }
}
