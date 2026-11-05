package com.vandorlabs.neoforge.client;

import com.vandorlabs.animation.AnimationFrames;
import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Direct-texture glazing playback. Capture once per bake; sampling allocates nothing. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeMaterialPlayback {
    private static final VisualClock CLOCK=new VisualClock();
    private static long currentTick;
    private NativeMaterialPlayback(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){tick(System.nanoTime(),Minecraft.getInstance().isPaused());}
    /** Shared client phase matches legacy glazing; configuration packets never restart it. */
    public static long tick(){return currentTick;}
    static long tick(long nanos,boolean paused){currentTick=(long)CLOCK.sample(nanos,paused);return currentTick;}
    public static Material capture(ModernFinishCatalog.Entry entry){return entry.playback()==null?null:new Material(entry.playback());}
    public static final class Material {
        private final ResourceLocation stationary,off,animation;
        private final int count;
        private final double aspect;
        public Material(ModernFinishCatalog.Playback descriptor){
            if(!Double.isFinite(descriptor.aspect())||descriptor.aspect()<=0||descriptor.frameCount()<1||descriptor.frameCount()>65535||descriptor.frameCount()>1&&descriptor.animationTexture()==null)throw new IllegalArgumentException("Invalid playback descriptor");
            stationary=ResourceLocation.parse(descriptor.staticTexture());off=ResourceLocation.parse(descriptor.offTexture());animation=descriptor.animationTexture()==null?stationary:ResourceLocation.parse(descriptor.animationTexture());count=descriptor.frameCount();aspect=descriptor.aspect();
        }
        public int frameCount(){return count;}
        public double aspect(){return aspect;}
        public ResourceLocation texture(int mode){return mode==0?off:mode==2?animation:stationary;}
        public int frame(int mode,int speed,long tick){return mode==2?AnimationFrames.frame(tick,ScreenBehavior.animationTicks(speed),count):0;}
        public double top(int mode,int frame){return mode==2?AnimationFrames.top(frame,count):0;}
        public double bottom(int mode,int frame){return mode==2?AnimationFrames.bottom(frame,count):1;}
    }
}
