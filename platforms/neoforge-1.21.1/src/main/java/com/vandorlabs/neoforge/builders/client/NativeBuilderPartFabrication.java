package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.geometry.BuilderFabricationMesh;
import com.vandorlabs.neoforge.builders.NativeBuilderProducts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Uses the live atlas and item models with the same fabrication mesh as Forge. */
final class NativeBuilderPartFabrication {
    private static final long BUDGET = 12L * 1024 * 1024;
    private static final int[][] TRIANGLE_VERTICES = {{0, 1, 2}, {0, 2, 3}};
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(16, .75F, true);
    private static final Map<String, ItemStack> PRODUCTS = new HashMap<>();
    private static long retained;

    static final class Entry {
        final String key;
        final BuilderFabricationMesh.Model model;
        int count = -1;
        int[] vertices;

        Entry(String key, ItemStack stack, boolean press) {
            this.key = key;
            var minecraft = Minecraft.getInstance();
            var item = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
            var input = new ArrayList<BuilderFabricationMesh.Triangle>();
            for (var pass : item.getRenderPasses(stack, false)) {
                for (int side = -1; side < 6 && input.size() < 2048; side++) {
                    var face = side < 0 ? null : Direction.from3DDataValue(side);
                    for (var quad : pass.getQuads(null, face, RandomSource.create(0))) {
                        if (input.size() >= 2048) break;
                        int[] raw = quad.getVertices();
                        int stride = raw.length / 4;
                        if (stride < 8 || raw.length % 4 != 0) continue;
                        int tint = quad.isTinted() ? minecraft.getItemColors().getColor(stack, quad.getTintIndex()) : -1;
                        append(input, raw, stride, quad.getSprite(), tint);
                    }
                }
            }
            var scale = item.getTransforms().fixed.scale;
            double fixedScale = Math.min(Math.abs(scale.x()), Math.min(Math.abs(scale.y()), Math.abs(scale.z())));
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            model = new BuilderFabricationMesh.Model(input, press, path.endsWith("_module"), fixedScale);
        }
    }

    static ItemStack item(String machine, int index) {
        String key = NativeBuilderProducts.parts(machine).get(index);
        return PRODUCTS.computeIfAbsent(key, unused -> NativeBuilderProducts.partResult(machine, index));
    }

    static Entry geometry(String machine, int index) {
        String key = NativeBuilderProducts.parts(machine).get(index);
        var entry = CACHE.get(key);
        if (entry != null) return entry;
        var stack = item(machine, index);
        if (stack.isEmpty()) return null;
        entry = new Entry(key, stack, machine.equals("assembly_press"));
        evict(entry.model.bytes);
        if (entry.model.bytes <= BUDGET) {
            CACHE.put(key, entry);
            retained += entry.model.bytes;
        }
        return entry;
    }

    static void emit(Entry entry, double progress, PoseStack.Pose pose, VertexConsumer target, TextureAtlasSprite colorSprite) {
        int count = entry.model.count(progress);
        int[] data = entry.vertices;
        if (data == null || count != entry.count) {
            data = entry.model.build(count);
            if (entry.vertices != null) {
                retained -= entry.vertices.length * 4L;
                entry.vertices = null;
            }
            long bytes = data.length * 4L;
            while (CACHE.size() > 1 && retained + bytes > BUDGET) retire();
            if (CACHE.containsKey(entry.key) && retained + bytes <= BUDGET) {
                entry.vertices = data;
                entry.count = count;
                retained += bytes;
            }
        }
        float u = colorSprite.getU(.5F), v = colorSprite.getV(.5F);
        for (int at = 0; at < data.length; at += 4) {
            int color = data[at + 3];
            int face = color >>> 24;
            int sign = (face & 1) == 0 ? -1 : 1;
            target.addVertex(pose.pose(), Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]),
                            Float.intBitsToFloat(data[at + 2]))
                    .setColor(0xff000000 | color & 0xffffff).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0)
                    .setNormal(pose, face / 2 == 0 ? sign : 0, face / 2 == 1 ? sign : 0, face / 2 == 2 ? sign : 0);
        }
    }

    private static void append(List<BuilderFabricationMesh.Triangle> input, int[] raw, int stride,
                               TextureAtlasSprite sprite, int tint) {
        int color = raw[3];
        int red = (color & 255) * (tint >>> 16 & 255) / 255;
        int green = (color >>> 8 & 255) * (tint >>> 8 & 255) / 255;
        int blue = (color >>> 16 & 255) * (tint & 255) / 255;
        int surface = input.size() / 2;
        BuilderFabricationMesh.Pixels pixels = (u, v) -> {
            int width = sprite.contents().width(), height = sprite.contents().height();
            int x = Math.clamp((int) ((u - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * width), 0, width - 1);
            int y = Math.clamp((int) ((v - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * height), 0, height - 1);
            int pixel = sprite.getPixelRGBA(0, x, y);
            return pixel & 0xff000000 | ((pixel & 255) * red / 255) << 16
                    | ((pixel >>> 8 & 255) * green / 255) << 8 | (pixel >>> 16 & 255) * blue / 255;
        };
        for (var triangle : TRIANGLE_VERTICES) {
            double[] positions = new double[9], uv = new double[6];
            for (int index = 0; index < 3; index++) {
                int base = triangle[index] * stride;
                for (int axis = 0; axis < 3; axis++) positions[index * 3 + axis] = Float.intBitsToFloat(raw[base + axis]);
                uv[index * 2] = Float.intBitsToFloat(raw[base + 4]);
                uv[index * 2 + 1] = Float.intBitsToFloat(raw[base + 5]);
            }
            input.add(new BuilderFabricationMesh.Triangle(positions, uv, surface, pixels));
        }
    }

    private static void evict(long bytes) {
        while (!CACHE.isEmpty() && retained + bytes > BUDGET) retire();
    }

    private static void retire() {
        var iterator = CACHE.values().iterator();
        var entry = iterator.next();
        retained -= entry.model.bytes + (entry.vertices == null ? 0 : entry.vertices.length * 4L);
        iterator.remove();
    }

    static void clear() {
        CACHE.clear();
        PRODUCTS.clear();
        retained = 0;
    }

    private NativeBuilderPartFabrication() {}
}
