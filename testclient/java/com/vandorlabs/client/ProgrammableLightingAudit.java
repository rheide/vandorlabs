package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/** Inspects existing submitted geometry without a GL context or renderer changes. */
public final class ProgrammableLightingAudit {
    private static final TextureAtlasSprite SPRITE = new TextureAtlasSprite("audit") { };

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        SPRITE.setIconWidth(16);
        SPRITE.setIconHeight(16);
        SPRITE.initSprite(256, 256, 32, 48, false);
        System.out.println("Geometry inspection only; no shader or visual acceptance claim.");
        System.out.println("case,quads,front_inward,back_inward,top_inward,bottom_inward,normal_attribute,vertex_lightmap");
        BufferBuilder plain = legacyBuffer();
        invoke("panelRect", plain, SPRITE, null, 0, 0, 16, 16);
        rims(plain, null);
        inspect("wall_isolated", plain);

        for (int shape = 0; shape < 4; shape++) {
            PortholeHex.Slice slice = new PortholeHex(1, 1, shape).slice(0, 0);
            BufferBuilder hole = legacyBuffer();
            for (double[] q : slice.frameQuads)
                invoke("panelQuad", hole, SPRITE, null, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7]);
            for (double[] edge : slice.hexEdges)
                invoke("rimSegment", hole, SPRITE, null, edge[0], edge[1], edge[2], edge[3], 4D);
            rims(hole, null);
            inspect("porthole_shape_" + shape, hole);
        }

        Constructor<?> corner = BlockProgrammableWall.FlatCorner.class.getDeclaredConstructors()[0];
        corner.setAccessible(true);
        Object joined = corner.newInstance(6D, null, Boolean.TRUE, null, true, true);
        BufferBuilder bend = legacyBuffer();
        invoke("renderFlatWall", bend, SPRITE, SPRITE, 6D, joined);
        inspect("wall_corner", bend);

        BlockProgrammableWall wall = new BlockProgrammableWall("audit_wall", BlockProgrammableWall.Shape.PLAIN);
        BlockProgrammableWall porthole = new BlockProgrammableWall("audit_porthole", BlockProgrammableWall.Shape.PORTHOLE);
        if (wall.getDefaultState().useNeighborBrightness() != porthole.getDefaultState().useNeighborBrightness()
                || wall.getDefaultState().getLightOpacity() != porthole.getDefaultState().getLightOpacity())
            throw new IllegalStateException("Wall/porthole light propagation settings differ");
        System.out.println("Wall/porthole propagation: neighborBrightness=" + wall.getDefaultState().useNeighborBrightness()
                + ", lightOpacity=" + wall.getDefaultState().getLightOpacity());
        DiagonalSurfaceChecks.run();
        BufferBuilder trapdoor = new BufferBuilder(4096);
        trapdoor.begin(7, BlockSurfaceFormat.get());
        TEProgrammableTrapdoor.drawLeaf(trapdoor, SPRITE, 0, false, 0, 0, (192 << 16) | 80);
        trapdoor.finishDrawing();
        VertexFormat format = trapdoor.getVertexFormat();
        ByteBuffer data = trapdoor.getByteBuffer();
        for (int vertex = 0; vertex < trapdoor.getVertexCount(); vertex++) {
            int offset = vertex * format.getNextOffset();
            int light = offset + format.getUvOffsetById(1);
            if (data.getShort(light) != 80 || data.getShort(light + 2) != 192)
                throw new IllegalStateException("trapdoor lightmap channels differ from control");
            if (vertex % 4 == 0) {
                double[] a = position(data, offset), b = position(data, offset + format.getNextOffset());
                double[] c = position(data, offset + 2 * format.getNextOffset());
                double[] cross = cross(a, b, c);
                int normal = offset + format.getNormalOffset();
                double dot = cross[0] * data.get(normal) + cross[1] * data.get(normal + 1)
                        + cross[2] * data.get(normal + 2);
                if (dot <= 0) throw new IllegalStateException("trapdoor normal disagrees with winding");
            }
        }
        System.out.println("Trapdoor control: explicit normals agree with winding; block=80, sky=192.");
    }

    private static BufferBuilder legacyBuffer() {
        BufferBuilder buffer = new BufferBuilder(4096);
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        return buffer;
    }

    private static void rims(BufferBuilder buffer, DiagonalPortholeMesh mesh) throws Exception {
        for (double y : new double[]{0, 16}) invoke("topRim", buffer, SPRITE, mesh, y, 0D, 16D, 4D);
        for (double x : new double[]{0, 16}) invoke("sideRim", buffer, SPRITE, mesh, x, 0D, 16D, 4D);
    }

    private static void invoke(String name, Object... args) throws Exception {
        for (Method method : TEAnimatedScreenSelector.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                method.invoke(null, args);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void inspect(String label, BufferBuilder buffer) {
        buffer.finishDrawing();
        VertexFormat format = buffer.getVertexFormat();
        ByteBuffer data = buffer.getByteBuffer();
        int[] inward = new int[4];
        for (int i = 0; i < buffer.getVertexCount(); i += 4) {
            int offset = i * format.getNextOffset();
            double[] a = position(data, offset), b = position(data, offset + format.getNextOffset());
            double[] c = position(data, offset + 2 * format.getNextOffset());
            double[] d = position(data, offset + 3 * format.getNextOffset());
            double[] n = cross(a, b, c);
            if (same(a[2], b[2], c[2], d[2])) {
                if (Math.abs(a[2] - 6) < 1E-6 && n[2] > 1E-6) inward[0]++;
                if (Math.abs(a[2] - 10) < 1E-6 && n[2] < -1E-6) inward[1]++;
            }
            if (same(a[1], b[1], c[1], d[1])) {
                if (Math.abs(a[1] - 16) < 1E-6 && n[1] < -1E-6) inward[2]++;
                if (Math.abs(a[1]) < 1E-6 && n[1] > 1E-6) inward[3]++;
            }
        }
        System.out.printf("%s,%d,%d,%d,%d,%d,%s,%s%n", label, buffer.getVertexCount() / 4,
                inward[0], inward[1], inward[2], inward[3], format.hasNormal(), format.hasUvOffset(1));
    }

    private static boolean same(double a, double b, double c, double d) {
        return Math.abs(a - b) + Math.abs(a - c) + Math.abs(a - d) < 1E-6;
    }

    private static double[] position(ByteBuffer data, int offset) {
        return new double[]{data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8)};
    }

    private static double[] cross(double[] a, double[] b, double[] c) {
        double x = b[0] - a[0], y = b[1] - a[1], z = b[2] - a[2];
        double u = c[0] - a[0], v = c[1] - a[1], w = c[2] - a[2];
        return new double[]{y * w - z * v, z * u - x * w, x * v - y * u};
    }
}
