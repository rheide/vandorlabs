package com.vandorlabs.neoforge.ramp;
import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
/** Loaded-only Canopy admission. Unknown active geometry waits; it never supplies a READY fallback. */
public final class NativeRampReservations {
 public enum Status {READY,PENDING,REFUSED}
 public interface SweepQuery {Status readiness();boolean intersects(AABB worldBounds);}
 public record Result(Status status,Set<BlockPos> blockedCells,Set<BlockPos> partnerRoots){public Result{blockedCells=Set.copyOf(blockedCells);partnerRoots=Set.copyOf(partnerRoots);}}
 public record View(boolean attached,boolean moving,boolean open,List<AABB> moatSweep){public View{if(moatSweep.size()>65536)throw new IllegalArgumentException("Ramp sweep budget");moatSweep=List.copyOf(moatSweep);}}
 private static final NativeLoadedTileIndex<NativeRampEntity> ROOTS=new NativeLoadedTileIndex<>(NativeRampEntity.class);private static final NativeLoadedTileIndex<NativeRampJournalPart> PARTS=new NativeLoadedTileIndex<>(NativeRampJournalPart.class);private static final Result CLEAR=new Result(Status.READY,Set.of(),Set.of());private static BiConsumer<ServerLevel,BlockPos> listener;
 static void add(NativeRampEntity root){ROOTS.add(root);}static void remove(NativeRampEntity root){ROOTS.remove(root);}static void add(NativeRampJournalPart part){PARTS.add(part);}static void remove(NativeRampJournalPart part){PARTS.remove(part);}
 /** Receivers must only queue retry positions; onLoad registration itself never invokes this hook. */
 public static BiConsumer<ServerLevel,BlockPos> bindChanged(BiConsumer<ServerLevel,BlockPos> next){var previous=listener;listener=next;return previous;}
 static void changed(ServerLevel level,BlockPos root){var callback=listener;if(callback!=null)callback.accept(level,root);}
 public static Result prepareClose(ServerLevel level,BlockPos canopyRoot,ChannelList canopyChannels,AABB conservativeArea,SweepQuery sweep){
  if(!level.getServer().isSameThread())return new Result(Status.REFUSED,Set.of(),Set.of());Objects.requireNonNull(canopyRoot);Objects.requireNonNull(canopyChannels);Objects.requireNonNull(sweep);if(!ROOTS.hasMembers(level)&&!PARTS.hasMembers(level))return NativeRampJournal.publishingInArea(level,conservativeArea)?new Result(Status.PENDING,Set.of(),Set.of()):CLEAR;
  var blocked=new LinkedHashSet<BlockPos>();var partners=new LinkedHashSet<BlockPos>();var candidates=PARTS.query(level,conservativeArea.inflate(1));var roots=new LinkedHashSet<>(ROOTS.query(level,conservativeArea));boolean unknown=false;
  for(var part:candidates){var pos=part.controller();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var owner=chunk==null?null:chunk.getBlockEntities().get(pos);if(owner instanceof NativeRampEntity root)roots.add(root);else unknown=true;}
  for(var root:roots)if(root.inert()){blocked.add(root.getBlockPos());return new Result(Status.REFUSED,blocked,Set.of());}
  if(NativeRampJournal.publishingInArea(level,conservativeArea))return new Result(Status.PENDING,Set.of(),Set.of());
  for(var root:roots){var view=root.reservationView();if(root.settings().moat()&&!root.getRedstoneChannels().intersect(canopyChannels).isEmpty()){if(view==null){if(NativeRampJournal.atRoot(level,root.getBlockPos())!=null)unknown=true;}else if(view.attached()&&(view.moving()||view.open()))partners.add(root.getBlockPos());}}
  if(candidates.isEmpty()&&partners.isEmpty()&&!unknown)return CLEAR;var ready=sweep.readiness();if(ready!=Status.READY)return new Result(ready,blocked,partners);
  for(var part:candidates){var physical=part.physicalBounds();if(physical==null){unknown=true;continue;}for(var box:physical)if(sweep.intersects(box.deflate(1e-7))){blocked.add(part.getBlockPos());partners.add(part.controller());break;}}
  for(var root:roots)if(partners.contains(root.getBlockPos())){var view=root.reservationView();if(view!=null&&root.settings().moat()&&view.attached()&&(view.moving()||view.open())&&!root.getRedstoneChannels().intersect(canopyChannels).isEmpty())for(var box:view.moatSweep())if(sweep.intersects(box.deflate(1e-7))){blocked.add(root.getBlockPos());break;}}
  return blocked.isEmpty()&&!unknown?CLEAR:new Result(Status.PENDING,blocked,partners);
 }
 private NativeRampReservations(){}
}
