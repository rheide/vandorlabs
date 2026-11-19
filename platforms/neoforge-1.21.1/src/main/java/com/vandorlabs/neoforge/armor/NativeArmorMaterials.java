package com.vandorlabs.neoforge.armor;

import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.armor.ArmorMaterialSkin;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.client.NativeBlockMaterials;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.tiles.MaterialChoicePolicy;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final int LIMIT = 64, SAMPLE_LIMIT = 32;
    private static final Int2ObjectLinkedOpenHashMap<ResourceLocation> CACHE = new Int2ObjectLinkedOpenHashMap<>();
    private record SampleTexture(String sprite, boolean inner) {}
    private static final Map<SampleTexture, ResourceLocation> SAMPLES = new LinkedHashMap<>(16, .75F, true);
    private static final Map<String, ModernFinishCatalog.Entry> BY_SPRITE = bySprite();

    static ResourceLocation worn(int choice, ResourceLocation maskLocation, ResourceLocation fallback) {
        if (choice < 0 || choice >= MaterialChoicePolicy.FILESYSTEM_BASE) return fallback;
        boolean inner = maskLocation.getPath().endsWith("_layer_2.png");
        int key = (choice << 1) | (inner ? 1 : 0);
        var previous = CACHE.getAndMoveToLast(key);
        if (previous != null) return previous;
        if (CACHE.size() >= LIMIT * 2) return fallback;

        var material = material(choice);
        var path = ResourceLocation.fromNamespaceAndPath("vandorlabs", "armor_material/" + key);
        if (material == null || !bake(material, maskLocation, path)) return fallback;
        CACHE.put(key, path);
        return path;
    }

    static ResourceLocation wornSample(String sprite, ResourceLocation maskLocation, ResourceLocation fallback) {
        boolean inner = maskLocation.getPath().endsWith("_layer_2.png");
        var key = new SampleTexture(sprite, inner);
        var previous = SAMPLES.get(key);
        if (previous != null) return previous;
        if (SAMPLES.size() >= SAMPLE_LIMIT * 2) return fallback;
        var material = sampleMaterial(sprite);
        if (material == null) return fallback;
        var id = ResourceLocation.parse(sprite);
        var path = ResourceLocation.fromNamespaceAndPath("vandorlabs",
            "armor_sample/" + (inner ? "inner/" : "outer/") + id.getNamespace() + "/" + id.getPath());
        if (!bake(material, maskLocation, path)) return fallback;
        SAMPLES.put(key, path);
        return path;
    }

    private static boolean bake(ModernFinishCatalog.Entry material, ResourceLocation maskLocation, ResourceLocation path) {
        var game = Minecraft.getInstance();
        var sprite = game.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(material.texture()));
        if (sprite.contents().name().getPath().equals("missingno")) return false;
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
            return true;
        } catch (IOException missingMask) {
            return false;
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

    static ModernFinishCatalog.Entry sampleMaterial(String sprite) {
        var catalog = BY_SPRITE.get(sprite);
        if (catalog != null) return catalog;
        var id = NativeArmorWorldSampling.sprite(sprite);
        if (id == null) return null;
        var texture = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
        if (!texture.contents().name().equals(id)) return null;
        return new ModernFinishCatalog.Entry(sprite, sprite, "Sampled", true, false, -1, sprite, 1, 1);
    }

    private static Map<String, ModernFinishCatalog.Entry> bySprite() {
        var result = new HashMap<String, ModernFinishCatalog.Entry>();
        for (var entry : ModernFinishCatalog.ENTRIES) result.putIfAbsent(entry.texture(), entry);
        return Map.copyOf(result);
    }

    private static int width(ModernFinishCatalog.Entry entry, TextureAtlasSprite sprite) {
        return Math.max(1, Math.round(sprite.contents().width() * entry.u()));
    }

    private static int height(ModernFinishCatalog.Entry entry, TextureAtlasSprite sprite) {
        return Math.max(1, Math.round(sprite.contents().height() * entry.v()));
    }

    @SubscribeEvent
    public static void trim(RenderFrameEvent.Post event) {
        if (CACHE.size() > LIMIT || SAMPLES.size() > SAMPLE_LIMIT) releaseUntil(LIMIT, SAMPLE_LIMIT);
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    static void clear() {
        releaseUntil(0, 0);
    }

    private static void releaseUntil(int limit, int sampleLimit) {
        var textures = Minecraft.getInstance().getTextureManager();
        while (CACHE.size() > limit) textures.release(CACHE.removeFirst());
        var samples = SAMPLES.values().iterator();
        while (SAMPLES.size() > sampleLimit) {
            textures.release(samples.next());
            samples.remove();
        }
    }

    private NativeArmorMaterials() {}
}
