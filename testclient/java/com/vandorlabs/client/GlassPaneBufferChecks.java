package com.vandorlabs.client;

import com.vandorlabs.render.JoinedPaneMesher;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

/** CPU checks of the actual retained emitter, independent of a hardware shader pack. */
public final class GlassPaneBufferChecks {
    private static long checks;
    private static void need(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static final class Sprite extends TextureAtlasSprite {
        Sprite() {
            super("vandorlabs:checks/pane");
            setIconWidth(16); setIconHeight(16); initSprite(512, 512, 96, 144, false);
        }
    }

    /** An extension written through endVertex detects lost/repacked shader payloads. */
    private static final class Records extends BufferBuilder {
        int ends;
        Records() { super(32768); }
        @Override public void endVertex() {
            super.endVertex(); ends++;
            int stride = getVertexFormat().getNextOffset();
            if (stride > 32) {
                int offset = (getVertexCount() - 1) * stride;
                for (int i = 32; i < stride; i++) getByteBuffer().put(offset + i, (byte) (ends * 17 + i));
            }
        }
        @Override public void addVertexData(int[] data) { throw new AssertionError("Captured pane records were repacked"); }
    }

    private static VertexFormat format(boolean extended) {
        VertexFormat f = new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F)
                .addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.TEX_2F)
                .addElement(DefaultVertexFormats.TEX_2S).addElement(DefaultVertexFormats.NORMAL_3B)
                .addElement(DefaultVertexFormats.PADDING_1B);
        if (extended) f.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT,
                VertexFormatElement.EnumUsage.PADDING, 6));
        return f;
    }

    private static void compare(Records previous, Records retained) {
        need(previous.getVertexCount() == retained.getVertexCount(), "compatibility vertex count");
        ByteBuffer expected = previous.getByteBuffer(), actual = GlassPaneBuffers.records(retained);
        int bytes = previous.getVertexCount() * previous.getVertexFormat().getNextOffset();
        need(actual.remaining() == bytes, "complete upload byte range");
        for (int i = 0; i < bytes; i++) need(expected.get(i) == actual.get(i), "native/shader record byte " + i);
    }

    private static void compatibility(Sprite sprite) {
        for (boolean extended : new boolean[] {false, true}) {
            VertexFormat f = format(extended);
            need(GlassPaneBuffers.ordinaryLayout(f), "supported standard prefix");
            for (boolean rotated : new boolean[] {false, true}) for (int depth = 0; depth < 3; depth++) {
                for (int light : new int[] {0, 0x00f00020, 0x005000e0}) {
                    Records before = new Records(), after = new Records();
                    before.begin(7, f); after.begin(7, f);
                    new GlassNativePanes.Pane(sprite, rotated, depth).draw(before,
                            rotated ? 9 : 12, 7, rotated ? 12 : 9, light >>> 16, light & 65535);
                    GlassPaneBuffers.emit(after, Collections.singletonList(new JoinedPaneMesher.Rect(12, 7, 1, 1)),
                            rotated, depth, 9, sprite, false, light);
                    before.finishDrawing(); after.finishDrawing();
                    need(before.ends == 8 && after.ends == 8, "all shader writer hooks execute");
                    compare(before, after);
                }
            }
        }
    }

    private static void coverage(Sprite sprite) {
        List<JoinedPaneMesher.Cell> cells = new ArrayList<>();
        boolean[] occupied = new boolean[256];
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++) {
            if (u >= 5 && u < 11 && v >= 4 && v < 12) continue;
            occupied[v * 16 + u] = true;
            cells.add(new JoinedPaneMesher.Cell(u, v, true));
        }
        List<JoinedPaneMesher.Rect> rectangles = JoinedPaneMesher.merge(cells);
        for (boolean rotated : new boolean[] {false, true}) for (boolean single : new boolean[] {false, true}) {
            Records records = new Records(); VertexFormat f = format(true);
            records.begin(7, f);
            GlassPaneBuffers.emit(records, rectangles, rotated, 0, 6, sprite, single, 0x00b00070);
            records.finishDrawing();
            ByteBuffer data = GlassPaneBuffers.records(records);
            int stride = f.getNextOffset();
            need(records.getVertexCount() == rectangles.size() * (single ? 4 : 8), "single-surface geometry halves records");
            for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++) {
                int covered = 0;
                for (int vertex = 0; vertex < records.getVertexCount(); vertex += 4) {
                    float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
                    float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
                    for (int i = 0; i < 4; i++) {
                        int at = (vertex + i) * stride;
                        float horizontal = data.getFloat(at + (rotated ? 8 : 0)), vertical = data.getFloat(at + 4);
                        minU = Math.min(minU, horizontal); maxU = Math.max(maxU, horizontal);
                        minV = Math.min(minV, vertical); maxV = Math.max(maxV, vertical);
                        need(Math.abs(data.getFloat(at + (rotated ? 0 : 8)) - 6.5F) <= .0625F,
                                "single/dual surfaces remain in native pane thickness");
                        need(data.getFloat(at + 16) >= sprite.getMinU() && data.getFloat(at + 16) <= sprite.getMaxU(),
                                "UV stays inside glass atlas sprite");
                    }
                    if (u + .5F > minU && u + .5F < maxU && v + .5F > minV && v + .5F < maxV) covered++;
                    // A nonzero UV area is necessary for finite shader tangent construction.
                    float ux = data.getFloat(vertex * stride + 16), uy = data.getFloat(vertex * stride + 20);
                    float vx = data.getFloat((vertex + 1) * stride + 16), vy = data.getFloat((vertex + 1) * stride + 20);
                    float wx = data.getFloat((vertex + 2) * stride + 16), wy = data.getFloat((vertex + 2) * stride + 20);
                    need(Math.abs((vx - ux) * (wy - uy) - (vy - uy) * (wx - ux)) > 0, "nondegenerate shader UV basis");
                }
                need(covered == (occupied[v * 16 + u] ? single ? 1 : 2 : 0), "actual vertices preserve aperture and coverage");
            }
        }
    }

    private static void worldPrecision() {
        for (int origin : new int[] {-30000000, -16, 0, 29999984}) {
            for (int localAnchor = 0; localAnchor < 16; localAnchor++) {
                int anchor = origin + localAnchor;
                double camera = origin + 7.375;
                double translation = GlassPaneBuffers.translation(anchor - camera, origin, anchor);
                need(translation == origin - camera, "camera-relative translation keeps world-limit precision");
                for (double local : new double[] {0, .0625, .5, 15.9375, 16})
                    need(translation + local == (origin + local) - camera, "retained local vertex reconstructs exact world position");
            }
        }
    }

    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        Sprite sprite = new Sprite(); compatibility(sprite); coverage(sprite); worldPrecision();
        need(!GlassPaneBuffers.ordinaryLayout(DefaultVertexFormats.POSITION_TEX), "unknown format rejects before publication");
        System.out.println("GlassPaneBuffers PASS checks=" + checks
                + " native and 56-byte record parity, complete shader payloads, exact apertures, one surface, atlas bounds, tangents and world-limit precision; hardware shader acceptance separate");
    }
    private GlassPaneBufferChecks() {}
}
