package com.vandorlabs.client.segmented;

import com.vandorlabs.segmented.SegmentedDoorTile;
import com.vandorlabs.segmented.SegmentedDoorGeometry;

import com.vandorlabs.animation.VisualClock;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Targets are sequenced; repeated packets never restart the pause-aware client clock. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SegmentedDoorVisuals {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<World,LinkedHashMap<UUID,Column>> WORLDS=new WeakHashMap<>();
    private static final Map<World,LinkedHashMap<Object,State>> MOTIONS=new WeakHashMap<>();
    private static final class Column {long sequence;State motion;}
    public static boolean authored;
    private static double frame;
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START)frame=CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    private static final class State {
        UUID id;long sequence;boolean target;double from,started,duration;
        double pose(double now){double t=Math.max(0,Math.min(1,(now-started)/duration));return from+((target?1:0)-from)*t;}
    }
    public static double pose(SegmentedDoorTile tile,float partial){return tile.clientAwaitingChunk?0:authored?tile.pose(partial):sample(tile,frame);}
    private static World physical(SegmentedDoorTile tile){World world=tile.getWorld();while(world instanceof com.vandorlabs.vehicle.VehicleWorld)world=((com.vandorlabs.vehicle.VehicleWorld)world).parent;return world;}
    private static Object key(SegmentedDoorTile tile){
        if(tile.motionIdentity!=null)return tile.motionIdentity;
        World view=tile.getWorld();Object owner=view instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)view).craft==null?System.identityHashCode(view):((com.vandorlabs.vehicle.VehicleWorld)view).craft.getUniqueID():0;
        BlockPos anchor=tile.getPos().offset(tile.facing().rotateY(),-tile.column);return Arrays.asList(owner,anchor,tile.started,tile.targetOpen,tile.duration);
    }
    public static void resume(SegmentedDoorTile tile){
        Map<Object,State> motions=MOTIONS.get(physical(tile));boolean shared=motions!=null&&motions.containsKey(key(tile));sample(tile,frame);
        if(!shared){State state=WORLDS.get(physical(tile)).get(tile.identity).motion;state.from=0;state.started=frame;state.target=tile.targetOpen;state.duration=Math.max(1,(tile.settings.direction<2?tile.height:tile.columns)*tile.ticksPerSection);}
    }
    public static double sample(SegmentedDoorTile tile,double now){
        World world=physical(tile);LinkedHashMap<UUID,Column> columns=WORLDS.computeIfAbsent(world,w->new LinkedHashMap<>(32,.75F,true));Column column=columns.get(tile.identity);
        if(column==null||tile.sequence>column.sequence){
            LinkedHashMap<Object,State> motions=MOTIONS.computeIfAbsent(world,w->new LinkedHashMap<>(32,.75F,true));Object key=key(tile);State state=motions.get(key);
            if(state==null){state=new State();state.from=column==null?tile.pose(0):column.motion.pose(now);state.started=now;state.target=tile.targetOpen;state.duration=Math.max(1,(tile.settings.direction<2?tile.height:tile.columns)*tile.ticksPerSection*Math.abs((state.target?1:0)-state.from));motions.put(key,state);if(motions.size()>1024)motions.remove(motions.keySet().iterator().next());}
            if(column==null){column=new Column();columns.put(tile.identity,column);if(columns.size()>1024)columns.remove(columns.keySet().iterator().next());}column.sequence=tile.sequence;column.motion=state;
        }
        return column.motion.pose(now);
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(()->{WORLDS.remove(event.getWorld());MOTIONS.remove(event.getWorld());});}
    private SegmentedDoorVisuals(){}
}
