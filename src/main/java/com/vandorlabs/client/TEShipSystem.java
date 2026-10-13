package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.shipsystems.ShipSystemMesh;
import com.vandorlabs.tiles.TileEntityShipSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Authored static machinery: opaque parts first, camera-sorted glass last. */
public final class TEShipSystem extends TileEntitySpecialRenderer<TileEntityShipSystem> {
    private static final class DrawFace {
        CanopyMesh.Face source;
        Vec3d[] v;
        double distance;
        Vec3d normal;
    }

    @Override
    public void render(
            TileEntityShipSystem tile,
            double x,
            double y,
            double z,
            float partial,
            int stage,
            float alpha) {
        if (tile.block() == null || !tile.draws()) return;
        String key=tile.model()+":"+tile.facing()+":"+tile.mount;
        Prepared prepared=CACHE.get(key);
        net.minecraft.client.renderer.texture.TextureAtlasSprite atlas=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/iron_block");
        if(atlas!=lastAtlas) { for(Prepared old:CACHE.values())old.delete(); CACHE.clear(); lastAtlas=atlas; prepared=null; }
        if(prepared==null) { prepared=prepare(tile,tile.frame()); CACHE.put(key,prepared); }
        List<DrawFace> glass=prepared.glass;
        if(glass.size()>1) {
            Vec3d camera=new Vec3d(rendererDispatcher.entityX-tile.getPos().getX(),rendererDispatcher.entityY-tile.getPos().getY(),rendererDispatcher.entityZ-tile.getPos().getZ());
            for(DrawFace face:glass)face.distance=face.v[0].squareDistanceTo(camera);
            glass.sort((a, b) -> Double.compare(b.distance, a.distance));
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
        call(prepared.opaque);
        float boost=tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld && com.vandorlabs.vehicle.VehicleWorld.isPropulsion(tile.getBlockType())?((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).propulsionLevel/15F:1;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (light & 65535)+(240-(light & 65535))*boost, (light >>> 16)+(240-(light >>> 16))*boost);
        if(tile.isAnimated()) { GlStateManager.enableBlend(); GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_CONSTANT_COLOR,org.lwjgl.opengl.GL11.GL_ZERO); }
        if(!tile.isAnimated())call(prepared.steadyEmissive);
        else for(int band=0;band<prepared.emissive.length;band++) {
            float pulse=tile.isAnimated() ? .62F+.38F*(float)Math.sin((tile.getWorld().getTotalWorldTime()+partial)*.14-band*.9) : 1F;
            org.lwjgl.opengl.GL14.glBlendColor(pulse,pulse,pulse,1F);
            call(prepared.emissive[band]);
        }
        GlStateManager.disableBlend();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
        if(!glass.isEmpty()) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
            draw(glass);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
        GlStateManager.enableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
        GlStateManager.popMatrix();
    }

    private static final Map<String,Prepared> CACHE=new HashMap<>();
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite lastAtlas;
    private static final class Prepared {
        int opaque,steadyEmissive; int[] emissive=new int[8]; List<DrawFace> glass=new ArrayList<>();
        void delete() { if(opaque!=0)org.lwjgl.opengl.GL11.glDeleteLists(opaque,1);if(steadyEmissive!=0)org.lwjgl.opengl.GL11.glDeleteLists(steadyEmissive,1); for(int list:emissive)if(list!=0)org.lwjgl.opengl.GL11.glDeleteLists(list,1); }
    }
    private Prepared prepare(TileEntityShipSystem tile,com.vandorlabs.shipsystems.MountFrame frame) {
        Prepared p=new Prepared(); List<DrawFace> opaque=new ArrayList<>(); List<List<DrawFace>> bands=new ArrayList<>();
        for(int i=0;i<8;i++)bands.add(new ArrayList<>());
        for(CanopyMesh.Face f:ShipSystemMesh.MODELS.get(tile.model())) {
            DrawFace d=new DrawFace(); d.source=f; d.v=new Vec3d[f.vertices.length];
            for(int i=0;i<d.v.length;i++)d.v[i]=frame.point(f.vertices[i]);
            d.normal=d.v[1].subtract(d.v[0]).crossProduct(d.v[2].subtract(d.v[0])).normalize();
            String m=f.baseMaterial;
            boolean glow=tile.visuallyActive() && (m.equals("cyan")||m.equals("amber")||m.equals("display_on")||m.startsWith("energy_")&&!m.endsWith("_off")||m.equals("red_light")||m.equals("green_light")||m.equals("hot")||m.equals("energy")||m.equals("light")||m.equals("display"));
            if(m.equals("glass"))p.glass.add(d);
            else if(glow)bands.get(Math.floorMod((int)Math.floor(f.vertices[0].x*4),8)).add(d);
            else opaque.add(d);
        }
        p.opaque=compile(opaque);List<DrawFace> steady=new ArrayList<>();
        for(int i=0;i<8;i++) { p.emissive[i]=compile(bands.get(i));steady.addAll(bands.get(i)); }
        p.steadyEmissive=compile(steady);return p;
    }
    private static void call(int list) { if(list!=0)org.lwjgl.opengl.GL11.glCallList(list); }
    private int compile(List<DrawFace> faces) {
        if(faces.isEmpty())return 0;
        int id=org.lwjgl.opengl.GL11.glGenLists(1); org.lwjgl.opengl.GL11.glNewList(id,org.lwjgl.opengl.GL11.GL_COMPILE); draw(faces); org.lwjgl.opengl.GL11.glEndList(); return id;
    }
    private void draw(List<DrawFace> faces) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for (DrawFace draw : faces) {
            CanopyMesh.Face f = draw.source;
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite("vandorlabs:blocks/ship_systems/" + f.material);
            if(f.vertices.length==4) {
                vertices(b,draw,sprite,new int[]{0,1,2,3});
                if(f.doubleSided)vertices(b,draw,sprite,new int[]{3,2,1,0});
            } else for (int i = 1; i < f.vertices.length - 1; i++) {
                vertices(b, draw, sprite, new int[] {0, i, i + 1, i + 1});
                if (f.doubleSided) vertices(b, draw, sprite, new int[] {i + 1, i, 0, 0});
            }
        }
        Tessellator.getInstance().draw();
    }

    private void vertices(
            BufferBuilder b,
            DrawFace f,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            int[] indices) {
        Vec3d normal = f.normal;
        for (int index : indices) {
            Vec3d p = f.v[index];
            b.pos(p.x, p.y, p.z)
                    .tex(
                            sprite.getInterpolatedU(f.source.uv[index][0] * 16),
                            sprite.getInterpolatedV((1 - f.source.uv[index][1]) * 16))
                    .color(f.source.material.startsWith("rivet/") && f.source.baseMaterial.equals("ivory") ? .49F : 1F, f.source.material.startsWith("rivet/") && f.source.baseMaterial.equals("ivory") ? .62F : 1F, f.source.material.startsWith("rivet/") && f.source.baseMaterial.equals("ivory") ? .65F : 1F, 1F)
                    .normal((float) normal.x, (float) normal.y, (float) normal.z)
                    .endVertex();
        }
    }
}
