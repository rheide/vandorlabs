package com.vandorlabs.neoforge;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vandorlabs.neoforge.client.NativeBlockMaterials;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Bake-owned static inventory geometry. Retexturing preserves each authored sub-face UV range. */
public final class NativeTrapdoorItemModel extends BakedModelWrapper<BakedModel> {
    private static final int BUDGET=4*1024*1024;
    private static final Direction[] FACES={null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private record Surface(TextureAtlasSprite sprite,float u,float v){}
    private final ToIntFunction<ItemStack> choice;
    private final Cache<CustomData,Integer> choices=CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
    private final Function<String,TextureAtlasSprite> sprites;
    private final Cache<Surface,Selected> meshes=CacheBuilder.newBuilder().maximumWeight(BUDGET)
        .weigher((Surface key,Selected value)->value.bytes).build();
    private final ItemOverrides selector=new ItemOverrides(){
        @Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){
            var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if(component==null)component=stack.get(DataComponents.CUSTOM_DATA);
            Integer selected=component==null?null:choices.getIfPresent(component);
            if(selected==null){selected=choice.applyAsInt(stack);if(component!=null)choices.put(component,selected);}
            return selected(selected);
        }
    };
    public NativeTrapdoorItemModel(BakedModel source,ToIntFunction<ItemStack> choice,Function<String,TextureAtlasSprite> sprites){
        super(Objects.requireNonNull(source));this.choice=Objects.requireNonNull(choice);this.sprites=Objects.requireNonNull(sprites);
    }
    private Selected selected(int choice){
        var custom=NativeBlockMaterials.material(choice);
        var entry=ModernFinishCatalog.entry(choice);
        var surface=new Surface(custom==null?sprites.apply(entry.texture()):custom.lower(),custom==null?entry.u():1,custom==null?entry.v():1);
        var result=meshes.getIfPresent(surface);
        if(result==null){result=new Selected(surface);if(result.bytes<=BUDGET)meshes.put(surface,result);}
        return result;
    }
    @Override public ItemOverrides getOverrides(){return selector;}
    private final class Selected extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad>[] faces;
        private final TextureAtlasSprite sprite;
        private final int bytes;
        private final ItemTransforms transforms;
        private final List<BakedModel> passes=List.of(this);
        @SuppressWarnings("unchecked") private Selected(Surface surface){
            super(NativeTrapdoorItemModel.this.originalModel);sprite=surface.sprite();faces=new List[FACES.length];long cost=512;
            for(int side=0;side<FACES.length;side++){
                var result=new ArrayList<BakedQuad>();
                for(var quad:originalModel.getQuads(null,FACES[side],RandomSource.create(42),ModelData.EMPTY,null)){
                    var changed=NativeItemQuads.retexture(quad,sprite,surface.u(),surface.v());
                    result.add(changed);cost+=96L+changed.getVertices().length*4L;
                }
                faces[side]=List.copyOf(result);
            }
            bytes=(int)Math.min(Integer.MAX_VALUE,cost);
            transforms=NativeItemPadding.fit(this,originalModel.getTransforms());
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return faces[side==null?0:side.ordinal()+1];}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return getQuads(state,side,random);}
        @Override public TextureAtlasSprite getParticleIcon(){return sprite;}
        @Override public TextureAtlasSprite getParticleIcon(ModelData data){return sprite;}
        @Override public ItemTransforms getTransforms(){return transforms;}
        @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return passes;}
        @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
    }
}
