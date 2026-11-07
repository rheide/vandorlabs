package com.vandorlabs.neoforge.ramp;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
/** Gameplay entry guards run before ordinary inventory use and loot publication. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampHeldEvents {
 @SubscribeEvent public static void right(PlayerInteractEvent.RightClickBlock event){if(NativeRampHeldOwners.blocked(event.getLevel(),event.getPos()))event.setCanceled(true);}
 @SubscribeEvent public static void left(PlayerInteractEvent.LeftClickBlock event){if(NativeRampHeldOwners.blocked(event.getLevel(),event.getPos()))event.setCanceled(true);}
 @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){if(event.getLevel() instanceof Level world&&NativeRampHeldOwners.blocked(world,event.getPos()))event.setCanceled(true);}
 @SubscribeEvent public static void drops(BlockDropsEvent event){if(NativeRampHeldOwners.blocked(event.getLevel(),event.getPos()))event.setCanceled(true);}
 @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){if(!NativeRampHeldOwners.active(event.getLevel()))return;event.getAffectedBlocks().removeIf(pos->NativeRampHeldOwners.blocked(event.getLevel(),pos));}
 @SubscribeEvent public static void piston(PistonEvent.Pre event){if(NativeRampHeldPhysics.blocked(event))event.setCanceled(true);}
 private NativeRampHeldEvents(){}
}
