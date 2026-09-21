package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Stitches the pack's native 1:2 artwork without treating it as animation frames. */
public final class SpaceDoorTextures {
    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        for (String level : new String[]{"","low/","medium/","high/"}) {
        for (String name : new String[] {"airlock", "observation", "observation_metal",
                "observation_glass", "standard", "security", "reactor", "door_viewport", "door_laboratory",
                "door_cargo","door_ventilation","door_viewport_metal","door_viewport_glass",
                "door_laboratory_metal","door_laboratory_glass","lift_cargo_lift","lift_blast_shield",
                "lift_glazed_hangar","lift_glazed_hangar_metal","lift_glazed_hangar_glass",
                "lift_quarantine_seal","lift_reactor_barrier","lift_modular_shutter"}) {
            event.getMap().setTextureEntry(new RectangularSprite(
                    "vandorlabs:blocks/space_doors/" + level + name));
        }
        }
    }

    private static final class RectangularSprite extends TextureAtlasSprite {
        private float usedU = 1, usedV = 1;
        RectangularSprite(String name) { super(name); }

        @Override public float getMaxU() {
            return getMinU() + (super.getMaxU() - getMinU()) * usedU;
        }
        @Override public float getMaxV() {
            return getMinV() + (super.getMaxV() - getMinV()) * usedV;
        }
        @Override public float getInterpolatedU(double u) {
            return getMinU() + (getMaxU() - getMinU()) * (float) u / 16;
        }
        @Override public float getInterpolatedV(double v) {
            return getMinV() + (getMaxV() - getMinV()) * (float) v / 16;
        }
        @Override public float getUnInterpolatedU(float u) {
            return (u - getMinU()) / (getMaxU() - getMinU()) * 16;
        }
        @Override public float getUnInterpolatedV(float v) {
            return (v - getMinV()) / (getMaxV() - getMinV()) * 16;
        }

        @Override
        public void generateMipmaps(int levels) {
            int[][] pixels = Arrays.copyOf(getFrameTextureData(0), levels + 1);
            if (!getIconName().endsWith("_glass")) {
                setFramesTextureData(Collections.singletonList(pixels));
                super.generateMipmaps(levels);
                return;
            }
            // Vanilla's cutout mipmaps discard alpha below 96. This pack's
            // translucent reflections never exceed 51, so preserve their alpha.
            for (int level = 1; level <= levels; level++) {
                int width = getIconWidth() >> level;
                int height = getIconHeight() >> level;
                int[] previous = pixels[level - 1];
                pixels[level] = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int a = 0, r = 0, g = 0, b = 0;
                        for (int dy = 0; dy < 2; dy++) {
                            for (int dx = 0; dx < 2; dx++) {
                                int c = previous[(y * 2 + dy) * (width * 2) + x * 2 + dx];
                                int alpha = c >>> 24;
                                a += alpha;
                                r += ((c >> 16) & 255) * alpha;
                                g += ((c >> 8) & 255) * alpha;
                                b += (c & 255) * alpha;
                            }
                        }
                        pixels[level][y * width + x] = a == 0 ? 0
                                : (a / 4 << 24) | (r / a << 16) | (g / a << 8) | b / a;
                    }
                }
            }
            setFramesTextureData(Collections.singletonList(pixels));
        }

        @Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
            return true;
        }

        @Override
        public boolean load(IResourceManager manager, ResourceLocation location,
                Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
            try (IResource resource = manager.getResource(location)) {
                BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());
                // 1.12's stitcher may rotate rectangular slots but its uploader
                // does not rotate pixels. Reserve a square slot and expose only
                // the native image region through UV accessors. No resampling.
                int size = Math.max(image.getWidth(), image.getHeight());
                setIconWidth(size);
                setIconHeight(size);
                usedU = (float) image.getWidth() / size;
                usedV = (float) image.getHeight() / size;
                int[][] pixels = new int[1][];
                pixels[0] = new int[size * size];
                // Extend edge texels into unused padding to avoid mipmap bleed.
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                    pixels[0][y * size + x] = image.getRGB(
                            Math.min(x, image.getWidth()-1), Math.min(y, image.getHeight()-1));
                }
                setFramesTextureData(Collections.singletonList(pixels));
                // Forge 1.12's caller stitches custom sprites when load returns false.
                return false;
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load Space door texture " + location, exception);
            }
        }
    }
}
