package com.vandorlabs.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

/**
 * Opt-in, bounded render-thread measurements. Timestamp pairs support nested
 * frame/group intervals. Results are consumed only after the end query is
 * available; profiling never finishes or waits for the graphics pipeline.
 */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneGpuTimers {
    private static final int CAPACITY=128, SAMPLE_LIMIT=8192, LABEL_LIMIT=16;
    private static final Token[] RING=new Token[CAPACITY];
    private static final Map<String,Series> SERIES=new LinkedHashMap<>();
    private static boolean capturing,gpu;
    private static int cursor,frames,frameLimit,pending,dropped;
    private static long frameStart;
    private static Token frameToken;
    private static JsonObject startContext,endContext;
    private static Path automaticOutput;
    private static boolean outputPending;
    private static String lastOutput="",failure="";

    public static final class Token {
        private int startQuery,endQuery,frame;
        private long cpuStart,cpuNanos;
        private boolean open,waiting;
        private Series series;
        private Token(){}
    }
    private static final class Series {
        final long[] cpu=new long[SAMPLE_LIMIT],gpu=new long[SAMPLE_LIMIT];
        final int[] frame=new int[SAMPLE_LIMIT];
        int size,dropped;
        void add(long cpuNanos,long gpuNanos,int atFrame){
            if(size==SAMPLE_LIMIT){dropped++;return;}
            cpu[size]=cpuNanos;gpu[size]=gpuNanos;frame[size++]=atFrame;
        }
    }

    /** Must be called on the client render thread, with an active GL context. */
    public static void start(int limit){
        if(limit<1||limit>10000)throw new IllegalArgumentException("Frames must be between 1 and 10000");
        clearQueries();SERIES.clear();cursor=frames=pending=dropped=0;frameStart=0;frameToken=null;
        frameLimit=limit;failure="";lastOutput="";automaticOutput=null;outputPending=false;
        gpu=GLContext.getCapabilities().OpenGL33||GLContext.getCapabilities().GL_ARB_timer_query;
        if(gpu)for(int i=0;i<CAPACITY;i++){
            Token token=new Token();token.startQuery=GL15.glGenQueries();token.endQuery=GL15.glGenQueries();RING[i]=token;
        }
        else for(int i=0;i<CAPACITY;i++)RING[i]=new Token();
        startContext=context();endContext=null;capturing=true;
    }
    public static void automaticOutput(Path path){automaticOutput=path;}
    public static boolean active(){return capturing;}
    public static boolean draining(){return pending>0;}

    /** Null is the normal disabled result, so callers do not allocate wrappers. */
    public static Token begin(String label){
        if(!capturing)return null;
        Series series=SERIES.get(label);
        if(series==null){if(SERIES.size()>=LABEL_LIMIT){dropped++;return null;}SERIES.put(label,series=new Series());}
        for(int i=0;i<CAPACITY;i++){
            Token token=RING[cursor];cursor=(cursor+1)%CAPACITY;
            if(token.open||token.waiting)continue;
            token.series=series;token.frame=frames;token.open=true;token.cpuStart=System.nanoTime();
            if(gpu)ARBTimerQuery.glQueryCounter(token.startQuery,ARBTimerQuery.GL_TIMESTAMP);
            return token;
        }
        dropped++;return null;
    }
    public static void end(Token token){
        if(token==null||!token.open)return;
        if(gpu)ARBTimerQuery.glQueryCounter(token.endQuery,ARBTimerQuery.GL_TIMESTAMP);
        token.cpuNanos=System.nanoTime()-token.cpuStart;token.open=false;
        if(gpu){token.waiting=true;pending++;}
        else{token.series.add(token.cpuNanos,-1,token.frame);token.series=null;}
    }
    /** Poll once per frame, never read an unavailable query result. */
    public static void poll(){
        if(pending==0){finishOutput();return;}
        for(Token token:RING){
            if(token==null||!token.waiting||GL15.glGetQueryObjecti(token.endQuery,GL15.GL_QUERY_RESULT_AVAILABLE)==0)continue;
            long end=ARBTimerQuery.glGetQueryObjectui64(token.endQuery,GL15.GL_QUERY_RESULT);
            long begin=ARBTimerQuery.glGetQueryObjectui64(token.startQuery,GL15.GL_QUERY_RESULT);
            token.series.add(token.cpuNanos,Math.max(0,end-begin),token.frame);
            token.series=null;token.waiting=false;pending--;
        }
        finishOutput();
    }
    public static void stop(){
        if(!capturing)return;
        end(frameToken);frameToken=null;capturing=false;endContext=context();outputPending=automaticOutput!=null;
        poll();
    }
    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event){
        if(!capturing&&pending==0&&!outputPending)return;
        if(event.phase==TickEvent.Phase.START){
            poll();
            if(!capturing)return;
            long now=System.nanoTime();
            if(frameStart!=0){
                Series intervals=SERIES.get("frame-interval");
                if(intervals==null){intervals=new Series();SERIES.put("frame-interval",intervals);}
                intervals.add(now-frameStart,-1,frames);
            }
            frameStart=now;frameToken=begin("render-event");
        }else if(capturing){
            end(frameToken);frameToken=null;
            if(++frames>=frameLimit)stop();
        }
    }
    private static JsonObject context(){
        Minecraft mc=Minecraft.getMinecraft();JsonObject context=new JsonObject();
        context.addProperty("gl_vendor",GL11.glGetString(GL11.GL_VENDOR));
        context.addProperty("gl_renderer",GL11.glGetString(GL11.GL_RENDERER));
        context.addProperty("gl_version",GL11.glGetString(GL11.GL_VERSION));
        context.addProperty("java_version",System.getProperty("java.version"));
        context.addProperty("block_vertex_stride",DefaultVertexFormats.BLOCK.getNextOffset());
        context.addProperty("native_policy",GlassPanePolicy.diagnostics());
        context.addProperty("native_coverage",GlassPaneTerrain.diagnostics());
        try{context.addProperty("retained_coverage",String.valueOf(Class.forName("com.vandorlabs.client.GlassPaneRetained").getMethod("diagnostics").invoke(null)));}
        catch(ReflectiveOperationException|LinkageError absent){context.addProperty("retained_coverage","unavailable in this build");}
        if(mc!=null){
            context.addProperty("width",mc.displayWidth);context.addProperty("height",mc.displayHeight);
            if(mc.gameSettings!=null){context.addProperty("render_distance",mc.gameSettings.renderDistanceChunks);context.addProperty("fps_limit",mc.gameSettings.limitFramerate);context.addProperty("vsync",mc.gameSettings.enableVsync);}
            Entity camera=mc.getRenderViewEntity();
            if(camera!=null){context.addProperty("camera_x",camera.posX);context.addProperty("camera_y",camera.posY);context.addProperty("camera_z",camera.posZ);context.addProperty("yaw",camera.rotationYaw);context.addProperty("pitch",camera.rotationPitch);}
        }
        // Read optional shader state without changing shader settings or requiring OptiFine.
        try{
            Class<?> shaders=Class.forName("net.optifine.shaders.Shaders");
            java.lang.reflect.Field field=shaders.getDeclaredField("shaderPack");field.setAccessible(true);Object pack=field.get(null);
            context.addProperty("shader_pack",pack==null?"none":String.valueOf(pack.getClass().getMethod("getName").invoke(pack)));
        }catch(ClassNotFoundException absent){context.addProperty("shader_pack","OptiFine not installed");}
        catch(ReflectiveOperationException|LinkageError unavailable){context.addProperty("shader_pack","unavailable through this OptiFine version");}
        return context;
    }
    private static JsonObject stats(long[] input,int size){
        long[] sorted=Arrays.copyOf(input,size);Arrays.sort(sorted);double total=0;for(long value:sorted)total+=value;
        JsonObject stats=new JsonObject();stats.addProperty("count",size);
        if(size>0){stats.addProperty("mean_ms",total/size/1e6);stats.addProperty("p50_ms",sorted[(size-1)/2]/1e6);stats.addProperty("p95_ms",sorted[(int)Math.ceil(size*.95)-1]/1e6);stats.addProperty("p99_ms",sorted[(int)Math.ceil(size*.99)-1]/1e6);stats.addProperty("max_ms",sorted[size-1]/1e6);}
        return stats;
    }
    public static JsonObject report(){
        JsonObject report=new JsonObject();report.addProperty("schema",1);report.addProperty("active",capturing);report.addProperty("frames",frames);report.addProperty("requested_frames",frameLimit);report.addProperty("gpu_timestamps",gpu);
        report.addProperty("pending_queries",pending);report.addProperty("ring_dropped_intervals",dropped);report.addProperty("sample_limit_per_label",SAMPLE_LIMIT);
        report.addProperty("scope","frame-interval is render-start to render-start; render-event covers Forge RenderTick START to END; retained scopes cover glass draws only, not the complete shader shadow pass. Nested scopes overlap and must not be summed.");
        report.add("start",startContext);report.add("end",endContext);
        if(startContext!=null&&endContext!=null&&startContext.has("camera_x")&&endContext.has("camera_x")){
            boolean moved=false;for(String key:new String[]{"camera_x","camera_y","camera_z","yaw","pitch"})if(Math.abs(startContext.get(key).getAsDouble()-endContext.get(key).getAsDouble())>0.0001)moved=true;
            report.addProperty("camera_changed_between_endpoints",moved);
        }
        JsonObject series=new JsonObject();
        for(Map.Entry<String,Series> entry:SERIES.entrySet()){
            Series values=entry.getValue();JsonObject row=new JsonObject();row.add("cpu",stats(values.cpu,values.size));
            if(gpu&&!entry.getKey().equals("frame-interval"))row.add("gpu",stats(values.gpu,values.size));
            row.addProperty("sample_limit_dropped_intervals",values.dropped);
            JsonArray samples=new JsonArray();for(int i=0;i<values.size;i++){
                JsonArray sample=new JsonArray();sample.add(values.frame[i]);sample.add(values.cpu[i]);sample.add(values.gpu[i]);samples.add(sample);
            }
            row.addProperty("sample_columns","frame,cpu_ns,gpu_ns (-1 when unavailable)");row.add("samples",samples);series.add(entry.getKey(),row);
        }
        report.add("intervals",series);return report;
    }
    public static void writeReport(Path path)throws IOException{
        Path parent=path.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        Files.write(path,new GsonBuilder().setPrettyPrinting().create().toJson(report()).getBytes(StandardCharsets.UTF_8));
    }
    private static void finishOutput(){
        if(!capturing&&pending==0&&outputPending){
            outputPending=false;
            try{writeReport(automaticOutput);lastOutput=automaticOutput.toString();}
            catch(IOException error){failure=error.toString();}
        }
    }
    public static String status(){
        return "Glass profile "+(capturing?"recording":pending>0?"draining GPU queries":"stopped")+": "+frames+"/"+frameLimit+" frames, GPU="+gpu+", pending="+pending+", dropped="+dropped
                +(lastOutput.isEmpty()?"":"; saved "+lastOutput)+(failure.isEmpty()?"":"; save failed: "+failure);
    }
    private static void clearQueries(){
        for(int i=0;i<CAPACITY;i++){Token token=RING[i];if(token!=null){if(token.startQuery!=0)GL15.glDeleteQueries(token.startQuery);if(token.endQuery!=0)GL15.glDeleteQueries(token.endQuery);}RING[i]=null;}
    }
    private GlassPaneGpuTimers(){}
}
