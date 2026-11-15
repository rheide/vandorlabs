package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.legacylanding.*;
import com.vandorlabs.neoforge.legacylanding.client.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
/** One fixed authored working set per reload; size selectors never retain worlds or duplicate meshes. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeLegacyLandingModels {
 public static final String[] SIZES={"small","medium","large","extra_large"};private static Map<String,List<BakedQuad>> bodies=Map.of();
 private static ResourceLocation id(String name){return ResourceLocation.parse("vandorlabs:block/native_legacy_landing/"+name);}
 private static List<String> names(){var names=new ArrayList<String>();for(var kind:NativeLegacyLandingKind.values())for(String size:SIZES){String prefix=kind.id+"_"+size;for(String part:new String[]{"fixed","wheel","piston","item"})names.add(prefix+"_"+part);if(kind.ski){names.add(prefix+"_nose_front");names.add(prefix+"_nose_rear");}}return names;}
 @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event){if(NativeLegacyLandingBindings.enabled())for(String name:names())event.register(ModelResourceLocation.standalone(id(name)));}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(NativeLegacyLandingBindings.enabled())for(var type:NativeLegacyLandingBindings.types())event.registerBlockEntityRenderer(type,NativeLegacyLandingRenderer::new);}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativeLegacyLandingBindings.enabled())event.register(NativeLegacyLandingBindings.MENU,NativeLegacyLandingScreen::new);}
 @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){if(!NativeLegacyLandingBindings.enabled())return;var next=new HashMap<String,List<BakedQuad>>();long bytes=0;for(String name:names()){var model=Objects.requireNonNull(event.getModels().get(ModelResourceLocation.standalone(id(name))),name);var quads=new ArrayList<BakedQuad>();for(Direction side:new Direction[]{null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})quads.addAll(model.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));for(var q:quads){if(q.getSprite().contents().name().getPath().contains("missingno"))throw new IllegalStateException("Missing legacy landing sprite");bytes+=96L+q.getVertices().length*4L;}next.put(name,List.copyOf(quads));}if(bytes>16*1024*1024)throw new IllegalArgumentException("Legacy landing model bound");bodies=Map.copyOf(next);for(var kind:NativeLegacyLandingKind.values()){var location=new ModelResourceLocation(ResourceLocation.parse("vandorlabs:"+kind.id),"inventory");event.getModels().put(location,new ItemsModel(Objects.requireNonNull(event.getModels().get(location)),kind,event.getModels()));}NativeLegacyVisualClock.clear();}
 public static List<BakedQuad> quads(String name){return bodies.getOrDefault(name,List.of());}
 private static final class ItemsModel extends BakedModelWrapper<BakedModel>{
  private final BakedModel[] variants=new BakedModel[4];private final com.google.common.cache.Cache<CustomData,Integer> choices=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
  private final ItemOverrides overrides=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity actor,int seed){var data=stack.get(DataComponents.CUSTOM_DATA);int size=0;if(data!=null){var cached=choices.getIfPresent(data);if(cached==null){var tag=data.copyTag();var settings=tag.contains("LegacyLandingSettings")?tag.getCompound("LegacyLandingSettings"):tag.getCompound("BlockEntityTag");cached=Math.max(0,Math.min(3,settings.getInt("GearSize")));choices.put(data,cached);}size=cached;}return variants[size];}};
  ItemsModel(BakedModel base,NativeLegacyLandingKind kind,Map<ModelResourceLocation,BakedModel> models){super(base);for(int size=0;size<4;size++)variants[size]=new Padded(Objects.requireNonNull(models.get(ModelResourceLocation.standalone(id(kind.id+"_"+SIZES[size]+"_item")))));}
  @Override public ItemOverrides getOverrides(){return overrides;}
 }
 private static final class Padded extends BakedModelWrapper<BakedModel>{private final ItemTransforms transforms;Padded(BakedModel base){super(base);transforms=NativeItemPadding.fit(base,base.getTransforms());}@Override public ItemTransforms getTransforms(){return transforms;}@Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}@Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}}
 private NativeLegacyLandingModels(){}
}
