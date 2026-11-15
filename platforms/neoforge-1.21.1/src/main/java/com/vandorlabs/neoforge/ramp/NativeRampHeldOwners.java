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
 private record Hold(WeakReference<ServerLevel> world,NativeRampJournal.Operation operation,Set<BlockPos> positions,List<WeakReference<Container>> containers,Set<BlockPos> candidates,net.minecraft.world.level.block.Block partBlock){}
 private static volatile List<Hold> holds=List.of();
 private static final ThreadLocal<NativeRampJournal.Operation> MUTATION=new ThreadLocal<>();
 private static final ThreadLocal<WriteAdmission> WRITE=new ThreadLocal<>();
 private static final class WriteAdmission {final ServerLevel world;final NativeRampJournal.Operation operation;final BlockPos pos;boolean consumed;WriteAdmission(ServerLevel world,NativeRampJournal.Operation operation,BlockPos pos){this.world=world;this.operation=operation;this.pos=pos.immutable();}}
 /** The identity scope alone grants no world mutation. Admit exactly the immediate native setBlock call. */
 static boolean setBlock(ServerLevel world,BlockPos pos,net.minecraft.world.level.block.state.BlockState state,int flags){
  var operation=MUTATION.get();if(operation==null||!mutationAllows(world,operation.id(),operation.root(),pos)||WRITE.get()!=null)throw new IllegalStateException("Ramp write outside exact durable operation");
  WRITE.set(new WriteAdmission(world,operation,pos));try{return world.setBlock(pos,state,flags);}finally{WRITE.remove();}
 }
 /** Own-operation exclusion for read-only restoration preflight, independent of every mutation token. */
 static boolean foreignHold(Level world,BlockPos pos,NativeRampJournal.Operation own){
  if(com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(world,pos)||com.vandorlabs.neoforge.gear.NativeGearHolds.reserved(world,pos))return true;
  if(!(world instanceof ServerLevel server))return false;for(var hold:holds)if(hold.world.get()==world&&hold.operation!=own){if(hold.positions.contains(pos))return true;if(hold.candidates.contains(pos)&&NativeRampPartOwnership.read(server,pos,hold.partBlock,hold.operation.id(),hold.operation.root())!=NativeRampPartOwnership.State.OTHER)return true;}return false;
 }
 static void freeze(ServerLevel world,NativeRampJournal.Operation operation,Collection<BlockPos> positions){
  if(!world.getServer().isSameThread())throw new IllegalStateException("Ramp freeze requires server thread");
  var next=new ArrayList<Hold>();var containers=new ArrayList<WeakReference<Container>>();Set<BlockPos> candidates=Set.of();net.minecraft.world.level.block.Block partBlock=null;
  for(var hold:holds)if(hold.world.get()!=null){if(hold.operation==operation){containers.addAll(hold.containers);candidates=hold.candidates;partBlock=hold.partBlock;}else next.add(hold);}
  for(var pos:positions)if(world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&world.getBlockEntity(pos) instanceof Container container){boolean known=false;for(var ref:containers)if(ref.get()==container){known=true;break;}if(!known)containers.add(new WeakReference<>(container));}
  next.add(new Hold(new WeakReference<>(world),operation,Set.copyOf(positions),List.copyOf(containers),candidates,partBlock));holds=List.copyOf(next);
  for(var player:world.players())closeMenu(player);
 }
 static void closeMenu(net.minecraft.server.level.ServerPlayer player){for(var slot:player.containerMenu.slots)if(blocked(slot.container)){player.closeContainer();break;}}
 static boolean held(NativeRampJournal.Operation operation){for(var hold:holds)if(hold.world.get()!=null&&hold.operation==operation)return true;return false;}
 static void release(NativeRampJournal.Operation operation){holds=holds.stream().filter(hold->hold.world.get()!=null&&hold.operation!=operation).toList();}
 static void unload(ServerLevel world){holds=holds.stream().filter(hold->hold.world.get()!=null&&hold.world.get()!=world).toList();}
 public static boolean active(Level world){if(com.vandorlabs.neoforge.transfer.NativeTransferHolds.active(world))return true;if(com.vandorlabs.neoforge.gear.NativeGearHolds.active(world))return true;if(!(world instanceof ServerLevel)||holds.isEmpty())return false;for(var hold:holds)if(hold.world.get()==world)return true;return false;}
 /** Candidate cells are not reserved: only this operation's installed/pending markers are held.
  * A durable motion header and reconciliation must precede callers enabling this scope. */
 static void movingCandidates(ServerLevel world,NativeRampJournal.Operation operation,Collection<BlockPos> positions,net.minecraft.world.level.block.Block partBlock){
  if(!world.getServer().isSameThread()||positions.size()>8192||partBlock==null)throw new IllegalStateException("Ramp moving-candidate admission");
  var candidates=new HashSet<BlockPos>();for(var pos:positions){if(Math.abs((long)pos.getX()-operation.root().getX())>64||Math.abs((long)pos.getY()-operation.root().getY())>64||Math.abs((long)pos.getZ()-operation.root().getZ())>64)throw new IllegalArgumentException("Ramp candidate bounds");candidates.add(pos.immutable());}
  var next=new ArrayList<Hold>();boolean found=false;for(var hold:holds){if(hold.world.get()==null)continue;if(hold.world.get()==world&&hold.operation==operation){next.add(new Hold(hold.world,operation,hold.positions,hold.containers,Set.copyOf(candidates),partBlock));found=true;}else next.add(hold);}if(!found)throw new IllegalStateException("Ramp operation not held");holds=List.copyOf(next);
 }
 /** Strict read-only foreign ownership check; no own mutation scope is considered. */
 public static boolean foreignToCellReservation(Level world,BlockPos pos){
  if(com.vandorlabs.neoforge.gear.NativeGearHolds.reserved(world,pos))return true;if(!(world instanceof ServerLevel server))return false;for(var hold:holds)if(hold.world.get()==world){if(hold.positions.contains(pos))return true;if(hold.candidates.contains(pos)&&NativeRampPartOwnership.read(server,pos,hold.partBlock,hold.operation.id(),hold.operation.root())!=NativeRampPartOwnership.State.OTHER)return true;}return false;
 }
 public static boolean blocked(Level world,BlockPos pos){return blockedForBreak(world,pos,null);}
 public static boolean blockedForBreak(Level world,BlockPos pos,UUID admittedGear){
  if(com.vandorlabs.neoforge.transfer.NativeTransferHolds.blocked(world,pos))return true;if(com.vandorlabs.neoforge.gear.NativeGearHolds.blockedForBreak(world,pos,admittedGear))return true;
  if(!(world instanceof ServerLevel)||holds.isEmpty())return false;var write=WRITE.get();boolean admitted=write!=null&&!write.consumed&&write.world==world&&write.pos.equals(pos)&&write.operation==MUTATION.get();
  // Consume even when the intended candidate is still air. A callback sees the new marker, not another grant.
  if(admitted)write.consumed=true;
  for(var hold:holds)if(hold.world.get()==world&&!(admitted&&hold.operation==write.operation)){if(hold.positions.contains(pos))return true;if(hold.candidates.contains(pos)&&NativeRampPartOwnership.read((ServerLevel)world,pos,hold.partBlock,hold.operation.id(),hold.operation.root())!=NativeRampPartOwnership.State.OTHER)return true;}return false;
 }
 public static boolean blocked(Container container){
  if(com.vandorlabs.neoforge.transfer.NativeTransferHolds.blocked(container))return true;
  if(com.vandorlabs.neoforge.gear.NativeGearHolds.blocked(container))return true;
  if(holds.isEmpty())return false;
  if(container instanceof BlockEntity tile&&tile.getLevel() instanceof ServerLevel world&&foreignHold(world,tile.getBlockPos(),null))return true;
  for(var hold:holds)if(hold.world.get()!=null)for(var ref:hold.containers){var owner=ref.get();if(owner!=null&&(owner==container||container instanceof CompoundContainer compound&&compound.contains(owner)))return true;}return false;
 }
 /** Exact live transaction and frozen coordinate, without block/tile reads. */
 static boolean mutationAllows(ServerLevel world,UUID operation,BlockPos root,BlockPos pos){
  if(!world.getServer().isSameThread())return false;var current=MUTATION.get();if(current==null||!current.mutationArmed()||!current.id().equals(operation)||!current.root().equals(root)||!current.containsPotential(pos))return false;
  var snapshot=holds;for(int i=0;i<snapshot.size();i++){var hold=snapshot.get(i);if(hold.world.get()==world&&hold.operation==current)return true;}return false;
 }
 static Scope mutate(NativeRampJournal.Operation operation){var previous=MUTATION.get();if(previous!=null&&previous!=operation)throw new IllegalStateException("Nested foreign Ramp mutation");MUTATION.set(operation);return new Scope(previous);}
 static final class Scope implements AutoCloseable {private final NativeRampJournal.Operation previous;private Scope(NativeRampJournal.Operation previous){this.previous=previous;}public void close(){if(previous==null)MUTATION.remove();else MUTATION.set(previous);}}
 private NativeRampHeldOwners(){}
}
