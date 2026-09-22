package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.BlockDetailedDoor;
import com.vandorlabs.blocks.BlockConnectingDetailedDoor;
import com.vandorlabs.animation.DoorAnimation;
import com.vandorlabs.render.DoorLeaf;
import com.vandorlabs.render.DoorLeafTransform;
import com.vandorlabs.render.DoorPanelLayout;
import com.vandorlabs.tiles.TileEntitySlidingDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;

/**
 * Draws animated door leaves. Sliders glide eased between shut (full panel)
 * and open: split sliders part a pair of halves, single-panel sliders glide
 * the whole face to one jamb. Hinged doors rotate a single leaf about the
 * block's inside corner, swinging INWARD so the parked leaf stays entirely
 * inside its own block footprint (no protrusion over the neighbor); split
 * doors (double/hangar) swing two half-leaves to their own sides. Every
 * animated door's statics is a leaf-free frame, so the TESR owns the leaves
 * at every pose (no parked leaves, consistent lighting).
 * <p>
 * The swept-lead code paths are deliberately separated: sliding panels are
 * straight drawPanel calls in the door frame, swung leaves all route through
 * drawSwungLeaf with a pivot + angle. The rotation DIRECTION (not a mirror
 * flag) is what makes a swung leaf read correctly, verified against the
 * approved static UVs (test_doors.py locks the constants). Panel modes come
 * from the generated sliding_panels.json, so new blocks need no code changes.
 */
@SideOnly(Side.CLIENT)
public class TESlidingDoor extends TileEntitySpecialRenderer<TileEntitySlidingDoor> {

    private static class WindowOpening {
        final float u0, u1, v0, v1;

        WindowOpening(float u0, float u1, float v0, float v1) {
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
        }
    }

    /** Transparent-component bounds measured from the canonical 512px upper
     * sheets and converted into the atlas's 0..16 coordinate space. */
    private static WindowOpening windowFor(String art, boolean upper) {
        if (!upper) {
            return null;
        }
        if (art.endsWith("door_airlock_glass")) {
            return new WindowOpening(6.375F, 9.625F, 2.875F, 14.625F);
        }
        return null;
    }

    private static class PanelInfo {
        /** "single"/"split" = sliding doors; "hinge_split" = double/hangar
         * doors whose two 8px leaves each swing to one side of the block. */
        final String kind;
        final String art;

        PanelInfo(String kind, String art) {
            this.kind = kind;
            this.art = art;
        }
    }

    private static Map<String, PanelInfo> panels;

