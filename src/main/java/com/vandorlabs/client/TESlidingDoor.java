package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.BlockDetailedDoor;
import com.vandorlabs.blocks.BlockConnectingDetailedDoor;
import com.vandorlabs.animation.DoorAnimation;
import com.vandorlabs.render.DoorLeafTransform;
import com.vandorlabs.render.DoorLeaf;
import com.vandorlabs.render.SpaceDoorControlPanel;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;

/** Animated Space Door leaves and fixed control panels. */
@SideOnly(Side.CLIENT)
public class TESlidingDoor extends TileEntitySpecialRenderer<TileEntitySlidingDoor> {

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
        if (!glass) {
            SpaceDoorControlPanel.Side side=com.vandorlabs.blocks.BlockConfigurableSpaceDoor
                    .panelSide(tile.getWorld(),tile.getPos(),state);
            if (side!=SpaceDoorControlPanel.Side.NONE)
                renderSpaceDoorControlPanel(tile,facing,side,x,y,z);
        }
    }

    private static void renderSpaceDoorControlPanel(com.vandorlabs.tiles.TileEntitySpaceDoor tile,
            EnumFacing facing,SpaceDoorControlPanel.Side side,double x,double y,double z) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        orientDetailedDoor(facing);
        GlStateManager.translate(0,0,tile.positionOffset());
        GlStateManager.scale(1F/16,1F/16,1F/16);
        GlStateManager.disableLighting();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite buttons=Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/control_buttons");
        TextureAtlasSprite wall=Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        float x0=(float)SpaceDoorControlPanel.x0(side),x1=(float)SpaceDoorControlPanel.x1(side);
        float y0=(float)SpaceDoorControlPanel.Y0,y1=(float)SpaceDoorControlPanel.Y1;
        boolean farEdge=tile.getPlacementDepth()==2;
        float z0=(float)SpaceDoorControlPanel.z0(tile.isSliding(),farEdge),
                z1=(float)SpaceDoorControlPanel.z1(tile.isSliding(),farEdge);
        float u0=buttons.getInterpolatedU(0),u1=buttons.getInterpolatedU(16);
        float v0=buttons.getInterpolatedV(0),v1=buttons.getInterpolatedV(16);
        // Match the Programmable Console's deck-side housing: U follows
        // depth and V follows the local height, using the native atlas pixels.
        // Keep UVs in the original sprite even when the far-edge pad's local
        // model coordinates extend beyond 16 before the placement offset.
        double textureZ0=SpaceDoorControlPanel.z0(tile.isSliding()),
                textureZ1=SpaceDoorControlPanel.z1(tile.isSliding());
        float sideU0=wall.getInterpolatedU(textureZ0),sideU1=wall.getInterpolatedU(textureZ1);
        float sideV0=wall.getInterpolatedV(32-y1),sideV1=wall.getInterpolatedV(32-y0);
        float topV0=wall.getInterpolatedV(textureZ0),topV1=wall.getInterpolatedV(textureZ1);
        float wallX0=wall.getInterpolatedU(x0),wallX1=wall.getInterpolatedU(x1);
        BufferBuilder buf=Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
        if (side==SpaceDoorControlPanel.Side.LEFT) {
            quad(buf,x1,y1,z1,x1,y0,z1,x1,y0,z0,x1,y1,z0,
                    u0,v0,u0,v1,u1,v1,u1,v0);
            quad(buf,x0,y1,z0,x0,y0,z0,x0,y0,z1,x0,y1,z1,
                    sideU0,sideV0,sideU0,sideV1,sideU1,sideV1,sideU1,sideV0);
        } else {
            quad(buf,x0,y1,z0,x0,y0,z0,x0,y0,z1,x0,y1,z1,
                    u0,v0,u0,v1,u1,v1,u1,v0);
            quad(buf,x1,y1,z1,x1,y0,z1,x1,y0,z0,x1,y1,z0,
                    sideU1,sideV0,sideU1,sideV1,sideU0,sideV1,sideU0,sideV0);
        }
        quad(buf,x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0,
                wallX0,topV0,wallX0,topV1,wallX1,topV1,wallX1,topV0);
        quad(buf,x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1,
                wallX0,topV1,wallX0,topV0,wallX1,topV0,wallX1,topV1);
        quad(buf,x0,y1,z1,x0,y0,z1,x1,y0,z1,x1,y1,z1,
                wallX0,sideV0,wallX0,sideV1,wallX1,sideV1,wallX1,sideV0);
        quad(buf,x1,y1,z0,x1,y0,z0,x0,y0,z0,x0,y1,z0,
                wallX1,sideV0,wallX1,sideV1,wallX0,sideV1,wallX0,sideV0);
        Tessellator.getInstance().draw();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
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

    private static void quad(BufferBuilder buf,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        buf.pos(x0, y0, z0).tex(u0, v0).endVertex();
        buf.pos(x1, y1, z1).tex(u1, v1).endVertex();
        buf.pos(x2, y2, z2).tex(u2, v2).endVertex();
        buf.pos(x3, y3, z3).tex(u3, v3).endVertex();
    }

}
