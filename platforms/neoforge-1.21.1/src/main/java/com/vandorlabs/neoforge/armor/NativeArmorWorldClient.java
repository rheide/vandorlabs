package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.light.NativeLightBlock;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read the live baked geometry and use a representative face if the exact plane is unavailable. */
final class NativeArmorWorldClient {
    static void sample(NativeArmorWorldSampling.Request request) {
        var hit = request.hit();
        String result = "";
        try {
            var sprite = resolve(Minecraft.getInstance().level, hit);
            if (usable(sprite)) {
                String name = sprite.contents().name().toString();
                if (NativeArmorWorldSampling.sprite(name) != null) result = name;
            }
        } catch (RuntimeException unsupportedModel) {
            // A resource pack or dynamic model may have no stable sample for the requested face.
        }
        PacketDistributor.sendToServer(new NativeArmorWorldSampling.Result(request.id(), hit.getBlockPos(),
            hit.getDirection(), result));
    }

    private static TextureAtlasSprite resolve(ClientLevel level, BlockHitResult hit) {
        var pos = hit.getBlockPos();
        if (level == null || !level.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        if (!NativeArmorWorldSampling.supported(state)) return null;
        var tile = level.getBlockEntity(pos);
        var minecraft = Minecraft.getInstance();
        if (state.getBlock() instanceof NativeLightBlock) {
            var material = NativeArmorWorldSampling.sprite(NativeArmorWorldSampling.verifiedMaterial(state, tile, hit.getDirection()));
            return material == null ? null : minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(material);
        }
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(level, pos, state, tile == null ? ModelData.EMPTY : tile.getModelData());
        var random = RandomSource.create(state.getSeed(pos));
        var point = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        var culled = model.getQuads(state, hit.getDirection(), random, data, null);
        var first = closest(culled, point, hit.getDirection());
        random.setSeed(state.getSeed(pos));
        var full = model.getQuads(state, null, random, data, null);
        var second = closest(full, point, hit.getDirection());
        if (first.distance() <= second.distance() && first.distance() < .08) return first.sprite();
        if (second.distance() < .08) return second.sprite();
        if (first.sprite() != null) return first.sprite();
        if (second.sprite() != null) return second.sprite();
        return model.getParticleIcon(data);
    }

    private record Match(TextureAtlasSprite sprite, double distance) {}

    private static Match closest(List<BakedQuad> quads, Vec3 point, Direction face) {
        TextureAtlasSprite fallback = null, best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var quad : quads) {
            var sprite = quad.getSprite();
            if (!usable(sprite)) continue;
            if (fallback == null && quad.getDirection() == face) fallback = sprite;
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            if (stride < 3) continue;
            var a = at(vertices, 0);
            var b = at(vertices, stride);
            var c = at(vertices, 2 * stride);
            var d = at(vertices, 3 * stride);
            double candidate = Math.min(distance(point, a, b, c), distance(point, a, c, d));
            if (candidate < distance) {
                distance = candidate;
                best = sprite;
            }
        }
        return new Match(distance < .08 ? best : fallback, distance);
    }

    private static Vec3 at(int[] vertices, int offset) {
        return new Vec3(Float.intBitsToFloat(vertices[offset]), Float.intBitsToFloat(vertices[offset + 1]),
            Float.intBitsToFloat(vertices[offset + 2]));
    }

    private static double distance(Vec3 point, Vec3 a, Vec3 b, Vec3 c) {
        var ab = b.subtract(a);
        var ac = c.subtract(a);
        var normal = ab.cross(ac);
        double norm = normal.lengthSqr();
        if (norm < 1.e-12) return Double.POSITIVE_INFINITY;
        var relative = point.subtract(a);
        double plane = relative.dot(normal);
        var projected = relative.subtract(normal.scale(plane / norm));
        double ab2 = ab.dot(ab), abac = ab.dot(ac), ac2 = ac.dot(ac);
        double pAB = projected.dot(ab), pAC = projected.dot(ac);
        double determinant = ab2 * ac2 - abac * abac;
        double u = (ac2 * pAB - abac * pAC) / determinant;
        double v = (ab2 * pAC - abac * pAB) / determinant;
        if (u < -1.e-5 || v < -1.e-5 || u + v > 1.00001) return Double.POSITIVE_INFINITY;
        return Math.abs(plane) / Math.sqrt(norm);
    }

    private static boolean usable(TextureAtlasSprite sprite) {
        return sprite != null && !sprite.contents().name().getPath().equals("missingno");
    }

    private NativeArmorWorldClient() {}
}
