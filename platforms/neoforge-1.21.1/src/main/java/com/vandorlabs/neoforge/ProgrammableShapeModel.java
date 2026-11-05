package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.ProgrammableShapeEntity;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.*;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** One housing backend for current slab/stair projections; workers consume immutable model data only. */
final class ProgrammableShapeModel extends BakedModelWrapper<BakedModel> {
    private static final ProgrammableShapeEntity.Appearance DEFAULT=new ProgrammableShapeEntity.Appearance(ProgrammableBlockEntity.defaults(),-1,new StairState(8,0));
    record Pose(Direction facing,boolean upper,StairGeometry.Shape shape){}
    private static final Pose ITEM_POSE=new Pose(Direction.EAST,false,StairGeometry.Shape.STRAIGHT);
    private record Key(BakedModel source,Pose pose,ProgrammableShapeEntity.Appearance appearance,boolean slab,boolean item,boolean tagged,Direction side){}
    static final class Bake {
        static final int MESH_BUDGET=16*1024*1024;
        final Map<Integer,ProgrammableModel.MaterialSnapshot> materials;
        // All listed states and configured item children share one generation's retained-byte budget.
        final com.google.common.cache.Cache<Key,List<BakedQuad>> quads=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(MESH_BUDGET)
                .weigher((Key key,List<BakedQuad> values)->384+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum()).build();
        Bake(Map<Integer,ProgrammableModel.MaterialSnapshot> materials){this.materials=materials;}
        BakedModel wrap(BakedModel model,boolean slab,boolean item){return new ProgrammableShapeModel(model,this,slab,item,null,false);}
    }
    private final Bake bake;
    private final boolean slab,item,tagged;
    private final ProgrammableShapeEntity.Appearance itemAppearance;
    private ProgrammableShapeModel(BakedModel original,Bake bake,boolean slab,boolean item,ProgrammableShapeEntity.Appearance appearance,boolean tagged){super(original);this.bake=bake;this.slab=slab;this.item=item;this.itemAppearance=appearance;this.tagged=tagged;}
    private ProgrammableModel.MaterialSnapshot material(int choice){return bake.materials.get(ProgrammableModel.renderIndex(bake.materials,choice));}
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){return data;}
    @Override public TextureAtlasSprite getParticleIcon(){return getParticleIcon(ModelData.EMPTY);}
    @Override public TextureAtlasSprite getParticleIcon(ModelData data){var appearance=item?itemAppearance:data.get(ProgrammableShapeEntity.APPEARANCE);return material(appearance==null?0:appearance.housing().mainTexture).sprite();}
    @Override public boolean useAmbientOcclusion(){return !slab&&originalModel.useAmbientOcclusion();}
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return slab?net.neoforged.neoforge.common.util.TriState.FALSE:originalModel.useAmbientOcclusion(state,data,layer);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        Pose pose=item?ITEM_POSE:new Pose(state.getValue(slab?BlockStateProperties.FACING:BlockStateProperties.HORIZONTAL_FACING),state.getValue(BlockStateProperties.HALF)==Half.TOP,slab?StairGeometry.Shape.STRAIGHT:StairGeometry.Shape.values()[state.getValue(BlockStateProperties.STAIRS_SHAPE).ordinal()]);
        return quadsForPose(pose,state,side,random,data,layer);
    }
    List<BakedQuad> quadsForPose(Pose pose,BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        var appearance=item?itemAppearance:data.get(ProgrammableShapeEntity.APPEARANCE);if(appearance==null)appearance=DEFAULT;
        Key key=new Key(originalModel,pose,appearance,slab,item,tagged,side);
        boolean cache=slab&&!item||NativeAuthoredControlBake.isStatic(originalModel);
        List<BakedQuad> result=cache?bake.quads.getIfPresent(key):null;if(result!=null)return result;
        result=slab?slab(pose,state,side,random,data,layer,appearance):stairs(pose,state,side,random,data,layer,appearance);
        if(cache)bake.quads.put(key,result);return result;
    }
    private List<BakedQuad> slab(Pose pose,BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer,ProgrammableShapeEntity.Appearance appearance){
        if(item){
            var source=originalModel.getQuads(state,side,random,data,layer);if(!tagged)return source;
            List<BakedQuad> result=new ArrayList<>(source.size());
            for(var quad:source){int choice=HousingTexturePolicy.baseChoice(quad.getDirection().get3DDataValue(),appearance.housing().mainTexture,appearance.sideTexture(),true);result.add(itemLayout(quad,appearance.housing().layout,material(choice)));}return List.copyOf(result);
        }
        Direction facing=pose.facing;boolean upper=pose.upper;var settings=appearance.housing();List<BakedQuad> result=new ArrayList<>(side==null?0:1);
        int[] locals={1,0,4,5,2,3};for(int index=0;index<6;index++){
            int local=locals[index];Direction world=Direction.from3DDataValue(HousingTexturePolicy.housingWorldFace(local,facing.get3DDataValue()));if(world!=side)continue;
            int choice=HousingTexturePolicy.baseChoice(local,settings.mainTexture,appearance.sideTexture(),true);if(settings.facesEnabled&&settings.face(local)>=0)choice=settings.face(local);result.add(NativeHousingQuads.slab(upper,facing,index,settings.layout,material(choice)));
        }
        return List.copyOf(result);
    }
    private List<BakedQuad> stairs(Pose pose,BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer,ProgrammableShapeEntity.Appearance appearance){
        Direction facing=pose.facing;boolean upper=pose.upper;
        StairGeometry.Shape shape=pose.shape;int geometry=appearance.stairs().packed();
        List<BakedQuad> source;if(geometry==8)source=originalModel.getQuads(state,side,random,data,layer);else{if(side!=null)return List.of();source=NativeHousingQuads.stairs(facing,upper,shape,geometry,material(0).sprite());}
        var settings=appearance.housing();List<BakedQuad> result=new ArrayList<>(source.size());
        for(var quad:source){Direction textureFace=NativeStairGeometry.unrolledFace(facing,upper,appearance.stairs().roll,quad.getDirection());int local=HousingTexturePolicy.stairLocalFace(textureFace.get3DDataValue(),facing.get3DDataValue());int choice=HousingTexturePolicy.baseChoice(local,settings.mainTexture,appearance.sideTexture(),true);if(settings.facesEnabled&&settings.face(local)>=0)choice=settings.face(local);result.add(NativeHousingQuads.retextureStair(quad,settings.layout,textureFace,material(choice)));}return List.copyOf(result);
    }
    /** Released slab item normalization is separate from world slab crops and raw invalid modes survive. */
    private static BakedQuad itemLayout(BakedQuad source,int layout,ProgrammableModel.MaterialSnapshot material){
        int[] data=source.getVertices().clone();int stride=data.length/4;double[] u=new double[4],v=new double[4];double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU,unitU=1,unitV=1;var old=source.getSprite();
        for(int i=0;i<4;i++){u[i]=(Float.intBitsToFloat(data[i*stride+4])-old.getU0())/(old.getU1()-old.getU0());v[i]=(Float.intBitsToFloat(data[i*stride+5])-old.getV0())/(old.getV1()-old.getV0());minU=Math.min(minU,u[i]);maxU=Math.max(maxU,u[i]);minV=Math.min(minV,v[i]);maxV=Math.max(maxV,v[i]);}
        for(int a=0;a<4;a++)for(int b=0;b<4;b++){double length=0;for(int axis=0;axis<3;axis++){double delta=Float.intBitsToFloat(data[a*stride+axis])-Float.intBitsToFloat(data[b*stride+axis]);length+=delta*delta;}length=Math.sqrt(length);if(Math.abs(u[a]-u[b])>1e-8&&Math.abs(v[a]-v[b])<1e-8)unitU=length/Math.abs(u[a]-u[b]);if(Math.abs(v[a]-v[b])>1e-8&&Math.abs(u[a]-u[b])<1e-8)unitV=length/Math.abs(v[a]-v[b]);}
        double width=(maxU-minU)*unitU,height=(maxV-minV)*unitV,aspect=material.usedU()/material.usedV();boolean fit=TextureLayoutMode.fit(layout,width,height,aspect);
        for(int i=0;i<4;i++){data[i*stride+4]=Float.floatToRawIntBits(NativeHousingQuads.atlas(material,HousingTexturePolicy.itemU(fit,u[i],minU,maxU,width,aspect)*16,true));data[i*stride+5]=Float.floatToRawIntBits(NativeHousingQuads.atlas(material,HousingTexturePolicy.itemV(fit,v[i],minV,maxV,height,aspect)*16,false));}
        return new BakedQuad(data,source.getTintIndex(),source.getDirection(),material.sprite(),source.isShade(),source.hasAmbientOcclusion());
    }
    private static final com.mojang.serialization.MapDecoder<ItemAppearance> ITEM=new com.mojang.serialization.MapDecoder.Implementation<>(){
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){return java.util.stream.Stream.of("BlockEntityTag","housingTexture","SideTexture","MainTextureLayout","SlabTileSides","FaceTexturesEnabled","FaceTextures",StairState.TREAD,StairState.ROLL).map(ops::createString);}
        @Override public <T> com.mojang.serialization.DataResult<ItemAppearance> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){
            if(input.get("BlockEntityTag")!=null){var nested=ops.getMap(input.get("BlockEntityTag")).result();if(nested.isPresent())input=nested.get();}
            Object main=input.get("housingTexture"),side=input.get("SideTexture"),layout=input.get("MainTextureLayout"),sides=input.get("SlabTileSides"),enabled=input.get("FaceTexturesEnabled"),faceTag=input.get("FaceTextures"),tread=input.get(StairState.TREAD),roll=input.get(StairState.ROLL);
            int[] faces={-1,-1,-1,-1,-1,-1};if(faceTag instanceof IntArrayTag array&&array.getAsIntArray().length==6)for(int i=0;i<6;i++){int choice=array.getAsIntArray()[i];if(choice>=0&&ModernFinishCatalog.validChoice(choice))faces[i]=choice;}
            return com.mojang.serialization.DataResult.success(new ItemAppearance(number(main),side instanceof IntTag tag?tag.getAsInt():-1,layout instanceof IntTag tag?tag.getAsInt():0,layout instanceof IntTag,sides instanceof ByteTag,bool(sides),bool(enabled),faces,StairState.readTread(tread instanceof IntTag,number(tread)),StairState.readRoll(number(roll))));
        }
        private int number(Object value){return value instanceof NumericTag tag?tag.getAsInt():0;}private boolean bool(Object value){return value instanceof NumericTag tag&&tag.getAsByte()!=0;}
    };
    private record ItemAppearance(int main,int side,int rawLayout,boolean typedLayout,boolean typedSides,boolean sides,boolean enabled,int[] faces,int tread,int roll){}
    private final com.google.common.cache.Cache<net.minecraft.world.item.component.CustomData,BakedModel> items=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(128).build();
    private final ItemOverrides overrides=new ItemOverrides(){
        @Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){
            var custom=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);if(custom==null)custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(custom==null)return ProgrammableShapeModel.this;
            return resolveItem(custom);
        }
    };
    BakedModel resolveItem(net.minecraft.world.item.component.CustomData custom){
            var cached=items.getIfPresent(custom);if(cached!=null)return cached;var value=custom.read(ITEM).getOrThrow();int layout=slab?HousingTexturePolicy.itemLayout(value.typedLayout,value.rawLayout,value.typedSides,value.sides):HousingTexturePolicy.stairItemLayout(value.typedLayout,value.rawLayout,value.sides);
            var housing=new ProgrammableBlockDialog.State(value.main,layout,!slab&&value.enabled,value.faces,-1);var appearance=new ProgrammableShapeEntity.Appearance(housing,value.side,new StairState(value.tread,value.roll));var result=new ProgrammableShapeModel(originalModel,bake,slab,true,appearance,true);items.put(custom,result);return result;
    }
    @Override public ItemOverrides getOverrides(){return item?overrides:originalModel.getOverrides();}
    private volatile ItemTransforms padded;
    @Override public ItemTransforms getTransforms(){if(!item||slab)return originalModel.getTransforms();var found=padded;if(found==null)padded=found=NativeItemPadding.fit(this,originalModel.getTransforms());return found;}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    private final List<BakedModel> passes=List.of(this);
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return passes;}
}
