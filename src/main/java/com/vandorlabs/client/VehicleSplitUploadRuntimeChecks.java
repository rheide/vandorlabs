package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

/** Real VBO readback and pixels for multiple opaque uploads; no shader pack is activated. */
final class VehicleSplitUploadRuntimeChecks {
    private static final int SIDE = 64, QUADS = SIDE * SIDE;

    static void run(Minecraft minecraft, File output, VertexFormat... formats) throws Exception {
        BufferedImage first = null;
        for (VertexFormat format : formats) {
            int stride = format.getNextOffset();
            ByteBuffer source = records(format);
            require(source.remaining() > VehicleMeshUpload.BATCH_BYTES, "fixture does not cross upload boundary");
            List<VertexBuffer> complete = new ArrayList<>(), sliced = new ArrayList<>();
            try {
                upload(complete, format, source, false);
                upload(sliced, format, source, true);
                require(sliced.size() > 1, "opaque upload did not split");
                BufferedImage reference = capture(minecraft, complete, format);
                BufferedImage actual = capture(minecraft, sliced, format);
                BufferedImage missingTail = capture(minecraft, sliced.subList(0, sliced.size() - 1), format);
                require(same(reference, actual), "split upload changed pixels for stride " + stride);
                require(!same(reference, missingTail), "missing final slice negative control did not change pixels");
                if (first == null) first = reference;
                else require(same(first, actual), "28/56-byte split upload pixel parity");
                javax.imageio.ImageIO.write(reference, "png", new File(output, "vehicle-vbo-unsplit-" + stride + ".png"));
                javax.imageio.ImageIO.write(actual, "png", new File(output, "vehicle-vbo-split-" + stride + ".png"));
                System.out.println("[vandorlabs][reprolab] vehicle-vbo-split-live PASS stride=" + stride
                        + " vertices=" + QUADS * 4 + " bytes=" + source.remaining() + " slices=" + sliced.size()
                        + " exact-GPU-payload-and-pixels; missing-tail negative control differs");
            } finally {
                for (VertexBuffer buffer : complete) buffer.deleteGlBuffers();
                for (VertexBuffer buffer : sliced) buffer.deleteGlBuffers();
            }
        }
    }

    private static ByteBuffer records(VertexFormat format) {
        int stride = format.getNextOffset();
        ByteBuffer result = BufferUtils.createByteBuffer(QUADS * 4 * stride);
        for (int quad = 0; quad < QUADS; quad++) for (int corner = 0; corner < 4; corner++) {
            int vertex = quad * 4 + corner, at = vertex * stride;
            // Distinct payload words make truncated/rebased shader records observable in GPU readback.
            for (int word = 0; word < stride / 4; word++)
                result.putInt(at + word * 4, 0x3f000000 | ((vertex * 37 + word) & 0x007fffff));
            float x = quad % SIDE + (corner == 1 || corner == 2 ? 1 : 0);
            float y = quad / SIDE + (corner >= 2 ? 1 : 0);
            result.putFloat(at, x); result.putFloat(at + 4, y); result.putFloat(at + 8, 0);
            result.put(at + 12, (byte) (32 + quad * 73 % 192));
            result.put(at + 13, (byte) (32 + quad * 131 % 192));
            result.put(at + 14, (byte) (32 + quad * 19 % 192));
            result.put(at + 15, (byte) 255);
            result.putFloat(at + 16, 0); result.putFloat(at + 20, 0); result.putInt(at + 24, 0x00f000f0);
            if (stride > 28) {
                result.put(at + 28, (byte) 0); result.put(at + 29, (byte) 0); result.put(at + 30, (byte) 127);
            }
        }
        return result;
    }

    private static void upload(List<VertexBuffer> buffers, VertexFormat format, ByteBuffer source, boolean split) {
        int total = source.remaining(), stride = format.getNextOffset(), offset = 0;
        while (offset < total) {
            int bytes = split ? VehicleMeshUpload.nextBytes(total, offset, stride, false) : total;
            require(bytes % (stride * 4) == 0, "slice cut through a quad");
            if (split) require(bytes <= VehicleMeshUpload.BATCH_BYTES, "slice exceeds opaque upload limit");
            VertexBuffer buffer = new VertexBuffer(format);
            buffers.add(buffer);
            buffer.bufferData(VehicleMeshUpload.slice(source, offset, bytes));
            ByteBuffer stored = BufferUtils.createByteBuffer(bytes);
            buffer.bindBuffer();
            try {
                if (GLContext.getCapabilities().OpenGL15)
                    GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, stored);
                else ARBVertexBufferObject.glGetBufferSubDataARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, 0L, stored);
            } finally { buffer.unbindBuffer(); }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "GPU readback failed");
            for (int i = 0; i < bytes; i++) if (stored.get(i) != source.get(offset + i))
                throw new IllegalStateException("Vehicle split upload: GPU payload changed at " + (offset + i));
            offset += bytes;
        }
        require(offset == total && source.position() == 0 && source.limit() == total, "upload changed source buffer bounds");
    }

    private static BufferedImage capture(Minecraft minecraft, List<VertexBuffer> buffers, VertexFormat format) {
        Framebuffer frame = new Framebuffer(SIDE, SIDE, true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.ortho(0, SIDE, SIDE, 0, -10, 10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        try (TurntableRenderState saved = new TurntableRenderState()) {
            frame.bindFramebuffer(true); GlStateManager.loadIdentity();
            GlStateManager.depthMask(true); GlStateManager.clearColor(0, 0, 0, 1);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.disableLighting(); GlStateManager.disableCull(); GlStateManager.disableBlend();
            GlStateManager.disableAlpha(); GlStateManager.enableDepth(); GlStateManager.depthFunc(GL11.GL_LEQUAL);
            minecraft.entityRenderer.disableLightmap(); GlStateManager.disableTexture2D();
            for (VertexBuffer buffer : buffers) BlockMeshVbo.draw(buffer, format, BlockRenderLayer.SOLID);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "split VBO draw failed");
            return net.minecraft.util.ScreenShotHelper.createScreenshot(SIDE, SIDE, frame);
        } finally {
            GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); frame.deleteFramebuffer(); minecraft.getFramebuffer().bindFramebuffer(true);
        }
    }
    private static boolean same(BufferedImage a, BufferedImage b) {
        return Arrays.equals(a.getRGB(0, 0, SIDE, SIDE, null, 0, SIDE), b.getRGB(0, 0, SIDE, SIDE, null, 0, SIDE));
    }
    private static void require(boolean yes, String message) { if (!yes) throw new IllegalStateException("Vehicle split upload: " + message); }
    private VehicleSplitUploadRuntimeChecks() { }
}
