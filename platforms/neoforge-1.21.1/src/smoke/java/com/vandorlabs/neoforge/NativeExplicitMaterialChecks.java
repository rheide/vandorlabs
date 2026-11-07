package com.vandorlabs.neoforge;
import com.vandorlabs.tiles.CustomMaterialIdentity;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Current-state coverage and explicit collisions use the actual production publication helper. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeExplicitMaterialChecks {
 private static int checks;private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Explicit materials: "+why);}
 private static void run(MinecraftServer server){var snapshot=NativeMaterialRegistry.snapshot();int current=0,accepted=0;for(var block:BuiltInRegistries.BLOCK){if(!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft"))continue;for(var state:block.getStateDefinition().getPossibleStates()){current++;var choice=snapshot.stateSamples().get(state);if(choice==null){need(snapshot.ambiguousStates().contains(state),"unsupported state explicitly marked");continue;}need(snapshot.states().get(choice)==state&&!snapshot.ambiguousStates().contains(state),"exact current state retained");if(state==block.defaultBlockState())need(choice.equals(snapshot.samples().get(block)),"prior default domain preserved");else need(!snapshot.reservedLegacyIds().contains(choice)&&!snapshot.samples().containsValue(choice),"prior saved/default domains protected");accepted++;}}
  need(current>20000&&accepted>=current-20,"bounded current vanilla-state coverage");need(snapshot.samples().get(Blocks.STONE_SLAB)==0x28f93e8c,"existing default-state golden");need(snapshot.states().get(770554706).is(Blocks.SMOOTH_STONE_SLAB),"legacy alias unchanged");
  var x=Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X);var z=x.setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z);need(snapshot.stateSamples().get(x)==0x3c622350&&snapshot.stateSamples().get(z)==0x2e5441d2,"property-specific literal domain goldens");
  var states=new HashMap<Integer,BlockState>();states.put(11,Blocks.STONE.defaultBlockState());var collision=new NativeMaterialRegistry.ExplicitStates(states,Map.of(Blocks.STONE,11),Set.of(7),Set.of(13));collision.add(x,7);collision.add(z,11);need(!states.containsKey(7)&&states.get(11)==Blocks.STONE.defaultBlockState()&&collision.samples.get(Blocks.STONE.defaultBlockState())==11,"reserved and earlier defaults never displaced");collision.add(x,13);need(!states.containsKey(13),"previously ambiguous domain stays unavailable");collision=new NativeMaterialRegistry.ExplicitStates(states,Map.of(Blocks.STONE,11),Set.of(7),Set.of());collision.add(x,19);collision.add(z,19);collision.add(Blocks.OAK_LOG.defaultBlockState(),19);need(!states.containsKey(19)&&!collision.samples.containsKey(x)&&!collision.samples.containsKey(z)&&collision.ambiguous.size()==3,"entire explicit collision group refused without a winner");
  System.out.println("[vandorlabs][explicit-material] PASS checks="+checks+" vanillaStates="+current+" accepted="+accepted+" ambiguous="+snapshot.ambiguousStates().size()+" old/new defaults unchanged");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!"explicit-material".equals(System.getProperty("vandorlabs.transmitterPhase")))return;try{run(event.getServer());}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][explicit-material] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
