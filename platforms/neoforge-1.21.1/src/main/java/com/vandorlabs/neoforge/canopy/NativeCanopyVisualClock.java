package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.animation.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Pause-aware cosmetic progress survives tile replacement and ignores repeated packet progress. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeCanopyVisualClock {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<Object,LinkedHashMap<BlockPos,CanopyAnimation>> WORLDS=new WeakHashMap<>();
    private static double time;
    private NativeCanopyVisualClock(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){time=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static double pose(Object world,BlockPos owner,NativeCanopyEntity.Publication state){return sample(world,owner,state.targetOpen(),state.waiting(),state.progress(),time);}
    static double sample(Object world,BlockPos owner,boolean target,boolean waiting,double progress,double now){
        var animations=WORLDS.computeIfAbsent(world,unused->new LinkedHashMap<>(32,.75F,true));var animation=animations.get(owner);
        if(animation==null){animation=new CanopyAnimation();animations.put(owner.immutable(),animation);if(animations.size()>1024)animations.remove(animations.keySet().iterator().next());}
        return animation.sample(target,waiting,progress,now);
    }
    public static void clear(Object world){WORLDS.remove(world);}
    static int retained(Object world){var animations=WORLDS.get(world);return animations==null?0:animations.size();}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())clear(event.getLevel());}
}
