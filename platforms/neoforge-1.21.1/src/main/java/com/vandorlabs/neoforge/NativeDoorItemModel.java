package com.vandorlabs.neoforge;

import com.mojang.serialization.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.SpaceDoorModels;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Inactive bake-owned item selector. The integration supplies sources from the completed bake. */
public final class NativeDoorItemModel extends BakedModelWrapper<BakedModel> {
 private record Choice(Integer key,String id,boolean padded){}
 private static final Choice[][] CHOICES=new Choice[2][240];
 static{for(int large=0;large<2;large++)for(int design=0;design<30;design++)for(int flags=0;flags<8;flags++){boolean sliding=(flags&1)!=0,framed=(flags&2)!=0,hinges=(flags&4)!=0;int key=design*8+flags;var data=new SpaceDoorData(design,1,framed,0,false,sliding,hinges);CHOICES[large][key]=new Choice(key,"vandorlabs:item/"+SpaceDoorModels.itemModel(data,large==1),design==29);}}
 private static Choice choice(int large,int design,boolean sliding,boolean framed,boolean hinges){return CHOICES[large][design*8+(sliding?1:0)+(framed?2:0)+((sliding||hinges)?4:0)];}
 private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
 private record OverrideKey(Choice choice,BakedModel source){@Override public int hashCode(){return choice.key().hashCode()*31+System.identityHashCode(source);}@Override public boolean equals(Object other){return other instanceof OverrideKey key&&key.choice==choice&&key.source==source;}}
 private static final int MESH_BUDGET=16*1024*1024;
 private final boolean large;private final Map<String,BakedModel> sources;private final Choice empty;
 private record Hot(java.lang.ref.WeakReference<CustomData> component,java.lang.ref.WeakReference<Selected> model){}
 private final Hot[] hot=new Hot[64];private int nextHot;private volatile Hot lastHot;
 private final Map<String,Boolean> canonical=new java.util.concurrent.ConcurrentHashMap<>();
 private final com.google.common.cache.Cache<CustomData,Choice> components=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
 private final com.google.common.cache.Cache<Integer,Selected> prepared=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(MESH_BUDGET).weigher((Integer key,Selected model)->model.weight()).build();
 private final Map<Integer,com.google.common.cache.Cache<BakedModel,Selected>> dynamic=new java.util.concurrent.ConcurrentHashMap<>();
 private final com.google.common.cache.Cache<OverrideKey,Selected> overrides=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(MESH_BUDGET).weigher((OverrideKey key,Selected model)->model.weight()).build();
 private NativeDoorItemModel(BakedModel fallback,boolean large,Function<String,BakedModel> sources){super(fallback);this.large=large;Objects.requireNonNull(sources);var owned=new HashMap<String,BakedModel>();for(var choice:CHOICES[large?1:0])if(!owned.containsKey(choice.id())){var model=sources.apply(choice.id());if(model!=null)owned.put(choice.id(),model);}this.sources=Map.copyOf(owned);empty=choice(large?1:0,large?23:2,false,true,true);}
 public static NativeDoorItemModel wrap(BakedModel fallback,boolean large,Function<String,BakedModel> sources){return new NativeDoorItemModel(Objects.requireNonNull(fallback),large,sources);}
 private final MapDecoder<Choice> decoder=new MapDecoder.Implementation<>(){
  @Override public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops){return java.util.stream.Stream.of(ops.createString("SpaceDoorSettings"));}
  @Override public <T> DataResult<Choice> decode(DynamicOps<T> ops,MapLike<T> input){T raw=input.get("SpaceDoorSettings");var nested=raw==null?Optional.<MapLike<T>>empty():ops.getMap(raw).result();if(nested.isEmpty())return DataResult.success(empty);input=nested.get();var data=new SpaceDoorData(integer(input,"SpaceDesign",2),integer(input,"SpaceDetail",1),bool(input,"SpaceFramed",true),0,false,bool(input,"SpaceDoorSliding",false),bool(input,"SpaceDoorHinges",true));return DataResult.success(choice(large?1:0,data.design,data.sliding,data.framed,data.hinges));}
  private int integer(MapLike<?> map,String key,int missing){Object value=map.get(key);return value==null?missing:value instanceof NumericTag number?number.getAsInt():0;}
  private boolean bool(MapLike<?> map,String key,boolean missing){Object value=map.get(key);return value==null?missing:value instanceof NumericTag number&&number.getAsByte()!=0;}
 };
 private Choice project(ItemStack stack){var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return empty;var value=components.getIfPresent(custom);if(value==null){value=custom.read(decoder).result().orElse(empty);components.put(custom,value);}return value;}
 private Selected base(Choice choice){var model=prepared.getIfPresent(choice.key());if(model==null){var source=sources.get(choice.id());model=new Selected(source==null?originalModel:source,choice,source!=null);prepared.put(choice.key(),model);}return model;}
 private Selected selected(Choice choice,BakedModel source){if(!NativeDoorArtwork.isStatic(source)){var cache=dynamic.computeIfAbsent(choice.key(),key->com.google.common.cache.CacheBuilder.newBuilder().weakKeys().weakValues().maximumSize(16).build());var value=cache.getIfPresent(source);if(value==null){value=new Selected(source,choice,false);cache.put(source,value);}return value;}var key=new OverrideKey(choice,source);var model=overrides.getIfPresent(key);if(model==null){model=new Selected(source,choice,false);if(model.weight()<=MESH_BUDGET)overrides.put(key,model);}return model;}
 private Selected hotBase(ItemStack stack){
  var custom=stack.get(DataComponents.CUSTOM_DATA);var latest=lastHot;var found=hotModel(latest,custom);if(found!=null)return found;
  for(var entry:hot){found=hotModel(entry,custom);if(found!=null){lastHot=entry;return found;}}
  var model=base(project(stack));var entry=new Hot(custom==null?null:new java.lang.ref.WeakReference<>(custom),new java.lang.ref.WeakReference<>(model));hot[nextHot]=entry;nextHot=(nextHot+1)&63;lastHot=entry;return model;
 }
 private Selected hotModel(Hot entry,CustomData component){return entry!=null&&(component==null?entry.component()==null:entry.component()!=null&&entry.component().get()==component)?entry.model().get():null;}
 private final ItemOverrides selector=new ItemOverrides(){
  @Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){var base=hotBase(stack);var choice=base.choice;var resolved=base.source().getOverrides().resolve(base.source(),stack,level,entity,seed);return resolved==null||resolved==base.source()?base:selected(choice,resolved);}
 };
 @Override public ItemOverrides getOverrides(){return selector;}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return base(empty).getQuads(state,side,random);}
 @Override public ItemTransforms getTransforms(){return base(empty).getTransforms();}
 private static ItemTransforms ordinaryTransforms(ItemTransforms source,boolean padded){var hand=new ItemTransform(new Vector3f(),new Vector3f(0,-.25F,0),new Vector3f(.5F));var gui=new ItemTransform(new Vector3f(15,205,0),new Vector3f(0,padded?-.15F:-.25F,0),new Vector3f(padded?.4F:.5F));return new ItemTransforms(hand,hand,hand,hand,ItemTransform.NO_TRANSFORM,gui,hand,hand,source.moddedTransforms);}
 private static boolean needsFit(BakedModel model,ItemTransforms transforms){
  var gui=transforms.gui;double bound=.5-1D/32;for(var side:FACES)for(var q:model.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null)){var raw=q.getVertices();for(int i=0;i<4;i++){int at=i*(raw.length/4);double x=(Float.intBitsToFloat(raw[at])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(raw[at+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(raw[at+2])-.5)*gui.scale.z();double rz=Math.toRadians(gui.rotation.z()),ry=Math.toRadians(gui.rotation.y()),rx=Math.toRadians(gui.rotation.x());double a=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=a;a=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=a;y=y*Math.cos(rx)-z*Math.sin(rx);x+=gui.translation.x();y+=gui.translation.y();if(x< -bound||x>bound||y< -bound||y>bound)return true;}}return false;
 }
 public final class Selected extends BakedModelWrapper<BakedModel> {
  private final Choice choice;private final boolean staticSource;private final List<BakedQuad> quads;private final ItemTransforms transforms,baseTransforms;private final List<BakedModel> passes=List.of(this);private final int bytes;
  private Selected(BakedModel source,Choice choice,boolean authored){
   super(source);this.choice=choice;staticSource=NativeDoorArtwork.isStatic(source);List<BakedQuad> built=null;
   if(staticSource){boolean exact=authored&&canonical.computeIfAbsent(choice.id(),id->NativeDoorArtwork.canonicalGeometry(id,Minecraft.getInstance().getResourceManager()));if(exact)built=NativeDoorArtwork.quads(choice.id(),source);else{var all=new ArrayList<BakedQuad>();for(var side:FACES)all.addAll(source.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));built=NativeDoorArtwork.content(all);}}
   long cost=512;if(built!=null)for(var quad:built)cost+=2L*(96+quad.getVertices().length*4L);
   // Oversized pack meshes keep their source behavior rather than retaining unbounded copied vertices.
   quads=cost>MESH_BUDGET?null:built;bytes=(int)Math.min(Integer.MAX_VALUE,cost);baseTransforms=large?source.getTransforms():ordinaryTransforms(source.getTransforms(),choice.padded());transforms=needsFit(this,baseTransforms)?NativeItemPadding.fit(this,baseTransforms):baseTransforms;
  }
  private BakedModel source(){return originalModel;}private int weight(){return bytes;}public String modelId(){return choice.id();}public ItemTransforms baseTransforms(){return baseTransforms;}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(quads!=null)return side==null?quads:List.of();return NativeDoorArtwork.content(originalModel.getQuads(state,side,random,data,layer));}
  @Override public ItemTransforms getTransforms(){return transforms;}
  @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
  @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){if(staticSource)return passes;var source=originalModel.getRenderPasses(stack,fabulous);if(source.size()==1&&source.get(0)==originalModel)return passes;var result=new ArrayList<BakedModel>();for(var model:source)result.add(model==originalModel?this:selected(choice,model));return List.copyOf(result);}
 }
}
