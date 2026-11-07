package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.canopy.NativeCanopyEntity;
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

/** Fixed regular glass remains chunk geometry; workers consume only immutable prepared model data. */
public final class NativeCanopyTerrainModel extends BakedModelWrapper<BakedModel> {
    private static final RenderType SOLID=RenderType.solid(),TRANSLUCENT=RenderType.translucent();
    private static final ChunkRenderTypeSet TYPES=ChunkRenderTypeSet.of(SOLID,TRANSLUCENT);
    private final NativeCanopyRenderer.Bake bake;
    public NativeCanopyTerrainModel(BakedModel empty,NativeCanopyRenderer.Bake bake){super(empty);this.bake=java.util.Objects.requireNonNull(bake);}
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return TYPES;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        if(side!=null||layer!=null&&layer!=SOLID&&layer!=TRANSLUCENT)return List.of();var visual=data.get(NativeCanopyEntity.TERRAIN);if(visual==null||!visual.kind().equals("regular_canopy_glass"))return List.of();var draw=bake.draw(visual,0);return layer==null?draw.terrain():draw.terrain(layer==TRANSLUCENT);
    }
    @Override public boolean useAmbientOcclusion(){return false;}
    @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return TriState.FALSE;}
}
