package com.vandorlabs.neoforge;
import com.google.common.cache.*;
import com.vandorlabs.neoforge.storage.NativeStorageSettings;
import com.vandorlabs.render.*;
import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.client.renderer.*;
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
/** Released raw item appearance, independent of placement normalization and all physical inventory data. */
public final class NativeStorageItemModel extends BakedModelWrapper<BakedModel> {
 private static final int BUDGET=4*1024*1024,DEFAULT=NativeStorageSettings.fresh().housing().mainTexture;
 private static final Direction[] FACES={null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
 private record Style(int choice,int layout,boolean present){}private static final Style EMPTY=new Style(DEFAULT,1,false);
 private record Selection(Style style,boolean wrapped,java.lang.ref.WeakReference<Selected> model){}
 private record Hot(java.lang.ref.WeakReference<CustomData> component,boolean wrapped,java.lang.ref.WeakReference<Selected> model){}
 private final Hot[] recent=new Hot[32];private int nextRecent;
 private Selected hot(CustomData component,boolean wrapped){synchronized(recent){for(var entry:recent)if(entry!=null&&entry.wrapped==wrapped&&(component==null?entry.component==null:entry.component!=null&&entry.component.get()==component)){var model=entry.model.get();if(model!=null)return model;}}return null;}
 private Selected remember(CustomData component,boolean wrapped,Selected model){synchronized(recent){recent[nextRecent]=new Hot(component==null?null:new java.lang.ref.WeakReference<>(component),wrapped,new java.lang.ref.WeakReference<>(model));nextRecent=(nextRecent+1)%recent.length;}return model;}
 private static MapDecoder<Style> decoder(boolean wrapped){return new MapDecoder.Implementation<>(){
  public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops){return java.util.stream.Stream.of("BlockEntityTag","housingTexture","MainTextureLayout","SlabTileSides").map(ops::createString);}
  public <T> DataResult<Style> decode(DynamicOps<T> ops,MapLike<T> input){if(wrapped){var value=input.get("BlockEntityTag");if(value==null)return DataResult.success(EMPTY);var nested=ops.getMap(value).result();if(nested.isEmpty())return DataResult.success(EMPTY);input=nested.get();}Object main=input.get("housingTexture"),layout=input.get("MainTextureLayout"),sides=input.get("SlabTileSides");int choice=main instanceof IntTag tag?tag.getAsInt():DEFAULT;int raw=HousingTexturePolicy.itemLayout(layout instanceof IntTag,layout instanceof IntTag tag?tag.getAsInt():0,sides instanceof ByteTag,sides instanceof ByteTag tag&&tag.getAsByte()!=0);return DataResult.success(new Style(choice,raw==0?0:raw==2?2:1,true));}
 };}
 private static final MapDecoder<Style> DIRECT=decoder(false),WRAPPED=decoder(true);
 private final NativeStorageModel.Bake bake;private final Cache<CustomData,Selection> components=CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
 private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Selected> models=new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();private long bytes;
 public NativeStorageItemModel(BakedModel authored,NativeStorageModel.Bake bake){super(authored);this.bake=Objects.requireNonNull(bake);}
 private Selected selected(Style style){int choice=bake.renderedChoice(style.choice);long key=((long)choice<<3)|(style.layout<<1)|(style.present?1:0);synchronized(models){var found=models.getAndMoveToLast(key);if(found!=null)return found;var built=new Selected(new Style(choice,style.layout,style.present));while(!models.isEmpty()&&bytes+built.bytes>BUDGET)bytes-=models.removeFirst().bytes;if(built.bytes<=BUDGET){models.putAndMoveToLast(key,built);bytes+=built.bytes;}return built;}}
 private Selected select(ItemStack stack){var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);boolean wrapped=false;if(component==null){component=stack.get(DataComponents.CUSTOM_DATA);wrapped=component!=null;}var hot=hot(component,wrapped);if(hot!=null)return hot;if(component==null)return remember(null,false,selected(EMPTY));var prior=components.getIfPresent(component);if(prior!=null&&prior.wrapped==wrapped){var found=prior.model.get();return remember(component,wrapped,found==null?selected(prior.style):found);}Style style=component.read(wrapped?WRAPPED:DIRECT).result().orElse(EMPTY);var model=selected(style);components.put(component,new Selection(style,wrapped,new java.lang.ref.WeakReference<>(model)));return remember(component,wrapped,model);}

 private final ItemOverrides selector=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){return select(stack);}};
 @Override public ItemOverrides getOverrides(){return selector;}
 public long retainedBytes(){synchronized(models){return 8192+bytes;}}
 private List<BakedQuad> retarget(List<BakedQuad> sources,Style style){var emitted=new ArrayList<BakedQuad>(sources.size());for(var source:sources){var material=bake.itemMaterial(style.choice,source.getDirection().get3DDataValue());int[] data=source.getVertices().clone();int stride=data.length/4;double[] u=new double[4],v=new double[4];double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=maxU;for(int i=0;i<4;i++){u[i]=(Float.intBitsToFloat(data[i*stride+4])-source.getSprite().getU0())/(source.getSprite().getU1()-source.getSprite().getU0());v[i]=(Float.intBitsToFloat(data[i*stride+5])-source.getSprite().getV0())/(source.getSprite().getV1()-source.getSprite().getV0());minU=Math.min(minU,u[i]);maxU=Math.max(maxU,u[i]);minV=Math.min(minV,v[i]);maxV=Math.max(maxV,v[i]);}double aspect=material.usedU()/material.usedV();boolean fit=TextureLayoutMode.fit(style.layout,1,1,aspect);for(int i=0;i<4;i++){double x=HousingTexturePolicy.itemU(fit,u[i],minU,maxU,1,aspect),y=HousingTexturePolicy.itemV(fit,v[i],minV,maxV,1,aspect);if(!style.present){x=.001+(u[i]-minU)/Math.max(1e-8,maxU-minU)*.998;y=.001+(v[i]-minV)/Math.max(1e-8,maxV-minV)*.998;}data[i*stride+4]=Float.floatToRawIntBits(material.sprite().getU((float)x*material.usedU()));data[i*stride+5]=Float.floatToRawIntBits(material.sprite().getV((float)y*material.usedV()));}emitted.add(new BakedQuad(data,source.getTintIndex(),source.getDirection(),material.sprite(),source.isShade(),source.hasAmbientOcclusion()));}return List.copyOf(emitted);}
 private final class Selected extends BakedModelWrapper<BakedModel> {
  private final Style style;private final List<BakedQuad>[] faces;private final ItemTransforms transforms;private final net.minecraft.client.renderer.texture.TextureAtlasSprite particle;private final List<BakedModel> passes=List.of(this);private final int bytes;
  @SuppressWarnings("unchecked") Selected(Style style){super(NativeStorageItemModel.this.originalModel);this.style=style;particle=bake.itemMaterial(style.choice,2).sprite();List<BakedQuad>[] built=NativeDoorArtwork.isStatic(originalModel)?new List[FACES.length]:null;long cost=512;if(built!=null)for(int side=0;side<FACES.length;side++){built[side]=retarget(originalModel.getQuads(null,FACES[side],RandomSource.create(0),ModelData.EMPTY,null),style);for(var quad:built[side])cost+=96L+quad.getVertices().length*4L;}bytes=(int)Math.min(Integer.MAX_VALUE,cost);faces=cost<=BUDGET?built:null;transforms=NativeItemPadding.needsFit(this,originalModel.getTransforms())?NativeItemPadding.fit(this,originalModel.getTransforms()):originalModel.getTransforms();}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return faces!=null?faces[side==null?0:side.ordinal()+1]:retarget(originalModel.getQuads(state,side,random,data,layer),style);}
  @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(){return particle;}
  @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(ModelData data){return particle;}
  @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
  @Override public ItemTransforms getTransforms(){return transforms;}
  @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
  @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return passes;}
 }
}
