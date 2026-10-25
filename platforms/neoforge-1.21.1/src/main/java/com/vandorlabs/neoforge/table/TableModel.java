package com.vandorlabs.neoforge.table;

import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Select canonical baked variants from the supplied render-region snapshot, never a live world. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TableModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockState> CONNECTIONS=new ModelProperty<>();
    private final Map<BlockState,BakedModel> variants;
    private TableModel(BakedModel base,Map<BlockState,BakedModel> variants){super(base);this.variants=variants;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        Map<BlockState,BakedModel> models=new IdentityHashMap<>();
        for(BlockState state:TableRegistration.BLOCK.get().getStateDefinition().getPossibleStates())models.put(state,Objects.requireNonNull(event.getModels().get(BlockModelShaper.stateToModelLocation(state)),"Missing table variant "+state));
        Map<BlockState,BakedModel> immutable=Collections.unmodifiableMap(models);
        for(var entry:models.entrySet())event.getModels().put(BlockModelShaper.stateToModelLocation(entry.getKey()),new TableModel(entry.getValue(),immutable));
    }
    @Override public ModelData getModelData(BlockAndTintGetter region,BlockPos pos,BlockState state,ModelData data){
        // RenderChunkRegion contains the loaded chunks captured for this section's rebuild.
        // The two reads also work with a client-thread Level supplied by picking/debug callers.
        BlockState connected=TableRegistration.BLOCK.get().actual(state,region,pos);
        return data.derive().with(CONNECTIONS,connected).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType){
        BlockState connected=data.get(CONNECTIONS);BakedModel selected=connected==null?originalModel:variants.getOrDefault(connected,originalModel);
        return selected.getQuads(connected==null?state:connected,side,random,data,renderType);
    }
}
