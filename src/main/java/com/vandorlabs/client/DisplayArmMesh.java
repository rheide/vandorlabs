package com.vandorlabs.client;

import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmGeometry.Box;
import com.vandorlabs.display.DisplayArmGeometry.Vec;
import com.vandorlabs.render.ScreenSurface;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.model.pipeline.LightUtil;

/** The world and inventory share the portable arm's actual component surfaces. */
public final class DisplayArmMesh {
    private static final Map<DisplayArmGeometry, Part[]> MESHES = new WeakHashMap<>();
    private DisplayArmMesh() { }

    /** Emit model-pixel geometry into an already-started buffer, without changing GL state. */
    public static void draw(BufferBuilder buffer, DisplayArmGeometry geometry,
            TextureAtlasSprite housing, TextureAtlasSprite white, int combinedLight) {
        for (Part part : mesh(geometry)) {
            TextureAtlasSprite sprite = part.housing ? housing : white;
            double[] v = part.vertices;
            for (int i = 0; i < v.length; i += 11)
                buffer.pos(v[i], v[i + 1], v[i + 2])
                        .color((float) v[i + 8], (float) v[i + 9], (float) v[i + 10], 1)
                        .tex(sprite.getInterpolatedU(v[i + 3]), sprite.getInterpolatedV(v[i + 4]))
                        // 1.12 BufferBuilder reverses SHORT lightmap arguments in the record.
                        .lightmap(combinedLight >>> 16 & 65535, combinedLight & 65535)
                        .normal((float) v[i + 5], (float) v[i + 6], (float) v[i + 7]).endVertex();
        }
    }

    private static synchronized Part[] mesh(DisplayArmGeometry geometry) {
        Part[] cached = MESHES.get(geometry);
        if (cached != null) return cached;
        Part[] parts = new Part[geometry.boxes.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = new Part(geometry.boxes.get(i));
        // Values contain only primitive arrays; they cannot retain the weak geometry key.
        MESHES.put(geometry, parts);
        return parts;
    }

    /** Static inventory artwork uses the same screen pose and viewer-facing UV direction. */
    static void surface(BufferBuilder buffer, DisplayArmGeometry geometry, int slot,
            TextureAtlasSprite sprite, int combinedLight) {
        if (!geometry.hasSurface(slot)) return;
        ScreenSurface.Quad quad = geometry.surface(slot);
        Vec normal = geometry.display.xAxis.scale(quad.nx)
                .add(geometry.display.yAxis.scale(quad.ny)).add(geometry.display.zAxis.scale(quad.nz));
        for (int i = 0; i < 4; i++) {
            ScreenSurface.Vertex local = quad.vertices[i];
            Vec point = geometry.display.point(local.x, local.y, local.z);
            double u = i == 0 || i == 3 ? 16 : 0;
            // Existing input artwork has the opposite authored U direction to screens.
            if (slot == 1) u = 16 - u;
            vertex(buffer, point, normal, sprite, u,
                    i < 2 ? 0 : 16, 0xffffff, 1, combinedLight);
        }
    }

    private static void vertex(BufferBuilder buffer, Vec point, Vec normal,
            TextureAtlasSprite sprite, double u, double v, int color, float shade, int light) {
        buffer.pos(point.x, point.y, point.z)
                .color((color >> 16 & 255) * shade / 255F,
                        (color >> 8 & 255) * shade / 255F, (color & 255) * shade / 255F, 1)
                .tex(sprite.getInterpolatedU(u), sprite.getInterpolatedV(v))
                .lightmap(light >>> 16 & 65535, light & 65535)
                .normal((float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }

    private static final class Part {
        final boolean housing;
        final double[] vertices = new double[24 * 11];
        Part(Box box) {
            housing = box.kind == DisplayArmGeometry.HOUSING;
            int color = box.kind == DisplayArmGeometry.ARM ? 0xa3adb5
                    : box.kind == DisplayArmGeometry.BASE ? 0x596269
                    : box.kind == DisplayArmGeometry.DETAIL ? 0x29353d : 0xffffff;
            Vec[] corners = box.corners();
            Vec[] normals = {box.xAxis.scale(-1), box.xAxis, box.yAxis.scale(-1),
                    box.yAxis, box.zAxis.scale(-1), box.zAxis};
            int next = 0;
            for (int face = 0; face < DisplayArmGeometry.FACES.length; face++) {
                Vec normal = normals[face];
                float shade = LightUtil.diffuseLight((float) normal.x, (float) normal.y, (float) normal.z);
                for (int i = 0; i < 4; i++) {
                    Vec point = corners[DisplayArmGeometry.FACES[face][i]];
                    vertices[next++] = point.x; vertices[next++] = point.y; vertices[next++] = point.z;
                    vertices[next++] = i == 0 || i == 3 ? 0 : 16; vertices[next++] = i < 2 ? 0 : 16;
                    vertices[next++] = normal.x; vertices[next++] = normal.y; vertices[next++] = normal.z;
                    vertices[next++] = (color >> 16 & 255) * shade / 255F;
                    vertices[next++] = (color >> 8 & 255) * shade / 255F;
                    vertices[next++] = (color & 255) * shade / 255F;
                }
            }
        }
    }
}
