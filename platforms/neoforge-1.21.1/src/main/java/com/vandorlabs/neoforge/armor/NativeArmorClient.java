package com.vandorlabs.neoforge.armor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeArmorClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(NativeArmorBindings.MENU, NativeArmorScreen::new);
    }

    @SubscribeEvent
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        NativeArmorWorldSampling.receive = NativeArmorWorldClient::sample;
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        for (var design : NativeArmorCatalog.designs()) event.register(model(design));
        for (var type : NativeArmorBindings.IDS.keySet()) event.register(materialModel(type));
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        Map<Integer, BakedModel> designs = new HashMap<>();
        for (var design : NativeArmorCatalog.designs()) {
            var baked = event.getModels().get(model(design));
            if (baked != null) designs.put(design.choice(), baked);
        }
        NativeArmorBindings.IDS.forEach((type, id) -> {
            var geometry = event.getModels().get(materialModel(type));
            event.getModels().computeIfPresent(ModelResourceLocation.inventory(id),
                (ignored, base) -> new SelectDesign(base, designs, geometry));
        });
    }

    @SubscribeEvent
    public static void reload(ModelEvent.BakingCompleted event) {
        net.minecraft.client.Minecraft.getInstance().execute(NativeArmorMaterials::clear);
    }

    private static ModelResourceLocation materialModel(ArmorItem.Type type) {
        return ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("vandorlabs", "item/armor/material_" + type.getName()));
    }

    private static ModelResourceLocation model(NativeArmorCatalog.Design design) {
        return ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(design.model().getNamespace(), "item/" + design.model().getPath()));
    }

    private static final class SelectDesign extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides selector;

        SelectDesign(BakedModel base, Map<Integer, BakedModel> designs, BakedModel geometry) {
            super(base);
            selector = new ItemOverrides() {
                private final Int2ObjectLinkedOpenHashMap<NativeArmorMaterialModel> materials = new Int2ObjectLinkedOpenHashMap<>();
                private final Map<String, NativeArmorMaterialModel> samples = new LinkedHashMap<>(16, .75F, true);

                @Override
                public BakedModel resolve(BakedModel original, ItemStack stack, ClientLevel level,
                                          LivingEntity entity, int seed) {
                    if (!(stack.getItem() instanceof NativeArmorItem)) return base;
                    String sample = NativeArmorItem.worldSample(stack);
                    if (geometry != null && sample != null) {
                        var found = samples.get(sample);
                        if (found != null) return found;
                        var material = NativeArmorMaterials.sampleMaterial(sample);
                        if (material == null) return base;
                        var created = new NativeArmorMaterialModel(geometry, material);
                        samples.put(sample, created);
                        if (samples.size() > 24) samples.remove(samples.keySet().iterator().next());
                        return created;
                    }
                    int choice = NativeArmorItem.choice(stack);
                    if (geometry != null && (NativeArmorItem.hasInventorySample(stack) || NativeArmorItem.listedMaterial(choice))) {
                        var model = materials.getAndMoveToLast(choice);
                        if (model == null) {
                            var material = NativeArmorMaterials.material(choice);
                            if (material == null) return base;
                            model = new NativeArmorMaterialModel(geometry, material);
                            materials.put(choice, model);
                            if (materials.size() > 32) materials.removeFirst();
                        }
                        return model;
                    }
                    return designs.getOrDefault(NativeArmorItem.design(stack).choice(), base);
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() { return selector; }
    }

    private NativeArmorClient() {}
}
