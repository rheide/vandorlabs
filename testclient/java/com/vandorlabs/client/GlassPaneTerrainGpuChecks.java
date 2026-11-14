package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

/** Real framebuffer checks, run with an existing OpenGL compatibility context. */
public final class GlassPaneTerrainGpuChecks {
    private static final int WIDTH=512,HEIGHT=384;
    private static int checks,draws;
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String name,int width,int height,int atlasWidth,int atlasHeight,int x,int y){
            super("vandorlabs:checks/"+name);setIconWidth(width);setIconHeight(height);initSprite(atlasWidth,atlasHeight,x,y,false);
        }
    }
    private static final class Model implements IBakedModel {
        final GlassPaneTerrainMesh mesh;
        Model(GlassPaneTerrainMesh mesh){this.mesh=mesh;}
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?mesh.quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}
        public boolean isGui3d(){return false;}
        public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return mesh.sprite;}
        public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
        public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    }
    private static final class Access implements IBlockAccess {
        public TileEntity getTileEntity(BlockPos pos){return null;}
        public int getCombinedLight(BlockPos pos,int emission){return 0x00f000f0;}
        public IBlockState getBlockState(BlockPos pos){return Blocks.AIR.getDefaultState();}
        public boolean isAirBlock(BlockPos pos){return true;}
        public Biome getBiome(BlockPos pos){return Biomes.PLAINS;}
        public int getStrongPower(BlockPos pos,EnumFacing side){return 0;}
        public WorldType getWorldType(){return WorldType.DEFAULT;}
        public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean fallback){return false;}
    }
    private static void need(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private static int texture(BufferedImage image){
        ByteBuffer pixels=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int argb=image.getRGB(x,y);pixels.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>>24));
        }
        pixels.flip();int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        return texture;
    }
    private static final int GL12_CLAMP_TO_EDGE=33071;
    private static BufferedImage solid(int width,int height,int argb){
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,argb);return image;
    }
    private static void setup(int texture,int light,boolean rotated,boolean back,boolean cull,boolean unit){
        GL11.glViewport(0,0,WIDTH,HEIGHT);GL11.glDepthMask(true);GL11.glClearColor(.12F,.18F,.26F,1);GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
        // Match the native translucent terrain pass: depth tests but no writes.
        GL11.glDepthMask(false);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1F);
        GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_DITHER);
        if(cull)GL11.glEnable(GL11.GL_CULL_FACE);else GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);GL11.glFrontFace(GL11.GL_CCW);GL11.glColor4f(1,1,1,1);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,unit?1:4,0,unit?1:3,-4,4);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        if(back)GL11.glScalef(1,1,-1);if(rotated)GL11.glRotatef(90,0,1,0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,light);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
    }
    private static BufferBuilder emit(GlassPaneTerrainMesh mesh,boolean forge,boolean rotated){
        BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
        BlockModelRenderer renderer=forge?new ForgeBlockModelRenderer(new BlockColors()):new BlockModelRenderer(new BlockColors());
        for(int x=0;x<4;x++)for(int y=0;y<3;y++)if(x!=1||y!=1){
            BlockPos pos=rotated?new BlockPos(0,y,x):new BlockPos(x,y,0);
            need(renderer.renderModelFlat(new Access(),new Model(mesh),Blocks.GLASS.getDefaultState(),pos,buffer,false,0),"native renderer omitted a pane");
        }
        need(buffer.getVertexCount()==11*8,"unexpected native scene vertex count");return buffer;
    }
    private static void draw(BufferBuilder buffer,boolean retained){
        buffer.finishDrawing();
        if(!retained)new WorldVertexBufferUploader().draw(buffer);
        else{
            VertexBuffer vbo=new VertexBuffer(DefaultVertexFormats.BLOCK);VertexBufferUploader uploader=new VertexBufferUploader();
            uploader.setVertexBuffer(vbo);uploader.draw(buffer);vbo.bindBuffer();
            int stride=DefaultVertexFormats.BLOCK.getNextOffset();
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glVertexPointer(3,GL11.GL_FLOAT,stride,0L);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);GL11.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,stride,12L);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);GL11.glTexCoordPointer(2,GL11.GL_FLOAT,stride,16L);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE1);GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);GL11.glTexCoordPointer(2,GL11.GL_SHORT,stride,24L);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);vbo.drawArrays(GL11.GL_QUADS);vbo.unbindBuffer();
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE1);GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);vbo.deleteGlBuffers();
        }
        draws++;need(GL11.glGetError()==GL11.GL_NO_ERROR,"native draw left an OpenGL error");
    }
    private static byte[] read(){
        ByteBuffer buffer=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
        byte[] data=new byte[buffer.remaining()];buffer.get(data);return data;
    }
    private static int channel(byte[] pixels,int x,int y,int channel){return pixels[(y*WIDTH+x)*4+channel]&255;}
    private static long difference(byte[] a,byte[] b){long result=0;for(int i=0;i<a.length;i++)if((i&3)!=3)result+=Math.abs((a[i]&255)-(b[i]&255));return result;}
    private static byte[] scene(int texture,int light,GlassPaneTerrainMesh mesh,boolean forge,boolean retained,boolean rotated,boolean back,boolean cull){
        setup(texture,light,rotated,back,cull,false);draw(emit(mesh,forge,rotated),retained);return read();
    }
    private static void sceneGeometry(byte[] pixels){
        // Empty central cell remains clear; surviving cells receive native panes.
        int hx=WIDTH*3/8,hy=HEIGHT/2;
        for(int c=0;c<3;c++)need(Math.abs(channel(pixels,hx,hy,c)-new int[]{31,46,66}[c])<=1,"opening filled with pane geometry");
        need(Math.abs(channel(pixels,WIDTH/8,HEIGHT/6,0)-31)>8,"visible Gold pane missing");
    }
    private static byte[] order(int texture,int light,Sprite red,Sprite blue,boolean sort,boolean correct){
        setup(texture,light,false,false,true,true);
        BufferBuilder buffer=new BufferBuilder(2048);buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
        BlockModelRenderer renderer=new ForgeBlockModelRenderer(new BlockColors());
        for(int i=0;i<2;i++){
            boolean near=correct?i==1:i==0;
            GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(false,0,near?blue:red,true,DefaultVertexFormats.BLOCK);
            need(renderer.renderModelFlat(new Access(),new Model(mesh),Blocks.GLASS.getDefaultState(),new BlockPos(0,0,near?1:0),buffer,false,0),"ordering pane missing");
        }
        if(sort)buffer.sortVertexData(.5F,.5F,3F);draw(buffer,true);return read();
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        OpenGlHelper.defaultTexUnit=GL13.GL_TEXTURE0;OpenGlHelper.lightmapTexUnit=GL13.GL_TEXTURE1;
        OpenGlHelper.GL_ARRAY_BUFFER=GL15.GL_ARRAY_BUFFER;OpenGlHelper.GL_STATIC_DRAW=GL15.GL_STATIC_DRAW;
        need(DefaultVertexFormats.BLOCK.getNextOffset()==28,"fixture expects native block vertex layout");
        System.out.println("GL "+GL11.glGetString(GL11.GL_VERSION)+" renderer="+GL11.glGetString(GL11.GL_RENDERER));
        BufferedImage gold=ImageIO.read(new File("texture-packs/additional/assets/vandorlabs/textures/blocks/programmable_glass/low/gold.png"));
        need(gold!=null,"authored Gold source missing");
        BufferedImage authored=GlassPaneTextures.transform(gold,true,false),smooth=GlassPaneTextures.transform(gold,true,true);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);int authoredTex=texture(authored),smoothTex=texture(smooth);
        int white=texture(solid(1,1,0xffffffff)),dim=texture(solid(1,1,0xff606060));
        Sprite sprite=new Sprite("authored-gold",gold.getWidth(),gold.getHeight(),gold.getWidth(),gold.getHeight(),0,0);
        GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(false,0,sprite,true,DefaultVertexFormats.BLOCK);
        byte[] baseline=scene(authoredTex,white,mesh,true,false,false,false,true);sceneGeometry(baseline);
        need(difference(baseline,scene(authoredTex,white,mesh,false,false,false,false,true))==0,"Forge/vanilla native emission differs");
        need(difference(baseline,scene(authoredTex,white,mesh,true,true,false,false,true))==0,"retained native VBO differs from client arrays");
        for(boolean rotated:new boolean[]{false,true})for(boolean back:new boolean[]{false,true}){
            GlassPaneTerrainMesh variant=GlassPaneTerrainMesh.build(rotated,0,sprite,true,DefaultVertexFormats.BLOCK);
            byte[] pixels=scene(authoredTex,white,variant,true,true,rotated,back,true);sceneGeometry(pixels);
            need(difference(baseline,pixels)==0,"pane changes with native facing or side");
        }
        need(difference(baseline,scene(smoothTex,white,mesh,true,true,false,false,true))>10000,"authored glimmer indistinguishable from smooth negative control");
        need(difference(baseline,scene(authoredTex,dim,mesh,true,true,false,false,true))>10000,"native lightmap ignored");
        need(difference(baseline,scene(authoredTex,white,mesh,true,true,false,false,false))>10000,"culling negative control did not detect duplicate sheet blending");
        BufferedImage atlas=solid(32,16,0x80ff0000);for(int y=0;y<16;y++)for(int x=16;x<32;x++)atlas.setRGB(x,y,0x800000ff);
        int orderTex=texture(atlas);Sprite red=new Sprite("red",16,16,32,16,0,0),blue=new Sprite("blue",16,16,32,16,16,0);
        byte[] sorted=order(orderTex,white,red,blue,true,false),manual=order(orderTex,white,red,blue,false,true),wrong=order(orderTex,white,red,blue,false,false);
        need(difference(sorted,manual)==0,"native quad sort differs from known far-to-near order");
        need(difference(sorted,wrong)>10000,"sorting negative control failed to change blended order");
        need(channel(sorted,WIDTH/2,HEIGHT/2,2)>channel(sorted,WIDTH/2,HEIGHT/2,0),"near blue pane did not blend last");
        for(int alpha:new int[]{25,26,27}){
            int tex=texture(solid(gold.getWidth(),gold.getHeight(),(alpha<<24)|0xffffff));
            byte[] pixels=scene(tex,white,mesh,true,true,false,false,true);
            int change=Math.abs(channel(pixels,WIDTH/8,HEIGHT/6,0)-31);
            if(alpha==25)need(change<=1,"native alpha threshold retained below-threshold texel");
            if(alpha==27)need(change>8,"native alpha margin erased eligible texel");
            System.out.println("Native alpha probe "+alpha+" change="+change);GL11.glDeleteTextures(tex);
        }
        for(int tex:new int[]{authoredTex,smoothTex,white,dim,orderTex})GL11.glDeleteTextures(tex);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glDisable(GL11.GL_TEXTURE_2D);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glDepthMask(true);
        need(GL11.glGetError()==GL11.GL_NO_ERROR,"framebuffer verification left GL error");
        System.out.println("GlassPaneTerrainGpu PASS checks="+checks+" native_draws="+draws+" native_stride=28 cells=11 quads=22; authored Gold, glimmer, both orientations/sides, aperture, Forge/vanilla/client-array/VBO parity, lightmap, native alpha and sorting/culling negatives");
        System.out.println("Software framebuffer fixture only; no full-world, OptiFine shader-pack or hardware FPS acceptance.");
    }
    private GlassPaneTerrainGpuChecks(){}
}
