package com.vandorlabs.neoforge.armor;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
    public static void models(ModelEvent.RegisterAdditional event) {
        for (var design : NativeArmorCatalog.designs()) event.register(model(design));
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        Map<Integer, BakedModel> designs = new HashMap<>();
        for (var design : NativeArmorCatalog.designs()) {
            var baked = event.getModels().get(model(design));
            if (baked != null) designs.put(design.choice(), baked);
        }
        for (var id : NativeArmorBindings.IDS.values()) {
            event.getModels().computeIfPresent(ModelResourceLocation.inventory(id),
                (ignored, base) -> new SelectDesign(base, designs));
        }
    }

    private static ModelResourceLocation model(NativeArmorCatalog.Design design) {
        return ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(design.model().getNamespace(), "item/" + design.model().getPath()));
    }

    private static final class SelectDesign extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides selector;

        SelectDesign(BakedModel base, Map<Integer, BakedModel> designs) {
            super(base);
            selector = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel original, ItemStack stack, ClientLevel level,
                                          LivingEntity entity, int seed) {
                    if (!(stack.getItem() instanceof NativeArmorItem)) return base;
                    return designs.getOrDefault(NativeArmorItem.design(stack).choice(), base);
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() { return selector; }
    }

    private NativeArmorClient() {}
}
