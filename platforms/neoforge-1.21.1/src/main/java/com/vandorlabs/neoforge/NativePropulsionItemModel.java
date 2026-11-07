package com.vandorlabs.neoforge;

import com.google.common.cache.*;
import com.vandorlabs.neoforge.propulsion.NativePropulsionItem;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Source inventory selector: only shape changes artwork; world power/material settings do not. */
public final class NativePropulsionItemModel extends BakedModelWrapper<BakedModel> {
 private final Selected[] selections=new Selected[3];
 private final Cache<CustomData,Integer> choices=CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
 public NativePropulsionItemModel(BakedModel fallback,String id,NativePropulsionModel.Bake bake){super(fallback);for(int shape=0;shape<3;shape++){var source=bake.itemSource(id,shape);selections[shape]=new Selected(source==null?fallback:source,id,shape,bake);}}
 private int shape(ItemStack stack){var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(component==null)component=stack.get(DataComponents.CUSTOM_DATA);if(component==null)return 0;var cached=choices.getIfPresent(component);if(cached!=null)return cached;int value=0;try{var tag=NativePropulsionItem.configuration(stack);if(tag!=null)value=Math.max(0,Math.min(2,tag.getInt("PropulsionShape")));}catch(IllegalArgumentException malformed){}choices.put(component,value);return value;}
 private final ItemOverrides selector=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){var selected=selections[shape(stack)];var source=selected.source();var overridden=source.getOverrides().resolve(source,stack,world,entity,seed);return overridden==null||overridden==source?selected:overridden;}};
 @Override public ItemOverrides getOverrides(){return selector;}
 private static final class Selected extends BakedModelWrapper<BakedModel> {
  private final String id;private final int shape;private final NativePropulsionModel.Bake bake;private final ItemTransforms transforms;private final List<BakedModel> passes=List.of(this);
  Selected(BakedModel source,String id,int shape,NativePropulsionModel.Bake bake){super(source);this.id=id;this.shape=shape;this.bake=bake;transforms=NativeItemPadding.fit(this,source.getTransforms());}
  BakedModel source(){return originalModel;}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return bake.itemQuads(id,shape,side,random);}
  @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){return bake.itemQuads(id,shape,side,random);}
  @Override public ItemTransforms getTransforms(){return transforms;}
  @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
  @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return passes;}
  @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
 }
}
