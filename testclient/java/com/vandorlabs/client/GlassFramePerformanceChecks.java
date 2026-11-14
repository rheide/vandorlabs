package com.vandorlabs.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.vandorlabs.ClientProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Exact branch-before/current comparison of the formerly per-cell emissive-frame path. */
public final class GlassFramePerformanceChecks {
    private static final int WIDTH=256,HEIGHT=1024,WARMUP=16,FRAMES=80;
    private static final String MODE=System.getProperty("vandorlabs.glassBenchmarkMode","frames-after");
    private static final boolean AFTER=MODE.endsWith("after");
    private static final Path OUTPUT=Paths.get(System.getProperty("vandorlabs.glassBenchmarkOutput","build/test-artifacts/glass-performance"));
    private static int program;
    private static Capture capture;
    private static Field tessellator;
    private static Object originalTessellator;
    private static final class Capture extends Tessellator {
        long draws,vertices;
        Capture(){super(65536);}
        @Override public void draw(){draws++;vertices+=getBuffer().getVertexCount();super.draw();}
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static long counter(String owner,String name)throws Exception{
        Field field=Class.forName("com.vandorlabs.client."+owner).getDeclaredField(name);field.setAccessible(true);return field.getLong(null);
    }
    private static void frameFlush()throws Exception{if(AFTER)Class.forName("com.vandorlabs.client.GlassFrameRetained").getMethod("flush").invoke(null);}
    private static void flush(GlassPanePipelineChecks.Fixture scene)throws Exception{
        scene.flush();frameFlush();GlassPaneRetained.flush();scene.flush();
    }
    private static void installCapture()throws Exception{
        for(Field field:Tessellator.class.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==Tessellator.class)tessellator=field;
        need(tessellator!=null,"missing Tessellator singleton");tessellator.setAccessible(true);originalTessellator=tessellator.get(null);capture=new Capture();
        GlassPanePipelineChecks.unsafe.putObject(GlassPanePipelineChecks.unsafe.staticFieldBase(tessellator),GlassPanePipelineChecks.unsafe.staticFieldOffset(tessellator),capture);
    }
    private static void restoreCapture(){if(tessellator!=null)GlassPanePipelineChecks.unsafe.putObject(GlassPanePipelineChecks.unsafe.staticFieldBase(tessellator),GlassPanePipelineChecks.unsafe.staticFieldOffset(tessellator),originalTessellator);}
    static final class Target implements AutoCloseable {
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
    private static int shader(int type,String source){int value=GL20.glCreateShader(type);GL20.glShaderSource(value,source);GL20.glCompileShader(value);need(GL20.glGetShaderi(value,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(value,8192));return value;}
    static void shaders(){
        int vertex=shader(GL20.GL_VERTEX_SHADER,"#version 150 compatibility\nout vec2 artwork; out vec2 illumination; out vec4 tint;\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;artwork=gl_MultiTexCoord0.xy;illumination=(gl_TextureMatrix[1]*gl_MultiTexCoord1).xy;tint=gl_Color;}\n");
        int fragment=shader(GL20.GL_FRAGMENT_SHADER,"#version 150 compatibility\nuniform sampler2D atlas; uniform sampler2D lightmap; in vec2 artwork; in vec2 illumination; in vec4 tint; out vec4 result;\nvoid main(){vec4 surface=texture(atlas,artwork)*tint;if(surface.a<0.003)discard;result=vec4(surface.rgb*texture(lightmap,illumination).rgb,surface.a);}\n");
        program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);need(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program,8192));GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);
        GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"atlas"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"lightmap"),1);
    }
    static void setup(Target target){
        target.bind();GL20.glUseProgram(program);GlStateManager.depthMask(true);GlStateManager.clearColor(.12F,.18F,.26F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.color(1,1,1,1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);GlStateManager.enableTexture2D();GlStateManager.bindTexture(GlassPanePipelineChecks.lightTexture);
        GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glLoadIdentity();GL11.glScalef(1F/256,1F/256,1F/256);GL11.glTranslatef(8,8,8);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(GlassPanePipelineChecks.atlasTexture);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(.013,16.013,.037,128.037,-20,20);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
    }
    static BufferedImage image(){
        ByteBuffer pixels=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage out=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int r=pixels.get()&255,g=pixels.get()&255,b=pixels.get()&255,a=pixels.get()&255;out.setRGB(x,HEIGHT-1-y,a<<24|r<<16|g<<8|b);}return out;
    }
    static int changed(BufferedImage a,BufferedImage b){int count=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);for(int shift:new int[]{0,8,16})if(Math.abs((p>>shift&255)-(q>>shift&255))>3){count++;break;}}return count;}
    static void disposeShader(){GL20.glUseProgram(0);GL20.glDeleteProgram(program);}
    private static final class Submission {
        final List<TileEntityProgrammableGlass> visible;
        final TEProgrammableGlass renderer=new TEProgrammableGlass();
        final boolean shadow;
        int frameDraws,paneDraws,legacyDraws,pass0,pass1;
        Submission(List<TileEntityProgrammableGlass> visible,boolean shadow){this.visible=visible;this.shadow=shadow;renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);}
        void run()throws Exception{
            String field=shadow?"shadowDraws":"mainDraws";long frame=AFTER?counter("GlassFrameRetained",field):0,pane=counter("GlassPaneRetained",field),legacy=capture.draws;pass0=pass1=0;
            for(int pass=0;pass<2;pass++){
                ForgeHooksClient.setRenderPass(pass);
                for(TileEntityProgrammableGlass tile:visible)if(tile.shouldRenderInPass(pass)){
                    if(pass==0)pass0++;else pass1++;
                    BlockPos pos=tile.getPos();renderer.render(tile,pos.getX(),pos.getY(),pos.getZ(),0,-1,1);
                }
            }
            frameDraws=AFTER?(int)(counter("GlassFrameRetained",field)-frame):0;paneDraws=(int)(counter("GlassPaneRetained",field)-pane);legacyDraws=(int)(capture.draws-legacy);
        }
        int draws(){return frameDraws+paneDraws+legacyDraws;}
    }
    static void shadow(boolean value)throws Exception{Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass").setBoolean(null,value);}
    private static void measure(List<TileEntityProgrammableGlass> visible,boolean shadow,GlassAuthoredAtlas atlas)throws Exception{
        String pass=shadow?"shadow":"main";shadow(shadow);Submission draw=new Submission(visible,shadow);
        try(Target target=new Target()){
            for(int i=0;i<WARMUP;i++){setup(target);draw.run();}
            setup(target);draw.run();int calls=draw.draws();
            need(draw.paneDraws==8,"eight stationary pane sections must stay eight draws, got "+draw.paneDraws);
            need(AFTER?draw.frameDraws==8&&draw.legacyDraws==0:draw.legacyDraws==544,"wrong actual frame path: retained="+draw.frameDraws+" legacy="+draw.legacyDraws);
            BufferedImage picture=image();Files.createDirectories(OUTPUT);ImageIO.write(picture,"png",OUTPUT.resolve(MODE+"-"+pass+".png").toFile());
            int difference=-1;Path baseline=OUTPUT.resolve("frames-before-"+pass+".png");if(AFTER&&Files.exists(baseline)){difference=changed(ImageIO.read(baseline.toFile()),picture);need(difference==0,"authored frame/glimmer image changed above 3 RGB levels: "+difference);}
            GlassPaneGpuTimers.start(FRAMES);
            for(int i=0;i<FRAMES;i++){
                GlassPaneGpuTimers.renderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START,0));setup(target);GlassPaneGpuTimers.Token timer=GlassPaneGpuTimers.begin("authored-frame-and-pane-"+pass);draw.run();GlassPaneGpuTimers.end(timer);need(draw.draws()==calls,"stationary draw count changed");
                GlassPaneGpuTimers.renderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.END,0));GL11.glFlush();Thread.sleep(2);
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(GlassPaneGpuTimers.draining()&&System.nanoTime()<deadline){GlassPaneGpuTimers.poll();Thread.sleep(1);}
            need(!GlassPaneGpuTimers.draining(),"query drain timeout");JsonObject report=GlassPaneGpuTimers.report();need(report.get("gpu_timestamps").getAsBoolean(),"missing GPU timestamps");need(report.get("ring_dropped_intervals").getAsInt()==0,"query ring overflow");
            report.addProperty("mode",MODE);report.addProperty("target",pass);report.addProperty("cells",2040);report.addProperty("authored_emissive_frame_assets",atlas.emissiveChoices.size());report.addProperty("atlas_mipmap_levels",GlassAuthoredAtlas.MIPMAPS);
            report.addProperty("submitted_tiles",visible.size());report.addProperty("pass0_setups",draw.pass0);report.addProperty("pass1_setups",draw.pass1);report.addProperty("frame_vbo_draws",draw.frameDraws);report.addProperty("legacy_frame_draws",draw.legacyDraws);report.addProperty("pane_vbo_draws",draw.paneDraws);report.addProperty("total_draws",calls);report.addProperty("pixel_difference_above_3",difference);report.addProperty("actual_optifine",false);
            report.addProperty("scope","Real RenderChunk admission lists and both TE passes, authored partial-alpha frame assets and generated rail crops with four mip levels, eight joined 16x16 walls each with one hole; separate main/shadow FBOs and GLSL150 fixture. Shader pass selection facade only; not full OptiFine/shader-pack or hardware FPS acceptance.");
            Files.write(OUTPUT.resolve(MODE+"-"+pass+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
            JsonObject interval=report.getAsJsonObject("intervals").getAsJsonObject("authored-frame-and-pane-"+pass);
            System.out.println("GlassFramePerformance "+MODE+" "+pass+" cells=2040 admitted="+visible.size()+" frame="+(draw.frameDraws+draw.legacyDraws)+" pane="+draw.paneDraws+" pixels="+difference+" cpu="+interval.get("cpu")+" gpu="+interval.get("gpu"));
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("vandorlabs.staticGlassRenderer","groups");GlassPanePipelineChecks.initialize();VandorLabs.proxy=new ClientProxy();GlassAuthoredAtlas atlas=new GlassAuthoredAtlas();GlassPanePipelineChecks.mc.displayHeight=HEIGHT;shaders();installCapture();
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(16,16,true,true);List<TileEntityProgrammableGlass> first=new ArrayList<>(scene.cells);
            for(TileEntityProgrammableGlass tile:first)tile.frameAppearance.front=atlas.emissiveChoices.get(0);
            for(int section=1;section<8;section++)for(TileEntityProgrammableGlass old:first){
                TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setAppearance(1,true);tile.setJoin(true);tile.setShade(GlassPanePipelineChecks.goldShade());tile.frameAppearance.front=atlas.emissiveChoices.get(section);tile.setWorld(scene.world);tile.setPos(old.getPos().up(section*16));
                scene.world.states.put(tile.getPos(),GlassPanePipelineChecks.block.getDefaultState());scene.world.tiles.put(tile.getPos(),tile);scene.world.loadedTileEntityList.add(tile);scene.cells.add(tile);
            }
            scene.queue();for(int i=0;i<24;i++)flush(scene);
            List<CompiledChunk> chunks=new ArrayList<>();for(int i=0;i<8;i++)chunks.add(scene.compileAndUpload((GlassPanePipelineChecks.SceneChunk)scene.view.renderChunks[i]));for(int i=0;i<24;i++)flush(scene);
            List<TileEntityProgrammableGlass> visible=new ArrayList<>();for(CompiledChunk chunk:chunks)for(TileEntity tile:chunk.getTileEntities())visible.add((TileEntityProgrammableGlass)tile);Collections.sort(visible,(a,b)->a.getPos().compareTo(b.getPos()));
            need(visible.size()==(AFTER?8:544),"actual admission mismatch "+visible.size()+" "+GlassPaneTerrain.diagnostics());
            long paneUploads=counter("GlassPaneRetained","uploads"),frameUploads=AFTER?counter("GlassFrameRetained","uploads"):0;
            measure(visible,false,atlas);measure(visible,true,atlas);for(int i=0;i<20;i++)flush(scene);
            need(counter("GlassPaneRetained","uploads")==paneUploads,"stationary panes reuploaded");if(AFTER)need(counter("GlassFrameRetained","uploads")==frameUploads,"stationary frames reuploaded");
        }finally{shadow(false);restoreCapture();GL20.glUseProgram(0);GL20.glDeleteProgram(program);ForgeHooksClient.setRenderPass(-1);frameFlush();GlassPaneRetained.flush();}
        need(GL11.glGetError()==0,"frame benchmark GL error");System.out.println("GlassFramePerformance PASS mode="+MODE+" actual eight authored blended emissive assets, both render passes, actual admission lists, stationary upload reuse; actualOptiFine=false");
    }
    private GlassFramePerformanceChecks(){}
}
