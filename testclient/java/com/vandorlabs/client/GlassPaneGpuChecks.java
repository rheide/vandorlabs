package com.vandorlabs.client;

import com.vandorlabs.render.JoinedPaneMesher;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Runs with an already-current compatibility GL context; no Minecraft instance or world. */
public final class GlassPaneGpuChecks {
    private static final int SIZE = 96;
    private static final IntBuffer ATTRIBUTE = BufferUtils.createIntBuffer(4);
    private static long checks;

    private static final class Sprite extends TextureAtlasSprite {
        Sprite() { super("vandorlabs:checks/uniform-pane"); setIconWidth(16); setIconHeight(16); initSprite(16, 16, 0, 0, false); }
    }

    private static VertexFormat format(boolean extended) {
        VertexFormat format = new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F)
                .addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.TEX_2F)
                .addElement(DefaultVertexFormats.TEX_2S).addElement(DefaultVertexFormats.NORMAL_3B)
                .addElement(DefaultVertexFormats.PADDING_1B);
        if (extended) format.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT,
                VertexFormatElement.EnumUsage.PADDING, 6));
        return format;
    }

    public static void main(String[] args) {
        OpenGlHelper.initializeTextures();
        int texture = GL11.glGenTextures();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            ByteBuffer rgba = BufferUtils.createByteBuffer(16 * 16 * 4);
            for (int i = 0; i < 256; i++) rgba.put((byte) 216).put((byte) 155).put((byte) 43).put((byte) 129);
            rgba.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            Sprite sprite = new Sprite();
            byte[] units = capture(sprite, texture, format(false), false, false, 0, false);
            byte[] merged = capture(sprite, texture, format(false), true, false, 0, false);
            byte[] extended = capture(sprite, texture, format(true), true, false, 29999984, false);
            byte[] rotated = capture(sprite, texture, format(true), true, true, -30000000, false);
            byte[] corrupt = capture(sprite, texture, format(true), true, false, 0, true);
            require(Arrays.equals(units, merged), "actual merged pixels match unit geometry");
            require(Arrays.equals(merged, extended), "actual 32/56-byte pixels and positive-world precision");
            require(Arrays.equals(merged, rotated), "actual rotated pixels and negative-world precision");
            require(!Arrays.equals(merged, corrupt), "negative control detects incorrect VBO stride");
            int hole = ((SIZE / 2) * SIZE + SIZE / 2) * 4;
            require(merged[hole] == 0 && merged[hole + 1] == 0 && merged[hole + 2] == 0, "empty window aperture");
            int filled = (3 * SIZE + 3) * 4;
            require((merged[filled] & 255) > 70, "tinted window actually rendered");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after pane smoke");
            System.out.println("GlassPaneGpu PASS checks=" + checks
                    + " unit_quads=208 merged_quads=4; actual GPU 32/56-byte pixel parity, uploaded extended payload, hole coverage, rotations, world precision, generic/client arrays, prior VBO restoration and wrong-stride negative control");
            System.out.println("GL renderer=" + GL11.glGetString(GL11.GL_RENDERER) + " version=" + GL11.glGetString(GL11.GL_VERSION)
                    + "; this fixture does not load an OptiFine shader pack or measure game FPS");
        } finally { GL11.glDeleteTextures(texture); }
    }

    private static byte[] capture(Sprite sprite, int texture, VertexFormat format,
            boolean merge, boolean rotated, int origin, boolean wrongStride) {
        List<JoinedPaneMesher.Cell> cells = new ArrayList<>();
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
            if (u < 5 || u >= 11 || v < 4 || v >= 12) cells.add(new JoinedPaneMesher.Cell(u, v, true));
        List<JoinedPaneMesher.Rect> rectangles = JoinedPaneMesher.merge(cells, merge);
        require(rectangles.size() == (merge ? 4 : 208), "rectangle reduction");
        BufferBuilder records = new BufferBuilder(32768);
        records.begin(GL11.GL_QUADS, format);
        GlassPaneBuffers.emit(records, rectangles, rotated, 0, 0, sprite, true, 0x00f000f0);
        records.finishDrawing();
        if (format.getNextOffset() > 32) for (int vertex = 0; vertex < records.getVertexCount(); vertex++)
            for (int offset = 32; offset < format.getNextOffset(); offset++)
                records.getByteBuffer().put(vertex * format.getNextOffset() + offset, (byte) (vertex * 17 + offset));

        VertexBuffer buffer = new VertexBuffer(new VertexFormat(format));
        VertexBuffer previous = new VertexBuffer(new VertexFormat(format));
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            buffer.bufferData(GlassPaneBuffers.records(records));
            buffer.bindBuffer();
            ByteBuffer returned = BufferUtils.createByteBuffer(records.getVertexCount() * format.getNextOffset());
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, returned);
            ByteBuffer expected = GlassPaneBuffers.records(records);
            for (int at = 0; at < returned.capacity(); at++) require(returned.get(at) == expected.get(at), "uploaded record byte " + at);
            previous.bufferData(GlassPaneBuffers.records(records));

            GL20.glUseProgram(0);
            GL11.glViewport(0, 0, SIZE, SIZE);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, 16, 16, 0, -10, 10);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glClearColor(0, 0, 0, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_DITHER);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            int anchor = origin + 15;
            GL11.glTranslated(GlassPaneBuffers.translation(anchor - (double) origin, origin, anchor), 0, 0);
            if (rotated) GL11.glRotatef(90, 0, 1, 0);

            previous.bindBuffer();
            int previousBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
            GL20.glVertexAttribPointer(8, 4, GL11.GL_FLOAT, false, format.getNextOffset(), 0L);
            GL20.glEnableVertexAttribArray(8);
            int attributeBuffer = attribute(8, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
            GlassPaneBuffers.bindArrays(buffer, format, false);
            try {
                GL20.glDisableVertexAttribArray(8);
                GL20.glVertexAttribPointer(8, 2, GL11.GL_FLOAT, false, format.getNextOffset(), 16L);
                if (wrongStride) {
                    GL11.glVertexPointer(3, GL11.GL_FLOAT, 32, 0L);
                    GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 32, 12L);
                }
                buffer.drawArrays(GL11.GL_QUADS);
            } finally { GlassPaneBuffers.unbindArrays(); }
            require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == previousBinding, "prior VBO binding restored");
            require(GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE) == GL13.GL_TEXTURE1, "client active texture restored");
            require(attribute(8, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0, "generic array enabled state restored");
            require(attribute(8, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING) == attributeBuffer, "generic array buffer restored");
            require(attribute(8, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE) == 4, "generic pointer restored");
            ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.capacity()]; pixels.get(result); return result;
        } finally {
            GL11.glPopClientAttrib(); buffer.deleteGlBuffers(); previous.deleteGlBuffers();
        }
    }

    private static int attribute(int index, int name) {
        ATTRIBUTE.clear(); GL20.glGetVertexAttrib(index, name, ATTRIBUTE); return ATTRIBUTE.get(0);
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException("Static glass GPU: " + message);
    }
    private GlassPaneGpuChecks() {}
}
