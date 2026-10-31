package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityCanopy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Frames join the opaque tile pass; sorted glazing follows all solid tile artwork. */
public final class TECanopy extends TileEntitySpecialRenderer<TileEntityCanopy> {
    private static final class DrawFace {
        CanopyMesh.Face source;
        Vec3d[] v;
        double distance;
        float opacity;
        Vec3d center,normal;
    }

    private static final class Prepared {
        List<CanopyMesh.Face> source,drawable;String settings;CanopyRenderGeometry geometry;net.minecraft.util.EnumFacing facing;int width;double progress=Double.NaN;
        final List<DrawFace> opaque=new ArrayList<>(),glass=new ArrayList<>();
        final net.minecraft.client.renderer.vertex.VertexBuffer solidBuffer=new net.minecraft.client.renderer.vertex.VertexBuffer(DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        final net.minecraft.client.renderer.vertex.VertexBuffer glassBuffer=new net.minecraft.client.renderer.vertex.VertexBuffer(DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        boolean solidDirty=true,glassDirty=true;
        DrawFace[] glassOrder;
        java.lang.ref.WeakReference<TileEntityCanopy> owner;
        void delete(){solidBuffer.deleteGlBuffers();glassBuffer.deleteGlBuffers();}
    }
    private static final Map<TileEntityCanopy,Prepared> CACHE=new WeakHashMap<>();
    private static final Map<String,net.minecraft.client.renderer.texture.TextureAtlasSprite> SPRITES=new HashMap<>();
    private static final Set<Prepared> BUFFERS=Collections.newSetFromMap(new IdentityHashMap<Prepared,Boolean>());
    static void cleanup(){Iterator<Prepared> it=BUFFERS.iterator();while(it.hasNext()){Prepared p=it.next();TileEntityCanopy tile=p.owner.get();if(tile==null||tile.isInvalid()||(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).parent:tile.getWorld())!=Minecraft.getMinecraft().world){p.delete();it.remove();if(tile!=null)CACHE.remove(tile);}}}
    static void invalidate(){for(Prepared p:BUFFERS)p.delete();BUFFERS.clear();CACHE.clear();SPRITES.clear();}

    @Override
    public void render(
            TileEntityCanopy tile,
            double x,
            double y,
            double z,
            float partial,
            int stage,
            float alpha) {
        renderPass(tile,x,y,z,partial,stage,alpha,net.minecraftforge.client.MinecraftForgeClient.getRenderPass());
    }

    /** Vehicle rendering uses the same phases inside its local world transform. */
    void renderPass(TileEntityCanopy tile,double x,double y,double z,float partial,int stage,float alpha,int pass) {
        if(pass==1&&com.vandorlabs.tiles.GlassMaterials.opaque(tile.large.material,tile.large.tint))return;
        if (tile.block() == null || !tile.draws()) return;
        double progress =
                tile.opening()
                        ? tile.previous + (tile.progress - tile.previous) * partial
                        : 0;
        List<CanopyMesh.Face> source=tile.baseFaces();
        String settings=tile.large.key();
        Prepared prepared=CACHE.get(tile);
        if(prepared==null || prepared.source!=source || !settings.equals(prepared.settings) || prepared.facing!=tile.facing() || prepared.width!=tile.width()){
            if(prepared!=null){prepared.delete();BUFFERS.remove(prepared);}
            prepared=new Prepared();prepared.owner=new java.lang.ref.WeakReference<>(tile);BUFFERS.add(prepared);prepared.source=source;prepared.settings=settings;prepared.facing=tile.facing();prepared.width=tile.width();prepared.geometry=new CanopyRenderGeometry(source);CACHE.put(tile,prepared);
        }
        List<CanopyMesh.Face> drawable=prepared.geometry.posed(tile.block().kind,prepared.width,tile.large,progress,tile.centeredPlacement);
        if(prepared.drawable!=drawable){
            prepared.drawable=drawable;prepared.opaque.clear();prepared.glass.clear();prepared.progress=Double.NaN;
            for(CanopyMesh.Face f:drawable){
                DrawFace draw=new DrawFace();draw.source=f;draw.v=new Vec3d[f.vertices.length];
                draw.opacity=com.vandorlabs.canopy.CanopyFinishes.opacity(f,tile.large.material,tile.large.tint);
                (com.vandorlabs.canopy.CanopyFinishes.translucent(f,tile.large.material,tile.large.tint)?prepared.glass:prepared.opaque).add(draw);
            }
        }
        if(prepared.progress!=progress){
            orient(prepared.opaque,prepared.facing);orient(prepared.glass,prepared.facing);prepared.progress=progress;
            prepared.solidDirty=prepared.glassDirty=true;
        }
        List<DrawFace> opaque=prepared.opaque,glass=prepared.glass;
        if(pass==0&&opaque.isEmpty()||pass==1&&glass.isEmpty())return;
        if(pass!=0){
            double cx=rendererDispatcher.entityX-tile.getPos().getX(),cy=rendererDispatcher.entityY-tile.getPos().getY(),cz=rendererDispatcher.entityZ-tile.getPos().getZ();
            for(DrawFace face:glass){double dx=face.center.x-cx,dy=face.center.y-cy,dz=face.center.z-cz;face.distance=dx*dx+dy*dy+dz*dz;}
            glass.sort((a, b) -> Double.compare(b.distance, a.distance));
            if(prepared.glassOrder==null||prepared.glassOrder.length!=glass.size())prepared.glassDirty=true;
            else for(int i=0;i<glass.size();i++)if(prepared.glassOrder[i]!=glass.get(i)){prepared.glassDirty=true;break;}
        }
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableLighting();
        GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 1);
        float oldX = OpenGlHelper.lastBrightnessX, oldY = OpenGlHelper.lastBrightnessY;
        int light = tile.getWorld().getCombinedLight(tile.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
        GlStateManager.disableBlend();GlStateManager.depthMask(true);
        if(pass!=1){drawCached(opaque,prepared.solidBuffer,prepared.solidDirty);prepared.solidDirty=!OpenGlHelper.useVbo();}
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        if(pass!=0){drawCached(glass,prepared.glassBuffer,prepared.glassDirty);if(prepared.glassDirty)prepared.glassOrder=glass.toArray(new DrawFace[0]);prepared.glassDirty=!OpenGlHelper.useVbo();}
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
        GlStateManager.popMatrix();
    }

