package com.vandorlabs.vehicle;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.*;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Pending ownership is protected from chunk/entity load through its verified checkpoint. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class VehicleCouplingEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void load(WorldEvent.Load event){if(event.getWorld() instanceof WorldServer)VehicleCouplingTransfer.load((WorldServer)event.getWorld());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void chunk(ChunkEvent.Load event){VehicleCouplingTransfer.chunkLoaded(event.getChunk());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void entity(EntityJoinWorldEvent event){if(event.getEntity() instanceof EntityGroundVehicle&&VehicleCouplingTransfer.joining((EntityGroundVehicle)event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.WorldTickEvent event){if(!(event.world instanceof WorldServer))return;WorldServer world=(WorldServer)event.world;if(event.phase==TickEvent.Phase.START){VehicleCouplingTransfer.beforeTick(world);VehicleCouplingTransfer.recover(world);}else VehicleCouplingTransfer.tick(world);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent event){if(VehicleCouplingTransfer.locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void placing(BlockEvent.PlaceEvent event){if(VehicleCouplingTransfer.locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickBlock event){if(!event.getWorld().isRemote&&VehicleCouplingTransfer.locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void explosion(ExplosionEvent.Detonate event){event.getAffectedBlocks().removeIf(pos->VehicleCouplingTransfer.locked(event.getWorld(),pos));}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){if(event.getWorld() instanceof WorldServer)VehicleCouplingTransfer.unload((WorldServer)event.getWorld());}
    private VehicleCouplingEvents(){}
}
