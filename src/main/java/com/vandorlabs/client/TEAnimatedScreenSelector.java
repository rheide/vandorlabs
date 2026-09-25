package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.animation.CompactAnimationDecoder;
import com.vandorlabs.animation.AnimationFrames;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.render.InputSurfaceLayout;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.BlockProgrammableConsole;
import com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
import com.vandorlabs.blocks.BlockProgrammableHalfConsole;
import com.vandorlabs.blocks.BlockProgrammableInput;
import com.vandorlabs.blocks.BlockProgrammableFullInput;
import com.vandorlabs.blocks.BlockProgrammableWall;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

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
    private static final Map<BlockPos, PortholeGroup> PORTHOLE_GROUPS = new HashMap<>();
    private static World portholeCacheWorld;
    private static long portholeCacheTick = Long.MIN_VALUE;
    private static long portholeCacheRevision = Long.MIN_VALUE;
    private static final Map<BlockPos, LightGroup> LIGHT_GROUPS = new HashMap<>();
    private static World lightCacheWorld;
    private static long lightCacheTick = Long.MIN_VALUE;
    private static long lightCacheRevision = Long.MIN_VALUE;

    static final class LightGroup {
        final int minAxis, minRow, columns, rows;
        final EnumFacing right, up;

        LightGroup(int minAxis, int minRow, int columns, int rows,
                EnumFacing right, EnumFacing up) {
            this.minAxis = minAxis;
            this.minRow = minRow;
            this.columns = columns;
            this.rows = rows;
            this.right = right;
            this.up = up;
        }

        double left(BlockPos pos) { return 16.0 * (axis(pos, right) - minAxis) / columns; }
        double right(BlockPos pos) { return 16.0 * (axis(pos, right) - minAxis + 1) / columns; }
        double top(BlockPos pos) { return 16.0 * (rows - 1 - (axis(pos, up) - minRow)) / rows; }
        double bottom(BlockPos pos) { return 16.0 * (rows - (axis(pos, up) - minRow)) / rows; }
    }

    static LightGroup lightGroup(com.vandorlabs.tiles.TileEntityProgrammableLight tile,
            IBlockState state) {
        World world = tile.getWorld();
        long tick = world.getTotalWorldTime();
        long revision = com.vandorlabs.tiles.TileEntityProgrammableLight.getJoinRevision();
        if (world != lightCacheWorld || tick != lightCacheTick
                || revision != lightCacheRevision) {
            LIGHT_GROUPS.clear();
            lightCacheWorld = world;
            lightCacheTick = tick;
            lightCacheRevision = revision;
        }
        LightGroup cached = LIGHT_GROUPS.get(tile.getPos());
        if (cached != null) return cached;
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        EnumFacing right = facing.getAxis().isHorizontal()
                ? facing.rotateY() : EnumFacing.EAST;
        EnumFacing up = facing == EnumFacing.UP ? EnumFacing.SOUTH
                : facing == EnumFacing.DOWN ? EnumFacing.NORTH : EnumFacing.UP;
        Set<BlockPos> members = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(tile.getPos());
        queue.add(tile.getPos());
        boolean capped = false;
        if (tile.isJoin()) {
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                for (EnumFacing side : new EnumFacing[] {
                        right, right.getOpposite(), up, up.getOpposite()}) {
                    BlockPos next = current.offset(side);
                    if (members.contains(next) || !world.isBlockLoaded(next)
                            || !eligibleLight(world, next, facing, tile)) continue;
                    members.add(next);
                    queue.addLast(next);
                    if (members.size() >= 4096) {
                        capped = true;
                        queue.clear();
                        break;
                    }
                }
            }
        }
        if (capped) members.clear();
        if (members.isEmpty()) members.add(tile.getPos());
        Map<Long, BlockPos> positions = new HashMap<>();
        for (BlockPos pos : members)
            positions.put(PortholeRectangles.cell(axis(pos, right), axis(pos, up)), pos);
        for (PortholeRectangles.Rect rect : PortholeRectangles.partition(positions.keySet())) {
            LightGroup group = new LightGroup(rect.x, rect.y, rect.width, rect.height,
                    right, up);
            for (int row = rect.y; row < rect.y + rect.height; row++)
                for (int col = rect.x; col < rect.x + rect.width; col++)
                    LIGHT_GROUPS.put(positions.get(PortholeRectangles.cell(col, row)), group);
        }
        return LIGHT_GROUPS.get(tile.getPos());
    }

    private static boolean eligibleLight(World world, BlockPos pos, EnumFacing facing,
            com.vandorlabs.tiles.TileEntityProgrammableLight first) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != ModBlocks.PROGRAMMABLE_LIGHT
                || state.getValue(BlockAnimatedScreenSelector.FACING) != facing) return false;
        net.minecraft.tileentity.TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof com.vandorlabs.tiles.TileEntityProgrammableLight)) return false;
        com.vandorlabs.tiles.TileEntityProgrammableLight other =
                (com.vandorlabs.tiles.TileEntityProgrammableLight) raw;
        return other.isJoin() && other.getTexture() == first.getTexture()
                && other.isOn() == first.isOn();
    }

    static final class PortholeGroup {
        final int minAxis, minY, columns, rows;
        final EnumFacing right;
        final PortholeHex hex;

        PortholeGroup(int minAxis, int minY, int columns, int rows,
                EnumFacing right, int shape) {
            this.minAxis = minAxis;
            this.minY = minY;
            this.columns = columns;
            this.rows = rows;
            this.right = right;
            this.hex = new PortholeHex(columns, rows, shape);
        }

        PortholeHex.Slice slice(BlockPos pos) {
            return hex.slice(axis(pos, right) - minAxis, pos.getY() - minY);
        }
    }

    private static int axis(BlockPos pos, EnumFacing right) {
        return pos.getX() * right.getFrontOffsetX()
                + pos.getY() * right.getFrontOffsetY()
                + pos.getZ() * right.getFrontOffsetZ();
    }

    static PortholeGroup portholeGroup(TileEntityAnimatedScreenSelector tile,
            IBlockState state) {
        World world = tile.getWorld();
        long tick = world.getTotalWorldTime();
        long revision = TileEntityAnimatedScreenSelector.getPortholeRevision();
        if (world != portholeCacheWorld || tick != portholeCacheTick
                || revision != portholeCacheRevision) {
            PORTHOLE_GROUPS.clear();
            portholeCacheWorld = world;
            portholeCacheTick = tick;
            portholeCacheRevision = revision;
        }
        PortholeGroup cached = PORTHOLE_GROUPS.get(tile.getPos());
        if (cached != null) return cached;
        EnumFacing facing = state.getValue(BlockProgrammableWall.FACING);
        EnumFacing right = facing.rotateY();
        int minimum = axis(tile.getPos(), right), maximum = minimum;
        int minY = tile.getPos().getY(), maxY = minY;
        Set<BlockPos> members = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(tile.getPos());
        queue.add(tile.getPos());
        boolean capped = false;
        if (tile.isJoinPortholes()) {
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                for (EnumFacing side : new EnumFacing[] {right,
                        right.getOpposite(), EnumFacing.UP, EnumFacing.DOWN}) {
                    BlockPos next = current.offset(side);
                    if (members.contains(next) || !world.isBlockLoaded(next)
                            || !eligiblePorthole(world, next, facing,
                                    state.getValue(BlockProgrammableWall.DEPTH),
                                    tile.getPortholeShape())) continue;
                    members.add(next);
                    queue.addLast(next);
                    int coordinate = axis(next, right);
                    minimum = Math.min(minimum, coordinate);
                    maximum = Math.max(maximum, coordinate);
                    minY = Math.min(minY, next.getY());
                    maxY = Math.max(maxY, next.getY());
                    if (members.size() >= 4096) {
                        capped = true;
                        queue.clear();
                        break;
                    }
                }
            }
        }
        if (capped) {
            for (BlockPos pos : members) PORTHOLE_GROUPS.put(pos,
                    new PortholeGroup(axis(pos, right), pos.getY(), 1, 1, right,
                            tile.getPortholeShape()));
            return PORTHOLE_GROUPS.get(tile.getPos());
        }
        if ((long) (maximum - minimum + 1) * (maxY - minY + 1) != members.size()) {
            Map<Long, BlockPos> positions = new HashMap<>();
            for (BlockPos pos : members)
                positions.put(PortholeRectangles.cell(axis(pos, right), pos.getY()), pos);
            for (PortholeRectangles.Rect rect : PortholeRectangles.partition(positions.keySet())) {
                PortholeGroup part = new PortholeGroup(rect.x, rect.y,
                        rect.width, rect.height, right, tile.getPortholeShape());
                for (int row = rect.y; row < rect.y + rect.height; row++)
                    for (int column = rect.x; column < rect.x + rect.width; column++)
                        PORTHOLE_GROUPS.put(positions.get(PortholeRectangles.cell(column, row)), part);
            }
            return PORTHOLE_GROUPS.get(tile.getPos());
        }
        PortholeGroup group = new PortholeGroup(minimum, minY,
                maximum - minimum + 1, maxY - minY + 1, right,
                tile.getPortholeShape());
        for (BlockPos pos : members) PORTHOLE_GROUPS.put(pos, group);
        return group;
    }

    private static boolean eligiblePorthole(World world, BlockPos pos,
            EnumFacing facing, int depth, int shape) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockProgrammableWall)
                || ((BlockProgrammableWall) state.getBlock()).getShape()
                != BlockProgrammableWall.Shape.PORTHOLE
                || state.getValue(BlockProgrammableWall.FACING) != facing
                || state.getValue(BlockProgrammableWall.DEPTH) != depth) return false;
        net.minecraft.tileentity.TileEntity raw = world.getTileEntity(pos);
        return raw instanceof TileEntityAnimatedScreenSelector
                && ((TileEntityAnimatedScreenSelector) raw).isJoinPortholes()
                && ((TileEntityAnimatedScreenSelector) raw).getPortholeShape() == shape;
    }

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
        if (state.getBlock() instanceof BlockProgrammableWall) {
            renderProgrammableWall(te, state, x, y, z);
            return;
        }
        if (state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableLight) {
            com.vandorlabs.tiles.TileEntityProgrammableLight light =
                    (com.vandorlabs.tiles.TileEntityProgrammableLight) te;
            beginLightTransform(x, y, z, state.getValue(BlockAnimatedScreenSelector.FACING));
            GlStateManager.disableLighting();
            bindAtlas();
            setNeighborWorldLight(te);
            TextureAtlasSprite housing = wallSprite(te);
            TextureAtlasSprite face = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite(com.vandorlabs.tiles.ProgrammableLightTextures.texture(
                            light.getTexture(), light.isOn() && light.getLightLevel() > 0));
            renderWallBox(housing, 0, 0, 0, 16, 16, 16);
            renderProgrammableLightFace(face, lightGroup(light, state), te.getPos());
            GlStateManager.enableLighting();
            endLocalTransform();
            return;
        }
        if (state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableSlab) {
            beginLocalTransform(x, y, z, state.getValue(BlockAnimatedScreenSelector.FACING));
            GlStateManager.disableLighting();
            bindAtlas();
            setNeighborWorldLight(te);
            boolean upper = state.getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                    == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP;
            renderSlab(wallSprite(te), upper, te.isSlabTileSides());
            GlStateManager.enableLighting();
            endLocalTransform();
            return;
        }
        if (state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableBlock) {
            beginLocalTransform(x, y, z, state.getValue(BlockAnimatedScreenSelector.FACING));
            GlStateManager.disableLighting();
            bindAtlas();
            setNeighborWorldLight(te);
            renderWallBox(wallSprite(te), 0, 0, 0, 16, 16, 16);
            GlStateManager.enableLighting();
            endLocalTransform();
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
        } else {
            bindAtlas();
            setWorldLight(te);
            renderWallBox(wallSprite(te), 0, 0, 0, 16, 16, 16);
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

    private static void beginLightTransform(double x, double y, double z,
            EnumFacing facing) {
        if (facing.getAxis().isHorizontal()) {
            beginLocalTransform(x, y, z, facing);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y + .5D, z + .5D);
        GlStateManager.rotate(facing == EnumFacing.UP ? 90F : -90F, 1, 0, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        GlStateManager.scale(1F / 16F, 1F / 16F, 1F / 16F);
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
        TextureAtlasSprite wall = wallSprite(te);
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
        TextureAtlasSprite wall = wallSprite(te);
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
        TextureAtlasSprite wall = wallSprite(te);
        renderWallBox(wall, 0, 0, 0, 16, 1, 16);
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

    /** A full opaque cube has no useful light value at its own position. */
    private static void setNeighborWorldLight(TileEntityAnimatedScreenSelector te) {
        int sky = 0;
        int block = 0;
        for (EnumFacing side : EnumFacing.values()) {
            net.minecraft.util.math.BlockPos neighbor = te.getPos().offset(side);
            if (!te.getWorld().isBlockLoaded(neighbor)) continue;
            int combined = te.getWorld().getCombinedLight(neighbor, 0);
            sky = Math.max(sky, combined >>> 16);
            block = Math.max(block, combined & 65535);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, block, sky);
    }

    private static void renderWallBox(TextureAtlasSprite wall, double x0, double y0,
            double z0, double x1, double y1, double z1) {
        renderWallBox(wall, wall, true, x0, y0, z0, x1, y1, z1);
    }

    private static void renderSlab(TextureAtlasSprite sprite, boolean upper,
            boolean tileSides) {
        double low = upper ? 8 : 0;
        double high = upper ? 16 : 8;
        double v0 = tileSides && !upper ? 8 : 0;
        double v1 = tileSides && upper ? 8 : 16;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        spriteQuad(b, sprite, 0,high,0, 16,high,0, 16,high,16, 0,high,16,
                0,0,16,16);
        spriteQuad(b, sprite, 0,low,16, 16,low,16, 16,low,0, 0,low,0,
                0,0,16,16);
        spriteQuad(b, sprite, 0,high,0, 0,high,16, 0,low,16, 0,low,0,
                0,v0,16,v1);
        spriteQuad(b, sprite, 16,high,16, 16,high,0, 16,low,0, 16,low,16,
                0,v0,16,v1);
        spriteQuad(b, sprite, 16,high,0, 0,high,0, 0,low,0, 16,low,0,
                0,v0,16,v1);
        spriteQuad(b, sprite, 0,high,16, 16,high,16, 16,low,16, 0,low,16,
                0,v0,16,v1);
        tess.draw();
    }

    /** Broad faces use the selected wall art; exposed thickness uses frame metal. */
    private static void renderWallBox(TextureAtlasSprite face, TextureAtlasSprite side,
            boolean faceAlongZ, double x0, double y0, double z0,
            double x1, double y1, double z1) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        spriteQuad(b, side, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, 0,0,16,16);
        spriteQuad(b, side, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, 0,0,16,16);
        TextureAtlasSprite xFace = faceAlongZ ? side : face;
        TextureAtlasSprite zFace = faceAlongZ ? face : side;
        spriteQuad(b, xFace, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0, 0,0,16,16);
        spriteQuad(b, xFace, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1, 0,0,16,16);
        spriteQuad(b, zFace, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0, 0,0,16,16);
        spriteQuad(b, zFace, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1, 0,0,16,16);
        tess.draw();
    }

    /** Draw the console's non-cuboid wall wedge and selectable input deck. */
    private static void renderConsoleHousing(TileEntityAnimatedScreenSelector te) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (combined % 65536), (float) (combined / 65536));

        TextureAtlasSprite wall = wallSprite(te);
        renderWallBox(wall, 0, 0, 0, 16, 1, 16);
        drawWallMesh(wall,ScreenHousingMesh.console());

        // The supplied half-height controls are native 2:1 textures rather
        // than square atlas tiles. Bind them directly so the complete artwork
        // fills the deck without cropping or atlas-induced aspect changes.
        double[] inputUv = bindInput(te, te.getInputPanel());
        drawInputSurface(InputSurfaceLayout.halfConsoleFront(),inputUv);
    }

    private void renderProgrammableWall(TileEntityAnimatedScreenSelector te,
            IBlockState state, double x, double y, double z) {
        BlockProgrammableWall wallBlock = (BlockProgrammableWall) state.getBlock();
        BlockProgrammableWall.FlatCorner flat = wallBlock.getShape()
                == BlockProgrammableWall.Shape.PLAIN
                ? wallBlock.flatCorner(state, te.getWorld(), te.getPos()) : null;
        beginLocalTransform(x, y, z, state.getValue(BlockProgrammableWall.FACING));
        if (wallBlock.getShape() != BlockProgrammableWall.Shape.DIAGONAL && flat == null)
            GlStateManager.translate(0, 0, com.vandorlabs.blocks.PanelDepth.offset(
                    state.getValue(BlockProgrammableWall.DEPTH)));
        GlStateManager.disableLighting();
        bindAtlas();
        setWorldLight(te);
        TextureAtlasSprite wall = wallSprite(te);
        TextureAtlasSprite metal = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        PortholeHex.Slice porthole = wallBlock.getShape() == BlockProgrammableWall.Shape.PORTHOLE
                ? portholeGroup(te, state).slice(te.getPos()) : null;
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        if (wallBlock.getShape() == BlockProgrammableWall.Shape.DIAGONAL) {
            renderDiagonalWall(buf, wall, metal,
                    state.getValue(BlockProgrammableWall.INVERTED),
                    wallBlock.corner(state, te.getWorld(), te.getPos()));
        } else if (flat != null) {
            renderFlatWall(buf, wall, metal,
                    com.vandorlabs.blocks.PanelDepth.start(
                            state.getValue(BlockProgrammableWall.DEPTH)),
                    flat);
        } else {
            if (porthole != null) {
                for (double[] quad : porthole.frameQuads)
                    panelQuad(buf, wall, quad[0], quad[1], quad[2], quad[3],
                            quad[4], quad[5], quad[6], quad[7]);
                for (double[] edge : porthole.hexEdges)
                    rimSegment(buf, metal, edge[0], edge[1], edge[2], edge[3]);
            } else {
                panelRect(buf, wall, 0, 0, 16, 16);
            }
            panelOuterRim(buf, metal, porthole, te, state);
        }
        Tessellator.getInstance().draw();
        if (porthole != null) renderPortholeGlass(te.getGlassShade(), porthole);
        GlStateManager.enableLighting();
        endLocalTransform();
    }

    static boolean joinsPorthole(TileEntityAnimatedScreenSelector tile,
            IBlockState state, boolean right) {
        if (!tile.isJoinPortholes()) return false;
        EnumFacing side = state.getValue(BlockProgrammableWall.FACING).rotateY();
        if (!right) side = side.getOpposite();
        net.minecraft.util.math.BlockPos next = tile.getPos().offset(side);
        if (!tile.getWorld().isBlockLoaded(next)) return false;
        IBlockState neighbor = tile.getWorld().getBlockState(next);
        if (!(neighbor.getBlock() instanceof BlockProgrammableWall)
                || ((BlockProgrammableWall) neighbor.getBlock()).getShape()
                != BlockProgrammableWall.Shape.PORTHOLE
                || neighbor.getValue(BlockProgrammableWall.FACING)
                != state.getValue(BlockProgrammableWall.FACING)
                || neighbor.getValue(BlockProgrammableWall.DEPTH)
                != state.getValue(BlockProgrammableWall.DEPTH)) return false;
        net.minecraft.tileentity.TileEntity other = tile.getWorld().getTileEntity(next);
        return other instanceof TileEntityAnimatedScreenSelector
                && ((TileEntityAnimatedScreenSelector) other).isJoinPortholes()
                && ((TileEntityAnimatedScreenSelector) other).getPortholeShape()
                == tile.getPortholeShape();
    }

    private static void wallVertex(BufferBuilder buf, TextureAtlasSprite sprite,
            double x, double y, double z, double u, double v) {
        buf.pos(x, y, z).tex(sprite.getInterpolatedU(u),
                sprite.getInterpolatedV(v)).endVertex();
    }

    private static void panelRect(BufferBuilder buf, TextureAtlasSprite sprite,
            int x0, int y0, int x1, int y1) {
        for (int z : new int[] {6, 10}) {
            wallVertex(buf, sprite, x0, y0, z, x0, 16 - y0);
            wallVertex(buf, sprite, x1, y0, z, x1, 16 - y0);
            wallVertex(buf, sprite, x1, y1, z, x1, 16 - y1);
            wallVertex(buf, sprite, x0, y1, z, x0, 16 - y1);
        }
    }

    private static void panelQuad(BufferBuilder buf, TextureAtlasSprite sprite,
            double x0, double y0, double x1, double y1, double x2, double y2,
            double x3, double y3) {
        for (int z : new int[] {6, 10}) {
            wallVertex(buf, sprite, x0, y0, z, x0, 16 - y0);
            wallVertex(buf, sprite, x1, y1, z, x1, 16 - y1);
            wallVertex(buf, sprite, x2, y2, z, x2, 16 - y2);
            wallVertex(buf, sprite, x3, y3, z, x3, 16 - y3);
        }
    }

    private static void panelOuterRim(BufferBuilder buf, TextureAtlasSprite metal,
            PortholeHex.Slice opening, TileEntityAnimatedScreenSelector tile,
            IBlockState state) {
        for (int y : new int[] {0, 16}) {
            if (opening != null && adjacentPorthole(tile, state,
                    y == 0 ? EnumFacing.DOWN : EnumFacing.UP)) continue;
            double[] cut = opening == null ? null : opening.edgeOpening(1, y);
            if (cut == null) topRim(buf, metal, y, 0, 16);
            else {
                topRim(buf, metal, y, 0, cut[0]);
                topRim(buf, metal, y, cut[1], 16);
            }
        }
        for (int x : new int[] {0, 16}) {
            EnumFacing right = state.getValue(BlockProgrammableWall.FACING).rotateY();
            if (opening != null && adjacentPorthole(tile, state,
                    x == 0 ? right.getOpposite() : right)) continue;
            double[] cut = opening == null ? null : opening.edgeOpening(0, x);
            if (cut == null) sideRim(buf, metal, x, 0, 16);
            else {
                sideRim(buf, metal, x, 0, cut[0]);
                sideRim(buf, metal, x, cut[1], 16);
            }
        }
    }

    private static boolean adjacentPorthole(TileEntityAnimatedScreenSelector tile,
            IBlockState state, EnumFacing side) {
        BlockPos next = tile.getPos().offset(side);
        if (!tile.getWorld().isBlockLoaded(next)) return false;
        IBlockState other = tile.getWorld().getBlockState(next);
        return other.getBlock() == state.getBlock()
                && other.getValue(BlockProgrammableWall.FACING)
                == state.getValue(BlockProgrammableWall.FACING)
                && other.getValue(BlockProgrammableWall.DEPTH)
                == state.getValue(BlockProgrammableWall.DEPTH)
                && tile.isJoinPortholes()
                && tile.getWorld().getTileEntity(next) instanceof TileEntityAnimatedScreenSelector
                && ((TileEntityAnimatedScreenSelector)tile.getWorld().getTileEntity(next))
                .isJoinPortholes()
                && ((TileEntityAnimatedScreenSelector)tile.getWorld().getTileEntity(next))
                .getPortholeShape() == tile.getPortholeShape();
    }

    private static void topRim(BufferBuilder buf, TextureAtlasSprite metal,
            double y, double x0, double x1) {
        if (x1 - x0 < 1.0E-7) return;
        wallVertex(buf, metal, x0, y, 6, x0, 0);
        wallVertex(buf, metal, x1, y, 6, x1, 0);
        wallVertex(buf, metal, x1, y, 10, x1, 4);
        wallVertex(buf, metal, x0, y, 10, x0, 4);
    }

    private static void sideRim(BufferBuilder buf, TextureAtlasSprite metal,
            double x, double y0, double y1) {
        if (y1 - y0 < 1.0E-7) return;
        wallVertex(buf, metal, x, y0, 6, 0, 16 - y0);
        wallVertex(buf, metal, x, y0, 10, 4, 16 - y0);
        wallVertex(buf, metal, x, y1, 10, 4, 16 - y1);
        wallVertex(buf, metal, x, y1, 6, 0, 16 - y1);
    }

    private static void rimSegment(BufferBuilder buf, TextureAtlasSprite metal,
            double x0, double y0, double x1, double y1) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        // Atlas coordinates must stay inside this sprite, including diagonal
        // edges longer than 16 pixels. Tile the metal along those edges.
        for (double start = 0; start < length - 1.0E-7; start += 16) {
            double end = Math.min(length, start + 16);
            double ax = x0 + (x1 - x0) * start / length;
            double ay = y0 + (y1 - y0) * start / length;
            double bx = x0 + (x1 - x0) * end / length;
            double by = y0 + (y1 - y0) * end / length;
            wallVertex(buf, metal, ax, ay, 6, 0, 0);
            wallVertex(buf, metal, bx, by, 6, end - start, 0);
            wallVertex(buf, metal, bx, by, 10, end - start, 4);
            wallVertex(buf, metal, ax, ay, 10, 0, 4);
        }
    }

    private static void renderDiagonalWall(BufferBuilder buf,
            TextureAtlasSprite wall, TextureAtlasSprite metal, boolean inverted,
            BlockProgrammableWall.Corner corner) {
        double bottom = inverted ? 6 : 0, top = inverted ? 0 : 6;
        double[][] lower = diagonalOutline(bottom, corner);
        double[][] upper = diagonalOutline(top, corner);
        for (int i = 0; i < lower.length; i++) {
            int next = (i + 1) % lower.length;
            double[] a = lower[i], b = lower[next];
            double[] c = upper[next], d = upper[i];
            if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) < 1.0E-7
                    && Math.abs(c[0] - d[0]) + Math.abs(c[1] - d[1]) < 1.0E-7)
                continue;
            boolean endCap = Math.abs(a[0] - b[0]) < 1.0E-7
                    && Math.abs(c[0] - d[0]) < 1.0E-7
                    && Math.abs(Math.abs(a[1] - b[1]) - 4) < 1.0E-7
                    && Math.abs(Math.abs(c[1] - d[1]) - 4) < 1.0E-7;
            TextureAtlasSprite sprite = endCap ? metal : wall;
            boolean acrossX = Math.abs(a[0] - b[0]) + Math.abs(c[0] - d[0])
                    > Math.abs(a[1] - b[1]) + Math.abs(c[1] - d[1]);
            wallVertex(buf, sprite, a[0], 0, a[1], acrossX ? a[0] : a[1], 16);
            wallVertex(buf, sprite, b[0], 0, b[1], acrossX ? b[0] : b[1], 16);
            wallVertex(buf, sprite, c[0], 16, c[1], acrossX ? c[0] : c[1], 0);
            wallVertex(buf, sprite, d[0], 16, d[1], acrossX ? d[0] : d[1], 0);
        }
        for (int y : new int[] {0, 16}) {
            double near = y == 0 ? bottom : top;
            roofRect(buf, metal, y, corner == null ? 0 : corner.left(near),
                    corner == null ? 16 : corner.right(near), near, near + 4);
            if (corner != null) {
                if (corner.frontRight != null) {
                    double armX = corner.armLeft(near, corner.frontRight);
                    roofRect(buf, metal, y, armX, armX + 4, 0, near);
                }
                if (corner.backRight != null) {
                    double armX = corner.armLeft(near, corner.backRight);
                    roofRect(buf, metal, y, armX, armX + 4, near + 4, 16);
                }
            }
        }
    }

    private static void renderFlatWall(BufferBuilder buf,
            TextureAtlasSprite wall, TextureAtlasSprite metal, double near,
            BlockProgrammableWall.FlatCorner corner) {
        double[][] outline = flatOutline(near, corner);
        for (int i = 0; i < outline.length; i++) {
            double[] a = outline[i], b = outline[(i + 1) % outline.length];
            if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) < 1.0E-7) continue;
            boolean endCap = Math.abs(a[0] - b[0]) < 1.0E-7
                    && Math.abs(Math.abs(a[1] - b[1]) - 4) < 1.0E-7;
            // Exposed ends of a connecting arm are also four-pixel metal caps.
            endCap |= Math.abs(a[1] - b[1]) < 1.0E-7
                    && Math.abs(Math.abs(a[0] - b[0]) - 4) < 1.0E-7
                    && ((corner.frontArm != null && near > 0 && a[1] == 0)
                    || (corner.backArm != null && near + 4 < 16 && a[1] == 16));
            TextureAtlasSprite sprite = endCap ? metal : wall;
            boolean acrossX = Math.abs(a[0] - b[0]) > Math.abs(a[1] - b[1]);
            double u0 = acrossX ? a[0] : a[1];
            double u1 = acrossX ? b[0] : b[1];
            wallVertex(buf, sprite, a[0], 0, a[1], u0, 16);
            wallVertex(buf, sprite, b[0], 0, b[1], u1, 16);
            wallVertex(buf, sprite, b[0], 16, b[1], u1, 0);
            wallVertex(buf, sprite, a[0], 16, a[1], u0, 0);
        }
        for (int y : new int[] {0, 16}) {
            roofRect(buf, metal, y, corner.left(), corner.right(), near, near + 4);
            if (corner.frontArm != null)
                roofRect(buf, metal, y, corner.frontArm,
                        corner.frontArm + 4, 0, near);
            if (corner.backArm != null)
                roofRect(buf, metal, y, corner.backArm,
                        corner.backArm + 4, near + 4, 16);
        }
    }

    private static double[][] flatOutline(double near,
            BlockProgrammableWall.FlatCorner corner) {
        java.util.List<double[]> points = new java.util.ArrayList<>();
        points.add(new double[] {corner.left(), near});
        if (corner.frontArm != null) {
            points.add(new double[] {corner.frontArm, near});
            points.add(new double[] {corner.frontArm, 0});
            points.add(new double[] {corner.frontArm + 4, 0});
            points.add(new double[] {corner.frontArm + 4, near});
        }
        points.add(new double[] {corner.right(), near});
        points.add(new double[] {corner.right(), near + 4});
        if (corner.backArm != null) {
            points.add(new double[] {corner.backArm + 4, near + 4});
            points.add(new double[] {corner.backArm + 4, 16});
            points.add(new double[] {corner.backArm, 16});
            points.add(new double[] {corner.backArm, near + 4});
        }
        points.add(new double[] {corner.left(), near + 4});
        return points.toArray(new double[points.size()][]);
    }

    private static double[][] diagonalOutline(double near,
            BlockProgrammableWall.Corner corner) {
        if (corner == null) return new double[][] {
                {0, near}, {16, near}, {16, near + 4}, {0, near + 4}};
        double left = corner.left(near), right = corner.right(near);
        java.util.List<double[]> points = new java.util.ArrayList<>();
        points.add(new double[] {left, near});
        if (corner.frontRight != null) {
            double armX = corner.armLeft(near, corner.frontRight);
            points.add(new double[] {armX, near});
            points.add(new double[] {armX, 0});
            points.add(new double[] {armX + 4, 0});
            points.add(new double[] {armX + 4, near});
        }
        points.add(new double[] {right, near});
        points.add(new double[] {right, near + 4});
        if (corner.backRight != null) {
            double armX = corner.armLeft(near, corner.backRight);
            points.add(new double[] {armX + 4, near + 4});
            points.add(new double[] {armX + 4, 16});
            points.add(new double[] {armX, 16});
            points.add(new double[] {armX, near + 4});
        }
        points.add(new double[] {left, near + 4});
        return points.toArray(new double[points.size()][]);
    }

    private static void roofRect(BufferBuilder buf, TextureAtlasSprite metal,
            double y, double x0, double x1, double z0, double z1) {
        if (z1 - z0 < 1.0E-7) return;
        wallVertex(buf, metal, x0, y, z0, x0, z0);
        wallVertex(buf, metal, x1, y, z0, x1, z0);
        wallVertex(buf, metal, x1, y, z1, x1, z1);
        wallVertex(buf, metal, x0, y, z1, x0, z1);
    }

    private void renderPortholeGlass(int shade, PortholeHex.Slice opening) {
        if (opening.polygon.size() < 3) return;
        bindTexture(new ResourceLocation(VandorLabs.MODID,
                "textures/blocks/space_doors/medium/glass_tile.png"));
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER, .003F);
        GlStateManager.depthMask(false);
        GlStateManager.color(1F, 1F, 1F, 1F);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        for (int depth : new int[] {7, 9}) {
            for (int i = 1; i < opening.polygon.size() - 1; i++) {
                for (int vertex : new int[] {0, i, i + 1}) {
                    double[] point = opening.polygon.get(vertex);
                    buf.pos(point[0], point[1], depth)
                            .tex(point[0] / 16D, 1D - point[1] / 16D).endVertex();
                }
            }
        }
        Tessellator.getInstance().draw();
        if (shade != 0) {
            GlStateManager.disableTexture2D();
            if (shade == 1) GlStateManager.color(.20F, .85F, .95F, .0513F);
            else GlStateManager.color(.10F, .12F, .16F, .1754F);
            buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
            for (int depth : new int[] {7, 9}) {
                for (int i = 1; i < opening.polygon.size() - 1; i++) {
                    for (int vertex : new int[] {0, i, i + 1}) {
                        double[] point = opening.polygon.get(vertex);
                        buf.pos(point[0], point[1], depth).endVertex();
                    }
                }
            }
            Tessellator.getInstance().draw();
            GlStateManager.enableTexture2D();
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER, .1F);
        GlStateManager.disableBlend();
        bindAtlas();
    }

    /** Full solid half-cube wedge used by the standalone diagonal display. */
    private static void renderDiagonalHousing(TileEntityAnimatedScreenSelector te,
            boolean inverted) {
        Minecraft mc = Minecraft.getMinecraft();
        bindAtlas();
        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (combined % 65536), (float) (combined / 65536));
        TextureAtlasSprite wall = wallSprite(te);
        // Explicit upper/lower geometry keeps the artwork upright. Reflecting
        // the model matrix would also reflect the texture.
        drawWallMesh(wall,ScreenHousingMesh.diagonal(inverted));
    }

    private static void renderProgrammableLightFace(TextureAtlasSprite face,
            LightGroup group, BlockPos pos) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        spriteQuad(buf, face, 16,16,-.002, 0,16,-.002,
                0,0,-.002, 16,0,-.002, group.right(pos), group.top(pos),
                group.left(pos), group.bottom(pos));
        tess.draw();
    }

    private static void bindAtlas() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private static TextureAtlasSprite wallSprite(TileEntityAnimatedScreenSelector te) {
        int choice = te instanceof com.vandorlabs.tiles.TileEntityProgrammableTrigger
                ? ((com.vandorlabs.tiles.TileEntityProgrammableTrigger) te).getVisibleTexture()
                : te.getHousingTexture();
        return Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(ScreenHousingTextures.texture(choice));
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
