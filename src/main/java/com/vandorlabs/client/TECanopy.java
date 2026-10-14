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

/** Static mounts and posed shells share authored faces. Draw opaque first, sorted glass last. */
public final class TECanopy extends TileEntitySpecialRenderer<TileEntityCanopy> {
    private static final class DrawFace {
        CanopyMesh.Face source;
        Vec3d[] v;
        double distance;
        float opacity;
        Vec3d center,normal;
    }

    private static final class Prepared {
        List<CanopyMesh.Face> source;net.minecraft.util.EnumFacing facing;int width;double progress;
        final List<DrawFace> opaque=new ArrayList<>(),glass=new ArrayList<>();
    }
    private static final Map<TileEntityCanopy,Prepared> CACHE=new WeakHashMap<>();
    private static final Map<String,net.minecraft.client.renderer.texture.TextureAtlasSprite> SPRITES=new HashMap<>();
    static void invalidate(){CACHE.clear();SPRITES.clear();}

    @Override
    public void render(
            TileEntityCanopy tile,
            double x,
            double y,
            double z,
            float partial,
            int stage,
            float alpha) {
        if (tile.block() == null || !tile.draws()) return;
        double progress =
                tile.opening()
                        ? tile.previous + (tile.progress - tile.previous) * partial
                        : 0;
        List<CanopyMesh.Face> source=tile.faces(progress);
        Prepared prepared=CACHE.get(tile);
        if(prepared==null || prepared.source!=source || prepared.facing!=tile.facing() || prepared.width!=tile.width() || prepared.progress!=progress){
            prepared=new Prepared();prepared.source=source;prepared.facing=tile.facing();prepared.width=tile.width();prepared.progress=progress;
            for(CanopyMesh.Face f:com.vandorlabs.canopy.CanopyMaterialUv.drawable(com.vandorlabs.canopy.CanopySurfaces.visible(source))){
                DrawFace draw=new DrawFace();draw.source=f;draw.v=new Vec3d[f.vertices.length];Vec3d center=Vec3d.ZERO;
                for(int i=0;i<draw.v.length;i++){draw.v[i]=CanopyMesh.rotate(f.vertices[i],prepared.facing);center=center.add(draw.v[i]);}
                draw.center=center.scale(1D/draw.v.length);
                draw.normal=Vec3d.ZERO;for(int i=1;i<draw.v.length-1;i++){Vec3d normal=draw.v[i].subtract(draw.v[0]).crossProduct(draw.v[i+1].subtract(draw.v[0]));if(normal.lengthSquared()>1e-16){draw.normal=normal.normalize();break;}}
                draw.opacity=com.vandorlabs.canopy.CanopyFinishes.opacity(f,tile.large.material,tile.large.tint);
                (com.vandorlabs.canopy.CanopyFinishes.translucent(f,tile.large.material,tile.large.tint)?prepared.glass:prepared.opaque).add(draw);
            }
            CACHE.put(tile,prepared);
        }
        List<DrawFace> opaque=prepared.opaque,glass=prepared.glass;
        Vec3d camera=new Vec3d(rendererDispatcher.entityX-tile.getPos().getX(),rendererDispatcher.entityY-tile.getPos().getY(),rendererDispatcher.entityZ-tile.getPos().getZ());
        for(DrawFace face:glass)face.distance=face.center.squareDistanceTo(camera);
        glass.sort((a, b) -> Double.compare(b.distance, a.distance));
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
        draw(opaque);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        draw(glass);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
        GlStateManager.popMatrix();
    }

    private void draw(List<DrawFace> faces) {
        if(faces.isEmpty())return;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
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
        Tessellator.getInstance().draw();
    }

    private void vertex(BufferBuilder b,DrawFace f,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int index,int sign){
        Vec3d p=f.v[index],normal=f.normal;
        b.pos(p.x,p.y,p.z).tex(sprite.getInterpolatedU(f.source.uv[index][0]*16),sprite.getInterpolatedV((1-f.source.uv[index][1])*16))
                .color(1F,1F,1F,f.opacity).normal((float)normal.x*sign,(float)normal.y*sign,(float)normal.z*sign).endVertex();
    }
}
