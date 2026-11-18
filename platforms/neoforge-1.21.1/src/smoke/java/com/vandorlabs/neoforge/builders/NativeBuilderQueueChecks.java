package com.vandorlabs.neoforge.builders;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Actual pending mine requests under saving backpressure; no source or drop publication is allowed. */
final class NativeBuilderQueueChecks {
    static void run(ServerLevel world,ServerPlayer actor,BlockPos base)throws Exception {
        empty();boolean saving=world.noSave;var owners=new ArrayList<NativeBuilderEntity>();var before=new ArrayList<CompoundTag>();
        try {
            world.noSave=true;
            for(int i=0;i<65;i++){
                var pos=base.offset(i%9,0,i/9);need(world.isEmptyBlock(pos),"queue fixture requires empty loaded cells");
                world.setBlock(pos,NativeBuilderBindings.CORES.get("synthesis_tower").defaultBlockState(),3);
                var owner=(NativeBuilderEntity)world.getBlockEntity(pos);owners.add(owner);
                owner.setItem(3,com.vandorlabs.neoforge.credentials.NativeCredentialItems.dataChip(0));before.add(owner.saveWithFullMetadata(world.registryAccess()));
                need(NativeBuilderTransactions.mine(owner,actor,pos)==(i<64),"per-world pending job cap admits 64 then refuses without ownership transfer");
                need(owner.available()==(i==64),"only accepted queue entries freeze their original owner");
            }
            long retained=field("queuedBytes").getLong(null);need(retained>0&&field("queuedJobs").getInt(null)==64,"accepted requests account for retained snapshots");
            for(int retry=0;retry<4;retry++)NativeBuilderTransactions.tick(new LevelTickEvent.Post(()->true,world));
            need(field("queuedBytes").getLong(null)==retained&&field("queuedJobs").getInt(null)==64,"pending retry neither releases nor recounts retained requests");
            for(int i=0;i<owners.size();i++)need(world.getBlockEntity(owners.get(i).getBlockPos())==owners.get(i)&&before.get(i).equals(owners.get(i).saveWithFullMetadata(world.registryAccess())),"backpressure preserves exact source ownership "+i);
            NativeBuilderTransactions.unload(new LevelEvent.Unload(world));empty();
            for(var owner:owners)need(owner.available(),"unload releases local pending flags");
            var retry=owners.getLast();byteLimits(world,actor,retry);
            need(NativeBuilderTransactions.mine(retry,actor,retry.getBlockPos()),"capacity released on unload permits a fresh retry");
            need(field("queuedJobs").getInt(null)==1&&field("queuedBytes").getLong(null)>0,"retry is accounted exactly once");
            NativeBuilderTransactions.unload(new LevelEvent.Unload(world));empty();
        }finally{
            NativeBuilderTransactions.unload(new LevelEvent.Unload(world));world.noSave=saving;
            for(var owner:owners){owner.pending(false);owner.clearContent();world.setBlock(owner.getBlockPos(),Blocks.AIR.defaultBlockState(),3);}
        }
    }
    @SuppressWarnings("unchecked") private static void byteLimits(ServerLevel world,ServerPlayer actor,NativeBuilderEntity owner)throws Exception {
        var byWorld=(Map<ServerLevel,Long>)field("QUEUED_BYTES").get(null);var total=field("queuedBytes");var count=field("queuedJobs");var before=owner.saveWithFullMetadata(world.registryAccess());
        try {
            byWorld.put(world,64L*1024*1024);total.setLong(null,64L*1024*1024);
            need(!NativeBuilderTransactions.mine(owner,actor,owner.getBlockPos()),"world byte budget refuses another retained snapshot");
            byWorld.clear();total.setLong(null,128L*1024*1024);
            need(!NativeBuilderTransactions.mine(owner,actor,owner.getBlockPos()),"global byte budget refuses another retained snapshot");
            total.setLong(null,0);count.setInt(null,128);
            need(!NativeBuilderTransactions.mine(owner,actor,owner.getBlockPos()),"global job budget refuses another pending retry");
            need(owner.available()&&before.equals(owner.saveWithFullMetadata(world.registryAccess())),"all rejected admissions leave the source usable and exact");
        }finally{byWorld.clear();total.setLong(null,0);count.setInt(null,0);}
        empty();
    }
    static void empty()throws Exception {
        need(((Map<?,?>)field("QUEUED").get(null)).isEmpty()&&((Map<?,?>)field("QUEUED_BYTES").get(null)).isEmpty()&&field("queuedBytes").getLong(null)==0&&field("queuedJobs").getInt(null)==0,"completed/unloaded queue releases all retained capacity");
    }
    private static Field field(String name)throws Exception {var field=NativeBuilderTransactions.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void need(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private NativeBuilderQueueChecks(){}
}
