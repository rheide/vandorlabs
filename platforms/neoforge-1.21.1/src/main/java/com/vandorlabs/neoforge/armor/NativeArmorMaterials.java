package com.vandorlabs.neoforge.armor;

import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.armor.ArmorMaterialSkin;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.client.NativeBlockMaterials;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.tiles.MaterialChoicePolicy;
import java.io.IOException;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeArmorMaterials {
    // Each derived 256-by-128 skin owns 128 KiB of CPU pixels and a GPU texture.
    private static final int LIMIT = 64;
    private static final Int2ObjectLinkedOpenHashMap<ResourceLocation> CACHE = new Int2ObjectLinkedOpenHashMap<>();

    static ResourceLocation worn(int choice, ResourceLocation maskLocation, ResourceLocation fallback) {
        if (choice < 0 || choice >= MaterialChoicePolicy.FILESYSTEM_BASE) return fallback;
        boolean inner = maskLocation.getPath().endsWith("_layer_2.png");
        int key = (choice << 1) | (inner ? 1 : 0);
        var previous = CACHE.getAndMoveToLast(key);
        if (previous != null) return previous;
        if (CACHE.size() >= LIMIT * 2) return fallback;

        var material = material(choice);
        if (material == null) return fallback;
        var game = Minecraft.getInstance();
        var sprite = game.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(material.texture()));
        if (sprite.contents().name().getPath().equals("missingno")) return fallback;
        var path = ResourceLocation.fromNamespaceAndPath("vandorlabs", "armor_material/" + key);
        try (var input = game.getResourceManager().open(maskLocation);
             var mask = NativeImage.read(input)) {
            int width = width(material, sprite), height = height(material, sprite);
            int frame = sprite.contents().getUniqueFrames().findFirst().orElse(0);
            int[] baked = ArmorMaterialSkin.bake((x, y) -> sprite.getPixelRGBA(frame, x, y),
                width, height, mask::getPixelRGBA, mask.getWidth(), mask.getHeight());
            var image = new NativeImage(ArmorMaterialSkin.WIDTH, ArmorMaterialSkin.HEIGHT, false);
            for (int y = 0; y < ArmorMaterialSkin.HEIGHT; y++) {
                for (int x = 0; x < ArmorMaterialSkin.WIDTH; x++) {
                    image.setPixelRGBA(x, y, baked[y * ArmorMaterialSkin.WIDTH + x]);
                }
            }
            game.getTextureManager().register(path, new DynamicTexture(image));
            CACHE.put(key, path);
            return path;
        } catch (IOException missingMask) {
            return fallback;
        }
    }

    static ModernFinishCatalog.Entry material(int choice) {
        if (choice >= 0 && choice < ModernFinishCatalog.ENTRIES.size()) {
            var material = ModernFinishCatalog.option(choice);
            if (!material.visible()) return null;
            return material.door() == null ? material : NativeDoorAssets.preview(choice);
        }
        var sampled = NativeBlockMaterials.material(choice);
        return sampled == null ? null : sampled.entry();
    }

    private static int width(ModernFinishCatalog.Entry entry, TextureAtlasSprite sprite) {
        return Math.max(1, Math.round(sprite.contents().width() * entry.u()));
    }

    private static int height(ModernFinishCatalog.Entry entry, TextureAtlasSprite sprite) {
        return Math.max(1, Math.round(sprite.contents().height() * entry.v()));
    }

    @SubscribeEvent
    public static void trim(RenderFrameEvent.Post event) {
        if (CACHE.size() > LIMIT) releaseUntil(LIMIT);
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    static void clear() {
        releaseUntil(0);
    }

    private static void releaseUntil(int limit) {
        var textures = Minecraft.getInstance().getTextureManager();
        while (CACHE.size() > limit) textures.release(CACHE.removeFirst());
    }

    private NativeArmorMaterials() {}
}
