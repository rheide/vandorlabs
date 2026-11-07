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
    private static ResourceLocation listed(NativeCredentialReaderBlock block,String prefix,String suffix){var id=BuiltInRegistries.BLOCK.getKey(block);return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),prefix+id.getPath()+suffix);}
    public static boolean canonicalStates(NativeCredentialReaderBlock block){String path="/assets/vandorlabs/blockstates/"+block.mountedFamily()+".json";try(var builtin=NativeCredentialReaderModels.class.getResourceAsStream(path)){if(builtin==null)return false;var resources=net.minecraft.client.Minecraft.getInstance().getResourceManager();var resource=resources.getResource(listed(block,"blockstates/",".json")).orElseGet(()->resources.getResource(ResourceLocation.parse("vandorlabs:blockstates/"+block.mountedFamily()+".json")).orElseThrow());try(var original=new java.io.InputStreamReader(builtin,java.nio.charset.StandardCharsets.UTF_8);var active=resource.openAsReader()){return com.google.gson.JsonParser.parseReader(original).equals(com.google.gson.JsonParser.parseReader(active));}}catch(java.io.IOException|RuntimeException unsupported){return false;}}
    public static boolean canonicalItem(NativeCredentialReaderBlock block){var resources=net.minecraft.client.Minecraft.getInstance().getResourceManager();var actual=listed(block,"item/","");boolean supplied=resources.getResource(ResourceLocation.fromNamespaceAndPath(actual.getNamespace(),"models/"+actual.getPath()+".json")).isPresent();return com.vandorlabs.neoforge.NativeDoorArtwork.canonicalGeometry(NativeReaderAssets.itemModel(block.chip()),supplied?actual.toString():NativeReaderAssets.itemModel(block.chip()),resources);}
    public static boolean canonicalFrame(NativeCredentialReaderBlock block,net.minecraft.world.level.block.state.BlockState state){if(state.getValue(NativeMountedSwitchBlock.ROTATION)!=NativeMountedControlGeometry.rotation(state,state.getValue(NativeMountedSwitchBlock.ROTATION)))return false;String id=NativeReaderAssets.model(block.chip(),state.getValue(NativeMountedSwitchBlock.FACING).getSerializedName(),NativeMountedControlGeometry.rotation(state,state.getValue(NativeMountedSwitchBlock.ROTATION)));return com.vandorlabs.neoforge.NativeDoorArtwork.canonicalGeometry(id,net.minecraft.client.Minecraft.getInstance().getResourceManager());}
    public static void prepare(Map<ModelResourceLocation,BakedModel> models,List<? extends NativeCredentialReaderBlock> blocks){
        for(var block:blocks){
            var name=BuiltInRegistries.BLOCK.getKey(block);var resources=net.minecraft.client.Minecraft.getInstance().getResourceManager();String item=NativeReaderAssets.itemModel(block.chip());if(resources.getResource(listed(block,"models/item/",".json")).isEmpty())models.put(new ModelResourceLocation(name,"inventory"),Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse(item))),item));if(resources.getResource(listed(block,"blockstates/",".json")).isPresent())continue;
            for(var state:block.getStateDefinition().getPossibleStates()){
                int rotation=NativeMountedControlGeometry.rotation(state,state.getValue(NativeMountedSwitchBlock.ROTATION));String frame=NativeReaderAssets.model(block.chip(),state.getValue(NativeMountedSwitchBlock.FACING).getSerializedName(),rotation);
                models.put(BlockModelShaper.stateToModelLocation(state),Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse(frame))),frame));
            }
        }
        NativeMountedControlModel.prepare(models,blocks);
    }
}
