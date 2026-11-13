package com.vandorlabs.client;

import java.util.ArrayDeque;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

/** Opt-in asynchronous batch timings; ordinary play only increments small counters. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneDiagnostics {
    private static final boolean PROFILE=Boolean.getBoolean("vandorlabs.staticGlassDiagnostics");
    private static final int LIMIT=256;
    private static final Sample[] samples=new Sample[LIMIT];
    private static final ArrayDeque<Integer> available=new ArrayDeque<>(),pending=new ArrayDeque<>();
    private static final long[] draws=new long[2],vertices=new long[2],cpu=new long[2],gpu=new long[2],measured=new long[2],gpuMeasured=new long[2];
    private static int allocated,frame;
    private static long uploads,uploadBytes,dropped,lastReport;
    private static World world;
    private static boolean initialized,timers,announced;

    private static final class Sample {
        int start,end,pass,frame;
        long cpuStart;
        boolean active;
    }

    static void uploaded(int bytes){ensureWorld();uploads++;uploadBytes+=bytes;}

    static int begin(boolean shadow,int count){
        ensureWorld();
        int pass=shadow?1:0;draws[pass]++;vertices[pass]+=count;
        if(!PROFILE)return -1;
        if(!initialized){
            initialized=true;
            timers=GLContext.getCapabilities().OpenGL33||GLContext.getCapabilities().GL_ARB_timer_query;
        }
        int index;
        if(!available.isEmpty())index=available.removeFirst();
        else if(allocated<LIMIT){
            index=allocated++;Sample sample=new Sample();samples[index]=sample;
            if(timers){sample.start=GL15.glGenQueries();sample.end=GL15.glGenQueries();}
        }else{dropped++;return -1;}
        Sample sample=samples[index];sample.pass=pass;sample.frame=frame;sample.active=true;
        if(timers)ARBTimerQuery.glQueryCounter(sample.start,ARBTimerQuery.GL_TIMESTAMP);
        sample.cpuStart=System.nanoTime();return index;
    }

    static void end(int token){
        if(token<0)return;
        Sample sample=samples[token];if(!sample.active)return;sample.active=false;
        cpu[sample.pass]+=System.nanoTime()-sample.cpuStart;measured[sample.pass]++;
        if(timers){ARBTimerQuery.glQueryCounter(sample.end,ARBTimerQuery.GL_TIMESTAMP);pending.addLast(token);}
        else available.addLast(token);
    }

    private static void poll(){
        while(!pending.isEmpty()){
            int index=pending.peekFirst();Sample sample=samples[index];
            // Never read a result in the issuing frame or spin for completion.
            if(frame-sample.frame<2||GL15.glGetQueryObjecti(sample.end,GL15.GL_QUERY_RESULT_AVAILABLE)==0)break;
            long end=ARBTimerQuery.glGetQueryObjectui64(sample.end,GL15.GL_QUERY_RESULT);
            long start=ARBTimerQuery.glGetQueryObjectui64(sample.start,GL15.GL_QUERY_RESULT);
            if(end>=start){gpu[sample.pass]+=end-start;gpuMeasured[sample.pass]++;}
            pending.removeFirst();available.addLast(index);
        }
    }

    private static void reset(World next){
        for(int i=0;i<allocated;i++){
            Sample sample=samples[i];if(sample.start!=0)GL15.glDeleteQueries(sample.start);
            if(sample.end!=0)GL15.glDeleteQueries(sample.end);samples[i]=null;
        }
        allocated=frame=0;available.clear();pending.clear();
        for(long[] counters:new long[][]{draws,vertices,cpu,gpu,measured,gpuMeasured})java.util.Arrays.fill(counters,0);
        uploads=uploadBytes=dropped=0;initialized=timers=announced=false;world=next;lastReport=System.nanoTime();
    }

    private static void ensureWorld(){World next=Minecraft.getMinecraft().world;if(next!=world)reset(next);}

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.START)return;
        ensureWorld();
        if(world==null)return;frame++;if(PROFILE)poll();
        long now=System.nanoTime();
        if((!announced&&frame>=30)||(PROFILE&&now-lastReport>=5_000_000_000L)){
            lastReport=now;
            if(com.vandorlabs.VandorLabs.logger!=null){
                if(!announced)com.vandorlabs.VandorLabs.logger.info("Stationary glass mode: {} stride={} shaders={} GPU={} driver={}",
                        GlassPanePolicy.diagnostics(),BlockSurfaceFormat.get().getNextOffset(),GlassPaneBuffers.shaderName(),GL11.glGetString(GL11.GL_RENDERER),GL11.glGetString(GL11.GL_VERSION));
                com.vandorlabs.VandorLabs.logger.info("Stationary glass: {} {} {}",GlassPaneChunks.diagnostics(),GlassPaneTextures.diagnostics(),diagnostics());
            }
            announced=true;
        }
    }

    public static String diagnostics(){
        return "frames="+frame+" main-draws="+draws[0]+" shadow-draws="+draws[1]
                +" main-vertices="+vertices[0]+" shadow-vertices="+vertices[1]
                +" uploads="+uploads+" uploaded-bytes="+uploadBytes
                +" profiling="+PROFILE+" gpu-timers="+timers+" timing-dropped="+dropped
                +" main-cpu-ms="+millis(cpu[0])+" shadow-cpu-ms="+millis(cpu[1])
                +" main-gpu-ms="+millis(gpu[0])+" shadow-gpu-ms="+millis(gpu[1])
                +" cpu-samples="+measured[0]+","+measured[1]+" gpu-samples="+gpuMeasured[0]+","+gpuMeasured[1];
    }
    private static String millis(long nanos){return String.format(Locale.ROOT,"%.3f",nanos/1_000_000D);}
    private GlassPaneDiagnostics(){}
}
