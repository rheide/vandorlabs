package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.gear.NativeGearBindings;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Reuses the same rotated GUI-padding adapter as the other native configured block items. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeGearItemModels {
    private static final class Padded extends BakedModelWrapper<BakedModel> {private final ItemTransforms transforms;Padded(BakedModel source){super(source);transforms=NativeItemPadding.needsFit(source,source.getTransforms())?NativeItemPadding.fit(source,source.getTransforms()):source.getTransforms();}@Override public ItemTransforms getTransforms(){return transforms;}}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){if(NativeGearBindings.enabled())event.getModels().replaceAll((id,model)->id.getVariant().equals("inventory")&&id.id().getNamespace().equals("vandorlabs")&&NativeGearBindings.ITEMS.containsKey(id.id().getPath())?new Padded(model):model);}
    public static boolean fits(BakedModel model){return !NativeItemPadding.needsFit(model,model.getTransforms());}
    private NativeGearItemModels(){}
}
