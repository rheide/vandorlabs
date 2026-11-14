package com.vandorlabs.client;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.lang.reflect.Method;

/** VBO records retain their build format; shader records include extra attributes. */
public final class BlockMeshVbo {
    private static final ShaderHooks SHADERS=ShaderHooks.find();
    private static boolean warned;
    private static void unavailable(Throwable error){if(!warned){warned=true;if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Optional shader VBO integration unavailable; using standard vertex attributes",error);}}
    public static void draw(VertexBuffer buffer,VertexFormat format,BlockRenderLayer layer){
        draw(buffer,format,layer,0);
    }
    /** Retained two-sided sheets need their culling choice after shader setup. */
    public static void draw(VertexBuffer buffer,VertexFormat format,BlockRenderLayer layer,boolean cullFaces){
        draw(buffer,format,layer,cullFaces?1:-1);
    }
    /** Group owners may leave TESR lists only when every retained attribute is drawable. */
    public static boolean supportsRetained(VertexFormat format){
        if(!GlassPaneTerrainMesh.supports(format)||format.getColorOffset()!=12||format.getUvOffsetById(0)!=16||format.getUvOffsetById(1)!=24)return false;
        int stride=format.getNextOffset();
        if(stride==28)return !format.hasNormal();
        if(!format.hasNormal()||format.getNormalOffset()!=28)return false;
        if(stride==32)return true;
        for(int i=0;i<format.getElementCount();i++)if(format.getOffset(i)>=32
                &&format.getElement(i).getUsage()!=VertexFormatElement.EnumUsage.PADDING)return false;
        return stride==56&&format.equals(DefaultVertexFormats.BLOCK)&&SHADERS!=null&&SHADERS.active();
    }
    private static void draw(VertexBuffer buffer,VertexFormat format,BlockRenderLayer layer,int forcedCull){
        if(forcedCull!=0&&!supportsRetained(format))throw new IllegalStateException("Retained glass vertex layout or shader hooks are unavailable");
        boolean shaders=SHADERS!=null&&format.getNextOffset()==56&&SHADERS.active();
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        buffer.bindBuffer();
        try{
            pointers(format,true);
            if(shaders)SHADERS.begin(layer,forcedCull!=0);
            if(forcedCull>0)GlStateManager.enableCull();else if(forcedCull<0)GlStateManager.disableCull();
            buffer.drawArrays(GL11.GL_QUADS);
        }finally{
            try{if(shaders)SHADERS.end(layer,forcedCull!=0);}finally{pointers(format,false);buffer.unbindBuffer();
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
        }
    }
    private static void pointers(VertexFormat format,boolean enable){
        int stride=format.getNextOffset();
        for(int i=0;i<format.getElementCount();i++){
            VertexFormatElement e=format.getElement(i);int type=e.getType().getGlConstant(),count=e.getElementCount(),offset=format.getOffset(i),state;
            switch(e.getUsage()){
                case POSITION:state=GL11.GL_VERTEX_ARRAY;if(enable)GlStateManager.glVertexPointer(count,type,stride,offset);break;
                case COLOR:state=GL11.GL_COLOR_ARRAY;if(enable)GlStateManager.glColorPointer(count,type,stride,offset);break;
                case NORMAL:state=GL11.GL_NORMAL_ARRAY;if(enable)GL11.glNormalPointer(type,stride,(long)offset);break;
                case UV:
                    if(e.getIndex()>1)continue;
                    OpenGlHelper.setClientActiveTexture(e.getIndex()==0?OpenGlHelper.defaultTexUnit:OpenGlHelper.lightmapTexUnit);
                    state=GL11.GL_TEXTURE_COORD_ARRAY;if(enable)GlStateManager.glTexCoordPointer(count,type,stride,offset);break;
                default:continue;
            }
            if(enable)GlStateManager.glEnableClientState(state);else GlStateManager.glDisableClientState(state);
        }
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }
    private static final class ShaderHooks {
        final Method active,begin,pointers,end;final int mid,tangent,entity;
        ShaderHooks()throws ReflectiveOperationException{
            active=Class.forName("Config").getMethod("isShaders");Class<?> render=Class.forName("net.optifine.shaders.ShadersRender"),shaders=Class.forName("net.optifine.shaders.Shaders");
            begin=render.getMethod("preRenderChunkLayer",BlockRenderLayer.class);pointers=render.getMethod("setupArrayPointersVbo");end=render.getMethod("postRenderChunkLayer",BlockRenderLayer.class);
            mid=shaders.getField("midTexCoordAttrib").getInt(null);tangent=shaders.getField("tangentAttrib").getInt(null);entity=shaders.getField("entityAttrib").getInt(null);
        }
        static ShaderHooks find(){try{return new ShaderHooks();}catch(ClassNotFoundException absent){return null;}catch(ReflectiveOperationException|LinkageError incompatible){unavailable(incompatible);return null;}}
        boolean active(){try{return (Boolean)active.invoke(null);}catch(ReflectiveOperationException|LinkageError error){unavailable(error);return false;}}
        void begin(BlockRenderLayer layer,boolean strict){try{begin.invoke(null,layer);pointers.invoke(null);GL20.glEnableVertexAttribArray(mid);GL20.glEnableVertexAttribArray(tangent);GL20.glEnableVertexAttribArray(entity);}catch(ReflectiveOperationException|LinkageError error){unavailable(error);if(strict)throw new IllegalStateException("Retained shader attributes unavailable",error);}}
        void end(BlockRenderLayer layer,boolean strict){try{end.invoke(null,layer);}catch(ReflectiveOperationException|LinkageError error){unavailable(error);if(strict)throw new IllegalStateException("Cannot restore retained shader attributes",error);}finally{GL20.glDisableVertexAttribArray(mid);GL20.glDisableVertexAttribArray(tangent);GL20.glDisableVertexAttribArray(entity);}}
    }
    /** Avoid OptiFine addVertexData rewriting each component's block/entity shader IDs. */
    public static void merge(BufferBuilder builder,java.util.List<int[]> components,VertexFormat format){mergeRetained(builder,components,format);}
    static int[] mergeRetained(BufferBuilder builder,java.util.List<int[]> components,VertexFormat format){
        int size=0;for(int[] data:components)size=Math.addExact(size,data.length);
        int[] joined=new int[size];int offset=0;for(int[] data:components){System.arraycopy(data,0,joined,offset,data.length);offset+=data.length;}
        builder.setVertexState(builder.new State(joined,new VertexFormat(format)));return joined;
    }
}
