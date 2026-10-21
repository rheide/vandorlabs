package com.vandorlabs.client.cockpits;

import java.nio.FloatBuffer;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Client-only reference renderer. All OBJ and rig transform units are BLOCKS. */
@SideOnly(Side.CLIENT)
public final class CockpitRenderer {
    private CockpitRenderer() {}

    /** Each callback result is a position from 0 to 3, including fractions. */
    public static void renderAnimated(String id, double x, double y, double z,
            EnumFacing facing, int packedLight, ToDoubleFunction<String> detents) {
        renderAnimated(id,x,y,z,facing,packedLight,detents,-1);
    }
    public static void renderAnimated(String id, double x, double y, double z,
            EnumFacing facing, int packedLight, ToDoubleFunction<String> detents,int sideTexture) {
        if (!CockpitModels.ready()) return;
        CockpitModels.Rig rig = CockpitModels.rig(id);
        atAnchor(x, y, z, facing, packedLight, () -> {
            Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer()
                .renderModelBrightnessColor(CockpitModels.baked(rig.staticResource,sideTexture),1,1,1,1);
            for (CockpitModels.Part part : rig.parts) {
                double value = CockpitMotion.sample(part.values, detents.applyAsDouble(part.id));
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(part.pivot[0], part.pivot[1], part.pivot[2]);
                    if ("rotation".equals(part.type)) {
                        GlStateManager.rotate((float) value, (float) part.axis[0],
                            (float) part.axis[1], (float) part.axis[2]);
                    } else {
                        GlStateManager.translate(part.axis[0] * value,
                            part.axis[1] * value, part.axis[2] * value);
                    }
                    // Part vertices are already local to their pivot; do NOT translate -pivot.
                    draw(part.modelResource);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        });
    }

    /** Static complete assembly. Snapshots already include every moving part. */
    public static void renderPose(String id, String pose, double x, double y, double z,
            EnumFacing facing, int packedLight) {
        if (!CockpitModels.ready()) return;
        ResourceLocation model = CockpitModels.rig(id).poseResources.get(pose);
        if (model == null) throw new IllegalArgumentException("Unknown pose " + id + "/" + pose);
        atAnchor(x, y, z, facing, packedLight, () -> draw(model));
    }

    private static void draw(ResourceLocation location) {
        Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer()
            .renderModelBrightnessColor(CockpitModels.baked(location), 1, 1, 1, 1);
    }

    private static float yaw(EnumFacing facing) {
        switch (facing) {
            case NORTH: return 0;
            case EAST: return -90;
            case SOUTH: return 180;
            case WEST: return 90;
            default: throw new IllegalArgumentException("Cockpit facing must be horizontal");
        }
    }

    /** Preserves the GL states this helper changes; models are opaque. */
    private static void atAnchor(double x, double y, double z, EnumFacing facing,
            int packedLight, Runnable geometry) {
        float angle = yaw(facing);
        float oldLightX = OpenGlHelper.lastBrightnessX;
        float oldLightY = OpenGlHelper.lastBrightnessY;
        boolean oldLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        FloatBuffer oldColor = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, oldColor);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        boolean oldTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y, z + 0.5);
            GlStateManager.rotate(angle, 0, 1, 0);
            GlStateManager.translate(-0.5, 0, -0.5);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.color(1, 1, 1, 1);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                packedLight & 65535, (packedLight >>> 16) & 65535);
            geometry.run();
        } finally {
            GlStateManager.popMatrix();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
            GlStateManager.bindTexture(oldTexture);
            if (oldTexture2D) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(oldActiveTexture);
            if (oldLighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (oldCull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            GlStateManager.color(oldColor.get(0), oldColor.get(1), oldColor.get(2), oldColor.get(3));
        }
    }

    /**
     * World-space render bounds for a controller tile. Uses every X/Z corner,
     * because simply offsetting north-facing bounds is wrong after rotation.
     */
    public static AxisAlignedBB worldBounds(String id, BlockPos anchor, EnumFacing facing) {
        if (!CockpitModels.ready()) {
            // Conservative only during resource loading; exact bounds follow after baking.
            return new AxisAlignedBB(anchor.add(-2, 0, -2), anchor.add(3, 2, 3));
        }
        double[] b = CockpitModels.rig(id).render_bounds_blocks;
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[] {b[0], b[3]}) {
            for (double z : new double[] {b[2], b[5]}) {
                double dx = x - 0.5, dz = z - 0.5;
                double rx, rz;
                switch (facing) {
                    case NORTH: rx = dx; rz = dz; break;
                    case EAST: rx = -dz; rz = dx; break;
                    case SOUTH: rx = -dx; rz = -dz; break;
                    case WEST: rx = dz; rz = -dx; break;
                    default: throw new IllegalArgumentException("Cockpit facing must be horizontal");
                }
                minX = Math.min(minX, rx + 0.5); maxX = Math.max(maxX, rx + 0.5);
                minZ = Math.min(minZ, rz + 0.5); maxZ = Math.max(maxZ, rz + 0.5);
            }
        }
        return new AxisAlignedBB(minX, b[1], minZ, maxX, b[4], maxZ).offset(anchor);
    }
}
