package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.BlockProgrammableConsole;
import com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
import com.vandorlabs.blocks.BlockProgrammableHalfConsole;
import com.vandorlabs.blocks.BlockProgrammableInput;
import com.vandorlabs.blocks.BlockProgrammableFullInput;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Paints programmable blocks with their configured screen and supplies the
 * arbitrary triangular geometry used by the console and diagonal wedge.
 * <p>
 * Animated screens ship as lossless base-relative {@code .anim} resources.
 * The first frame is the existing static PNG; later frames store compressed
 * arithmetic RGB deltas and are reconstructed into a vertical dynamic texture
 * once on first use. Animation and speed control are then ordinary UV changes
 * over world time. The static view is {@code <id>_static.png}
 * and the dark view is the shared off texture matching the active variant
 * (framed ids use the border). Raw PNGs are bound directly (never the
 * atlas), so no model or texture registration is needed per screen: any
 * {@code display} block in the catalog works, including ones added later
 * with no Java changes.
 * <p>
 * Lit faces render fullbright (a screen glows); the dark face uses world
 * light so it sits naturally in shadow.
 */
@SideOnly(Side.CLIENT)
public class TEAnimatedScreenSelector
        extends TileEntitySpecialRenderer<TileEntityAnimatedScreenSelector> {

    private static final String TEX_ROOT = "textures/blocks/";
    private static final String OFF_PLAIN = "screen_off";
    private static final String OFF_FRAMED = "sequence_border_off";

    private static final int ANIM_MAGIC = 0x564C5441; // "VLTA"
    private static final int ANIM_VERSION = 2;

    /** Reconstructed animation texture, loaded once on first use. */
    private static class AnimData {
        final ResourceLocation texture;
        final int frameCount;

        AnimData(ResourceLocation texture, int frameCount) {
            this.texture = texture;
            this.frameCount = frameCount;
        }
    }

    private static final Map<String, AnimData> ANIM_CACHE = new HashMap<>();
    private static final Map<String, AnimData> INPUT_ANIM_CACHE = new HashMap<>();

    /** Uploads reconstructed pixels and releases them immediately afterward.
     * Resource reloads decode them again, just like a normal SimpleTexture. */
    private static class CompactAnimationTexture extends AbstractTexture {
        private final String pathRoot;
        private int frameCount;

        CompactAnimationTexture(String pathRoot) {
            this.pathRoot = pathRoot;
        }

        @Override public void loadTexture(IResourceManager manager) throws IOException {
            DecodedAnimation decoded = decodeCompactAnimation(manager, pathRoot);
            frameCount = decoded.frameCount;
            deleteGlTexture();
            TextureUtil.uploadTextureImageAllocate(getGlTextureId(), decoded.image,
                    false, false);
        }
    }

    private static class DecodedAnimation {
        final BufferedImage image;
        final int frameCount;

        DecodedAnimation(BufferedImage image, int frameCount) {
            this.image = image;
            this.frameCount = frameCount;
        }
    }

    private static synchronized AnimData animData(String screenId) {
        AnimData data = ANIM_CACHE.get(screenId);
        if (data == null) {
            data = loadAnimData(screenId);
            ANIM_CACHE.put(screenId, data);
        }
        return data;
    }

    private static AnimData loadAnimData(String screenId) {
        return loadCompactAnimation(screenId, TEX_ROOT + screenId);
    }

    private static synchronized AnimData inputAnimData(String id) {
        AnimData data = INPUT_ANIM_CACHE.get(id);
        if (data == null) {
            data = loadCompactAnimation("input_" + id,
                    TEX_ROOT + "console_inputs/" + id);
            INPUT_ANIM_CACHE.put(id, data);
        }
        return data;
    }

    private static AnimData loadCompactAnimation(String cacheName, String pathRoot) {
        try {
            ResourceLocation texture = new ResourceLocation(VandorLabs.MODID,
                    "compact_animation/" + cacheName);
            CompactAnimationTexture compact = new CompactAnimationTexture(pathRoot);
            if (!Minecraft.getMinecraft().getTextureManager().loadTexture(texture, compact)) {
                throw new IOException("texture manager rejected compact animation");
            }
            return new AnimData(texture, compact.frameCount);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Vandor Labs animation " + cacheName, e);
        }
    }

    private static DecodedAnimation decodeCompactAnimation(IResourceManager manager,
            String pathRoot) throws IOException {
        ResourceLocation packed = new ResourceLocation(VandorLabs.MODID,
                pathRoot + "_animated.anim");
        ResourceLocation baseLocation = new ResourceLocation(VandorLabs.MODID,
                pathRoot + "_static.png");
        BufferedImage base = TextureUtil.readBufferedImage(
                manager.getResource(baseLocation).getInputStream());
        InputStream resource = manager.getResource(packed).getInputStream();
        try (DataInputStream in = new DataInputStream(resource)) {
                if (in.readInt() != ANIM_MAGIC || in.readUnsignedByte() != ANIM_VERSION) {
                    throw new IllegalArgumentException("unsupported compact animation header");
                }
                int width = in.readUnsignedShort();
                int height = in.readUnsignedShort();
                int frames = in.readUnsignedShort();
                int expected = in.readInt();
                if (width != base.getWidth() || height != base.getHeight()
                        || frames < 2 || expected != (frames - 1) * width * height * 3) {
                    throw new IllegalArgumentException("animation dimensions do not match base");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(expected);
                try (InflaterInputStream inflated = new InflaterInputStream(in)) {
                    byte[] chunk = new byte[16384];
                    int count;
                    while ((count = inflated.read(chunk)) >= 0) bytes.write(chunk, 0, count);
                }
                byte[] delta = bytes.toByteArray();
                if (delta.length != expected) {
                    throw new IllegalArgumentException("truncated animation payload");
                }
                BufferedImage strip = new BufferedImage(width, height * frames,
                        BufferedImage.TYPE_INT_ARGB);
                int[] basePixels = base.getRGB(0, 0, width, height, null, 0, width);
                strip.setRGB(0, 0, width, height, basePixels, 0, width);
                int cursor = 0;
                int[] previous = basePixels;
                int[] pixels = new int[width * height];
                for (int frame = 1; frame < frames; frame++) {
                    for (int pixel = 0; pixel < pixels.length; pixel++) {
                        int original = previous[pixel];
                        int red = (((original >> 16) & 255) + (delta[cursor++] & 255)) & 255;
                        int green = (((original >> 8) & 255) + (delta[cursor++] & 255)) & 255;
                        int blue = ((original & 255) + (delta[cursor++] & 255)) & 255;
                        pixels[pixel] = 0xFF000000 | red << 16 | green << 8 | blue;
                    }
                    strip.setRGB(0, frame * height, width, height, pixels, 0, width);
                    int[] reusable = previous;
                    previous = pixels;
                    pixels = reusable;
                }
                return new DecodedAnimation(strip, frames);
        }
    }

    @Override
    public void render(TileEntityAnimatedScreenSelector te, double x, double y, double z,
            float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockAnimatedScreenSelector)) {
            return;
        }
        if (state.getBlock() instanceof BlockProgrammableFullInput) {
            EnumFacing facing = state.getValue(BlockProgrammableInput.FACING);
            beginLocalTransform(x, y, z, facing);
            GlStateManager.disableLighting();
            renderFullInput(te, state.getValue(BlockProgrammableInput.KEYBOARD),
                    state.getValue(BlockProgrammableInput.UPPER));
            GlStateManager.enableLighting();
            endLocalTransform();
            return;
        }
        if (state.getBlock() instanceof BlockProgrammableInput) {
            EnumFacing facing = state.getValue(BlockProgrammableInput.FACING);
            beginLocalTransform(x, y, z, facing);
            GlStateManager.disableLighting();
            renderInputHousing(te, state.getValue(BlockProgrammableInput.KEYBOARD),
                    state.getValue(BlockProgrammableInput.UPPER),
                    te.getWallPosition(state.getValue(BlockProgrammableInput.UPPER) ? 2 : 0),
                    te.isSmallInput());
            GlStateManager.enableLighting();
            endLocalTransform();
            return;
        }
        if (state.getBlock() instanceof BlockProgrammableHalfConsole) {
            EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
            beginLocalTransform(x, y, z, facing);
            GlStateManager.disableLighting();
            renderHalfConsole(te);
            GlStateManager.enableLighting();
            endLocalTransform();
            return;
        }
        boolean diagonal = state.getBlock() instanceof BlockProgrammableDiagonalScreen;
        EnumFacing facing = diagonal
                ? state.getValue(BlockProgrammableDiagonalScreen.FACING)
                : state.getValue(BlockAnimatedScreenSelector.FACING);
        boolean diagonalInverted = diagonal
                && state.getValue(BlockProgrammableDiagonalScreen.INVERTED);
        int mode = te.getEffectiveMode();
        boolean off = mode == TileEntityAnimatedScreenSelector.MODE_OFF;

        String screenId = te.getSelectedScreen();
        if (!ModBlocks.DISPLAY_SCREEN_IDS.contains(screenId)) {
            screenId = "engineering_screen";
        }

        ResourceLocation texture;
        float vTop;
        float vBottom;
        if (off) {
            // Off-texture follows the ACTIVE variant id, so it stays correct
            // even if the tile's framed flag ever skews from its screen id.
            boolean framed = ModBlocks.DISPLAY_FRAMED_IDS.contains(screenId);
            texture = new ResourceLocation(VandorLabs.MODID,
                    TEX_ROOT + (framed ? OFF_FRAMED : OFF_PLAIN) + ".png");
            vTop = 0.0F;
            vBottom = 1.0F;
        } else if (mode == TileEntityAnimatedScreenSelector.MODE_ANIMATED) {
            AnimData data = animData(screenId);
            texture = data.texture;
            int ticks = te.getAnimationSpeedTicks();
            long tick = te.getWorld().getTotalWorldTime();
            // Global phase is intentional: adjacent L/R halves of a 2x1
            // display must always select the same authored animation frame.
            int frame = (int) ((tick / Math.max(1, ticks)) % data.frameCount);
            // TextureUtil uploads the PNG's first scanline at V=0. The strip
            // stores frame zero at the top, so frame UVs advance downward.
            // The old 1-frame/n calculation sampled the reserved black tail.
            vTop = (float) frame / data.frameCount;
            vBottom = (float) (frame + 1) / data.frameCount;
        } else {
            texture = new ResourceLocation(VandorLabs.MODID,
                    TEX_ROOT + screenId + "_static.png");
            vTop = 0.0F;
            vBottom = 1.0F;
        }

        float lightU;
        float lightV;
        if (off) {
            int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
            lightU = (float) (combined % 65536);
            lightV = (float) (combined / 65536);
        } else {
            // Screens glow: fullbright when lit.
            lightU = 240.0F;
            lightV = 240.0F;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        // Centered on all axes: X-rotations for floor/ceiling mounts swing
        // around the block middle, otherwise the quad lands mid-block.
        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        if (facing == EnumFacing.UP) {
            // Local -Z (face normal) -> +Y, local +Y (texture top) -> north,
            // unmirrored viewed from above with north up.
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
        } else if (facing == EnumFacing.DOWN) {
            // Local -Z -> -Y, texture top -> north.
            GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
        } else {
            GlStateManager.rotate(180.0F - facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        }
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        GlStateManager.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GlStateManager.disableLighting();
        if (state.getBlock() instanceof BlockProgrammableConsole) {
            renderConsoleHousing(te);
        } else if (state.getBlock() instanceof BlockProgrammableDiagonalScreen) {
            renderDiagonalHousing(te, diagonalInverted);
        }
        bindTexture(texture);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        // Full-face quad just outside the local north face. U runs 1->0
        // toward +X so paint reads exactly like a vanilla north face
        // (U=0 sits east), matching BlockDisplaySequenced art. Offset ~6mm
        // out: kills z-fighting flicker against the dark housing front
        // behind it without a visible gap.
        if (state.getBlock() instanceof BlockProgrammableConsole) {
            // Inset into the full-height wedge: its lower edge starts behind
            // the controls and its upper edge reaches the block's back/top.
            // A small outward normal offset prevents z-fighting with the wall
            // backing while leaving a half-pixel wall-panel border.
            double x0 = 0.5D;
            double x1 = 15.5D;
            double bottomY = 1.46D;
            double bottomZ = 7.70D;
            double topY = 15.59D;
            double topZ = 15.71D;
            buf.pos(x0, topY, topZ).tex(1.0D, vTop).endVertex();
            buf.pos(x1, topY, topZ).tex(0.0D, vTop).endVertex();
            buf.pos(x1, bottomY, bottomZ).tex(0.0D, vBottom).endVertex();
            buf.pos(x0, bottomY, bottomZ).tex(1.0D, vBottom).endVertex();
        } else if (state.getBlock() instanceof BlockProgrammableDiagonalScreen) {
            // The full diagonal is sqrt(2)*16px long. A centered 15px run
            // matches the 15px screen width instead of stretching square art
            // by ~41%, leaving a 2.7px wall-panel border at each sloped end.
            // A tiny outward-normal offset prevents z-fighting.
            double x0 = 0.5D;
            double x1 = 15.5D;
            // Bias the inset toward the visually important end: high/back
            // for a floor placement, low/back for a ceiling placement. The
            // diagonal span stays constant, so neither variant stretches.
            double bottomY = diagonalInverted ? 1.15D : 4.25D;
            double bottomZ = diagonalInverted ? 14.75D : 4.15D;
            double topY = diagonalInverted ? 11.75D : 14.85D;
            double topZ = diagonalInverted ? 4.15D : 14.75D;
            buf.pos(x0, topY, topZ).tex(1.0D, vTop).endVertex();
            buf.pos(x1, topY, topZ).tex(0.0D, vTop).endVertex();
            buf.pos(x1, bottomY, bottomZ).tex(0.0D, vBottom).endVertex();
            buf.pos(x0, bottomY, bottomZ).tex(1.0D, vBottom).endVertex();
        } else {
            float faceZ = -0.1F;
            buf.pos(0.0D, 16.0D, faceZ).tex(1.0D, vTop).endVertex();
            buf.pos(16.0D, 16.0D, faceZ).tex(0.0D, vTop).endVertex();
            buf.pos(16.0D, 0.0D, faceZ).tex(0.0D, vBottom).endVertex();
            buf.pos(0.0D, 0.0D, faceZ).tex(1.0D, vBottom).endVertex();
        }
        tess.draw();
        GlStateManager.enableLighting();

        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.popMatrix();
    }

    private static void beginLocalTransform(double x, double y, double z,
            EnumFacing facing) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
        GlStateManager.rotate(180.0F - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        GlStateManager.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private static void endLocalTransform() {
        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.popMatrix();
    }

    /** Thin wall-mounted panel or folded-out half-depth keyboard shelf. */
    private static void renderInputHousing(TileEntityAnimatedScreenSelector te,
            boolean keyboard, boolean upper, int wallPosition, boolean small) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        setWorldLight(te);
        TextureAtlasSprite wall = mc.getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/wall_panel_dark");
        double scale = small ? BlockProgrammableInput.SMALL_SCALE : 1.0D;
        double x0 = (16.0D - 16.0D * scale) / 2.0D;
        double x1 = 16.0D - x0;
        if (keyboard) {
            double y0 = upper ? 15 : 7;
            double z0 = 16.0D - 8.0D * scale;
            renderWallBox(wall, x0, y0, z0, x1, y0 + 1, 16);
            double[] uv = bindInput(te, te.getInputPanel());
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            inputQuadHorizontal(buf, x0 + 0.25D, x1 - 0.25D,
                    z0 + 0.25D, 15.75D, y0 + 1.02D, uv[0], uv[1]);
            tess.draw();
        } else {
            double height = 8.0D * scale;
            double y0 = wallPosition == 0 ? 0.0D
                    : wallPosition == 2 ? 16.0D - height : (16.0D - height) / 2.0D;
            renderWallBox(wall, x0, y0, 15, x1, y0 + height, 16);
            double[] uv = bindInput(te, te.getInputPanel());
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buf.pos(x0 + 0.25, y0 + height - 0.25, 14.98).tex(0, uv[0]).endVertex();
            buf.pos(x1 - 0.25, y0 + height - 0.25, 14.98).tex(1, uv[0]).endVertex();
            buf.pos(x1 - 0.25, y0 + 0.25, 14.98).tex(1, uv[1]).endVertex();
            buf.pos(x0 + 0.25, y0 + 0.25, 14.98).tex(0, uv[1]).endVertex();
            tess.draw();
        }
    }

    /** Full-square screen surface using the half-input's mount/fold state. */
    private static void renderFullInput(TileEntityAnimatedScreenSelector te,
            boolean keyboard, boolean upper) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        setWorldLight(te);
        TextureAtlasSprite wall = mc.getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/wall_panel_dark");
        double[] uv = bindScreenSurface(te);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        if (keyboard) {
            double y0 = upper ? 15 : 7;
            bindAtlas();
            renderWallBox(wall, 0, y0, 0, 16, y0 + 1, 16);
            bindScreenSurface(te);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buf.pos(0.25, y0 + 1.02, 15.75).tex(0, uv[0]).endVertex();
            buf.pos(15.75, y0 + 1.02, 15.75).tex(1, uv[0]).endVertex();
            buf.pos(15.75, y0 + 1.02, 0.25).tex(1, uv[1]).endVertex();
            buf.pos(0.25, y0 + 1.02, 0.25).tex(0, uv[1]).endVertex();
        } else {
            bindAtlas();
            renderWallBox(wall, 0, 0, 15, 16, 16, 16);
            bindScreenSurface(te);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buf.pos(0.25, 15.75, 14.98).tex(0, uv[0]).endVertex();
            buf.pos(15.75, 15.75, 14.98).tex(1, uv[0]).endVertex();
            buf.pos(15.75, 0.25, 14.98).tex(1, uv[1]).endVertex();
            buf.pos(0.25, 0.25, 14.98).tex(0, uv[1]).endVertex();
        }
        tess.draw();
    }

    /** Bind a regular full-height programmable animation and return its V range. */
    private static double[] bindScreenSurface(TileEntityAnimatedScreenSelector te) {
        String id = te.getSelectedScreen();
        if (!ModBlocks.DISPLAY_SCREEN_IDS.contains(id)) id = "engineering_screen";
        int mode = te.getEffectiveMode();
        ResourceLocation texture;
        double vTop = 0;
        double vBottom = 1;
        if (mode == TileEntityAnimatedScreenSelector.MODE_OFF) {
            texture = new ResourceLocation(VandorLabs.MODID, TEX_ROOT
                    + (ModBlocks.DISPLAY_FRAMED_IDS.contains(id)
                            ? OFF_FRAMED : OFF_PLAIN) + ".png");
            setWorldLight(te);
        } else if (mode == TileEntityAnimatedScreenSelector.MODE_ANIMATED) {
            AnimData data = animData(id);
            int frame = (int) ((te.getWorld().getTotalWorldTime()
                    / Math.max(1, te.getAnimationSpeedTicks())) % data.frameCount);
            vTop = (double) frame / data.frameCount;
            vBottom = (double) (frame + 1) / data.frameCount;
            texture = data.texture;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        } else {
            texture = new ResourceLocation(VandorLabs.MODID, TEX_ROOT + id + "_static.png");
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        return new double[] {vTop, vBottom};
    }

    /** Existing console layout compressed to half height, with input art on
     * both independently selectable surfaces. */
    private static void renderHalfConsole(TileEntityAnimatedScreenSelector te) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        setWorldLight(te);
        TextureAtlasSprite wall = mc.getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/wall_panel_dark");
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        spriteQuad(buf, wall, 0, 8, 16, 16, 8, 16, 16, 1, 16, 0, 1, 16,
                0, 16, 16, 0);
        tess.draw();
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        spriteTriangle(buf, wall, 0, 1, 7.5, 0, 8, 16, 0, 1, 16);
        spriteTriangle(buf, wall, 16, 1, 16, 16, 8, 16, 16, 1, 7.5);
        tess.draw();

        double[] frontUv = bindInput(te, te.getInputPanel());
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        inputQuadHorizontal(buf, 1.02, true, frontUv[0], frontUv[1]);
        tess.draw();

        double[] rearUv = bindInput(te, te.getSecondaryInputPanel());
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(0.25, 7.75, 15.70).tex(0, rearUv[0]).endVertex();
        buf.pos(15.75, 7.75, 15.70).tex(1, rearUv[0]).endVertex();
        buf.pos(15.75, 1.25, 7.70).tex(1, rearUv[1]).endVertex();
        buf.pos(0.25, 1.25, 7.70).tex(0, rearUv[1]).endVertex();
        tess.draw();
    }

    private static void inputQuadHorizontal(BufferBuilder buf, double y, boolean frontHalf,
            double vTop, double vBottom) {
        double z0 = frontHalf ? 0.25 : 8.25;
        double z1 = frontHalf ? 7.25 : 15.75;
        inputQuadHorizontal(buf, 0.25, 15.75, z0, z1, y, vTop, vBottom);
    }

    private static void inputQuadHorizontal(BufferBuilder buf, double x0, double x1,
            double z0, double z1, double y, double vTop, double vBottom) {
        buf.pos(x0, y, z1).tex(0, vTop).endVertex();
        buf.pos(x1, y, z1).tex(1, vTop).endVertex();
        buf.pos(x1, y, z0).tex(1, vBottom).endVertex();
        buf.pos(x0, y, z0).tex(0, vBottom).endVertex();
    }

    private static double[] bindInput(TileEntityAnimatedScreenSelector te, String id) {
        int mode = te.getEffectiveMode();
        int frames = TileEntityAnimatedScreenSelector.getInputFrameCount(id);
        String suffix;
        double vTop = 0;
        double vBottom = 1;
        if (mode == TileEntityAnimatedScreenSelector.MODE_OFF) {
            suffix = "_off";
        } else if (mode == TileEntityAnimatedScreenSelector.MODE_ANIMATED && frames > 1) {
            AnimData data = inputAnimData(id);
            int frame = (int) ((te.getWorld().getTotalWorldTime()
                    / Math.max(1, te.getAnimationSpeedTicks())) % data.frameCount);
            vTop = (double) frame / data.frameCount;
            vBottom = (double) (frame + 1) / data.frameCount;
            Minecraft.getMinecraft().getTextureManager().bindTexture(data.texture);
            return new double[] {vTop, vBottom};
        } else {
            suffix = "_static";
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(
                VandorLabs.MODID, "textures/blocks/console_inputs/" + id + suffix + ".png"));
        return new double[] {vTop, vBottom};
    }

    private static void setWorldLight(TileEntityAnimatedScreenSelector te) {
        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                combined % 65536, combined / 65536);
    }

    private static void renderWallBox(TextureAtlasSprite wall, double x0, double y0,
            double z0, double x1, double y1, double z1) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        spriteQuad(b, wall, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, 0,0,16,16);
        spriteQuad(b, wall, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, 0,0,16,16);
        spriteQuad(b, wall, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0, 0,0,16,16);
        spriteQuad(b, wall, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1, 0,0,16,16);
        spriteQuad(b, wall, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0, 0,0,16,16);
        spriteQuad(b, wall, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1, 0,0,16,16);
        tess.draw();
    }

    /** Draw the console's non-cuboid wall wedge and selectable input deck. */
    private static void renderConsoleHousing(TileEntityAnimatedScreenSelector te) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (combined % 65536), (float) (combined / 65536));

        TextureAtlasSprite wall = mc.getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/wall_panel_dark");
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // Full-width triangular prism behind the screen: inclined front,
        // filled west/east triangles and a vertical back face.
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        spriteQuad(buf, wall,
                0, 1, 7.5, 16, 1, 7.5, 16, 16, 16, 0, 16, 16,
                0, 16, 16, 0);
        spriteQuad(buf, wall,
                0, 16, 16, 16, 16, 16, 16, 1, 16, 0, 1, 16,
                0, 16, 16, 0);
        tess.draw();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        spriteTriangle(buf, wall,
                0, 1, 7.5, 0, 16, 16, 0, 1, 16);
        spriteTriangle(buf, wall,
                16, 1, 16, 16, 16, 16, 16, 1, 7.5);
        tess.draw();

        // The supplied half-height controls are native 2:1 textures rather
        // than square atlas tiles. Bind them directly so the complete artwork
        // fills the deck without cropping or atlas-induced aspect changes.
        double[] inputUv = bindInput(te, te.getInputPanel());
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(0.25, 1.02, 7.25).tex(0.0D, inputUv[0]).endVertex();
        buf.pos(15.75, 1.02, 7.25).tex(1.0D, inputUv[0]).endVertex();
        buf.pos(15.75, 1.02, 0.25).tex(1.0D, inputUv[1]).endVertex();
        buf.pos(0.25, 1.02, 0.25).tex(0.0D, inputUv[1]).endVertex();
        tess.draw();
    }

    /** Full solid half-cube wedge used by the standalone diagonal display. */
    private static void renderDiagonalHousing(TileEntityAnimatedScreenSelector te,
            boolean inverted) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (combined % 65536), (float) (combined / 65536));
        TextureAtlasSprite wall = mc.getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/wall_panel_dark");
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        // Explicit upper/lower geometry keeps the artwork upright. Reflecting
        // the model matrix also reflected the texture, producing an upside-
        // down screen on the ceiling-style placement.
        if (inverted) {
            spriteQuad(buf, wall,
                    0, 16, 0, 16, 16, 0, 16, 0, 16, 0, 0, 16,
                    0, 16, 16, 0);
            spriteQuad(buf, wall,
                    0, 0, 16, 16, 0, 16, 16, 16, 16, 0, 16, 16,
                    0, 16, 16, 0);
            spriteQuad(buf, wall,
                    0, 16, 0, 16, 16, 0, 16, 16, 16, 0, 16, 16,
                    0, 0, 16, 16);
        } else {
            spriteQuad(buf, wall,
                    0, 0, 0, 16, 0, 0, 16, 16, 16, 0, 16, 16,
                    0, 16, 16, 0);
            spriteQuad(buf, wall,
                    0, 16, 16, 16, 16, 16, 16, 0, 16, 0, 0, 16,
                    0, 16, 16, 0);
            spriteQuad(buf, wall,
                    0, 0, 16, 16, 0, 16, 16, 0, 0, 0, 0, 0,
                    0, 0, 16, 16);
        }
        tess.draw();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        if (inverted) {
            spriteTriangle(buf, wall, 0, 16, 0, 0, 0, 16, 0, 16, 16);
            spriteTriangle(buf, wall, 16, 16, 16, 16, 0, 16, 16, 16, 0);
        } else {
            spriteTriangle(buf, wall, 0, 0, 0, 0, 16, 16, 0, 0, 16);
            spriteTriangle(buf, wall, 16, 0, 16, 16, 16, 16, 16, 0, 0);
        }
        tess.draw();
    }

    private static void bindAtlas() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private static void spriteQuad(BufferBuilder buf, TextureAtlasSprite sprite,
            double x0, double y0, double z0, double x1, double y1, double z1,
            double x2, double y2, double z2, double x3, double y3, double z3,
            double u0, double v0, double u1, double v1) {
        double ua = sprite.getInterpolatedU(u0);
        double va = sprite.getInterpolatedV(v0);
        double ub = sprite.getInterpolatedU(u1);
        double vb = sprite.getInterpolatedV(v1);
        buf.pos(x0, y0, z0).tex(ua, va).endVertex();
        buf.pos(x1, y1, z1).tex(ub, va).endVertex();
        buf.pos(x2, y2, z2).tex(ub, vb).endVertex();
        buf.pos(x3, y3, z3).tex(ua, vb).endVertex();
    }

    private static void spriteTriangle(BufferBuilder buf, TextureAtlasSprite sprite,
            double x0, double y0, double z0, double x1, double y1, double z1,
            double x2, double y2, double z2) {
        double u0 = sprite.getInterpolatedU(0);
        double u1 = sprite.getInterpolatedU(16);
        double v0 = sprite.getInterpolatedV(0);
        double v1 = sprite.getInterpolatedV(16);
        buf.pos(x0, y0, z0).tex(u0, v1).endVertex();
        buf.pos(x1, y1, z1).tex(u1, v0).endVertex();
        buf.pos(x2, y2, z2).tex(u1, v1).endVertex();
    }

    /** TextureManager caches per ResourceLocation; drop entries on pack reload. */
    public static synchronized void clearCache() {
        ANIM_CACHE.clear();
    }
}
