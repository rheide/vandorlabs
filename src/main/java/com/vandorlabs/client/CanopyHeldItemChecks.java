package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Actual item rendering for the plain Canopy Glass held-size regression. */
final class CanopyHeldItemChecks {
    static void run(Minecraft mc, File output) throws Exception {
        ItemStack reference = new ItemStack(Blocks.STONE);
        IBakedModel stone = mc.getRenderItem().getItemModelWithOverrides(reference, mc.world, mc.player);
        int cases = 0;
        for (boolean configured : new boolean[]{false, true}) {
            ItemStack item = new ItemStack(Block.getBlockFromName("vandorlabs:regular_canopy_glass"));
            if (configured) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("CanopyShade", 8);
                item.setTagCompound(tag);
            }
            IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(item, mc.world, mc.player);
            for (ItemCameraTransforms.TransformType type : new ItemCameraTransforms.TransformType[]{
                    ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,
                    ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
                    ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,
                    ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND,
                    ItemCameraTransforms.TransformType.GUI}) {
                ItemTransformVec3f actual = model.getItemCameraTransforms().getTransform(type);
                if (type != ItemCameraTransforms.TransformType.GUI) {
                    ItemTransformVec3f expected = stone.getItemCameraTransforms().getTransform(type);
                    require(actual.rotation.equals(expected.rotation)
                            && actual.translation.equals(expected.translation)
                            && actual.scale.equals(expected.scale), "normal block held transform " + type);
                }
                BufferedImage frame = SurfaceClientChecks.itemFrame(mc, item, type);
                int minX = 64, minY = 64, maxX = -1, maxY = -1, ink = 0;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    if ((frame.getRGB(x, y) & 0xffffff) != 0x203040) {
                        minX = Math.min(minX, x); minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); ink++;
                    }
                }
                require(ink > 20 && minX > 0 && minY > 0 && maxX < 63 && maxY < 63,
                        "visible, unclipped item " + type + " configured=" + configured);
                ImageIO.write(frame, "png", new File(output, "canopy-glass-item-"
                        + (configured ? "configured-" : "default-") + type.name() + ".png"));
                cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] canopy-glass-held-size PASS rendered=" + cases
                + " normal block first/third both hands and padded GUI");
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Canopy Glass item: " + message);
    }
    private CanopyHeldItemChecks() {}
}
