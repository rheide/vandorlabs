package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.NativeMountedControlGeometry;
import com.vandorlabs.neoforge.NativeMountedControlModel;
import com.vandorlabs.neoforge.NativeMountedSwitchBlock;
import java.util.*;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Caller-owned model bridge, usable by private acceptance blocks before public registration. */
public final class NativeCredentialReaderModels {
    private NativeCredentialReaderModels(){}
    public static void prepare(Map<ModelResourceLocation,BakedModel> models,List<? extends NativeCredentialReaderBlock> blocks){
        for(var block:blocks){
            var name=BuiltInRegistries.BLOCK.getKey(block);String item=NativeReaderAssets.itemModel(block.chip());models.put(new ModelResourceLocation(name,"inventory"),Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse(item))),item));
            for(var state:block.getStateDefinition().getPossibleStates()){
                int rotation=NativeMountedControlGeometry.rotation(state,state.getValue(NativeMountedSwitchBlock.ROTATION));String frame=NativeReaderAssets.model(block.chip(),state.getValue(NativeMountedSwitchBlock.FACING).getSerializedName(),rotation);
                models.put(BlockModelShaper.stateToModelLocation(state),Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse(frame))),frame));
            }
        }
        NativeMountedControlModel.prepare(models,blocks);
    }
}
