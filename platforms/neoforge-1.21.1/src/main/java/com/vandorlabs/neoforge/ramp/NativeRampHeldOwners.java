package com.vandorlabs.neoforge.ramp;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Frozen Ramp transaction cells, never a lock on merely prepared sources. */
public final class NativeRampHeldOwners {
 private record Hold(WeakReference<ServerLevel> world,NativeRampJournal.Operation operation,Set<BlockPos> positions,List<WeakReference<Container>> containers){}
 private static volatile List<Hold> holds=List.of();
 private static final ThreadLocal<NativeRampJournal.Operation> MUTATION=new ThreadLocal<>();
 static void freeze(ServerLevel world,NativeRampJournal.Operation operation,Collection<BlockPos> positions){
  if(!world.getServer().isSameThread())throw new IllegalStateException("Ramp freeze requires server thread");
  var next=new ArrayList<Hold>();var containers=new ArrayList<WeakReference<Container>>();
  for(var hold:holds)if(hold.world.get()!=null){if(hold.operation==operation)containers.addAll(hold.containers);else next.add(hold);}
  for(var pos:positions)if(world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&world.getBlockEntity(pos) instanceof Container container){boolean known=false;for(var ref:containers)if(ref.get()==container){known=true;break;}if(!known)containers.add(new WeakReference<>(container));}
  next.add(new Hold(new WeakReference<>(world),operation,Set.copyOf(positions),List.copyOf(containers)));holds=List.copyOf(next);
  for(var player:world.players())closeMenu(player);
 }
 static void closeMenu(net.minecraft.server.level.ServerPlayer player){for(var slot:player.containerMenu.slots)if(blocked(slot.container)){player.closeContainer();break;}}
 static boolean held(NativeRampJournal.Operation operation){for(var hold:holds)if(hold.world.get()!=null&&hold.operation==operation)return true;return false;}
 static void release(NativeRampJournal.Operation operation){holds=holds.stream().filter(hold->hold.world.get()!=null&&hold.operation!=operation).toList();}
 static void unload(ServerLevel world){holds=holds.stream().filter(hold->hold.world.get()!=null&&hold.world.get()!=world).toList();}
 public static boolean active(Level world){if(!(world instanceof ServerLevel)||holds.isEmpty())return false;for(var hold:holds)if(hold.world.get()==world)return true;return false;}
 public static boolean blocked(Level world,BlockPos pos){if(!(world instanceof ServerLevel)||holds.isEmpty())return false;var bypass=MUTATION.get();for(var hold:holds)if(hold.world.get()==world&&hold.operation!=bypass&&hold.positions.contains(pos))return true;return false;}
 public static boolean blocked(Container container){
  if(holds.isEmpty())return false;
  if(container instanceof BlockEntity tile&&tile.getLevel() instanceof ServerLevel world&&blocked(world,tile.getBlockPos()))return true;
  var bypass=MUTATION.get();for(var hold:holds)if(hold.world.get()!=null&&hold.operation!=bypass)for(var ref:hold.containers){var owner=ref.get();if(owner!=null&&(owner==container||container instanceof CompoundContainer compound&&compound.contains(owner)))return true;}return false;
 }
 static Scope mutate(NativeRampJournal.Operation operation){var previous=MUTATION.get();if(previous!=null&&previous!=operation)throw new IllegalStateException("Nested foreign Ramp mutation");MUTATION.set(operation);return new Scope(previous);}
 static final class Scope implements AutoCloseable {private final NativeRampJournal.Operation previous;private Scope(NativeRampJournal.Operation previous){this.previous=previous;}public void close(){if(previous==null)MUTATION.remove();else MUTATION.set(previous);}}
 private NativeRampHeldOwners(){}
}
