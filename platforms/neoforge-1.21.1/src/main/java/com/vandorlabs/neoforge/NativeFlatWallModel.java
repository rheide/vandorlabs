package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.render.PanelDepthPolicy;
import com.vandorlabs.tiles.MaterialChoicePolicy;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Current plain wall body and canonical item; all worker inputs are immutable per-bake snapshots. */
final class NativeFlatWallModel extends BakedModelWrapper<BakedModel> {
    private static final class Layers { static final ChunkRenderTypeSet VALUE=ChunkRenderTypeSet.of(RenderType.cutout()); }
    private record Key(NativeFlatWallGeometry.Geometry geometry,int main,int interior,int mode){}
    private record ItemKey(BakedModel source,int main,boolean custom,Direction side){}
    static final class Bake {
        final Map<Integer,ProgrammableModel.MaterialSnapshot> surfaces;
        final Map<Integer,NativePanelQuads.Material> interiors;
        final NativePanelQuads.Material originalMetal;
        final com.google.common.cache.Cache<Object,List<BakedQuad>> quads=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(16*1024*1024)
                .weigher((Object key,List<BakedQuad> values)->384+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum()).build();
        Bake(Map<Integer,ProgrammableModel.MaterialSnapshot> surfaces,Function<Material,TextureAtlasSprite> textures){
            this.surfaces=surfaces;Map<Integer,NativePanelQuads.Material> mapped=new HashMap<>();
            for(var entry:surfaces.entrySet()){
                ModernFinishCatalog.Frame frame=entry.getKey()<ModernFinishCatalog.ENTRIES.size()?ModernFinishCatalog.ENTRIES.get(entry.getKey()).frame():null;
                List<ProgrammableModel.MaterialSnapshot> edges=new ArrayList<>();if(frame!=null&&frame.border()&&frame.rail()!=null)for(int i=0;i<4;i++)edges.add(new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(frame.rail()+(i==0?"":"_edge_"+i)))),1,1));
                mapped.put(entry.getKey(),new NativePanelQuads.Material(entry.getValue(),edges));
            }
            interiors=Map.copyOf(mapped);originalMetal=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse("vandorlabs:frame-default/metal_side"))),1,1));
        }
        BakedModel wrap(BakedModel original,boolean item){return new NativeFlatWallModel(original,this,item,null);}
        int index(int choice){return ProgrammableModel.renderIndex(surfaces,choice);}
    }
    private final Bake bake;private final boolean item;private final ItemChoice choice;
    private NativeFlatWallModel(BakedModel original,Bake bake,boolean item,ItemChoice choice){super(original);this.bake=bake;this.item=item;this.choice=choice;}
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){return data;}
    @Override public TextureAtlasSprite getParticleIcon(){return getParticleIcon(ModelData.EMPTY);}
    @Override public TextureAtlasSprite getParticleIcon(ModelData data){var appearance=data.get(NativeWallEntity.WALL_APPEARANCE);int main=item?(choice==null?0:choice.main):appearance==null?0:appearance.base().housing().mainTexture;return bake.surfaces.get(bake.index(main)).sprite();}
    @Override public boolean useAmbientOcclusion(){return false;}
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return net.neoforged.neoforge.common.util.TriState.FALSE;}
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return Layers.VALUE;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        if(item)return itemQuads(state,side,random,data,layer);if(side!=null)return List.of();var appearance=data.get(NativeWallEntity.WALL_APPEARANCE);
        Direction facing=state.getValue(NativeFlatWallBlock.FACING);int depth=state.getValue(NativeFlatWallBlock.DEPTH);Object published=appearance==null?null:appearance.geometry();NativeFlatWallGeometry.Geometry geometry=published instanceof NativeFlatWallGeometry.Geometry value?value:null;if(geometry==null||geometry.facing()!=facing||geometry.depth()!=depth)geometry=NativeFlatWallGeometry.straight(facing,depth);
        return worldQuads(geometry,appearance==null?0:appearance.base().housing().mainTexture,appearance==null?-1:appearance.frames().interior,appearance==null?0:appearance.base().housing().layout);
    }
    List<BakedQuad> worldQuads(NativeFlatWallGeometry.Geometry geometry,int main,int interior,int mode){
        int body=bake.index(main),edge=interior<0?-1:bake.index(interior);Key key=new Key(geometry,body,edge,mode);var found=bake.quads.getIfPresent(key);if(found!=null)return found;
        var wall=new NativePanelQuads.Material(bake.surfaces.get(body));var metal=edge<0?bake.originalMetal:bake.interiors.get(edge);var join=geometry.join();var result=NativePanelQuads.flat(geometry.facing(),PanelDepthPolicy.start(geometry.depth()),join==null?null:join.corner(),wall,metal,mode,geometry.layout());bake.quads.put(key,result);return result;
    }
    private List<BakedQuad> itemQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        var source=originalModel.getQuads(state,side,random,data,layer);if(choice==null||bake.index(choice.main)==0&&!choice.custom)return source;
        ItemKey key=new ItemKey(originalModel,bake.index(choice.main),choice.custom,side);boolean cache=NativeAuthoredControlBake.isStatic(originalModel);var found=cache?bake.quads.getIfPresent(key):null;if(found!=null)return found;
        var target=bake.surfaces.get(bake.index(choice.main));var original=bake.surfaces.get(0).sprite();List<BakedQuad> result=new ArrayList<>(source.size());for(var quad:source){if(!choice.custom&&quad.getSprite()!=original){result.add(quad);continue;}int[] packed=quad.getVertices().clone();int stride=packed.length/4;var old=quad.getSprite();for(int i=0;i<4;i++){float u=(Float.intBitsToFloat(packed[i*stride+4])-old.getU0())/(old.getU1()-old.getU0())*16,v=(Float.intBitsToFloat(packed[i*stride+5])-old.getV0())/(old.getV1()-old.getV0())*16;packed[i*stride+4]=Float.floatToRawIntBits(NativeHousingQuads.atlas(target,u,true));packed[i*stride+5]=Float.floatToRawIntBits(NativeHousingQuads.atlas(target,v,false));}result.add(new BakedQuad(packed,quad.getTintIndex(),quad.getDirection(),target.sprite(),quad.isShade(),quad.hasAmbientOcclusion()));}found=List.copyOf(result);if(cache)bake.quads.put(key,found);return found;
    }
    // Legacy item mesh selection requires an exact IntTag for builtins; its later custom-material
    // override accepts all numeric tags and recolors caps too. Other wall settings remain dormant.
    private record ItemChoice(int main,boolean custom){}
    private static final com.mojang.serialization.MapDecoder<ItemChoice> ITEM=new com.mojang.serialization.MapDecoder.Implementation<>(){
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){return java.util.stream.Stream.of("housingTexture","BlockEntityTag").map(ops::createString);}
        @Override public <T> com.mojang.serialization.DataResult<ItemChoice> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){if(input.get("BlockEntityTag")!=null){var nested=ops.getMap(input.get("BlockEntityTag")).result();if(nested.isPresent())input=nested.get();}Object value=input.get("housingTexture");int raw=value instanceof NumericTag tag?tag.getAsInt():0;boolean custom=raw>=MaterialChoicePolicy.CUSTOM_BASE;return com.mojang.serialization.DataResult.success(new ItemChoice(custom||value instanceof IntTag?raw:0,custom));}
    };
    private final com.google.common.cache.Cache<net.minecraft.world.item.component.CustomData,BakedModel> items=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(128).build();
    BakedModel resolveItem(net.minecraft.world.item.component.CustomData data){var found=items.getIfPresent(data);if(found==null){found=new NativeFlatWallModel(originalModel,bake,true,data.read(ITEM).getOrThrow());items.put(data,found);}return found;}
    private final ItemOverrides overrides=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){var custom=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);if(custom==null)custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);return custom==null?NativeFlatWallModel.this:resolveItem(custom);}};
    @Override public ItemOverrides getOverrides(){return item?overrides:originalModel.getOverrides();}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    private final List<BakedModel> passes=List.of(this);
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return passes;}
}
