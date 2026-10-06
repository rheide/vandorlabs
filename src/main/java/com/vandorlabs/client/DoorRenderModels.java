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

    public static void clear() { MODELS.clear(); SelectedDoorFaceCache.clear(); XDoorMeshes.clear(); OpaqueDoorBatch.clear(); }
    static void checkPreparedDrawStates(){DoorDrawRuntimeChecks.run(new java.util.ArrayList<>(MODELS.values()));}

    static final class Entry {
        final ItemStack stack;
        final IBakedModel model;
        private SelectedDoorGeometry selected;
        private DoorQuadPlan plan,selectedPlan;
        private boolean planChecked,selectedPlanChecked;
        private OpaqueDoorBatch.Mesh batchMesh;
        private boolean batchChecked;
        OpaqueDoorBatch.Mesh batchMesh() {
            if (!batchChecked) { batchMesh=OpaqueDoorBatch.prepare(this); batchChecked=true; }
            return batchMesh;
        }
        DoorQuadPlan plan(IBakedModel current) {
            if(current==model){if(!planChecked){plan=DoorQuadPlan.prepare(current);planChecked=true;}return plan;}
            if(current==selected){if(!selectedPlanChecked){selectedPlan=DoorQuadPlan.prepare(current);selectedPlanChecked=true;}return selectedPlan;}
            return null;
        }
        SelectedDoorGeometry selected(){if(selected==null)selected=new SelectedDoorGeometry(model);return selected;}
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
