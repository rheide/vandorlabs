package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.glass.*;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;

/** Authored default rails only; panes and configurable frames belong to the BER. */
public final class NativeFlatGlassModel extends BakedModelWrapper<BakedModel> {
 private static final RenderType SOLID=RenderType.solid();
 private static final ChunkRenderTypeSet TYPES=ChunkRenderTypeSet.of(SOLID);
 private final NativeFlatGlassRenderer.Bake bake;
 public NativeFlatGlassModel(BakedModel empty,NativeFlatGlassRenderer.Bake bake){super(empty);this.bake=java.util.Objects.requireNonNull(bake);}
 @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return TYPES;}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
  if(side!=null||layer!=null&&layer!=SOLID)return List.of();
  var publication=data.get(NativeGlassEntity.GLASS);if(publication==null)return List.of();
  var visual=publication.visual();return visual.authoredFrame()?bake.authored(visual.pose(),visual.authoredMask()):List.of();
 }
 @Override public boolean useAmbientOcclusion(){return false;}
 @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return TriState.FALSE;}
}
