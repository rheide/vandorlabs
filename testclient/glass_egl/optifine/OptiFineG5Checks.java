package com.vandorlabs.client;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.optifine.shaders.SVertexBuilder;
import net.optifine.shaders.SVertexFormat;
import net.optifine.shaders.Shaders;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import sun.misc.Unsafe;

/** Runs official G5 classes, separately from Forge-only fixtures and the pass facade.
 * This tests binary writer/attribute/state compatibility, not a shader-pack world. */
public final class OptiFineG5Checks {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Field field(Class<?> owner,String name)throws Exception{Field value=owner.getDeclaredField(name);value.setAccessible(true);return value;}
    private static final class MaterialBlock extends Block {
        final int metadata;
        MaterialBlock(int metadata){super(Material.GLASS);this.metadata=metadata;}
        @Override public int getMetaFromState(IBlockState state){return metadata;}
    }
    private static VertexFormat initialize()throws Exception{
        String supplied=System.getProperty("vandorlabs.actualOptiFineJar");
        if(supplied!=null)for(Class<?> type:new Class<?>[]{BufferBuilder.class,SVertexFormat.class,SVertexBuilder.class,Shaders.class,net.optifine.shaders.ShadersRender.class})
            need(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile().equals(new File(supplied).getCanonicalFile()),"probe loaded a shader facade or different client class: "+type.getName());
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        for(Field value:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(value.getModifiers())&&value.getType()==Minecraft.class){value.setAccessible(true);value.set(null,mc);}
        field(Minecraft.class,"mcDataDir").set(mc,new File(System.getProperty("java.io.tmpdir")));
        GameSettings settings=(GameSettings)unsafe.allocateInstance(GameSettings.class);settings.useVbo=true;mc.gameSettings=settings;
        field(Class.forName("Config"),"gameSettings").set(null,settings);
        net.minecraft.init.Bootstrap.register();
        need(GLContext.getCapabilities().OpenGL30,"probe requires OpenGL 3.0 compatibility context");
        // The pbuffer owns the context. OptiFine's initializeTextures calls
        // Display.initDisplay, which requires an X window even with EGL current.
        // Supply the real context's core capabilities without replacing any
        // OptiFine writer, layout, buffer, shader hook or GL method.
        OpenGlHelper.defaultTexUnit=GL13.GL_TEXTURE0;OpenGlHelper.lightmapTexUnit=GL13.GL_TEXTURE1;
        OpenGlHelper.vboSupported=true;OpenGlHelper.GL_ARRAY_BUFFER=GL15.GL_ARRAY_BUFFER;OpenGlHelper.GL_STATIC_DRAW=GL15.GL_STATIC_DRAW;
        Shaders.shaderPackLoaded=true;
        VertexFormat format=SVertexFormat.makeDefVertexFormatBlock();DefaultVertexFormats.BLOCK=format;
        need(format.getNextOffset()==56&&format.getNormalOffset()==28&&format.getUvOffsetById(1)==24,"actual G5 layout changed");
        need(BlockMeshVbo.supportsRetained(format),"production guard rejected actual G5 layout/hooks");
        String version=(String)Class.forName("Config").getMethod("getVersion").invoke(null);
        need("OptiFine_1.12.2_HD_U_G5".equals(version),"probe requires the verified official G5 contract");
        System.out.println("Actual OptiFine="+version+" renderer="+GL11.glGetString(GL11.GL_RENDERER));
        return format;
    }
    private static int[] capture(VertexFormat format,int id,int metadata,double left,double right){
        MaterialBlock block=new MaterialBlock(metadata);Block.REGISTRY.register(id,new ResourceLocation("vandorlabs","g5_probe_"+id),block);
        BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(GL11.GL_QUADS,format);
        SVertexBuilder.pushEntity(block.getDefaultState(),BlockPos.ORIGIN,null,buffer);
        for(int vertex=0;vertex<4;vertex++){
            int u=vertex<2?0:1,v=vertex==0||vertex==3?0:1;
            buffer.pos(u==0?left:right,.1+.8*v,0).color(1F,1F,1F,1F).tex(.2+.6*u,.2+.6*v).lightmap(128,240).normal(0,0,1).endVertex();
        }
        SVertexBuilder.popEntity(buffer);buffer.finishDrawing();int[] raw=buffer.getVertexState().getRawBuffer();
        for(int at=0;at<raw.length;at+=14){
            need((raw[at+12]&65535)==id&&raw[at+13]==metadata,"real pushEntity/writer lost component material or metadata");
            need(raw[at+6]==((128<<16)|240),"actual writer changed frame lightmap");
            need(Math.abs((byte)(raw[at+7]>>16))>=126&&(raw[at+7]&65535)==0,"actual SVertexBuilder did not compute quad normal");
            need(Float.intBitsToFloat(raw[at+8])==.5F&&Float.intBitsToFloat(raw[at+9])==.5F,"actual midpoint UV missing");
            need(Math.abs((short)raw[at+10])>=32760&&Math.abs((short)(raw[at+11]>>16))==32767,"actual tangent/handedness missing");
        }
        return raw;
    }
    private static int compile(int type,String source){
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        need(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,"probe shader compile: "+GL20.glGetShaderInfoLog(shader,8192));return shader;
    }
    private static int program(){
        int vertex=compile(GL20.GL_VERTEX_SHADER,"#version 120\nattribute vec2 probeMid; attribute vec4 probeTangent; attribute vec3 probeEntity; varying vec3 payload; void main(){gl_Position=vec4(gl_Vertex.xy*2.0-1.0,0.0,1.0);payload=vec3(probeEntity.x/1024.0,probeMid.x,abs(probeTangent.x)/32767.0);}");
        int fragment=compile(GL20.GL_FRAGMENT_SHADER,"#version 120\nvarying vec3 payload;void main(){gl_FragColor=vec4(payload,1.0);}");
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);
        GL20.glBindAttribLocation(program,Shaders.midTexCoordAttrib,"probeMid");GL20.glBindAttribLocation(program,Shaders.tangentAttrib,"probeTangent");GL20.glBindAttribLocation(program,Shaders.entityAttrib,"probeEntity");
        GL20.glLinkProgram(program);need(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,"probe link: "+GL20.glGetProgramInfoLog(program,8192));GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);return program;
    }
    private static int attrib(int index,int query){java.nio.IntBuffer out=BufferUtils.createIntBuffer(4);GL20.glGetVertexAttrib(index,query,out);return out.get(0);}
    private static void draw(VertexBuffer buffer,VertexFormat format,int program,boolean shadow){
        Shaders.isShadowPass=shadow;Shaders.activeProgram=shadow?Shaders.ProgramShadow:Shaders.ProgramBlock;Shaders.activeProgramID=program;
        Object selected=Shaders.activeProgram;GL20.glUseProgram(program);GL11.glViewport(0,0,256,256);GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.23F);GL11.glDepthMask(true);
        GlStateManager.disableCull();GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);int framebuffer=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        BlockMeshVbo.draw(buffer,format,BlockRenderLayer.TRANSLUCENT,false);
        need(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program&&Shaders.activeProgram==selected&&Shaders.activeProgramID==program,"real G5 VBO hooks changed caller's main/shadow program");
        need(GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)==framebuffer,"real G5 VBO hooks changed framebuffer");
        need(GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glIsEnabled(GL11.GL_ALPHA_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"real G5 VBO hooks changed frame blend/alpha/depth state");
        need(Math.abs(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)-.23F)<1E-6,"real G5 VBO hooks changed alpha threshold");
        need(!GL11.glIsEnabled(GL11.GL_CULL_FACE),"forced two-sided frame state leaked");
        for(int index:new int[]{Shaders.midTexCoordAttrib,Shaders.tangentAttrib,Shaders.entityAttrib})need(attrib(index,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED)==0,"shader attribute array leaked enabled");
        for(int x:new int[]{64,192}){
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,128,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            int expected=(int)Math.round((x<128?700:701)*255D/1024);
            need(Math.abs((pixel.get(0)&255)-expected)<=1&&Math.abs((pixel.get(1)&255)-128)<=1&&(pixel.get(2)&255)>=254,"actual G5 attributes did not reach framebuffer at "+x+": "+(pixel.get(0)&255)+","+(pixel.get(1)&255)+","+(pixel.get(2)&255));
        }
        need(GL11.glGetError()==0,"actual G5 retained draw left a GL error");
    }
    public static void main(String[] args)throws Exception{
        VertexFormat format=initialize();int[] left=capture(format,700,5,.1,.45),right=capture(format,701,9,.55,.9);
        BufferBuilder joined=new BufferBuilder(8192);joined.begin(GL11.GL_QUADS,format);joined.sVertexBuilder.pushEntity(0x7777000055554444L);
        int[] records=BlockMeshVbo.mergeRetained(joined,Arrays.asList(left,right),format);joined.sVertexBuilder.popEntity();joined.finishDrawing();
        int[] actual=joined.getVertexState().getRawBuffer();need(Arrays.equals(records,actual),"production merge rewrote shader records");
        need(Arrays.equals(Arrays.copyOfRange(actual,0,left.length),left)&&Arrays.equals(Arrays.copyOfRange(actual,left.length,actual.length),right),"production merge lost per-component material payload");
        BufferBuilder bulk=new BufferBuilder(8192);bulk.begin(GL11.GL_QUADS,format);bulk.sVertexBuilder.pushEntity(0x7777000055554444L);
        bulk.addVertexData(records.clone());bulk.sVertexBuilder.popEntity();bulk.finishDrawing();
        need(bulk.getVertexState().getRawBuffer()[12]!=records[12],"negative control did not exercise actual G5 bulk-upload entity rewriting");
        VertexBuffer buffer=new VertexBuffer(format);buffer.bufferData(joined.getByteBuffer());int program=program();
        try{draw(buffer,format,program,false);draw(buffer,format,program,true);}finally{GL20.glUseProgram(0);GL20.glDeleteProgram(program);buffer.deleteGlBuffers();Shaders.shaderPackLoaded=false;Shaders.isShadowPass=false;}
        System.out.println("ActualOptiFineG5 PASS checks="+checks+" official BufferBuilder/SVertexFormat/SVertexBuilder 56-byte normal/midUV/tangent/entity records; distinct block IDs and metadata preserved by production retained merge; actual ShadersRender hooks + production BlockMeshVbo in EGL; shader attributes reached pixels; caller program/framebuffer/blend/alpha/depth preserved in main and shadow states. Full Forge client/world/shader-pack acceptance was not run.");
    }
    private OptiFineG5Checks(){}
}
