package com.vandorlabs.client.garage;

import com.vandorlabs.garage.GarageDoorTile;
import com.vandorlabs.garage.GarageDoorGeometry;

import com.vandorlabs.animation.VisualClock;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Targets are sequenced; repeated packets never restart the pause-aware client clock. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GarageDoorVisuals {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<World,LinkedHashMap<BlockPos,State>> WORLDS=new WeakHashMap<>();
    public static boolean authored;
    private static double frame;
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START)frame=CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    private static final class State {
        UUID id;long sequence;boolean target;double from,started,duration;
        double pose(double now){double t=Math.max(0,Math.min(1,(now-started)/duration));return from+((target?1:0)-from)*t;}
    }
    public static double pose(GarageDoorTile tile,float partial){return tile.clientAwaitingChunk?0:authored?tile.pose(partial):sample(tile,frame);}
    public static void resume(GarageDoorTile tile){
        double now=frame;sample(tile,now);
        State state=WORLDS.get(tile.getWorld()).get(tile.getPos());state.from=0;state.started=now;state.sequence=tile.sequence;state.target=tile.targetOpen;state.duration=Math.max(1,(tile.settings.direction<2?tile.height:tile.columns)*tile.ticksPerSection);
    }
    public static double sample(GarageDoorTile tile,double now){
        LinkedHashMap<BlockPos,State> states=WORLDS.computeIfAbsent(tile.getWorld(),w->new LinkedHashMap<>(32,.75F,true));State state=states.get(tile.getPos());
        if(state==null||!tile.identity.equals(state.id)){state=new State();state.id=tile.identity;state.sequence=tile.sequence;state.target=tile.targetOpen;state.from=tile.pose(0);state.started=now;state.duration=Math.max(1,(tile.settings.direction<2?tile.height:tile.columns)*tile.ticksPerSection*Math.abs((state.target?1:0)-state.from));states.put(tile.getPos().toImmutable(),state);if(states.size()>1024)states.remove(states.keySet().iterator().next());}
        else if(tile.sequence>state.sequence){state.from=state.pose(now);state.sequence=tile.sequence;state.target=tile.targetOpen;state.started=now;state.duration=Math.max(1,(tile.settings.direction<2?tile.height:tile.columns)*tile.ticksPerSection*Math.abs((state.target?1:0)-state.from));}
        return state.pose(now);
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(()->WORLDS.remove(event.getWorld()));}
    private GarageDoorVisuals(){}
}
