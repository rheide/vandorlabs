package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenJoinGeometry;
import com.vandorlabs.neoforge.redstone.NativeLoadedRedstonePower;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Source physical-only cache. Host lifecycle and synchronized group publication bind separately. */
public final class NativeScreenJoinedPower {
 private NativeScreenJoinedPower(){}
 private static final Map<ServerLevel,Epoch> EPOCHS=Collections.synchronizedMap(new WeakHashMap<>());
 private static final class Epoch{long revision;}
 private static Epoch epoch(ServerLevel level){synchronized(EPOCHS){return EPOCHS.computeIfAbsent(level,ignored->new Epoch());}}
 static final class Group {
  private final List<NativeScreenEntity> members;private final Epoch epoch;private final long revision;private long tick=Long.MIN_VALUE;private boolean invalid;private int power;
  Group(ServerLevel level,List<NativeScreenEntity> members){this.members=new ArrayList<>(members);epoch=epoch(level);revision=epoch.revision;}
  void invalidate(){invalid=true;members.clear();}
  int level(ServerLevel level){long now=level.getGameTime();if(tick==now)return power;tick=now;int next=0;for(var tile:members){if(!NativeScreenGroups.installed(level,tile)){invalid=true;continue;}next=Math.max(next,NativeLoadedRedstonePower.level(level,tile.getBlockPos()));}power=next;return next;}
 }
 private static ServerLevel authority(NativeScreenEntity tile){if(!(tile.getLevel() instanceof ServerLevel level))return null;if(!level.getServer().isSameThread())throw new IllegalStateException("Screen power requires the server thread");return level;}
 public static void changed(NativeScreenEntity tile){var level=authority(tile);if(level!=null)epoch(level).revision++;if(tile.joinedPower!=null)tile.joinedPower.invalidate();}
 public static void inputChanged(NativeScreenEntity tile){authority(tile);if(tile.joinedPower!=null)tile.joinedPower.tick=Long.MIN_VALUE;}
 public static int level(NativeScreenEntity tile){var level=authority(tile);if(level==null||!NativeScreenGroups.installed(level,tile))return 0;var settings=tile.screenSettings();int mode=settings.activation().data().mode<0?(settings.scalar().redstoneEnabled?1:0):settings.activation().data().mode;if(mode==0||!ScreenJoinGeometry.supports(tile.family(),settings.scalar().smallInput))return NativeLoadedRedstonePower.level(level,tile.getBlockPos());var group=tile.joinedPower;if(group==null||group.invalid||group.revision!=group.epoch.revision){var discovered=NativeScreenGroups.discover(tile,true);if(discovered==null)return 0;for(var rectangle:discovered.rectangles){var next=new Group(level,rectangle);for(var member:rectangle)member.joinedPower=next;}group=tile.joinedPower;}return group.level(level);}
}
