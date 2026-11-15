package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Planned drops remain unusable until their controller and native actor records are durable. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearDropEscrow {
    static final String KEY="VandorLabsGearEscrow";
    private static final Set<ItemEntity> MARKED=Collections.newSetFromMap(new WeakHashMap<>());
    static void prepare(ItemEntity item,UUID operation,BlockPos root){
        var tag=new CompoundTag();tag.putUUID("Operation",operation);tag.putLong("Root",root.asLong());item.getPersistentData().put(KEY,tag);MARKED.add(item);freeze(item);
    }
    private static void freeze(ItemEntity item){item.setPickUpDelay(32767);item.setNoGravity(true);item.setInvulnerable(true);item.setDeltaMovement(0,0,0);}
    static UUID operation(ItemEntity item){var tag=item.getPersistentData().getCompound(KEY);if(!tag.getAllKeys().equals(Set.of("Operation","Root"))||!tag.hasUUID("Operation")||!tag.contains("Root",Tag.TAG_LONG))throw new IllegalArgumentException("Malformed arm drop escrow");return tag.getUUID("Operation");}
    public static boolean held(ItemEntity item){
        if(!(item.level() instanceof ServerLevel level)||!MARKED.contains(item))return false;
        final UUID operation;try{operation=operation(item);}catch(IllegalArgumentException unsupported){freeze(item);return true;}if(NativeGearJournal.escrowAdmission(level)!=NativeGearJournal.Status.READY||NativeGearJournal.escrowOperation(level,operation)){freeze(item);return true;}
        // The saved marker makes this release idempotent after a crash immediately following journal deletion.
        item.getPersistentData().remove(KEY);MARKED.remove(item);item.setPickUpDelay(10);item.setNoGravity(false);item.setInvulnerable(false);return false;
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){if(!NativeGearBindings.enabled()||!(event.getLevel() instanceof ServerLevel)||!(event.getEntity() instanceof ItemEntity item)||!item.getPersistentData().contains(KEY))return;MARKED.add(item);freeze(item);}
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event){if(event.getEntity() instanceof ItemEntity item&&held(item))event.setCanceled(true);}
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Pre event){if(held(event.getItemEntity()))event.setCanPickup(TriState.FALSE);}
    private NativeGearDropEscrow(){}
}
