package com.vandorlabs.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Matched fixed-view comparison of ordinary pane passes and retained groups.
 * Run before/after in separate JVMs with the identical fixture and baseline
 * classes first on the before classpath. Actual GLSL150 and separate FBOs are
 * used; a scoped pass-selection facade is not an actual OptiFine shader pack.
 */
public final class GlassPanePerformanceChecks {
    private static final int WIDTH=256,HEIGHT=384,WARMUP=24,FRAMES=120;
    private static final String MODE=System.getProperty("vandorlabs.glassBenchmarkMode","after");
    private static final Path OUTPUT=Paths.get(System.getProperty("vandorlabs.glassBenchmarkOutput","build/test-artifacts/glass-performance"));
    private static int program;
    private static final class Target implements AutoCloseable {
        final int framebuffer,color,depth;
        Target(){
            framebuffer=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
            color=GL11.glGenTextures();GlStateManager.bindTexture(color);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,WIDTH,HEIGHT,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL14.GL_DEPTH_COMPONENT24,WIDTH,HEIGHT);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
            need(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"framebuffer incomplete");
        }
        void bind(){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);GL11.glViewport(0,0,WIDTH,HEIGHT);}
        public void close(){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(framebuffer);GL30.glDeleteRenderbuffers(depth);GL11.glDeleteTextures(color);}
    }
    private static void need(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static int shader(int type,String source){int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);need(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(shader,8192));return shader;}
    private static void shaders(){
        int vertex=shader(GL20.GL_VERTEX_SHADER,"#version 150 compatibility\nout vec2 artwork; out vec2 illumination; out vec4 tint;\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;artwork=gl_MultiTexCoord0.xy;illumination=(gl_TextureMatrix[1]*gl_MultiTexCoord1).xy;tint=gl_Color;}\n");
        int fragment=shader(GL20.GL_FRAGMENT_SHADER,"#version 150 compatibility\nuniform sampler2D atlas; uniform sampler2D lightmap; in vec2 artwork; in vec2 illumination; in vec4 tint; out vec4 result;\nvoid main(){vec4 surface=texture(atlas,artwork)*tint;if(surface.a<0.003)discard;result=vec4(surface.rgb*texture(lightmap,illumination).rgb,surface.a);}\n");
        program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);need(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program,8192));GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);
        GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"atlas"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"lightmap"),1);
    }
    private static void setup(Target target){
        target.bind();GL20.glUseProgram(program);
        GlStateManager.depthMask(true);GlStateManager.clearColor(.12F,.18F,.26F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);GlStateManager.enableTexture2D();GlStateManager.bindTexture(GlassPanePipelineChecks.lightTexture);
        GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glLoadIdentity();GL11.glScalef(1F/256,1F/256,1F/256);GL11.glTranslatef(8,8,8);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(GlassPanePipelineChecks.atlasTexture);
        // Avoid exactly half-texel nearest-sampling ties between atlas cells.
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(.013,16.013,.037,32.037,-20,20);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
    }
    private static BufferedImage image(){
        ByteBuffer pixels=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int r=pixels.get()&255,g=pixels.get()&255,b=pixels.get()&255,a=pixels.get()&255;image.setRGB(x,HEIGHT-1-y,(a<<24)|(r<<16)|(g<<8)|b);}
        return image;
    }
    private static int changed(BufferedImage a,BufferedImage b){int count=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);for(int shift:new int[]{0,8,16})if(Math.abs((p>>shift&255)-(q>>shift&255))>3){count++;break;}}return count;}
    private static final class Retained implements AutoCloseable {
        final List<TileEntityProgrammableGlass> anchors=new ArrayList<>();
        final Method flush;final long uploads;
        Retained(GlassPanePipelineChecks.Fixture scene)throws Exception{
            // The same compiled runner remains loadable against the previous
            // build. Only after mode resolves this new production manager.
            Class<?> owner=Class.forName("com.vandorlabs.client.GlassPaneRetained");
            flush=owner.getMethod("flush");flush.invoke(null);
            Method handles=owner.getMethod("handles",TileEntityProgrammableGlass.class),anchor=owner.getMethod("anchor",TileEntityProgrammableGlass.class);
            for(TileEntityProgrammableGlass tile:scene.cells){
                need((Boolean)handles.invoke(null,tile),"real manager did not retain benchmark cell");
                if((Boolean)anchor.invoke(null,tile))anchors.add(tile);
            }
            need(anchors.size()==6,"expected six actual section/plane anchors");
            Field count=owner.getDeclaredField("uploads");count.setAccessible(true);uploads=count.getLong(null);
        }
        public void close()throws Exception{GlassPanePipelineChecks.mc.world=null;flush.invoke(null);}
    }
    private static final class Submission implements Runnable {
        final GlassPanePipelineChecks.Fixture scene;final List<TileEntityProgrammableGlass> visible;final boolean shadow;
        final TEProgrammableGlass renderer=new TEProgrammableGlass();final Field retainedDraws;int draws;
        Submission(GlassPanePipelineChecks.Fixture scene,List<TileEntityProgrammableGlass> visible,boolean shadow)throws Exception{
            this.scene=scene;this.visible=visible;this.shadow=shadow;
            renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
            if(MODE.equals("after")){retainedDraws=Class.forName("com.vandorlabs.client.GlassPaneRetained").getDeclaredField(shadow?"shadowDraws":"mainDraws");retainedDraws.setAccessible(true);}else retainedDraws=null;
        }
        private void render(){for(TileEntityProgrammableGlass tile:visible){BlockPos p=tile.getPos();renderer.render(tile,p.getX(),p.getY(),p.getZ(),0,-1,1);}}
        public void run(){try{
            long old=retainedDraws==null?0:retainedDraws.getLong(null);
            if(shadow){ForgeHooksClient.setRenderPass(1);render();}
            else{
                GlazingRenderOrder.begin(scene.world);
                try{ForgeHooksClient.setRenderPass(0);render();ForgeHooksClient.setRenderPass(1);render();}
                finally{GlazingRenderOrder.end();}
            }
            draws=retainedDraws==null?(shadow?visible.size():GlassPaneBatch.draws):(int)(retainedDraws.getLong(null)-old);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    }
    private static void shadow(boolean value)throws Exception{Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass").setBoolean(null,value);}
    private static void measure(GlassPanePipelineChecks.Fixture scene,boolean shadow,Submission draw)throws Exception{
        String pass=shadow?"shadow-target":"main-target";shadow(shadow);
        try(Target target=new Target()){
            for(int i=0;i<WARMUP;i++){setup(target);draw.run();}
            setup(target);draw.run();int calls=draw.draws;need(calls>0,"scene submitted no actual draws");BufferedImage picture=image();Files.createDirectories(OUTPUT);Path png=OUTPUT.resolve(MODE+"-"+pass+".png");ImageIO.write(picture,"png",png.toFile());
            int difference=-1;Path baseline=OUTPUT.resolve("before-"+pass+".png");
            if(MODE.equals("after")&&Files.exists(baseline)){difference=changed(ImageIO.read(baseline.toFile()),picture);need(difference==0,"retained glimmer or holes differ from baseline beyond3 RGB levels: "+difference);}
            GlassPaneGpuTimers.start(FRAMES);
            for(int i=0;i<FRAMES;i++){
                GlassPaneGpuTimers.renderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START,0));setup(target);
                GlassPaneGpuTimers.Token token=GlassPaneGpuTimers.begin("scene-"+pass);draw.run();GlassPaneGpuTimers.end(token);need(draw.draws==calls,"draw count changed during stationary capture");
                GlassPaneGpuTimers.renderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.END,0));GL11.glFlush();
                // Equal host pacing outside all measured draw/render intervals
                // prevents an unbounded synthetic producer outrunning the query ring.
                Thread.sleep(2);
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(GlassPaneGpuTimers.draining()&&System.nanoTime()<deadline){GlassPaneGpuTimers.poll();Thread.sleep(1);}
            need(!GlassPaneGpuTimers.draining(),"GPU queries did not finish");JsonObject report=GlassPaneGpuTimers.report();
            need(report.get("gpu_timestamps").getAsBoolean(),"GPU timer queries unavailable");need(report.get("ring_dropped_intervals").getAsInt()==0,"measurement ring overflow");
            report.addProperty("mode",MODE);report.addProperty("scene_cells",scene.cells.size());report.addProperty("draws_per_scene",calls);report.addProperty("target",pass);report.addProperty("shader","GLSL150 compatibility textured/lightmapped pane fixture");report.addProperty("actual_optifine",false);report.addProperty("pixel_difference_above_3",difference);
            report.addProperty("submitted_tile_count",draw.visible.size());report.addProperty("draw_vertex_stride",BlockSurfaceFormat.get().getNextOffset());
            report.addProperty("projection","orthographic left=.013 right=16.013 bottom=.037 top=32.037 near=-20 far=20; identity modelview");
            report.addProperty("measurement_scope","Actual glass collection/sorting/batching for main, direct shadow fallback before and real retained-anchor TE path after, in deterministic isolated world. Excludes full RenderGlobal traversal and actual OptiFine shader pack.");
            Files.write(OUTPUT.resolve(MODE+"-"+pass+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
            System.out.println("GlassPanePerformance "+MODE+" "+pass+" cells="+scene.cells.size()+" draws="+calls+" pixels="+difference+" "+report.getAsJsonObject("intervals").getAsJsonObject("scene-"+pass).get("cpu")+" gpu="+report.getAsJsonObject("intervals").getAsJsonObject("scene-"+pass).get("gpu"));
        }
    }
    public static void main(String[] args)throws Exception{
        need(MODE.equals("before")||MODE.equals("after"),"mode must be before or after");GlassPanePipelineChecks.initialize();GlassPanePipelineChecks.mc.displayHeight=HEIGHT;shaders();ForgeHooksClient.setRenderPass(1);
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(16,32,false,true);List<TileEntityProgrammableGlass> first=new ArrayList<>(scene.cells);
            for(int plane=1;plane<3;plane++)for(TileEntityProgrammableGlass old:first){
                TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setAppearance(1,false);tile.setJoin(true);tile.setShade(GlassPanePipelineChecks.goldShade());tile.setWorld(scene.world);tile.setPos(old.getPos().south(plane*2));
                scene.world.states.put(tile.getPos(),GlassPanePipelineChecks.block.getDefaultState());scene.world.tiles.put(tile.getPos(),tile);scene.world.loadedTileEntityList.add(tile);scene.cells.add(tile);
            }
            if(MODE.equals("before")){
                measure(scene,false,new Submission(scene,scene.cells,false));measure(scene,true,new Submission(scene,scene.cells,true));
            }else try(Retained retained=new Retained(scene)){
                measure(scene,false,new Submission(scene,retained.anchors,false));measure(scene,true,new Submission(scene,retained.anchors,true));
                Field uploads=Class.forName("com.vandorlabs.client.GlassPaneRetained").getDeclaredField("uploads");uploads.setAccessible(true);
                need(uploads.getLong(null)==retained.uploads,"stationary retained scene reuploads during measured frames");
            }
        }finally{shadow(false);GL20.glUseProgram(0);GL20.glDeleteProgram(program);ForgeHooksClient.setRenderPass(-1);}
        need(GL11.glGetError()==0,"benchmark left GL error");System.out.println("GlassPanePerformance PASS mode="+MODE+" fixed camera, six section/plane groups, authored glimmer, two framebuffers, deferred GPU queries; actualOptiFine=false");
    }
    private GlassPanePerformanceChecks(){}
}
