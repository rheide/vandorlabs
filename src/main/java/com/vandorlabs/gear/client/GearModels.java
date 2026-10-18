package com.vandorlabs.gear.client;

import com.vandorlabs.VandorLabs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Atlas UVs and vertex arrays are decoded once per resource reload. */
@SideOnly(Side.CLIENT)
final class GearModels {
    private static final Map<String, Mesh> MESHES = new HashMap<String, Mesh>();

    private GearModels() { }

    static void reload() {
        MESHES.clear();
        List<String> names = new ArrayList<String>();
        for (String name : new String[] { "mount", "sleeve", "rod", "socket", "extender",
                "swing_linkage", "swivel", "swing_arm", "wheel", "wheel_large", "wheel_bracket", "wheel_tyre", "wheel_large_bracket", "wheel_large_tyre" }) names.add(name);
        for (int mask = 0; mask < 16; mask++) {
            names.add("plate_" + mask);
            names.add("ski_" + mask);
        }
        for (String name : names) {
            ResourceLocation location = new ResourceLocation(com.vandorlabs.VandorLabs.MODID, "block/gear/" + name);
            try {
                IModel unbaked = ModelLoaderRegistry.getModel(location);
                IBakedModel baked = unbaked.bake(unbaked.getDefaultState(), DefaultVertexFormats.BLOCK,
                        texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
                MESHES.put(name, new Mesh(baked));
            } catch (Exception exception) {
                com.vandorlabs.VandorLabs.logger.error("Could not bake landing gear part {}", location, exception);
            }
        }
    }

    static void draw(String name, GearRenderer.Affine transform, double x, double y, double z,
                     int light, BufferBuilder buffer) {
        Mesh mesh = MESHES.get(name);
        if (mesh != null) mesh.draw(transform, x, y, z, light, buffer);
    }

    private static final class Mesh {
        private final List<Quad> quads = new ArrayList<Quad>();

        Mesh(IBakedModel model) {
            for (BakedQuad quad : model.getQuads(null, null, 0L)) quads.add(new Quad(quad));
            for (EnumFacing face : EnumFacing.values()) {
                for (BakedQuad quad : model.getQuads(null, face, 0L)) quads.add(new Quad(quad));
            }
        }

        void draw(GearRenderer.Affine transform, double x, double y, double z, int light, BufferBuilder buffer) {
            int sky = light >> 16 & 65535;
            int block = light & 65535;
            for (Quad quad : quads) {
                double nx = transform.xx * quad.nx + transform.xy * quad.ny + transform.xz * quad.nz;
                double ny = transform.yx * quad.nx + transform.yy * quad.ny + transform.yz * quad.nz;
                double nz = transform.zx * quad.nx + transform.zy * quad.ny + transform.zz * quad.nz;
                double normalLengthSquared = nx * nx + ny * ny + nz * nz;
                float shade = !quad.diffuse || normalLengthSquared < 1e-12 ? 1.0F
                        : (float) ((nx * nx * 0.6 + ny * ny * (ny > 0 ? 1.0 : 0.5) + nz * nz * 0.8)
                        / normalLengthSquared);
                for (Vertex vertex : quad.vertices) {
                    buffer.pos(x + transform.xx * vertex.x + transform.xy * vertex.y + transform.xz * vertex.z + transform.tx,
                            y + transform.yx * vertex.x + transform.yy * vertex.y + transform.yz * vertex.z + transform.ty,
                            z + transform.zx * vertex.x + transform.zy * vertex.y + transform.zz * vertex.z + transform.tz)
                            .color(vertex.r * shade, vertex.g * shade, vertex.b * shade, vertex.a)
                            .tex(vertex.u, vertex.v).lightmap(sky, block).endVertex();
                }
            }
        }
    }

    private static final class Quad {
        final Vertex[] vertices = new Vertex[4];
        final double nx, ny, nz;
        final boolean diffuse;

        Quad(BakedQuad quad) {
            int[] packed = quad.getVertexData();
            VertexFormat format = quad.getFormat();
            int stride = format.getIntegerSize();
            int uv = format.getUvOffsetById(0) / 4;
            int color = format.getColorOffset() / 4;
            for (int i = 0; i < 4; i++) {
                int base = i * stride;
                int rgba = packed[base + color];
                vertices[i] = new Vertex(Float.intBitsToFloat(packed[base]),
                        Float.intBitsToFloat(packed[base + 1]), Float.intBitsToFloat(packed[base + 2]),
                        Float.intBitsToFloat(packed[base + uv]), Float.intBitsToFloat(packed[base + uv + 1]),
                        (rgba & 255) / 255.0F, (rgba >> 8 & 255) / 255.0F,
                        (rgba >> 16 & 255) / 255.0F, (rgba >>> 24) / 255.0F);
            }
            // Geometric normals also cover the bent arm's angled native
            // cuboids; quad.getFace() only gives their nearest axis.
            double ax = vertices[1].x - vertices[0].x;
            double ay = vertices[1].y - vertices[0].y;
            double az = vertices[1].z - vertices[0].z;
            double bx = vertices[2].x - vertices[0].x;
            double by = vertices[2].y - vertices[0].y;
            double bz = vertices[2].z - vertices[0].z;
            double crossX = ay * bz - az * by;
            double crossY = az * bx - ax * bz;
            double crossZ = ax * by - ay * bx;
            double length = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
            nx = length > 1e-9 ? crossX / length : quad.getFace().getFrontOffsetX();
            ny = length > 1e-9 ? crossY / length : quad.getFace().getFrontOffsetY();
            nz = length > 1e-9 ? crossZ / length : quad.getFace().getFrontOffsetZ();
            diffuse = quad.shouldApplyDiffuseLighting();
        }
    }

    private static final class Vertex {
        final float x, y, z, u, v, r, g, b, a;

        Vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
            this.x = x; this.y = y; this.z = z; this.u = u; this.v = v;
            this.r = r; this.g = g; this.b = b; this.a = a;
        }
    }
}
