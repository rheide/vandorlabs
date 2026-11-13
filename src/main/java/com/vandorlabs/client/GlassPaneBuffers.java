package com.vandorlabs.client;

import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.render.JoinedPaneMesher;
import com.vandorlabs.tiles.GlassMaterials;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
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
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Static native pane geometry, retaining the complete ordinary or shader record. */
final class GlassPaneBuffers {
    private static final long MAX_RETAINED_BYTES = 32L * 1024 * 1024;
    private static final int MAX_GROUP_BYTES = 256 * 8 * 56;
    private static final BufferBuilder STAGING = new BufferBuilder(32768);
    private static String unavailable = "not checked";
    private static boolean warned;
    private static final ShaderHooks SHADERS = ShaderHooks.find();
    private static long retainedBytes;
    static long uploads, mainDraws, shadowDraws, submittedVertices;

    private static final class Mesh {
        final VertexBuffer buffer;
        final VertexFormat format;
        final Object shaderIdentity;
        final int bytes, vertices;
        Mesh(VertexBuffer buffer, VertexFormat format, Object shaderIdentity, int bytes, int vertices) {
            this.buffer = buffer;
            this.format = new VertexFormat(format);
            this.shaderIdentity = shaderIdentity;
            this.bytes = bytes;
            this.vertices = vertices;
        }
    }

    static VertexFormat formatSnapshot() { return new VertexFormat(BlockSurfaceFormat.get()); }
    static long retainedBytes() { return retainedBytes; }
    static String unavailableReason() { return unavailable; }
    static void resetFailures() { if (SHADERS != null) SHADERS.broken = false; warned = false; unavailable = "not checked"; }
    /** A same-name shader reload can replace block aliases without changing stride. */
    static Object shaderIdentity() {
        // A retired drawing hook must not masquerade as a pack change: that
        // would reset its failure and repeatedly republish unusable meshes.
        // Read identity independently so a real pack reload can recover it.
        return SHADERS == null ? null : SHADERS.identity();
    }
    static String shaderName() {
        Object identity = shaderIdentity();
        if (identity == null) return SHADERS != null && SHADERS.broken ? "unavailable" : "off";
        try { return String.valueOf(identity.getClass().getMethod("getName").invoke(identity)); }
        catch (ReflectiveOperationException | RuntimeException failure) { return identity.getClass().getSimpleName(); }
    }

    static boolean supported() {
        if (!OpenGlHelper.useVbo()) return unavailable("VBOs disabled");
        VertexFormat format = BlockSurfaceFormat.get();
        boolean shaders = SHADERS != null && SHADERS.active();
        if (SHADERS != null && SHADERS.broken) return unavailable("shader state hook failed");
        if (!ordinaryLayout(format)) return unavailable("unsupported pane vertex layout");
        if (format.getNextOffset() == 32 && !shaders) {
            unavailable = "none";
            return true;
        }
        if (format.getNextOffset() == 56 && shaders && VehicleShaderContext.available() && SHADERS.identity() != null) {
            unavailable = "none";
            return true;
        }
        return unavailable("unsupported shader format or material hooks (stride=" + format.getNextOffset() + ")");
    }

