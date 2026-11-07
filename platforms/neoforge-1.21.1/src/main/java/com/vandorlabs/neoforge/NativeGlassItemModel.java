package com.vandorlabs.neoforge;

import com.google.common.cache.*;
import com.vandorlabs.neoforge.glass.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Bake-owned Source selector: only the outer glass envelope affects authored inventory artwork. */
public final class NativeGlassItemModel extends BakedModelWrapper<BakedModel> {
 private static final int BUDGET=8*1024*1024;
 // Source RenderItem.renderItemModelIntoGUI blends SRC_ALPHA/ONE_MINUS_SRC_ALPHA with cutoff0.1.
 private static final class Layers {static final List<RenderType> GUI=List.of(RenderType.itemEntityTranslucentCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));}
 private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
 private record Key(String id,BakedModel source,boolean authored){@Override public int hashCode(){return id.hashCode()*31+System.identityHashCode(source);}@Override public boolean equals(Object other){return other instanceof Key k&&id.equals(k.id)&&source==k.source&&authored==k.authored;}}
 private final Map<String,Key> sources;private final String plainId=NativeFlatGlassAssets.item(1,0,1,true);private final ResourceManager resources;
 private final Map<String,Boolean> canonical=new java.util.concurrent.ConcurrentHashMap<>();
 private final Cache<CustomData,String> choices=CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
 private final Cache<Key,Selected> meshes=CacheBuilder.newBuilder().maximumWeight(BUDGET).weigher((Key key,Selected value)->value.bytes).build();
 private record Hot(java.lang.ref.WeakReference<CustomData> component,java.lang.ref.WeakReference<Selected> model){}
 private volatile Hot lastHot;
 public NativeGlassItemModel(BakedModel fallback,Function<String,BakedModel> source,ResourceManager resources){super(Objects.requireNonNull(fallback));this.resources=Objects.requireNonNull(resources);var models=new HashMap<String,Key>();for(int size=0;size<2;size++)for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++)for(boolean framed:new boolean[]{false,true}){String id=NativeFlatGlassAssets.item(size,shade,tint,framed);var model=source.apply(id);models.put(id,new Key(id,model==null?fallback:model,model!=null));}sources=Map.copyOf(models);}
 private Selected selected(Key key){var value=meshes.getIfPresent(key);if(value==null){value=new Selected(key);if(value.bytes<=BUDGET)meshes.put(key,value);}return value;}
 private Selected base(ItemStack stack){var component=stack.get(DataComponents.CUSTOM_DATA);var hot=lastHot;if(hot!=null&&(component==null?hot.component==null:hot.component!=null&&hot.component.get()==component)){var found=hot.model.get();if(found!=null)return found;}
  String id=component==null?plainId:choices.getIfPresent(component);if(id==null){id=NativeGlassItem.sourceModel(stack);choices.put(component,id);}var value=selected(sources.get(id));lastHot=new Hot(component==null?null:new java.lang.ref.WeakReference<>(component),new java.lang.ref.WeakReference<>(value));return value;
 }
 private final ItemOverrides selector=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){var value=base(stack);var source=value.original();var resolved=source.getOverrides().resolve(source,stack,level,entity,seed);return resolved==null||resolved==source?value:selected(new Key(value.key.id,resolved,false));}};
 @Override public ItemOverrides getOverrides(){return selector;}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return selected(sources.get(plainId)).getQuads(state,side,random);}
 @Override public ItemTransforms getTransforms(){return selected(sources.get(plainId)).getTransforms();}
 public long retainedBytes(){meshes.cleanUp();return meshes.asMap().values().stream().mapToLong(value->value.bytes).sum();}
 public final class Selected extends BakedModelWrapper<BakedModel> {
  private final Key key;private final List<BakedQuad> quads;private final ItemTransforms transforms;private final List<BakedModel> passes=List.of(this);private final BakedModel gui=new BakedModelWrapper<Selected>(this){private final List<BakedModel> guiPasses=List.of(this);@Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous){return Layers.GUI;}@Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return guiPasses;}};private final int bytes;private final boolean staticSource;
  private Selected(Key key){super(key.source);this.key=key;staticSource=NativeDoorArtwork.isStatic(originalModel);List<BakedQuad> built=null;if(staticSource){if(key.authored&&canonical.computeIfAbsent(key.id,id->NativeDoorArtwork.canonicalGeometry(id,resources)))built=NativeDoorArtwork.quads(key.id,originalModel);else{var values=new ArrayList<BakedQuad>();for(var face:FACES)values.addAll(originalModel.getQuads(null,face,RandomSource.create(0),ModelData.EMPTY,null));built=NativeDoorArtwork.content(values);}}
   long cost=512;if(built!=null)for(var quad:built)cost+=2L*(96+quad.getVertices().length*4L);bytes=(int)Math.min(Integer.MAX_VALUE,cost);quads=cost<=BUDGET?built:null;var base=originalModel.getTransforms();transforms=NativeItemPadding.needsFit(this,base)?NativeItemPadding.fit(this,base):base;
  }
  private BakedModel original(){return originalModel;}public String modelId(){return key.id;}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return quads!=null?side==null?quads:List.of():NativeDoorArtwork.content(originalModel.getQuads(state,side,random,data,layer));}
  @Override public ItemTransforms getTransforms(){return transforms;}
  @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return context==ItemDisplayContext.GUI&&key.authored&&staticSource?gui:this;}
  @Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous){return originalModel.getRenderTypes(stack,fabulous);}
  @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){if(staticSource)return passes;var source=originalModel.getRenderPasses(stack,fabulous);if(source.size()==1&&source.getFirst()==originalModel)return passes;var result=new ArrayList<BakedModel>();for(var model:source)result.add(model==originalModel?this:selected(new Key(key.id,model,false)));return List.copyOf(result);}
 }
}
