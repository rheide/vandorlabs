package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.tiles.CustomMaterialIdentity;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Immutable registry/bake snapshots. No world reads, data fixers, tag copies or registration during rendering. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeBlockMaterials {
    public record Material(ModernFinishCatalog.Entry entry,TextureAtlasSprite lower,TextureAtlasSprite upper,boolean door){}
    private static volatile Map<Integer,Material> staged=Map.of();
    private static volatile Map<Integer,Material> active=Map.of();
    public static TextureAtlasSprite selectedSprite(Function<BlockState,BakedModel> models,BlockState state,boolean upper,TextureAtlasSprite fallback){
        if(state.getBlock() instanceof DoorBlock)state=state.setValue(DoorBlock.HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
        BakedModel model=models.apply(state);if(model==null)return fallback;
        if(state.getBlock() instanceof DoorBlock){
            for(var face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(var quad:model.getQuads(state,face,RandomSource.create(0),ModelData.EMPTY,null))if(usable(quad.getSprite()))return quad.getSprite();
            for(var quad:model.getQuads(state,null,RandomSource.create(0),ModelData.EMPTY,null))if(quad.getDirection().getAxis()!=Direction.Axis.Y&&usable(quad.getSprite()))return quad.getSprite();
        }
        var sprite=model.getParticleIcon(ModelData.EMPTY);return usable(sprite)?sprite:fallback;
    }
    private static boolean usable(TextureAtlasSprite sprite){return sprite!=null&&!sprite.contents().name().getPath().equals("missingno");}
    public static Map<Integer,Material> bake(Map<ModelResourceLocation,BakedModel> models,TextureAtlasSprite fallback){
        Map<Integer,Material> result=new HashMap<>();
        Function<BlockState,BakedModel> lookup=state->models.get(BlockModelShaper.stateToModelLocation(state));
        NativeMaterialRegistry.snapshot().states().forEach((choice,state)->{
            var lower=selectedSprite(lookup,state,false,fallback);var upper=selectedSprite(lookup,state,true,fallback);
            var entry=new ModernFinishCatalog.Entry("sample_"+Integer.toHexString(choice),state.getBlock().getName().getString(),"Custom",true,false,choice,lower.contents().name().toString(),1,1);
            result.put(choice,new Material(entry,lower,upper,state.getBlock() instanceof DoorBlock));
        });
        return staged=Map.copyOf(result);
    }
    @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){
        active=staged;Map<Integer,ModernFinishCatalog.Entry> entries=new HashMap<>();active.forEach((choice,material)->entries.put(choice,material.entry()));ModernFinishCatalog.publishCustom(entries);
    }
    public static Material material(int choice){return active.get(choice);}
    public static Map<Integer,Material> snapshot(){return active;}
    public static int sample(ItemStack stack){
        if(stack.isEmpty()||!(stack.getItem() instanceof BlockItem item))return -1;
        if(stack.is(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get())||stack.is(VandorLabsBootstrap.TRIGGER_ITEM.get())){
            var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);if(data==null)data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if(data==null)return 0;
            var tag=data.copyTag();if(tag.contains("BlockEntityTag",10))tag=tag.getCompound("BlockEntityTag");
            return ModernFinishCatalog.storedChoice(tag.getInt(com.vandorlabs.persistence.HousingSettings.MAIN));
        }
        Integer choice=NativeMaterialRegistry.snapshot().samples().get(item.getBlock());return choice!=null&&active.containsKey(choice)?choice:-1;
    }
    public static Set<Integer> ambiguous(){return NativeMaterialRegistry.snapshot().ambiguous();}
    public static int samples(){return NativeMaterialRegistry.snapshot().samples().size();}
    private NativeBlockMaterials(){}
}
