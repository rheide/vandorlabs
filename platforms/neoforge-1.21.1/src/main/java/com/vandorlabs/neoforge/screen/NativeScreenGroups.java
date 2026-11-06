package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Server-thread loaded identity adapter. Discovery does not load chunks or mutate authority. */
public final class NativeScreenGroups {
 private NativeScreenGroups(){}
 public static List<NativeScreenEntity> editMembers(NativeScreenEntity origin){return members(origin,false);}
 public static List<NativeScreenEntity> powerMembers(NativeScreenEntity origin){return members(origin,true);}
 /** Permission checks cover every member before any authority changes; publication sees the complete group. */
 public static boolean applyActivation(NativeScreenEntity origin,NativeScreenActivationEdit edit,java.util.function.Predicate<NativeScreenEntity> allowed){
  if(origin==null||edit==null||allowed==null||!(origin.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||!installed(level,origin)||!origin.activationEditAllowed())return false;
  var targets=editMembers(origin);var before=new IdentityHashMap<NativeScreenEntity,NativeScreenSettings>();var plans=new IdentityHashMap<NativeScreenEntity,NativeScreenSettings>();
  for(var tile:targets){if(!installed(level,tile)||!tile.activationEditAllowed())return false;before.put(tile,tile.screenSettings());if(!allowed.test(tile))return false;}
  for(var tile:targets)if(!installed(level,tile)||!tile.activationEditAllowed()||tile.screenSettings()!=before.get(tile))return false;
  if(!editMembers(origin).equals(targets))return false;
  for(var tile:targets)plans.put(tile,tile.planActivation(edit));
  for(var tile:targets)tile.guardActivationGroup(true);
  try{for(var tile:targets)tile.installActivation(plans.get(tile));for(var tile:targets)if(installed(level,tile))tile.publishActivation();}finally{for(var tile:targets)tile.guardActivationGroup(false);}
  return true;
 }
 private static List<NativeScreenEntity> members(NativeScreenEntity origin,boolean power){
  Objects.requireNonNull(origin);if(!(origin.getLevel() instanceof ServerLevel level))return List.of(origin);if(!level.getServer().isSameThread())throw new IllegalStateException("Screen discovery requires the server thread");
  if(!installed(level,origin)||!(origin.getBlockState().getBlock() instanceof NativeScreenBlock block)||block.family()!=origin.family())return List.of(origin);
  var state=origin.getBlockState();var first=origin.joinSignature();var signatures=new IdentityHashMap<NativeScreenEntity,ScreenJoinSignature>();signatures.put(origin,first);
  var access=new ScreenRectangleGroups.Access<NativeScreenEntity>(){
   public int x(NativeScreenEntity tile){return tile.getBlockPos().getX();}public int y(NativeScreenEntity tile){return tile.getBlockPos().getY();}public int z(NativeScreenEntity tile){return tile.getBlockPos().getZ();}
   public boolean loaded(int x,int y,int z){return level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;}
   public NativeScreenEntity at(int x,int y,int z){var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)return null;var tile=chunk.getBlockEntities().get(new BlockPos(x,y,z));return tile instanceof NativeScreenEntity screen?screen:null;}
   public boolean compatible(NativeScreenEntity ignored,NativeScreenEntity candidate){if(!installed(level,candidate)||candidate.family()!=origin.family()||candidate.getBlockState()!=state)return false;var signature=signatures.computeIfAbsent(candidate,NativeScreenEntity::joinSignature);return power?first.powerMatches(signature):first.editMatches(signature);}
  };
  return ScreenRectangleGroups.discover(origin,ScreenJoinGeometry.basis(origin.family(),block.screenState(state),origin.screenSettings().ceilingMounted()),access,power?first.powerEligible(origin.family()):first.editEligible(origin.family())).members(origin);
 }
 private static boolean installed(ServerLevel level,NativeScreenEntity tile){if(tile.isRemoved()||tile.inert()||tile.getLevel()!=level)return false;var pos=tile.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(pos)==tile&&chunk.getBlockState(pos)==tile.getBlockState()&&tile.getBlockState().getBlock() instanceof NativeScreenBlock block&&block.family()==tile.family();}
}
