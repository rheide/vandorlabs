package com.vandorlabs.client;

import com.vandorlabs.render.JoinedPaneMesher;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Focused live GPU submission of the actual pane emitter and VBO pointer path. */
final class GlassPaneRuntimeChecks {
    private static final int SIZE = 96;
    private static final IntBuffer ATTRIBUTE = BufferUtils.createIntBuffer(4);

    static void run(Minecraft mc, File output) throws Exception {
        GlassPaneTextures.Selection material = GlassPaneTextures.select(19, 1);
        require(material != null && material.smooth, "bundled smooth gold material was not stitched");
        VertexFormat standard = format(false), wide = format(true);
        BufferedImage units = capture(mc, material.sprite, standard, false, false, 0);
        BufferedImage merged = capture(mc, material.sprite, standard, true, false, 0);
        BufferedImage extended = capture(mc, material.sprite, wide, true, false, 29999984);
        BufferedImage rotated = capture(mc, material.sprite, wide, true, true, -30000000);
        require(same(units, merged), "merged native pane pixels differ from unit geometry");
        require(same(merged, extended), "56-byte pane pointers or positive-world translation differ");
        require(same(merged, rotated), "rotated pane pointers or negative-world translation differ");
        require((merged.getRGB(SIZE / 2, SIZE / 2) & 0xffffff) == 0, "pane aperture was filled");
        require((merged.getRGB(3, 3) & 0xffffff) != 0, "gold pane did not draw");
        javax.imageio.ImageIO.write(merged, "PNG", new File(output, "static-glass-merged.png"));
        javax.imageio.ImageIO.write(extended, "PNG", new File(output, "static-glass-extended.png"));
        System.out.println("[vandorlabs][reprolab] static-glass-live PASS 208 unit panes to 4 rectangles; actual gold pixels, 32/56-byte VBOs, rotations, aperture, world limits, generic arrays and buffer binding restored; shader-pack acceptance separate");
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

    private static BufferedImage capture(Minecraft mc, TextureAtlasSprite sprite, VertexFormat format,
            boolean merge, boolean rotated, int origin) {
        List<JoinedPaneMesher.Cell> cells = new ArrayList<>();
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
            if (u < 5 || u >= 11 || v < 4 || v >= 12) cells.add(new JoinedPaneMesher.Cell(u, v, true));
        List<JoinedPaneMesher.Rect> rectangles = JoinedPaneMesher.merge(cells, merge);
        require(rectangles.size() == (merge ? 4 : 208), "expected pane reduction");
        BufferBuilder records = new BufferBuilder(32768);
        records.begin(GL11.GL_QUADS, format);
        GlassPaneBuffers.emit(records, rectangles, rotated, 0, 0, sprite, true, 0x00f000f0);
        records.finishDrawing();
        VertexBuffer buffer = new VertexBuffer(new VertexFormat(format));
        VertexBuffer previous = new VertexBuffer(new VertexFormat(format));
        buffer.bufferData(GlassPaneBuffers.records(records));
        previous.bufferData(GlassPaneBuffers.records(records));
        Framebuffer framebuffer = new Framebuffer(SIZE, SIZE, true);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 16, 16, 0, -10, 10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        try (TurntableRenderState saved = new TurntableRenderState()) {
            GL20.glUseProgram(0);
            framebuffer.bindFramebuffer(true); GlStateManager.loadIdentity();
            GlStateManager.clearColor(0, 0, 0, 1); GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.disableLighting(); GlStateManager.disableCull(); GlStateManager.disableDepth();
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableAlpha(); GlStateManager.color(1, 1, 1, 1);
            GL11.glDisable(GL11.GL_FOG);
            mc.entityRenderer.disableLightmap();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            try {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                int anchor = origin + 15;
                GlStateManager.translate(GlassPaneBuffers.translation(anchor - (double) origin, origin, anchor), 0, 0);
                if (rotated) GlStateManager.rotate(90, 0, 1, 0);

                previous.bindBuffer();
                int previousBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL20.glVertexAttribPointer(8, 4, GL11.GL_FLOAT, false, format.getNextOffset(), 0L);
                GL20.glEnableVertexAttribArray(8);
                int previousAttributeBuffer = attribute(8, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
                GlassPaneBuffers.bindArrays(buffer, format, false);
                try {
                    // Shader pointer hooks change generic arrays inside this same scope.
                    GL20.glDisableVertexAttribArray(8);
                    GL20.glVertexAttribPointer(8, 2, GL11.GL_FLOAT, false, format.getNextOffset(), 16L);
                    buffer.drawArrays(GL11.GL_QUADS);
                } finally { GlassPaneBuffers.unbindArrays(); }
                require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == previousBinding, "previous VBO binding restored");
                require(GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE) == OpenGlHelper.lightmapTexUnit, "client texture unit restored");
                require(attribute(8, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0, "generic attribute enable restored");
                require(attribute(8, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING) == previousAttributeBuffer, "generic attribute buffer restored");
                require(attribute(8, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE) == 4, "generic attribute pointer restored");
                return net.minecraft.util.ScreenShotHelper.createScreenshot(SIZE, SIZE, framebuffer);
            } finally {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            }
        } finally {
            GL20.glUseProgram(program);
            GL11.glPopClientAttrib();
            buffer.deleteGlBuffers(); previous.deleteGlBuffers(); framebuffer.deleteFramebuffer();
            GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    private static int attribute(int index, int name) {
        ATTRIBUTE.clear(); GL20.glGetVertexAttrib(index, name, ATTRIBUTE); return ATTRIBUTE.get(0);
    }
    private static boolean same(BufferedImage a, BufferedImage b) {
        return Arrays.equals(a.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE), b.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Static glass GPU: " + message);
    }
    private GlassPaneRuntimeChecks() {}
}
