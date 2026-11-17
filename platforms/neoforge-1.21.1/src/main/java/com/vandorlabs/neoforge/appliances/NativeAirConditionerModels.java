package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.appliance.ApplianceCatalog;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Narrow immutable AC previews; every other configured item keeps its established item renderer. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeAirConditionerModels {
 public static ResourceLocation model(int preset){return ResourceLocation.fromNamespaceAndPath("vandorlabs","item/appliances/"+ApplianceCatalog.IDS[preset]);}
 @SubscribeEvent public static void additional(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event){for(int i=0;i<4;i++)event.register(ModelResourceLocation.standalone(model(i)));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void bake(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event){var previews=new BakedModel[4];for(int i=0;i<4;i++)previews[i]=java.util.Objects.requireNonNull(event.getModels().get(ModelResourceLocation.standalone(model(i))),"AC preview "+i);for(String item:new String[]{"programmable_block","programmable_slab"}){var id=new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("vandorlabs",item),"inventory");var original=event.getModels().get(id);if(original!=null)event.getModels().put(id,new Selector(original,previews));}}
 private static final class Selector extends BakedModelWrapper<BakedModel>{
  private final BakedModel[] previews;
  private final ItemOverrides overrides=new ItemOverrides(){@Override public BakedModel resolve(BakedModel model,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){int preset=NativeAirConditionerPresets.preset(stack);return preset>=0?previews[preset]:originalModel.getOverrides().resolve(originalModel,stack,level,entity,seed);}};
  Selector(BakedModel original,BakedModel[] previews){super(original);this.previews=previews;}
  @Override public ItemOverrides getOverrides(){return overrides;}
 }
 private NativeAirConditionerModels(){}
}
