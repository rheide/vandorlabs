package com.vandorlabs.neoforge.legacylanding.client;

import com.vandorlabs.animation.*;
import com.vandorlabs.neoforge.legacylanding.NativeLegacyLandingEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Cosmetic interpolation follows received poses, never world-time guesses or unsupported future motion. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeLegacyVisualClock {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<Object,LinkedHashMap<Key,Sample>> WORLDS=new WeakHashMap<>();
    private static double time;
    private record Key(BlockPos pos,java.util.UUID identity){}
    static final class Sample {double authority;boolean initialized;final PlatformMotionSmoothing smoothing=new PlatformMotionSmoothing(12);}
    static double advance(Sample sample,double authoritative,double now){if(!sample.initialized){sample.authority=authoritative;sample.initialized=true;}if(authoritative!=sample.authority){sample.smoothing.accept(authoritative-sample.authority,0,0,now);sample.authority=authoritative;}return Math.max(0,Math.min(4,authoritative+sample.smoothing.x(now)));}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){time=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static double pose(NativeLegacyLandingEntity tile){var map=WORLDS.computeIfAbsent(tile.getLevel(),ignored->new LinkedHashMap<>(32,.75F,true));var key=new Key(tile.getBlockPos(),tile.identity());var sample=map.get(key);if(sample==null){sample=new Sample();map.put(key,sample);if(map.size()>2048)map.remove(map.keySet().iterator().next());}return advance(sample,tile.progress(),time);}
    public static void clear(){WORLDS.clear();}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){WORLDS.remove(event.getLevel());}
}
