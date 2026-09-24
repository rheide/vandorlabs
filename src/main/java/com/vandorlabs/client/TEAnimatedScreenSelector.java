package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.animation.CompactAnimationDecoder;
import com.vandorlabs.animation.AnimationFrames;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.render.InputSurfaceLayout;
import com.vandorlabs.render.ScreenHousingMesh;
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
import java.io.IOException;
import java.io.InputStream;
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
        CompactAnimationDecoder.Result decoded = CompactAnimationDecoder.decode(base, resource);
        return new DecodedAnimation(decoded.strip, decoded.frameCount);
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
            int frame = AnimationFrames.frame(tick,ticks,data.frameCount);
            // TextureUtil uploads the PNG's first scanline at V=0. The strip
            // stores frame zero at the top, so frame UVs advance downward.
            // The old 1-frame/n calculation sampled the reserved black tail.
            vTop = (float)AnimationFrames.top(frame,data.frameCount);
            vBottom = (float)AnimationFrames.bottom(frame,data.frameCount);
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
        // U runs 1->0 toward +X so paint reads like a vanilla north face.
        // Diagonal artwork lies on the housing plane; depth bias makes it win
        // the depth test without opening a visible slit along the side.
        ScreenSurface.Kind surface=state.getBlock() instanceof BlockProgrammableConsole
                ?ScreenSurface.Kind.CONSOLE:state.getBlock() instanceof BlockProgrammableDiagonalScreen
                ?ScreenSurface.Kind.DIAGONAL:ScreenSurface.Kind.FLAT;
        ScreenSurface.Quad quad=ScreenSurface.quad(surface,diagonalInverted);
        buf.pos(quad.topLeft.x,quad.topLeft.y,quad.topLeft.z).tex(1,vTop).endVertex();
        buf.pos(quad.topRight.x,quad.topRight.y,quad.topRight.z).tex(0,vTop).endVertex();
        buf.pos(quad.bottomRight.x,quad.bottomRight.y,quad.bottomRight.z).tex(0,vBottom).endVertex();
        buf.pos(quad.bottomLeft.x,quad.bottomLeft.y,quad.bottomLeft.z).tex(1,vBottom).endVertex();
        if (surface==ScreenSurface.Kind.DIAGONAL) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-4.0F,-4.0F);
        }
        tess.draw();
        if (surface==ScreenSurface.Kind.DIAGONAL) {
            GL11.glPolygonOffset(0.0F,0.0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
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
                .getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        InputSurfaceLayout.Mounted layout=InputSurfaceLayout.halfInput(keyboard,upper,
                wallPosition,small);
        renderWallBox(wall,layout.housing.x0,layout.housing.y0,layout.housing.z0,
                layout.housing.x1,layout.housing.y1,layout.housing.z1);
        drawInputSurface(layout.surface,bindInput(te,te.getInputPanel()));
    }

    /** Full-square screen surface using the half-input's mount/fold state. */
    private static void renderFullInput(TileEntityAnimatedScreenSelector te,
            boolean keyboard, boolean upper) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        setWorldLight(te);
        TextureAtlasSprite wall = mc.getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        double[] uv=bindScreenSurface(te);
        InputSurfaceLayout.Mounted layout=InputSurfaceLayout.fullInput(keyboard,upper);
        bindAtlas();
        renderWallBox(wall,layout.housing.x0,layout.housing.y0,layout.housing.z0,
                layout.housing.x1,layout.housing.y1,layout.housing.z1);
        bindScreenSurface(te);
        drawInputSurface(layout.surface,uv);
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
            int frame=AnimationFrames.frame(te.getWorld().getTotalWorldTime(),
                    te.getAnimationSpeedTicks(),data.frameCount);
            vTop=AnimationFrames.top(frame,data.frameCount);
            vBottom=AnimationFrames.bottom(frame,data.frameCount);
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
                .getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        drawWallMesh(wall,ScreenHousingMesh.halfConsole());

        double[] frontUv = bindInput(te, te.getInputPanel());
        drawInputSurface(InputSurfaceLayout.halfConsoleFront(),frontUv);

        double[] rearUv = bindInput(te, te.getSecondaryInputPanel());
        drawInputSurface(InputSurfaceLayout.halfConsoleRear(),rearUv);
    }

    private static void drawInputSurface(InputSurfaceLayout.Quad quad,double[] uv) {
        Tessellator tess=Tessellator.getInstance();
        BufferBuilder buf=tess.getBuffer();
        buf.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
        for (InputSurfaceLayout.Vertex vertex:quad.vertices) {
            double v=uv[0]+vertex.v*(uv[1]-uv[0]);
            buf.pos(vertex.x,vertex.y,vertex.z).tex(vertex.u,v).endVertex();
        }
        tess.draw();
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
            int frame=AnimationFrames.frame(te.getWorld().getTotalWorldTime(),
                    te.getAnimationSpeedTicks(),data.frameCount);
            vTop=AnimationFrames.top(frame,data.frameCount);
            vBottom=AnimationFrames.bottom(frame,data.frameCount);
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
                .getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        drawWallMesh(wall,ScreenHousingMesh.console());

        // The supplied half-height controls are native 2:1 textures rather
        // than square atlas tiles. Bind them directly so the complete artwork
        // fills the deck without cropping or atlas-induced aspect changes.
        double[] inputUv = bindInput(te, te.getInputPanel());
        drawInputSurface(InputSurfaceLayout.halfConsoleFront(),inputUv);
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
                .getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        // Explicit upper/lower geometry keeps the artwork upright. Reflecting
        // the model matrix would also reflect the texture.
        drawWallMesh(wall,ScreenHousingMesh.diagonal(inverted));
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

    private static void drawWallMesh(TextureAtlasSprite sprite,ScreenHousingMesh mesh) {
        Tessellator tess=Tessellator.getInstance();
        BufferBuilder buf=tess.getBuffer();
        if (mesh.quads.length>0) {
            buf.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
            drawWallFaces(buf,sprite,mesh.quads);
            tess.draw();
        }
        if (mesh.triangles.length>0) {
            buf.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_TEX);
            drawWallFaces(buf,sprite,mesh.triangles);
            tess.draw();
        }
    }

    private static void drawWallFaces(BufferBuilder buf,TextureAtlasSprite sprite,
            ScreenHousingMesh.Face[] faces) {
        for (ScreenHousingMesh.Face face:faces) for (ScreenHousingMesh.Vertex vertex:face.vertices)
            buf.pos(vertex.x,vertex.y,vertex.z)
                    .tex(sprite.getInterpolatedU(vertex.u),sprite.getInterpolatedV(vertex.v))
                    .endVertex();
    }

    /** TextureManager caches per ResourceLocation; drop entries on pack reload. */
    public static synchronized void clearCache() {
        ANIM_CACHE.clear();
    }
}
