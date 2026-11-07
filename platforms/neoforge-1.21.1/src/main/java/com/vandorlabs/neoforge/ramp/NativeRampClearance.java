package com.vandorlabs.neoforge.ramp;
import com.vandorlabs.neoforge.canopy.*;
import com.vandorlabs.ramp.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
/** Preflight only. A cooperative canopy is a real pending command, never permission to remove it. */
public final class NativeRampClearance {
 public enum Status { READY,PENDING,REFUSED }
 public record Result(Status status,Set<BlockPos> cells,Set<BlockPos> canopyRoots,String error){public Result{cells=Set.copyOf(cells);canopyRoots=Set.copyOf(canopyRoots);}}
 private static Result refused(String error){return new Result(Status.REFUSED,Set.of(),Set.of(),error);}
 private static boolean loaded(ServerLevel level,BlockPos pos){return !level.isOutsideBuildHeight(pos)&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
 private static BlockState state(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4).getBlockState(pos);}
 public static NativeRampFootprint.Spec footprint(BlockPos root,NativeRampSettings settings,NativeRampCapture.Plan plan){var origins=new ArrayList<NativeRampGeometry.Origin>();for(var source:plan.sources())origins.add(new NativeRampGeometry.Origin(source.position(),source.shapes()));return new NativeRampFootprint.Spec(root,plan.facing(),plan.sourceY(),plan.minimumAlong(),plan.length(),plan.low(),plan.high(),settings.startHalf()/2D,settings.endHalf()/2D,settings.treadPixels(),settings.elevator(),settings.travelAxis(),settings.extend(),settings.speed()==0,settings.interpolation(),settings.verticalLayout(),settings.moat(),origins);}
 public static Result inspect(NativeRampEntity owner,ServerPlayer actor,NativeRampCapture.Plan plan,boolean deploying){
  if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||owner.inert()||owner.isRemoved()||!loaded(level,owner.getBlockPos())||level.getChunkSource().getChunkNow(owner.getBlockPos().getX()>>4,owner.getBlockPos().getZ()>>4).getBlockEntities().get(owner.getBlockPos())!=owner||actor==null||actor.level()!=level||!actor.isAlive()||actor.isSpectator())return refused("Ramp controller is unavailable");
  var settings=owner.settings();Set<BlockPos> cells;try{cells=NativeRampFootprint.cells(footprint(owner.getBlockPos(),settings,plan),0,1);}catch(IllegalArgumentException failure){return refused(failure.getMessage());}
  var sources=new HashSet<BlockPos>();for(var source:plan.sources()){if(!loaded(level,source.position()))return refused("Load all platform chunks first");if(state(level,source.position())!=source.state())return refused("Platform source changed before preparation");sources.add(source.position());}
  var canopies=new HashSet<BlockPos>();for(var cell:cells){String error=permission(level,actor,cell);if(error!=null)return refused(error);if(!sources.contains(cell)&&!state(level,cell).isAir()){var canopy=cooperative(level,cell,settings,deploying);if(canopy==null)return refused("Movement path is obstructed");canopies.add(canopy.getBlockPos());}}
  // Released ordinary vertical travel additionally requires the full source column to be clear.
  if(!settings.moat()&&!settings.verticalLayout()&&settings.travelAxis()==RampGeometry.VERTICAL)for(var source:plan.sources())for(int y=RampGeometry.firstOccupiedY(source.position().getY(),plan.low(),Math.min(0,settings.startHalf()/2D),Math.min(0,settings.endHalf()/2D));y<=RampGeometry.lastOccupiedY(source.position().getY(),plan.high(),Math.max(0,settings.startHalf()/2D),Math.max(0,settings.endHalf()/2D));y++){var cell=new BlockPos(source.position().getX(),y,source.position().getZ());String error=permission(level,actor,cell);if(error!=null)return refused(error);if(!sources.contains(cell)&&!state(level,cell).isAir())return refused("Movement path is obstructed");}
  return new Result(canopies.isEmpty()?Status.READY:Status.PENDING,cells,canopies,canopies.isEmpty()?"":"Waiting for canopy to open");
 }
 private static String permission(ServerLevel level,ServerPlayer actor,BlockPos cell){if(level.isOutsideBuildHeight(cell))return "Travel exceeds world height";if(!loaded(level,cell))return "Load all platform chunks first";if(NativeRampHeldOwners.blocked(level,cell)||!level.mayInteract(actor,cell)||!actor.mayUseItemAt(cell,Direction.UP,actor.getMainHandItem()))return "No permission to move this platform";return null;}
 static NativeCanopyEntity cooperative(ServerLevel level,BlockPos cell,NativeRampSettings ramp,boolean deploying){var part=NativeCanopyLifecycle.at(level,cell);if(part==null||!part.available()||part.links().anchor()==null||cell.equals(part.links().anchor()))return null;var anchor=NativeCanopyLifecycle.at(level,part.links().anchor());if(anchor==null||!anchor.available()||!anchor.getBlockState().is(part.getBlockState().getBlock()))return null;var owner=NativeCanopyLifecycle.owner(part);if(!owner.available()||!owner.publication().draws()||!NativeCanopyControl.opening(owner))return null;boolean shared=!ramp.channels().intersect(owner.getRedstoneChannels()).isEmpty();boolean expected=shared&&(deploying?ramp.powerOn()==owner.settings().data().activateOnPower:ramp.powerOn()!=owner.settings().data().activateOnPower);if(!owner.publication().targetOpen()&&!expected)return null;if(owner.publication().progress()>=1){NativeCanopyLifecycle.prepare(owner);var geometry=owner.publication().geometry();if(geometry==null)return owner;for(var box:geometry.boxes())if(box.intersects(new AABB(cell)))return null;}return owner;}
 private NativeRampClearance(){}
}
