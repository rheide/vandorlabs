package com.vandorlabs.neoforge;

import com.google.common.cache.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.neoforge.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import com.vandorlabs.render.ItemIconBounds;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Bake-owned inventory geometry; world contact masks and authoritative motion never enter icons. */
public final class NativeCanopyItemModel extends BakedModelWrapper<BakedModel> {
    public static final List<String> KINDS=NativeCanopyKinds.IDS;
    private static final int BUDGET=8*1024*1024;
    private record Key(int depth,int scale,int shade,int tint,int angle,int material,int layout) {
        static Key of(CanopySettingsData s){return new Key(s.depth,s.scale,s.shade,s.tint,s.angle,s.material,s.textureLayout());}
    }
    private final String kind;
    private final NativeFlatWallModel.Bake materials;
    private final Function<Material,TextureAtlasSprite> textures;
    // Identity lookup avoids copying unrelated item NBT on every rendered frame.
    private record Binding(java.lang.ref.WeakReference<CustomData> data,Key key,java.lang.ref.WeakReference<Selected> model){}
    private final Binding[] bindings=new Binding[256];private int nextBinding;
    private final Cache<Key,Selected> meshes=CacheBuilder.newBuilder().maximumWeight(BUDGET).weigher((Key key,Selected value)->value.bytes).build();
    private final Key plain=Key.of(CanopySettingsData.fresh());
    private CustomData hotData;private Selected hotModel;
    private static List<java.lang.ref.WeakReference<NativeCanopyItemModel>> live=List.of();
    private final ItemOverrides selector=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){return selected(stack);}};
    public NativeCanopyItemModel(BakedModel fallback,String kind,NativeFlatWallModel.Bake materials,Function<Material,TextureAtlasSprite> textures){super(fallback);this.kind=kind;this.materials=materials;this.textures=textures;}
    public static void prepare(Map<ModelResourceLocation,BakedModel> models,NativeFlatWallModel.Bake materials,Function<Material,TextureAtlasSprite> textures){
        if(!NativeCanopySpriteSource.active())return;
        for(var ref:live){var model=ref.get();if(model!=null)model.clear();}var current=new ArrayList<java.lang.ref.WeakReference<NativeCanopyItemModel>>();
        models.replaceAll((id,model)->id.getVariant().equals("inventory")&&id.id().getNamespace().equals("vandorlabs")&&KINDS.contains(id.id().getPath())?track(new NativeCanopyItemModel(model,id.id().getPath(),materials,textures),current):model);
        live=List.copyOf(current);
    }
    private static NativeCanopyItemModel track(NativeCanopyItemModel model,List<java.lang.ref.WeakReference<NativeCanopyItemModel>> current){current.add(new java.lang.ref.WeakReference<>(model));return model;}
    @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class WorldEvents {
        @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())for(var ref:live){var model=ref.get();if(model!=null)model.clear();}}
    }
    private Selected selected(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(hotModel!=null&&hotData==data)return hotModel;Key key=plain;
        if(data!=null){for(var binding:bindings)if(binding!=null&&binding.data.get()==data){var found=binding.model.get();if(found!=null){hotData=data;hotModel=found;return found;}key=binding.key;break;}
            if(key==plain)key=Key.of(NativeCanopySettings.read(data.getUnsafe()).data());}
        var model=selected(key);if(data!=null){bindings[nextBinding]=new Binding(new java.lang.ref.WeakReference<>(data),key,new java.lang.ref.WeakReference<>(model));nextBinding=(nextBinding+1)%bindings.length;}hotData=data;hotModel=model;return model;
    }
    private Selected selected(Key key){var found=meshes.getIfPresent(key);if(found==null){found=new Selected(key);if(found.bytes<=BUDGET)meshes.put(key,found);}return found;}
    public void clear(){Arrays.fill(bindings,null);nextBinding=0;meshes.invalidateAll();hotData=null;hotModel=null;}
    public long retainedBytes(){meshes.cleanUp();long cost=meshes.asMap().values().stream().mapToLong(value->value.bytes).sum();return cost+(hotModel!=null&&!meshes.asMap().containsValue(hotModel)?hotModel.bytes:0);}
    @Override public ItemOverrides getOverrides(){return selector;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return selected(plain).getQuads(state,side,random);}
    @Override public ItemTransforms getTransforms(){return selected(plain).getTransforms();}
    private ProgrammableModel.MaterialSnapshot panel(int choice){return materials.surfaces.get(materials.index(choice));}
    private ProgrammableModel.MaterialSnapshot surface(CanopyFace face,int material){
        if(face.baseMaterial.equals("panel"))return panel(material);String name=face.material;
        if(!name.contains(":"))name="vandorlabs:canopy/"+name;
        else if(name.startsWith("vandorlabs:blocks/programmable_glass/low/"))name="vandorlabs:glass-palette/"+name.substring("vandorlabs:blocks/programmable_glass/low/".length());
        return new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(name))),1,1);
    }
    private static ItemTransform transform(float[] f){return new ItemTransform(new Vector3f(f[0],f[1],f[2]),new Vector3f(f[3],f[4],f[5]),new Vector3f(f[6],f[7],f[8]));}
    private final class Selected extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad> quads;private final ItemTransforms transforms;private final TextureAtlasSprite particle;private final int bytes;
        private final List<BakedModel> passes=List.of(this);
        private final List<RenderType> layers=List.of(RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        Selected(Key key){super(NativeCanopyItemModel.this.originalModel);
            var fresh=CanopySettingsData.fresh();var s=new CanopySettingsData(key.depth,key.scale,key.shade,key.tint,fresh.mount,fresh.motion,fresh.hinge,key.angle,key.material,false,false,false,key.layout==1,true,key.layout,fresh.channels);
            double aspect=s.material<0?1:panel(s.material).usedU()/panel(s.material).usedV();
            var faces=CanopyItemGeometry.faces(NativeCanopyModels.builtin()::model,kind,s,aspect,choice->"vandorlabs:blocks/"+ModernFinishCatalog.option(choice).id());
            particle=surface(faces.getFirst(),s.material).sprite();var result=new ArrayList<BakedQuad>();var bounds=new ItemIconBounds(25,225,0,1,1,1,0);
            for(var face:faces){var material=surface(face,s.material);var sprite=material.sprite();
                CanopyItemEmission.emit(face,CanopyEmission.opacity(face,s.material,s.tint),new CanopyEmission.Sprite(sprite.getU0(),sprite.getU0()+(sprite.getU1()-sprite.getU0())*material.usedU(),sprite.getV0(),sprite.getV0()+(sprite.getV1()-sprite.getV0())*material.usedV()),new CanopyItemEmission.Sink(){
                    int[] words;int direction,normal,count;
                    public void quad(int direction,int normal){this.direction=direction;this.normal=normal;words=new int[32];count=0;}
                    public void vertex(float x,float y,float z,float u,float v,int color){int at=count++*8;words[at]=Float.floatToRawIntBits(x);words[at+1]=Float.floatToRawIntBits(y);words[at+2]=Float.floatToRawIntBits(z);words[at+3]=color;words[at+4]=Float.floatToRawIntBits(u);words[at+5]=Float.floatToRawIntBits(v);words[at+7]=normal;bounds.include(x,y,z);if(count==4)result.add(new BakedQuad(words,-1,Direction.from3DDataValue(direction),sprite,true,false));}
                });
            }
            quads=List.copyOf(result);bytes=512+quads.size()*(96+32*4);var gui=transform(bounds.fit());var third=ItemTransform.NO_TRANSFORM;var right=third;var left=third;
            if(kind.equals("regular_canopy_glass")){third=transform(new float[]{75,45,0,0,2.5F/16,0,.375F,.375F,.375F});right=transform(new float[]{0,45,0,0,0,0,.4F,.4F,.4F});left=transform(new float[]{0,225,0,0,0,0,.4F,.4F,.4F});}
            transforms=new ItemTransforms(third,third,left,right,ItemTransform.NO_TRANSFORM,gui,ItemTransform.NO_TRANSFORM,ItemTransform.NO_TRANSFORM);
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return side==null?quads:List.of();}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return side==null?quads:List.of();}
        @Override public boolean useAmbientOcclusion(){return false;}
        @Override public boolean isGui3d(){return true;}
        @Override public boolean isCustomRenderer(){return false;}
        @Override public TextureAtlasSprite getParticleIcon(){return particle;}
        @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
        @Override public ItemTransforms getTransforms(){return transforms;}
        @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack poses,boolean left){transforms.getTransform(context).apply(left,poses);return this;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return passes;}
        @Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous){return layers;}
    }
}