    private static void orient(List<DrawFace> faces,net.minecraft.util.EnumFacing facing){
        for(DrawFace draw:faces){
            CanopyMesh.Face f=draw.source;Vec3d center=Vec3d.ZERO;
            for(int i=0;i<draw.v.length;i++){
                draw.v[i]=CanopyMesh.rotate(f.vertices[i],facing);center=center.add(draw.v[i]);
            }
            draw.center=center.scale(1D/draw.v.length);draw.normal=Vec3d.ZERO;
            for(int i=1;i<draw.v.length-1;i++){Vec3d normal=draw.v[i].subtract(draw.v[0]).crossProduct(draw.v[i+1].subtract(draw.v[0]));if(normal.lengthSquared()>1e-16){draw.normal=normal.normalize();break;}}
        }
    }

    private void draw(List<DrawFace> faces) {
        if(faces.isEmpty())return;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        emit(b,faces);
        Tessellator.getInstance().draw();
    }
    private void drawCached(List<DrawFace> faces,net.minecraft.client.renderer.vertex.VertexBuffer buffer,boolean dirty){
        if(faces.isEmpty())return;
        if(!OpenGlHelper.useVbo()){draw(faces);return;}
        if(dirty){BufferBuilder b=new BufferBuilder(4096);b.begin(org.lwjgl.opengl.GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);emit(b,faces);b.finishDrawing();buffer.bufferData(b.getByteBuffer());}
        buffer.bindBuffer();
        int stride=DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL.getNextOffset();
        GlStateManager.glEnableClientState(org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY);GlStateManager.glVertexPointer(3,org.lwjgl.opengl.GL11.GL_FLOAT,stride,0);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.glEnableClientState(org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY);GlStateManager.glTexCoordPointer(2,org.lwjgl.opengl.GL11.GL_FLOAT,stride,12);
        GlStateManager.glEnableClientState(org.lwjgl.opengl.GL11.GL_COLOR_ARRAY);GlStateManager.glColorPointer(4,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,stride,20);
        GlStateManager.glEnableClientState(org.lwjgl.opengl.GL11.GL_NORMAL_ARRAY);org.lwjgl.opengl.GL11.glNormalPointer(org.lwjgl.opengl.GL11.GL_BYTE,stride,24L);
        buffer.drawArrays(org.lwjgl.opengl.GL11.GL_QUADS);buffer.unbindBuffer();
        GlStateManager.glDisableClientState(org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY);GlStateManager.glDisableClientState(org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY);GlStateManager.glDisableClientState(org.lwjgl.opengl.GL11.GL_COLOR_ARRAY);GlStateManager.glDisableClientState(org.lwjgl.opengl.GL11.GL_NORMAL_ARRAY);
        GlStateManager.resetColor();
    }
    private void emit(BufferBuilder b,List<DrawFace> faces){
        for (DrawFace draw : faces) {
            CanopyMesh.Face f = draw.source;
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=SPRITES.get(f.material);
            if(sprite==null){sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(f.material.contains(":")?f.material:"vandorlabs:blocks/canopy/"+f.material);SPRITES.put(f.material,sprite);}
            if(f.vertices.length==4){
                for(int i=0;i<4;i++)vertex(b,draw,sprite,i,1);
                if(f.doubleSided)for(int i=3;i>=0;i--)vertex(b,draw,sprite,i,-1);
                continue;
            }
            for(int i=1;i<f.vertices.length-1;i++){
                vertex(b,draw,sprite,0,1);vertex(b,draw,sprite,i,1);vertex(b,draw,sprite,i+1,1);vertex(b,draw,sprite,i+1,1);
                if(f.doubleSided){vertex(b,draw,sprite,i+1,-1);vertex(b,draw,sprite,i,-1);vertex(b,draw,sprite,0,-1);vertex(b,draw,sprite,0,-1);}
            }
        }
    }

    private void vertex(BufferBuilder b,DrawFace f,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int index,int sign){
        Vec3d p=f.v[index],normal=f.normal;
        b.pos(p.x,p.y,p.z).tex(sprite.getInterpolatedU(f.source.uv[index][0]*16),sprite.getInterpolatedV((1-f.source.uv[index][1])*16))
                .color(1F,1F,1F,f.opacity).normal((float)normal.x*sign,(float)normal.y*sign,(float)normal.z*sign).endVertex();
    }
}
