package com.vandorlabs.copier;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Region edits and chest access wait until copied inventories have a verified durable owner. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class CopyEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.WorldTickEvent event){if(event.world.isRemote)return;WorldServer world=(WorldServer)event.world;if(event.phase==TickEvent.Phase.END)CopyTransactions.tick(world);else{CopyTransactions.beforeTick(world);CopyTransactions.recover(world);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent event){if(CopyTransactions.locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void placing(BlockEvent.PlaceEvent event){if(CopyTransactions.locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickBlock event){if(!event.getWorld().isRemote&&!(event.getWorld().getTileEntity(event.getPos()) instanceof CopyControllerTile)&&CopyTransactions.locked(event.getWorld(),event.getPos())){event.setCanceled(true);event.getEntityPlayer().sendStatusMessage(new net.minecraft.util.text.TextComponentString("Copy area is locked until its save checkpoint finishes"),true);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void load(WorldEvent.Load event){if(event.getWorld() instanceof WorldServer)CopyTransactions.load((WorldServer)event.getWorld());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void chunk(ChunkEvent.Load event){CopyTransactions.chunkLoaded(event.getChunk());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void explosion(net.minecraftforge.event.world.ExplosionEvent.Detonate event){event.getAffectedBlocks().removeIf(p->CopyTransactions.locked(event.getWorld(),p));}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){if(event.getWorld() instanceof WorldServer)CopyTransactions.unload((WorldServer)event.getWorld());}
}