    private static synchronized Map<String, PanelInfo> panels() {
        if (panels == null) {
            panels = new HashMap<>();
            boolean loaded = false;
            try (InputStream in = TESlidingDoor.class
                    .getResourceAsStream("/assets/vandorlabs/data/sliding_panels.json")) {
                if (in != null) {
                    JsonObject root = new JsonParser()
                            .parse(new InputStreamReader(in, "UTF-8")).getAsJsonObject();
                    for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                        JsonObject v = e.getValue().getAsJsonObject();
                        panels.put(e.getKey(), new PanelInfo(
                                v.get("panel").getAsString(),
                                v.get("art").getAsString()));
                    }
                    loaded = true;
                }
            } catch (Exception e) {
                System.err.println("[vandorlabs] sliding_panels.json failed to load: " + e);
            }
            if (!loaded) {
                // Compiled-in fallback so sliding doors still render if the
                // generated data file is unreachable at runtime. If you see
                // the line below, the build pipeline output is missing.
                System.err.println("[vandorlabs] WARNING: using fallback sliding-door art map");
                panels.put("vandorlabs:sliding_airlock_glass", new PanelInfo("single", "vandorlabs:blocks/door_airlock_glass"));
                panels.put("vandorlabs:sliding_security_door", new PanelInfo("single", "vandorlabs:blocks/door_security"));
            }
        }
        return panels;
    }

    private static final float ANIM_TICKS = 9.0F;
    private static final int ANIMS_CAP = 1024;
    private static final Map<World, LinkedHashMap<net.minecraft.util.math.BlockPos, DoorAnimation>>
            ANIMS_BY_WORLD = new WeakHashMap<>();

    /** Eased open pose driven by the live blockstate: needs no ticking. */
    private static float animPose(World world, net.minecraft.util.math.BlockPos key,
            boolean open, double now) {
        LinkedHashMap<net.minecraft.util.math.BlockPos, DoorAnimation> animations =
                ANIMS_BY_WORLD.get(world);
        if (animations == null) {
            animations = new LinkedHashMap<>(32, 0.75F, true);
            ANIMS_BY_WORLD.put(world, animations);
        }
        DoorAnimation a = animations.get(key);
        if (a == null) {
            a = new DoorAnimation(ANIM_TICKS);
            animations.put(key, a);
            if (animations.size() > ANIMS_CAP) {
                // Access-ordered map: evict the genuinely least-recently used
                // door in this world, never an arbitrary door in another one.
                animations.remove(animations.keySet().iterator().next());
            }
        }
        return (float) a.sample(open, now);
    }

    @Override
    public void render(TileEntitySlidingDoor te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockVandorDoor)) {
            return;
        }
        state = state.getBlock().getActualState(state, te.getWorld(), te.getPos());
        EnumFacing facing = state.getValue(BlockVandorDoor.FACING);
        boolean upper = state.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER;
        ResourceLocation key = state.getBlock().getRegistryName();
        if (key == null) {
            return;
        }
        if (te instanceof com.vandorlabs.tiles.TileEntitySpaceDoor
                && state.getBlock() instanceof com.vandorlabs.blocks.BlockConfigurableSpaceDoor) {
            if (!upper) {
                float progress=animPose(te.getWorld(),te.getPos(),state.getValue(BlockVandorDoor.OPEN),
                        te.getWorld().getTotalWorldTime()+partialTicks);
                renderSpaceDoor((com.vandorlabs.tiles.TileEntitySpaceDoor)te,state,facing,progress,x,y,z);
            }
            return;
        }
        if (state.getBlock() instanceof BlockDetailedDoor) {
            if (!upper) {
                net.minecraft.util.math.BlockPos doorKey = te.getPos();
                float p = animPose(te.getWorld(), doorKey,
                        state.getValue(BlockVandorDoor.OPEN),
                        te.getWorld().getTotalWorldTime() + partialTicks);
                BlockDetailedDoor placedDoor = (BlockDetailedDoor) state.getBlock();
                BlockDetailedDoor visualDoor = placedDoor;
                if (visualDoor instanceof BlockConnectingDetailedDoor) {
                    visualDoor = ((BlockConnectingDetailedDoor) visualDoor)
                            .getVisualModel(state);
                }
                renderDetailedDoor(te, state, placedDoor, visualDoor,
                        facing, p, x, y, z);
            }
            return;
        }
        PanelInfo info = panels().get(key.toString());
        if (info == null) {
            // Generated panels keys may be bare ids; registry names always
            // carry the vandorlabs: namespace. Try both so the lookup survives
            // either style.
            info = panels().get(key.getResourcePath());
        }
        // Sliding doors keep a panel entry; hinged doors (tile-owning swing
        // doors) fall back to their own lower/upper halves.
        String art = info != null
                ? (info.art.contains(":") ? info.art : "vandorlabs:blocks/" + info.art)
                : "vandorlabs:blocks/" + key.getResourcePath();
        boolean hingeSplit = info != null && "hinge_split".equals(info.kind);
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(art + (upper ? "_upper" : "_lower"));
        TextureAtlasSprite wall = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/wall_panel_dark");
        WindowOpening window = windowFor(art, upper);
        boolean reverseGlassPaint = art.endsWith("door_airlock_glass");
        // Renderer-side motion: derived from the blockstate each frame, so it
        // cannot depend on tile-entity ticking (which proved unreliable here).
        // Both door halves share ONE animation keyed at the lower block: a half
        // that only re-enters render (e.g. the far half was culled) retargets
        // in lockstep with its partner instead of drawing a torn half-open door.
        net.minecraft.util.math.BlockPos doorKey = upper ? te.getPos().down() : te.getPos();
        float p = animPose(te.getWorld(), doorKey, state.getValue(BlockVandorDoor.OPEN),
                te.getWorld().getTotalWorldTime() + partialTicks);

        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        float lightU = (float) (combined % 65536);
        float lightV = (float) (combined / 65536);

        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.5D, 0.0D, 0.5D);
        GlStateManager.rotate(180.0F - facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-0.5D, 0.0D, -0.5D);
        GlStateManager.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        GL11.glDisable(GL11.GL_CULL_FACE);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        // Flat quads shaded by the block lightmap (no normals needed).
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
        boolean hingeLeft = state.getValue(BlockVandorDoor.HINGE) == BlockDoor.EnumHingePosition.LEFT;
        DoorPanelLayout.Kind layoutKind=hingeSplit?DoorPanelLayout.Kind.HINGE_SPLIT
                :info==null?DoorPanelLayout.Kind.HINGED
                :"single".equals(info.kind)?DoorPanelLayout.Kind.SLIDING_SINGLE
                :DoorPanelLayout.Kind.SLIDING_SPLIT;
        DoorPanelLayout.Panel[] panels=DoorPanelLayout.calculate(layoutKind,hingeLeft,reverseGlassPaint,p);
        if (panels[0].swung) {
            for (DoorPanelLayout.Panel panel:panels) drawSwungLeaf(buf,sprite,wall,
                    panel.usesWindow?window:null,(float)panel.x,(float)panel.width,
                    (float)panel.u0,(float)panel.u1,(float)panel.z0,(float)panel.z1,
                    (float)panel.angle,(float)panel.pivotX,(float)panel.pivotZ);
        } else {
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            for (DoorPanelLayout.Panel panel:panels) drawPanel(buf,sprite,wall,
                    panel.usesWindow?window:null,(float)panel.x,(float)panel.width,
                    (float)panel.u0,(float)panel.u1,(float)panel.z0,(float)panel.z1);
            tess.draw();
            GlStateManager.disableTexture2D();
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (DoorPanelLayout.Panel panel:panels) drawEdges(buf,(float)panel.x,
                    (float)panel.width,(float)panel.z0,(float)panel.z1);
            tess.draw();
            GlStateManager.enableTexture2D();
        }
        GlStateManager.enableLighting();

        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.popMatrix();
    }

    /** Renders the pack's full-height leaf model while the baked block model
     * renders only its stationary frame. Hidden item metas 1/2 make the leaf
     * JSONs participate in the 1.12 model bake without registering fake
     * blocks or exposing extra creative-tab entries. */
    private static void renderDetailedDoor(TileEntitySlidingDoor te, IBlockState state,
            BlockDetailedDoor placedDoor, BlockDetailedDoor visualDoor,
            EnumFacing facing, float progress,
            double x, double y, double z) {
        // BlockDoor's enum is mirrored relative to the visual hand names in
        // the SOUTH-authored model pack: vanilla hinge=LEFT rests on the
        // visual right and hinge=RIGHT rests on the visual left.
        boolean right = state.getValue(BlockVandorDoor.HINGE)
                == BlockDoor.EnumHingePosition.LEFT;
        if (visualDoor.isSplitInsideOneBlock()) {
            renderDetailedLeaf(te, state, placedDoor, visualDoor, facing,
                    false, progress, x, y, z);
            renderDetailedLeaf(te, state, placedDoor, visualDoor, facing,
                    true, progress, x, y, z);
        } else {
            renderDetailedLeaf(te, state, placedDoor, visualDoor, facing,
                    right, progress, x, y, z);
        }
    }

    private static void renderDetailedLeaf(TileEntitySlidingDoor te,
            IBlockState state, BlockDetailedDoor placedDoor,
            BlockDetailedDoor visualDoor, EnumFacing facing, boolean right,
            float progress, double x, double y, double z) {
        int metadata = placedDoor instanceof BlockConnectingDetailedDoor
                ? ((BlockConnectingDetailedDoor) placedDoor)
                        .getLeafMetadata(right, state)
                : DoorLeaf.fromRight(right).legacyMetadata;
        ItemStack leaf = new ItemStack(placedDoor, 1, metadata);
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();

        int combined = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (combined % 65536), (float) (combined / 65536));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        orientDetailedDoor(facing);
        moveDetailedDoorLeaf(visualDoor, right, progress);
        // RenderItem internally shifts baked coordinates by -0.5 on every
        // axis; cancel that so model [0..16]/16 begins at the block origin.
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        // The public transform entry point also binds/restores the block
        // atlas and GL color state. Calling the lower-level baked-model
        // method leaked the door atlas into later TESRs, turning screens dark.
        // Item rendering otherwise applies inventory-style directional GL
        // lighting on top of the world lightmap, making the animated leaf
        // visibly darker than its shade=false baked frame.
        GlStateManager.disableLighting();
        if (placedDoor instanceof com.vandorlabs.blocks.BlockSpaceDoor
                && net.minecraftforge.client.MinecraftForgeClient.getRenderPass() == 1) {
            // The normal item entry point forces alpha >= 0.1 and loses the
            // pack's faint reflections. Draw the isolated glass in pass 1.
            ItemStack glass = new ItemStack(placedDoor, 1, metadata + 4);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003F);
            GlStateManager.depthMask(false);
            renderer.renderItem(glass, renderer.getItemModelWithOverrides(glass, te.getWorld(), null));
            GlStateManager.depthMask(true);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.disableBlend();
        } else {
            renderer.renderItem(leaf, ItemCameraTransforms.TransformType.NONE);
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void renderSpaceDoor(com.vandorlabs.tiles.TileEntitySpaceDoor tile,IBlockState state,
            EnumFacing facing,float progress,double x,double y,double z) {
        boolean sliding=tile.isSliding();
        BlockDetailedDoor motion=tile.model(sliding).getVisualModel(state);
        boolean paired=state.getValue(BlockConnectingDetailedDoor.PAIRED);
        boolean right=state.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.LEFT;
        boolean glass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass()==1;
        if (glass && !tile.hasGlass()) return;
        RenderItem renderer=Minecraft.getMinecraft().getRenderItem();
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light%65536,light/65536);
        for (int part=glass?2:0;part<=(glass?2:1);part++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x,y,z);
            orientDetailedDoor(facing);
            GlStateManager.translate(0,0,tile.positionOffset());
            if (part!=0) {
                if (sliding && tile.getSlideDirection()!=0)
                    GlStateManager.translate(0,tile.verticalTravel()*progress,0);
                else moveDetailedDoorLeaf(motion,right,progress);
            }
            GlStateManager.translate(.5,.5,.5);
            GlStateManager.disableLighting();
            ItemStack item=new ItemStack(state.getBlock(),1,tile.metadata(paired,right,part));
            if (glass) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.color(1,1,1,1);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
                GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
                GlStateManager.depthMask(false);
                renderer.renderItem(item,renderer.getItemModelWithOverrides(item,tile.getWorld(),null));
                GlStateManager.depthMask(true);
                GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
                GlStateManager.disableBlend();
            } else renderer.renderItem(item,ItemCameraTransforms.TransformType.NONE);
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static void orientDetailedDoor(EnumFacing facing) {
        float degrees;
        switch (facing) {
            case WEST: degrees = -90.0F; break;
            case NORTH: degrees = 180.0F; break;
            case EAST: degrees = 90.0F; break;
            case SOUTH:
            default: degrees = 0.0F;
        }
        GlStateManager.translate(0.5F, 0.0F, 0.5F);
        GlStateManager.rotate(degrees, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-0.5F, 0.0F, -0.5F);
    }

    private static void moveDetailedDoorLeaf(BlockDetailedDoor door, boolean right,
            float progress) {
        DoorLeafTransform transform=DoorLeafTransform.calculate(door.isSlidingModel(),
                door.getSlide(right),door.getPivot(right),door.getPivotZ(),door.getAngle(right),progress);
        if (door.isSlidingModel()) {
            GlStateManager.translate(transform.translateX,0,0);
        } else {
            GlStateManager.translate(transform.pivotX,0,transform.pivotZ);
            GlStateManager.rotate((float)transform.angleDegrees,0,1,0);
            GlStateManager.translate(-transform.pivotX,0,-transform.pivotZ);
        }
    }

    /** One leaf/panel; u0/u1 are texture pixel columns (0-16 space). On the
     * north broad face u0 paint sits EAST (a base orientation, exactly what
     * the static models drew); the south face is UV-mirrored so front and
     * back present identically. Sliding panels pass u0/u1 already flipped
     * when the approved art for that rest side is mirrored, so this helper
     * has no further mirroring knob to flip by accident. */
    private static void drawPanel(BufferBuilder buf, TextureAtlasSprite sprite,
            TextureAtlasSprite wall, WindowOpening window,
            float x0, float width, float u0, float u1, float z0, float z1) {
        float x1 = x0 + width;
        float uA = sprite.getInterpolatedU(u0);
        float uB = sprite.getInterpolatedU(u1);
        float vA = sprite.getInterpolatedV(0.0);
        float vB = sprite.getInterpolatedV(16.0);
        // North broad face (u0 paint sits east, matching the static models).
        quad(buf, x1, 16, z0, x1, 0, z0, x0, 0, z0, x0, 16, z0, uA, vA, uA, vB, uB, vB, uB, vA);
        // South broad face, UV-mirrored so front and back present identically.
        quad(buf, x0, 16, z1, x0, 0, z1, x1, 0, z1, x1, 16, z1, uB, vA, uB, vB, uA, vB, uA, vA);
        if (window != null) {
            drawWindowJambs(buf, wall, window, x0, x1, u0, u1, z0, z1);
        }
    }

    /** Wall-panel-lined thickness around a transparent upper-door opening. */
    private static void drawWindowJambs(BufferBuilder buf, TextureAtlasSprite wall,
            WindowOpening opening, float x0, float x1, float u0, float u1,
            float z0, float z1) {
        float xa = xAtU(opening.u0, x0, x1, u0, u1);
        float xb = xAtU(opening.u1, x0, x1, u0, u1);
        float left = Math.min(xa, xb);
        float right = Math.max(xa, xb);
        float top = 16.0F - opening.v0;
        float bottom = 16.0F - opening.v1;
        float ua = wall.getInterpolatedU(0.0F);
        float ub = wall.getInterpolatedU(16.0F);
        float va = wall.getInterpolatedV(0.0F);
        float vb = wall.getInterpolatedV(16.0F);
        // Left/right jambs, then sill/header. Culling is disabled for the
        // whole TESR, so each inner surface is visible from either side.
        quad(buf, left, top, z0, left, bottom, z0, left, bottom, z1, left, top, z1,
                ua, va, ua, vb, ub, vb, ub, va);
        quad(buf, right, top, z1, right, bottom, z1, right, bottom, z0, right, top, z0,
                ua, va, ua, vb, ub, vb, ub, va);
        quad(buf, left, top, z1, right, top, z1, right, top, z0, left, top, z0,
                ua, va, ub, va, ub, vb, ua, vb);
        quad(buf, left, bottom, z0, right, bottom, z0, right, bottom, z1, left, bottom, z1,
                ua, va, ub, va, ub, vb, ua, vb);
    }

    private static float xAtU(float u, float x0, float x1, float u0, float u1) {
        return x1 + (x0 - x1) * (u - u0) / (u1 - u0);
    }

    /** A leaf drawn closed then GL-swung about pivot (pcx, pcz) by angle
     * (0-16 space, eased pose already folded in). u0/u1 select the paint
     * columns the same way drawPanel does; rotated leaves get the right
     * doorway-facing read by the rotation DIRECTION, so callers pass plain
     * base u0/u1 and no mirror flag. */
    private static void drawSwungLeaf(BufferBuilder buf, TextureAtlasSprite sprite,
            TextureAtlasSprite wall, WindowOpening window,
            float x0, float width, float u0, float u1, float z0, float z1,
            float angle, float pcx, float pcz) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(pcx, 0.0F, pcz);
        GlStateManager.rotate(angle, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-pcx, 0.0F, -pcz);
        Tessellator tess = Tessellator.getInstance();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        drawPanel(buf, sprite, wall, window, x0, width, u0, u1, z0, z1);
        tess.draw();
        GlStateManager.disableTexture2D();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        drawEdges(buf, x0, width, z0, z1);
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void quad(BufferBuilder buf,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        buf.pos(x0, y0, z0).tex(u0, v0).endVertex();
        buf.pos(x1, y1, z1).tex(u1, v1).endVertex();
        buf.pos(x2, y2, z2).tex(u2, v2).endVertex();
        buf.pos(x3, y3, z3).tex(u3, v3).endVertex();
    }

    private static void drawEdges(BufferBuilder buf, float x0, float width, float z0, float z1) {
        float x1 = x0 + width;
        float r = 0.09F, g = 0.10F, b = 0.12F, a = 1.0F;
        // East and west rims.
        for (float ex : new float[] { x0, x1 }) {
            buf.pos(ex, 0, z0).color(r, g, b, a).endVertex();
            buf.pos(ex, 16, z0).color(r, g, b, a).endVertex();
            buf.pos(ex, 16, z1).color(r, g, b, a).endVertex();
            buf.pos(ex, 0, z1).color(r, g, b, a).endVertex();
        }
        // Top and bottom rims.
        for (float ey : new float[] { 0.0F, 16.0F }) {
            buf.pos(x0, ey, z0).color(r, g, b, a).endVertex();
            buf.pos(x0, ey, z1).color(r, g, b, a).endVertex();
            buf.pos(x1, ey, z1).color(r, g, b, a).endVertex();
            buf.pos(x1, ey, z0).color(r, g, b, a).endVertex();
        }
    }
}
