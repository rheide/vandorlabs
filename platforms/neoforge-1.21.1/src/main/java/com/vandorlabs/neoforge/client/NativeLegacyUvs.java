package com.vandorlabs.neoforge.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Adapt canonical FaceBakery UVs to the legacy 0.999/0.001 opposite-corner inset. */
public final class NativeLegacyUvs {
    private NativeLegacyUvs(){}
    /** Call on native canonical quads before material/layout mapping, never on authored primitive quads. */
    public static BakedQuad correct(BakedQuad source){
        float shrink=source.getSprite().uvShrinkRatio();
        if(!Float.isFinite(shrink)||shrink<0||shrink>=1)throw new IllegalArgumentException("Invalid canonical sprite UV shrink");
        double coefficient=.998/(1-shrink);int[] vertices=source.getVertices().clone();int stride=vertices.length/4;
        if(vertices.length%4!=0||stride<6)throw new IllegalArgumentException("Unsupported canonical vertex format");
        for(int uv=4;uv<=5;uv++){
            double center=0;for(int i=0;i<4;i++)center+=Float.intBitsToFloat(vertices[i*stride+uv]);center/=4;
            for(int i=0;i<4;i++){int offset=i*stride+uv;vertices[offset]=Float.floatToRawIntBits((float)(center+(Float.intBitsToFloat(vertices[offset])-center)*coefficient));}
        }
        return new BakedQuad(vertices,source.getTintIndex(),source.getDirection(),source.getSprite(),source.isShade(),source.hasAmbientOcclusion());
    }
    public static BakedModel wrap(BakedModel source){return source instanceof Adapter?source:new Adapter(source);}
    private static List<BakedQuad> correct(List<BakedQuad> source){List<BakedQuad> result=new ArrayList<>(source.size());for(BakedQuad quad:source)result.add(correct(quad));return List.copyOf(result);}
    /** Preserve SDK routing through overrides, transforms and render passes instead of unwrapping. */
    private static final class Adapter extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides=new ItemOverrides(){
            @Override public BakedModel resolve(BakedModel model,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){BakedModel resolved=originalModel.getOverrides().resolve(originalModel,stack,level,entity,seed);return resolved==null?null:resolved==originalModel?Adapter.this:wrap(resolved);}
        };
        Adapter(BakedModel source){super(source);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random){return correct(originalModel.getQuads(state,face,random));}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random,ModelData data,RenderType layer){return correct(originalModel.getQuads(state,face,random,data,layer));}
        @Override public ItemOverrides getOverrides(){return overrides;}
        @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){List<BakedModel> result=new ArrayList<>();for(BakedModel pass:originalModel.getRenderPasses(stack,fabulous))result.add(pass==originalModel?this:wrap(pass));return List.copyOf(result);}
        @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){BakedModel transformed=originalModel.applyTransform(context,pose,left);return transformed==originalModel?this:wrap(transformed);}
    }
}
