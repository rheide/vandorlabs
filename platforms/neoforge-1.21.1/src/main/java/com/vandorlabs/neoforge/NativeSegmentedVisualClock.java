package com.vandorlabs.neoforge;
import com.vandorlabs.animation.VisualClock;
import net.minecraft.core.BlockPos;
import java.util.*;
/** Released sequenced cosmetic grouping, world/position replacement and pause-aware client time. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NativeSegmentedVisualClock {
 private NativeSegmentedVisualClock(){}
 private static final VisualClock CLOCK=new VisualClock();private static double frameTime;
 @net.neoforged.bus.api.SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){frameTime=time(System.nanoTime(),net.minecraft.client.Minecraft.getInstance().isPaused());}
 public static double current(){return frameTime;}
 private static final Map<Object,World> WORLDS=new WeakHashMap<>();
 private static final class World {final LinkedHashMap<UUID,Column> columns=new LinkedHashMap<>(32,.75F,true);final LinkedHashMap<Object,State> motions=new LinkedHashMap<>(32,.75F,true);}
 private static final class Column {long sequence;State state;}
 private static final class State {double from,started,duration;boolean target;double pose(double time){double t=Math.max(0,Math.min(1,(time-started)/duration));return from+((target?1:0)-from)*t;}}
 private record Group(BlockPos anchor,long started,boolean target,int duration){}
 public static double time(long nanos,boolean paused){return CLOCK.sample(nanos,paused);}
 private static Object key(BlockPos owner,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual visual,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual.Motion motion){return motion.motionIdentity()!=null?motion.motionIdentity():new Group(owner.relative(visual.facing().getClockWise(),-visual.column()).immutable(),motion.started(),motion.targetOpen(),motion.duration());}
 public static double sample(Object world,BlockPos owner,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual visual,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual.Motion motion,double time){if(visual.awaitingChunk())return 0;return observe(world,owner,visual,motion,time);}
 private static double observe(Object world,BlockPos owner,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual visual,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual.Motion motion,double time){
  var cache=WORLDS.computeIfAbsent(world,ignored->new World());var column=cache.columns.get(motion.identity());if(column==null||motion.sequence()>column.sequence){var key=key(owner,visual,motion);var state=cache.motions.get(key);if(state==null){state=new State();state.from=column==null?motion.initialPose():column.state.pose(time);state.started=time;state.target=motion.targetOpen();int span=visual.settings().direction<2?visual.height():visual.width();state.duration=Math.max(1,span*(double)motion.ticksPerSection()*Math.abs((state.target?1:0)-state.from));cache.motions.put(key,state);trim(cache.motions);}if(column==null){column=new Column();cache.columns.put(motion.identity(),column);trim(cache.columns);}column.sequence=motion.sequence();column.state=state;}return column.state.pose(time);
 }
 public static void resume(Object world,BlockPos owner,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual visual,com.vandorlabs.neoforge.segmented.NativeSegmentedVisual.Motion motion,double time){var old=WORLDS.get(world);boolean shared=old!=null&&old.motions.containsKey(key(owner,visual,motion));observe(world,owner,visual,motion,time);if(!shared){var state=WORLDS.get(world).columns.get(motion.identity()).state;state.from=0;state.started=time;state.target=motion.targetOpen();state.duration=Math.max(1,(visual.settings().direction<2?visual.height():visual.width())*(double)motion.ticksPerSection());}}
 private static void trim(LinkedHashMap<?,?> values){if(values.size()>1024)values.remove(values.keySet().iterator().next());}
 public static void clear(Object world){WORLDS.remove(world);}
 static int owners(Object world){var value=WORLDS.get(world);return value==null?0:value.columns.size();}
}
