package com.vandorlabs.neoforge;

import com.vandorlabs.animation.DoorAnimation;
import com.vandorlabs.animation.VisualClock;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Released nine-tick cosmetic motion, retained across tile replacement and sampled once per frame. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeTrapdoorVisualClock {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<Object,LinkedHashMap<BlockPos,DoorAnimation>> WORLDS=new WeakHashMap<>();
    private static double time;
    private NativeTrapdoorVisualClock(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){time=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static double pose(Object world,BlockPos owner,boolean open){return sample(world,owner,open,time);}
    static double sample(Object world,BlockPos owner,boolean open,double now){
        var animations=WORLDS.computeIfAbsent(world,ignored->new LinkedHashMap<>(32,.75F,true));
        var animation=animations.get(owner);
        if(animation==null){animation=new DoorAnimation(9);animations.put(owner.immutable(),animation);if(animations.size()>1024)animations.remove(animations.keySet().iterator().next());}
        return animation.sample(open,now);
    }
    public static void clear(Object world){WORLDS.remove(world);}
    static int retained(Object world){var animations=WORLDS.get(world);return animations==null?0:animations.size();}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())clear(event.getLevel());}
}
