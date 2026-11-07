package com.vandorlabs.neoforge;

import com.vandorlabs.animation.RampPartAnimation;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.ramp.NativeRampPartData;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Local cosmetic clocks retain no level/entity references and survive same-operation replacement. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRampPartVisualClock {
    static final long BUDGET=256*1024,ENTRY_BYTES=512;
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<Object,LinkedHashMap<BlockPos,State>> WORLDS=new WeakHashMap<>();
    private static double time;
    public static final class State {
        private final UUID operation;
        private final RampPartAnimation animation=new RampPartAnimation();
        private State(UUID operation){this.operation=operation;}
        public double pose(){return animation.pose();}
        public double distance(){return animation.distance();}
    }
    private NativeRampPartVisualClock(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){time=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static State sample(Object world,BlockPos position,UUID operation,long revision,NativeRampPartData data){
        var motion=data.motion();var belt=data.belt();
        return sample(world,position,operation,revision,motion.moving(),motion.observed(),motion.target(),motion.duration(),belt.mode()!=0,belt.observed(),belt.running()?belt.velocity(data.spec()):0,time);
    }
    static State sample(Object world,BlockPos position,UUID operation,long revision,boolean moving,double observed,double target,int duration,boolean belt,double distance,double velocity,double now){
        Objects.requireNonNull(world);Objects.requireNonNull(operation);
        var states=WORLDS.computeIfAbsent(world,ignored->new LinkedHashMap<>(32,.75F,true));
        var state=states.get(position);
        if(state==null||!state.operation.equals(operation)){
            state=new State(operation);states.put(position.immutable(),state);
            while(256L+states.size()*ENTRY_BYTES>BUDGET)states.remove(states.keySet().iterator().next());
        }
        state.animation.sample(revision,moving,observed,target,duration,belt,distance,velocity,now);
        return state;
    }
    public static void clear(Object world){WORLDS.remove(world);}
    static long retainedBytes(Object world){var states=WORLDS.get(world);return states==null?0:256L+states.size()*ENTRY_BYTES;}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())clear(event.getLevel());}
}
