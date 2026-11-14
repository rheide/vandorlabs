package com.vandorlabs.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.vandorlabs.ClientProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/** Matched real porthole housing+aperture paths, including palette and custom opacity variants. */
public final class PortholePerformanceChecks {
    private static final String MODE=System.getProperty("vandorlabs.glassBenchmarkMode","portholes-after");
    private static final boolean AFTER=MODE.endsWith("after");
    private static final int WARMUP=12,FRAMES=80,HOST_PACING_MS=8;
    private static final Path OUTPUT=Paths.get(System.getProperty("vandorlabs.glassBenchmarkOutput","build/test-artifacts/glass-performance"));
    private static Capture capture;
    private static Field tessellator;
    private static Object original;
    private static final class Capture extends Tessellator {
        long calls,draws,vertices;
        Capture(){super(65536);}
        @Override public void draw(){int count=getBuffer().getVertexCount();calls++;if(count>0){draws++;vertices+=count;}super.draw();}
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static long counter(String name)throws Exception{if(!AFTER)return 0;Field field=Class.forName("com.vandorlabs.client.PortholeRetained").getDeclaredField(name);field.setAccessible(true);return field.getLong(null);}
    private static void retainedFlush()throws Exception{if(AFTER)Class.forName("com.vandorlabs.client.PortholeRetained").getMethod("flush").invoke(null);}
    private static void flush(GlassPanePipelineChecks.Fixture scene)throws Exception{scene.flush();retainedFlush();}
    @SuppressWarnings("unchecked") private static TEAnimatedScreenSelector initializeRenderer()throws Exception{
        TEAnimatedScreenSelector renderer=new TEAnimatedScreenSelector();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        ((Map<Class<? extends TileEntity>,Object>)GlassPanePipelineChecks.field(TileEntityRendererDispatcher.class,"renderers").get(TileEntityRendererDispatcher.instance)).put(TileEntityAnimatedScreenSelector.class,renderer);
        for(Field field:Tessellator.class.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==Tessellator.class)tessellator=field;
        need(tessellator!=null,"missing Tessellator singleton");tessellator.setAccessible(true);original=tessellator.get(null);capture=new Capture();
        GlassPanePipelineChecks.unsafe.putObject(GlassPanePipelineChecks.unsafe.staticFieldBase(tessellator),GlassPanePipelineChecks.unsafe.staticFieldOffset(tessellator),capture);return renderer;
    }
    private static void restore(){if(tessellator!=null)GlassPanePipelineChecks.unsafe.putObject(GlassPanePipelineChecks.unsafe.staticFieldBase(tessellator),GlassPanePipelineChecks.unsafe.staticFieldOffset(tessellator),original);}
    private static final class Submission {
        final List<TileEntityAnimatedScreenSelector> tiles;final TEAnimatedScreenSelector renderer;final boolean shadow;
        long draws,calls,vertices,housing,panes,retained;int pass0,pass1;
        Submission(List<TileEntityAnimatedScreenSelector> tiles,TEAnimatedScreenSelector renderer,boolean shadow){this.tiles=tiles;this.renderer=renderer;this.shadow=shadow;}
        void run()throws Exception{
            long oldCalls=capture.calls,oldDraws=capture.draws,oldVertices=capture.vertices,h=counter("housingDraws"),p=counter("paneDraws"),s=counter("shadowDraws");pass0=pass1=0;
            for(int pass=0;pass<2;pass++){
                ForgeHooksClient.setRenderPass(pass);
                for(TileEntityAnimatedScreenSelector tile:tiles)if(tile.shouldRenderInPass(pass)){
                    if(pass==0)pass0++;else pass1++;BlockPos pos=tile.getPos();renderer.render(tile,pos.getX(),pos.getY(),pos.getZ(),0,-1,1);
                }
            }
            calls=capture.calls-oldCalls;draws=capture.draws-oldDraws;vertices=capture.vertices-oldVertices;housing=counter("housingDraws")-h;panes=counter("paneDraws")-p;retained=shadow?counter("shadowDraws")-s:housing+panes;
        }
    }
    private static void measure(List<TileEntityAnimatedScreenSelector> visible,TEAnimatedScreenSelector renderer,boolean shadow)throws Exception{
        String pass=shadow?"shadow":"main";GlassFramePerformanceChecks.shadow(shadow);Submission draw=new Submission(visible,renderer,shadow);
        try(GlassFramePerformanceChecks.Target target=new GlassFramePerformanceChecks.Target()){
            for(int i=0;i<WARMUP;i++){GlassFramePerformanceChecks.setup(target);draw.run();}
            GlassFramePerformanceChecks.setup(target);draw.run();long actual=draw.draws+draw.retained;
            need(AFTER?draw.retained==16&&draw.calls==0:draw.draws>256&&draw.retained==0,"wrong actual porthole path: retained="+draw.retained+" tessellator="+draw.draws+" calls="+draw.calls);
            BufferedImage picture=GlassFramePerformanceChecks.image();Files.createDirectories(OUTPUT);ImageIO.write(picture,"png",OUTPUT.resolve(MODE+"-"+pass+".png").toFile());
            int difference=-1;Path baseline=OUTPUT.resolve("portholes-before-"+pass+".png");
            if(AFTER&&Files.exists(baseline)){difference=GlassFramePerformanceChecks.changed(ImageIO.read(baseline.toFile()),picture);need(difference==0,"porthole aperture/glimmer image changed above3 RGB levels: "+difference);}
            GlassPaneGpuTimers.start(FRAMES);
            for(int i=0;i<FRAMES;i++){
                GlassPaneGpuTimers.renderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START,0));GlassFramePerformanceChecks.setup(target);GlassPaneGpuTimers.Token timer=GlassPaneGpuTimers.begin("porthole-complete-"+pass);draw.run();GlassPaneGpuTimers.end(timer);need(draw.draws+draw.retained==actual,"stationary porthole draw count changed");
                GlassPaneGpuTimers.renderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.END,0));GL11.glFlush();Thread.sleep(HOST_PACING_MS);
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(GlassPaneGpuTimers.draining()&&System.nanoTime()<deadline){GlassPaneGpuTimers.poll();Thread.sleep(1);}
            need(!GlassPaneGpuTimers.draining(),"query drain timeout");JsonObject report=GlassPaneGpuTimers.report();need(report.get("gpu_timestamps").getAsBoolean(),"missing GPU timestamps");need(report.get("ring_dropped_intervals").getAsInt()==0,"query ring overflow");
            report.addProperty("mode",MODE);report.addProperty("target",pass);report.addProperty("cells",2048);report.addProperty("submitted_tiles",visible.size());report.addProperty("pass0_setups",draw.pass0);report.addProperty("pass1_setups",draw.pass1);report.addProperty("legacy_tessellator_calls",draw.calls);report.addProperty("legacy_nonempty_draws",draw.draws);report.addProperty("legacy_vertices",draw.vertices);report.addProperty("retained_draws",draw.retained);report.addProperty("housing_main_draws",draw.housing);report.addProperty("pane_main_draws",draw.panes);report.addProperty("total_draws",actual);report.addProperty("pixel_difference_above_3",difference);report.addProperty("actual_optifine",false);
            report.addProperty("host_pacing_ms",HOST_PACING_MS);
            report.addProperty("scope","Eight actual16x16 joined flat Portholes, all four aperture shapes and tint values, palette and static custom panes, shipped housing pixels+mipmaps; actual chunk admission lists and both TE passes. Real GLSL150 main/shadow FBOs with pass-selection facade, not actual OptiFine/shader-pack/hardware FPS acceptance.");
            Files.write(OUTPUT.resolve(MODE+"-"+pass+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));JsonObject interval=report.getAsJsonObject("intervals").getAsJsonObject("porthole-complete-"+pass);
            System.out.println("PortholePerformance "+MODE+" "+pass+" cells=2048 admitted="+visible.size()+" nonempty-draws="+actual+" legacy-calls="+draw.calls+" pixels="+difference+" cpu="+interval.get("cpu")+" gpu="+interval.get("gpu"));
        }
    }
    public static void main(String[] args)throws Exception{
        GlassPanePipelineChecks.initialize();VandorLabs.proxy=new ClientProxy();GlassAuthoredAtlas atlas=new GlassAuthoredAtlas();GlassFramePerformanceChecks.shaders();TEAnimatedScreenSelector renderer=initializeRenderer();
        BlockProgrammableWall block=new BlockProgrammableWall("performance_portholes",BlockProgrammableWall.Shape.PORTHOLE);
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            for(int section=0;section<8;section++)for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH).withProperty(BlockProgrammableWall.DEPTH,section%3);
                TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setJoinPortholes(true);tile.setPortholeShape(section%4);tile.setGlassShade(GlassPanePipelineChecks.goldShade());tile.setGlassAppearance(section%4,false);tile.setGlassMaterial(section<4?-1:ScreenHousingTextures.screenIndex("light_alloy_hull"));tile.setHousingTexture(atlas.emissiveChoices.get(section));tile.setGlassTextureLayout(0);tile.setWorld(scene.world);tile.setPos(new BlockPos(x,section*16+y,1));
                scene.world.states.put(tile.getPos(),state);scene.world.tiles.put(tile.getPos(),tile);scene.world.loadedTileEntityList.add(tile);
            }
            for(int i=0;i<24;i++)flush(scene);List<CompiledChunk> chunks=new ArrayList<>();for(int section=0;section<8;section++)chunks.add(scene.compileAndUpload((GlassPanePipelineChecks.SceneChunk)scene.view.renderChunks[section]));for(int i=0;i<24;i++)flush(scene);
            List<TileEntityAnimatedScreenSelector> visible=new ArrayList<>();for(CompiledChunk chunk:chunks)for(TileEntity tile:chunk.getTileEntities())visible.add((TileEntityAnimatedScreenSelector)tile);Collections.sort(visible,(a,b)->a.getPos().compareTo(b.getPos()));
            need(AFTER?visible.size()<=16:visible.size()==2048,"actual porthole admission count "+visible.size());long uploads=counter("uploads");
            measure(visible,renderer,false);measure(visible,renderer,true);for(int i=0;i<20;i++)flush(scene);need(counter("uploads")==uploads,"stationary portholes reuploaded");TEAnimatedScreenSelector.unloadGroups(scene.world);
        }finally{GlassFramePerformanceChecks.shadow(false);restore();GlassFramePerformanceChecks.disposeShader();ForgeHooksClient.setRenderPass(-1);retainedFlush();}
        need(GL11.glGetError()==0,"porthole benchmark GL error");System.out.println("PortholePerformance PASS mode="+MODE+" all4apertures/tints, palette/custom, exact runtime admissions and real TE draws, stationary reuse; actualOptiFine=false");
    }
    private PortholePerformanceChecks(){}
}
