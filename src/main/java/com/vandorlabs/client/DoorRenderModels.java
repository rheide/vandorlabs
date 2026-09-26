package com.vandorlabs.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Models are stable between resource reloads; motion and world lighting remain live. */
public final class DoorRenderModels {
    private static final int LIMIT = 1024;
    private static final Map<Key, Entry> MODELS = new LinkedHashMap<>(64, .75F, true);

    private DoorRenderModels() { }

    static Entry get(Block block, int metadata) {
        Key key = new Key(block, metadata);
        Entry cached = MODELS.get(key);
        if (cached != null) return cached;
        ItemStack stack = new ItemStack(block, 1, metadata);
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();
        IBakedModel model = renderer.getItemModelWithOverrides(stack,
                Minecraft.getMinecraft().world, null);
        Entry entry = new Entry(stack, model);
        MODELS.put(key, entry);
        if (MODELS.size() > LIMIT) MODELS.remove(MODELS.keySet().iterator().next());
        return entry;
    }

    public static void clear() { MODELS.clear(); }

    static final class Entry {
        final ItemStack stack;
        final IBakedModel model;
        Entry(ItemStack stack, IBakedModel model) { this.stack = stack; this.model = model; }
    }

    private static final class Key {
        final Block block;
        final int metadata;
        Key(Block block, int metadata) { this.block = block; this.metadata = metadata; }
        @Override public int hashCode() { return 31 * System.identityHashCode(block) + metadata; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key)other;
            return block == key.block && metadata == key.metadata;
        }
    }
}
