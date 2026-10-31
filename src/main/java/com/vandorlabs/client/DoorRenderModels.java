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
    private static final Map<Block,it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Entry>> BY_BLOCK=new java.util.IdentityHashMap<>();

    private DoorRenderModels() { }

    static Entry get(Block block, int metadata) {
        it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Entry> entries=BY_BLOCK.get(block);
        Entry cached=entries==null?null:entries.get(metadata);
        if(cached!=null){MODELS.get(cached.key);return cached;}
        Key key = new Key(block, metadata);
        ItemStack stack = new ItemStack(block, 1, metadata);
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();
        IBakedModel model = renderer.getItemModelWithOverrides(stack,
                Minecraft.getMinecraft().world, null);
        Entry entry = new Entry(stack, model,key);
        MODELS.put(key, entry);
        if(entries==null){entries=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();BY_BLOCK.put(block,entries);}entries.put(metadata,entry);
        if(MODELS.size()>LIMIT){Key old=MODELS.keySet().iterator().next();MODELS.remove(old);it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Entry> group=BY_BLOCK.get(old.block);group.remove(old.metadata);if(group.isEmpty())BY_BLOCK.remove(old.block);}
        return entry;
    }

    public static void clear() { FrameDoorModel.clear();DoorGlassModel.clear();MODELS.clear(); BY_BLOCK.clear(); SelectedDoorFaceCache.clear(); XDoorMeshes.clear(); OpaqueDoorBatch.clear(); }
    static void checkPreparedDrawStates(){DoorDrawRuntimeChecks.run(new java.util.ArrayList<>(MODELS.values()));}

    static final class Entry {
        private final Key key;
        final ItemStack stack;
        final IBakedModel model;
        private SelectedDoorGeometry selected;
        private java.lang.ref.WeakReference<IBakedModel> lastFrame;
        private int frameFront,frameInterior;private double frameScale;
        IBakedModel framed(com.vandorlabs.tiles.FrameAppearance a,double scale){
            if(a.front<0&&a.interior<0)return model;
            if(lastFrame!=null&&frameFront==a.front&&frameInterior==a.interior&&frameScale==scale){IBakedModel cached=lastFrame.get();if(cached!=null)return cached;}
            IBakedModel result=FrameDoorModel.get(model,a,scale);frameFront=a.front;frameInterior=a.interior;frameScale=scale;lastFrame=new java.lang.ref.WeakReference<>(result);return result;
        }
        private DoorQuadPlan plan,selectedPlan;
        private boolean planChecked,selectedPlanChecked;
        private OpaqueDoorBatch.Mesh batchMesh;
        private boolean batchChecked;
        OpaqueDoorBatch.Mesh batchMesh() {
            if (!batchChecked) { batchMesh=OpaqueDoorBatch.prepare(this); batchChecked=true; }
            return batchMesh;
        }
        DoorQuadPlan plan(IBakedModel current) {
            if(current instanceof DoorGlassModel)return ((DoorGlassModel)current).plan();
            if(current instanceof FrameDoorModel)return ((FrameDoorModel)current).plan();
            if(current==model){if(!planChecked){plan=DoorQuadPlan.prepare(current);planChecked=true;}return plan;}
            if(current==selected){if(!selectedPlanChecked){selectedPlan=DoorQuadPlan.prepare(current);selectedPlanChecked=true;}return selectedPlan;}
            return null;
        }
        SelectedDoorGeometry selected(){if(selected==null)selected=new SelectedDoorGeometry(model);return selected;}
        Entry(ItemStack stack, IBakedModel model) {this(stack,model,null);}
        private Entry(ItemStack stack,IBakedModel model,Key key){this.stack=stack;this.model=model;this.key=key;}
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