    /** Verify the standard prefix used by the optional 56-byte OptiFine pointer hook. */
    static boolean ordinaryLayout(VertexFormat format) {
        VertexFormatElement[] prefix = {DefaultVertexFormats.POSITION_3F, DefaultVertexFormats.COLOR_4UB,
                DefaultVertexFormats.TEX_2F, DefaultVertexFormats.TEX_2S,
                DefaultVertexFormats.NORMAL_3B, DefaultVertexFormats.PADDING_1B};
        if (format.getElementCount() < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (!prefix[i].equals(format.getElement(i))) return false;
        return format.getOffset(0) == 0 && format.getColorOffset() == 12
                && format.getUvOffsetById(0) == 16 && format.getUvOffsetById(1) == 24
                && format.getNormalOffset() == 28;
    }

    private static boolean unavailable(String reason) { unavailable = reason; return false; }

    /** Publication follows successful upload; a failed or unsupported build keeps every tile fallback. */
    static boolean prepare(GlassPaneChunks.Plan plan) {
        if (!supported()) return false;
        VertexFormat format = formatSnapshot();
        Object shaderIdentity = shaderIdentity();
        if (plan.gpu instanceof Mesh && ((Mesh) plan.gpu).format.equals(format)
                && ((Mesh) plan.gpu).shaderIdentity == shaderIdentity) return true;
        dispose(plan);
        int vertices = Math.multiplyExact(plan.rectangles.size(), plan.singleSurface ? 4 : 8);
        int bytes = Math.multiplyExact(vertices, format.getNextOffset());
        if (bytes <= 0 || bytes > MAX_GROUP_BYTES) return unavailable("pane group exceeds geometry budget");
        if (retainedBytes + bytes > MAX_RETAINED_BYTES) return unavailable("pane VBO byte budget exhausted");

        VertexBuffer uploaded = null;
        boolean drawing = false, shaderContext = false;
        int bound = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            STAGING.setTranslation(0, 0, 0);
            STAGING.begin(GL11.GL_QUADS, format);
            drawing = true;
            // endVertex invokes OptiFine's normal/tangent/mid-UV generation. The
            // context supplies the native block/entity identity before capture.
            shaderContext = VehicleShaderContext.push(plan.anchor, STAGING);
            if (format.getNextOffset() > 32 && !shaderContext)
                return unavailable("shader material capture unavailable");
            emit(STAGING, plan.rectangles, plan.rotated, plan.depth, plan.plane,
                    plan.sprite, plan.singleSurface, plan.light);
            if (shaderContext) { VehicleShaderContext.pop(STAGING); shaderContext = false; }
            STAGING.finishDrawing();
            drawing = false;
            if (STAGING.getVertexCount() != vertices) throw new IllegalStateException("Incomplete retained pane mesh");
            ByteBuffer data = records(STAGING);
            if (data.remaining() != bytes) throw new IllegalStateException("Unexpected retained pane record size");
            uploaded = new VertexBuffer(new VertexFormat(format));
            uploaded.bufferData(data);
            if (format.getNextOffset() > 32) {
                // Exercise optional pointer hooks before suppressing any owner.
                bindArrays(uploaded, format, true);
                unbindArrays();
            }
            plan.gpu = new Mesh(uploaded, format, shaderIdentity, bytes, vertices);
            uploaded = null;
            retainedBytes += bytes;
            uploads++;
            GlassPaneDiagnostics.uploaded(bytes);
            unavailable = "none";
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (format.getNextOffset() > 32 && SHADERS != null) SHADERS.broken = true;
            failed("pane buffer preparation", failure);
            return false;
        } finally {
            if (shaderContext) VehicleShaderContext.pop(STAGING);
            if (drawing) STAGING.finishDrawing();
            STAGING.setTranslation(0, 0, 0);
            if (uploaded != null) uploaded.deleteGlBuffers();
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, bound);
        }
    }

    /** A view of all record bytes: no addVertexData call may rewrite captured shader IDs. */
    static ByteBuffer records(BufferBuilder source) {
        ByteBuffer bytes = source.getByteBuffer().duplicate().order(source.getByteBuffer().order());
        bytes.position(0);
        bytes.limit(Math.multiplyExact(source.getVertexCount(), source.getVertexFormat().getNextOffset()));
        return bytes;
    }

    /** Package access keeps geometry checks independent of a world and OpenGL context. */
    static void emit(BufferBuilder out, List<JoinedPaneMesher.Rect> rectangles,
            boolean rotated, int depth, int plane, TextureAtlasSprite sprite,
            boolean single, int light) {
        double offset = PanelDepth.offset(depth) / 16D;
        for (JoinedPaneMesher.Rect rect : rectangles) {
            for (int face = 0; face < (single ? 1 : 2); face++) {
                boolean near = face == 0;
                double d = (single ? .5 : near ? 7D / 16 : 9D / 16) + offset;
                for (int i = 0; i < 4; i++) {
                    int index = near ? (4 - i) & 3 : i;
                    double u = index == 1 || index == 2 ? 1 : 0;
                    double v = index < 2 ? 0 : 1;
                    double horizontal = rect.u + u * rect.width, y = rect.v + v * rect.height;
                    // All stored positions remain section-local even at world limits.
                    double x = rotated ? plane + 1 - d : horizontal;
                    double z = rotated ? horizontal : plane + d;
                    // Smooth sprites are uniform, so stretching within their bounds
                    // is exact. Nondegenerate UVs also preserve finite shader tangents.
                    // Authored sprites are only assigned unit rectangles by the plan.
                    out.pos(x, y, z).color(1F, 1F, 1F, 1F)
                            .tex(sprite.getInterpolatedU(u * 16), sprite.getInterpolatedV((1 - v) * 16))
                            .lightmap(light >>> 16, light & 65535)
                            .normal(rotated ? (near ? 1 : -1) : 0, 0, rotated ? 0 : (near ? -1 : 1))
                            .endVertex();
                }
            }
        }
    }

    static double translation(double renderCoordinate, int sectionCoordinate, int anchorCoordinate) {
        return renderCoordinate + ((double) sectionCoordinate - anchorCoordinate);
    }

    static boolean draw(GlassPaneChunks.Plan plan, double x, double y, double z) {
        if (!(plan.gpu instanceof Mesh) || !((Mesh) plan.gpu).format.equals(BlockSurfaceFormat.get())
                || ((Mesh) plan.gpu).shaderIdentity != shaderIdentity()) {
            // Shader toggles can occur after the last render-tick notification.
            // Rebuild once when supported, otherwise release ownership safely.
            if (!prepare(plan)) { GlassPaneChunks.failed(plan); dispose(plan); return false; }
            plan.ready = true;
        }
        Mesh mesh = (Mesh) plan.gpu;
        boolean shaders = mesh.format.getNextOffset() > 32;
        boolean shadow = VehicleRenderPass.shadow();
        if (GlassPanePolicy.omitPane(GlassMaterials.opaque(-1, plan.tint), shadow)) return true;
        int timer = GlassPaneDiagnostics.begin(shadow, mesh.vertices);
        boolean submitted = false;
        // The glazing collector already owns an exact outer snapshot. Reusing
        // it avoids synchronous GL state queries for every stationary group.
        boolean sharedState = GlazingRenderOrder.ownsState();
        try (TurntableRenderState saved = sharedState ? null : new TurntableRenderState()) {
            // Select the same block-entity material as the old per-tile renderer.
            // preRenderChunkLayer would switch shader programs and must not run here.
            if (shaders) SHADERS.select(plan.anchor);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            boolean opaque = GlassMaterials.opaque(-1, plan.tint);
            if (opaque) GlStateManager.disableBlend(); else GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, .003F);
            GlStateManager.depthMask(opaque);
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.pushMatrix();
            boolean arrays = false;
            try {
                GlStateManager.translate(translation(x, plan.origin.getX(), plan.anchor.getPos().getX()),
                        translation(y, plan.origin.getY(), plan.anchor.getPos().getY()),
                        translation(z, plan.origin.getZ(), plan.anchor.getPos().getZ()));
                bindArrays(mesh.buffer, mesh.format, shaders);
                arrays = true;
                submitted = true;
                mesh.buffer.drawArrays(GL11.GL_QUADS);
                if (shadow) shadowDraws++; else mainDraws++;
                submittedVertices += mesh.vertices;
            } finally {
                if (arrays) unbindArrays();
                GlStateManager.popMatrix();
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            // A failure after submission cannot be retried without double blending.
            if (submitted) throw failure;
            if (shaders && SHADERS != null) SHADERS.broken = true;
            failed("pane draw setup", failure);
            GlassPaneChunks.failed(plan);
            dispose(plan);
            return false;
        } finally {
            if (sharedState) {
                // Match GlassPaneBatch's hand-off state for the next legacy
                // glazing draw; the collector restores its caller exactly.
                GlStateManager.enableCull();
                GlStateManager.depthMask(true);
                GlStateManager.alphaFunc(GL11.GL_GREATER, .1F);
                GlStateManager.disableBlend();
                GlStateManager.enableLighting();
                GlStateManager.color(1, 1, 1, 1);
            }
            GlassPaneDiagnostics.end(timer);
        }
    }

    /** Shared production/test pointer path. The client stack also owns generic
     * attribute enables/pointers, array-buffer bindings and active client texture. */
    static void bindArrays(VertexBuffer buffer, VertexFormat format, boolean shaders) {
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            buffer.bindBuffer();
            pointers(format, true);
            if (shaders) SHADERS.pointers();
        } catch (RuntimeException | LinkageError failure) {
            GL11.glPopClientAttrib();
            throw failure;
        }
    }

    static void unbindArrays() { GL11.glPopClientAttrib(); }

    private static void pointers(VertexFormat format, boolean enable) {
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            int type = element.getType().getGlConstant(), count = element.getElementCount();
            int stride = format.getNextOffset(), offset = format.getOffset(i), state;
            switch (element.getUsage()) {
                case POSITION:
                    state = GL11.GL_VERTEX_ARRAY;
                    if (enable) GlStateManager.glVertexPointer(count, type, stride, offset);
                    break;
                case COLOR:
                    state = GL11.GL_COLOR_ARRAY;
                    if (enable) GlStateManager.glColorPointer(count, type, stride, offset);
                    break;
                case NORMAL:
                    state = GL11.GL_NORMAL_ARRAY;
                    if (enable) GL11.glNormalPointer(type, stride, (long) offset);
                    break;
                case UV:
                    if (element.getIndex() > 1) continue;
                    OpenGlHelper.setClientActiveTexture(element.getIndex() == 0
                            ? OpenGlHelper.defaultTexUnit : OpenGlHelper.lightmapTexUnit);
                    state = GL11.GL_TEXTURE_COORD_ARRAY;
                    if (enable) GlStateManager.glTexCoordPointer(count, type, stride, offset);
                    break;
                default: continue;
            }
            if (enable) GlStateManager.glEnableClientState(state); else GlStateManager.glDisableClientState(state);
        }
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    static void dispose(GlassPaneChunks.Plan plan) {
        plan.ready = false;
        if (!(plan.gpu instanceof Mesh)) { plan.gpu = null; return; }
        Mesh mesh = (Mesh) plan.gpu;
        plan.gpu = null;
        retainedBytes -= mesh.bytes;
        mesh.buffer.deleteGlBuffers();
    }

    private static void failed(String operation, Throwable failure) {
        unavailable = operation + ": " + failure.getClass().getSimpleName();
        if (!warned && com.vandorlabs.VandorLabs.logger != null) {
            warned = true;
            com.vandorlabs.VandorLabs.logger.warn("Stationary glass uses its tile fallback: " + operation, failure);
        }
    }

    private static final class ShaderHooks {
        final Method active, nextTile, arrayPointers;
        final Field shaderPack;
        final int mid, tangent, entity;
        boolean broken;
        ShaderHooks() throws ReflectiveOperationException {
            active = Class.forName("Config").getMethod("isShaders");
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
            shaderPack = shaders.getDeclaredField("shaderPack");
            shaderPack.setAccessible(true);
            nextTile = shaders.getMethod("nextBlockEntity", TileEntity.class);
            arrayPointers = Class.forName("net.optifine.shaders.ShadersRender").getMethod("setupArrayPointersVbo");
            mid = shaders.getField("midTexCoordAttrib").getInt(null);
            tangent = shaders.getField("tangentAttrib").getInt(null);
            entity = shaders.getField("entityAttrib").getInt(null);
            if (mid < 0 || tangent < 0 || entity < 0 || mid == tangent || mid == entity || tangent == entity)
                throw new IllegalStateException("Invalid shader pane attribute locations");
        }
        static ShaderHooks find() {
            try { return new ShaderHooks(); }
            catch (ClassNotFoundException absent) { return null; }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                failed("shader attribute hooks", failure);
                return null;
            }
        }
        boolean active() {
            if (broken) return false;
            try { return (Boolean) active.invoke(null); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                broken = true;
                failed("shader status", failure);
                return false;
            }
        }
        Object identity() {
            try { return shaderPack.get(null); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                broken = true;
                failed("shader pack identity", failure);
                return null;
            }
        }
        void select(TileEntity tile) {
            try { nextTile.invoke(null, tile); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot select glass shader material", failure); }
        }
        void pointers() {
            try { arrayPointers.invoke(null); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot bind glass shader attributes", failure); }
            GL20.glEnableVertexAttribArray(mid);
            GL20.glEnableVertexAttribArray(tangent);
            GL20.glEnableVertexAttribArray(entity);
        }
    }

    private GlassPaneBuffers() {}
}
