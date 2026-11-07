package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.screen.ScreenFamily;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;

/** Source surface-command rectangle: authorize the whole current layout before changing its signature. */
public final class NativeScreenSurfaceGroups {
 private NativeScreenSurfaceGroups(){}
 public static boolean apply(NativeScreenEntity origin,NativeScreenSurfaceEdit edit,Predicate<NativeScreenEntity> allowed){
  if(origin==null||edit==null||allowed==null||!(origin.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||!NativeScreenGroups.installed(level,origin)||!origin.activationEditAllowed())return false;
  if(edit.choice()==-4&&!NativeScreenChipInteractions.available(origin,edit.slot()))return false;
  boolean rectangle=origin.family()==ScreenFamily.SCREEN&&(edit.choice()==-4||origin.chips().selected(edit.slot()));
  var targets=rectangle?NativeScreenGroups.editMembers(origin):List.of(origin);
  var before=new IdentityHashMap<NativeScreenEntity,NativeScreenSettings>();var plans=new IdentityHashMap<NativeScreenEntity,NativeScreenSurfaceEdit.Result>();
  for(var tile:targets){if(!NativeScreenGroups.installed(level,tile)||!tile.activationEditAllowed()||!allowed.test(tile))return false;var plan=tile.planSurface(edit);if(plan==null)return false;before.put(tile,tile.screenSettings());plans.put(tile,plan);}
  for(var tile:targets)if(!NativeScreenGroups.installed(level,tile)||!tile.activationEditAllowed()||tile.screenSettings()!=before.get(tile))return false;
  if(rectangle&&!NativeScreenGroups.editMembers(origin).equals(targets))return false;
  for(var tile:targets){NativeScreenPowerService.queueTopology(tile);tile.guardActivationGroup(true);}
  try{NativeRedstoneChannels.batch(level,()->{for(var tile:targets)tile.installSurface(plans.get(tile),edit.slot());NativeScreenPowerService.refresh(origin);});}finally{for(var tile:targets)tile.guardActivationGroup(false);}
  return true;
 }
}
