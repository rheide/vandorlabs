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
        for (String name : new String[] { "mount", "fixed_mount","fixed_mount_compact", "sleeve", "rod", "socket", "extender",
                "swing_linkage", "swivel", "swing_arm", "wheel", "wheel_large", "wheel_bracket", "wheel_tyre", "wheel_large_bracket", "wheel_large_tyre", "wheel_connector", "wheel_large_connector" }) names.add(name);
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
                     int light, BufferBuilder buffer, boolean cache) {
        Mesh mesh = MESHES.get(name);
        if (mesh != null) mesh.draw(transform, x, y, z, light, buffer, cache);
    }

    static long cachedVertexBytes(){long bytes=0;for(Mesh mesh:MESHES.values())for(double[] data:mesh.transformed.values())bytes+=data.length*8L;return bytes;}

    private static final class Mesh {
        private final List<Quad> quads = new ArrayList<Quad>();

        // Bounded per mesh, shared by identical assemblies. World positions and light
        // remain dynamic; resource reload drops all transformed data.
        private final java.util.LinkedHashMap<GearRenderer.Affine,double[]> transformed=new java.util.LinkedHashMap<GearRenderer.Affine,double[]>(16,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<GearRenderer.Affine,double[]> entry){return size()>64;}
        };
        Mesh(IBakedModel model) {
            for (BakedQuad quad : model.getQuads(null, null, 0L)) quads.add(new Quad(quad));
            for (EnumFacing face : EnumFacing.values()) {
                for (BakedQuad quad : model.getQuads(null, face, 0L)) quads.add(new Quad(quad));
            }
        }

        void draw(GearRenderer.Affine transform, double x, double y, double z, int light, BufferBuilder buffer,boolean cache) {
            if(cache){
                double[] data=transformed.get(transform);
                if(data==null){data=transformedVertices(transform);transformed.put(transform,data);}
                for(int i=0;i<data.length;i+=9)buffer.pos(x+data[i],y+data[i+1],z+data[i+2]).color((float)data[i+3],(float)data[i+4],(float)data[i+5],(float)data[i+6]).tex(data[i+7],data[i+8]).lightmap(light>>16&65535,light&65535).endVertex();
                return;
            }
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
        private double[] transformedVertices(GearRenderer.Affine t){
            double[] data=new double[quads.size()*4*9];int i=0;
            for(Quad q:quads){
                double nx=t.xx*q.nx+t.xy*q.ny+t.xz*q.nz,ny=t.yx*q.nx+t.yy*q.ny+t.yz*q.nz,nz=t.zx*q.nx+t.zy*q.ny+t.zz*q.nz;
                double length=nx*nx+ny*ny+nz*nz;
                float shade=!q.diffuse||length<1e-12?1f:(float)((nx*nx*.6+ny*ny*(ny>0?1:.5)+nz*nz*.8)/length);
                for(Vertex v:q.vertices){
                    data[i++]=t.xx*v.x+t.xy*v.y+t.xz*v.z+t.tx;data[i++]=t.yx*v.x+t.yy*v.y+t.yz*v.z+t.ty;data[i++]=t.zx*v.x+t.zy*v.y+t.zz*v.z+t.tz;
                    data[i++]=v.r*shade;data[i++]=v.g*shade;data[i++]=v.b*shade;data[i++]=v.a;data[i++]=v.u;data[i++]=v.v;
                }
            }return data;
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
