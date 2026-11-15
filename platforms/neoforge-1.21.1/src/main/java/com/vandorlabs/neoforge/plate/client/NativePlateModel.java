package com.vandorlabs.neoforge;

import com.google.common.cache.*;
import com.vandorlabs.neoforge.plate.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Cached retexturing retains the original thin side UV ranges and material content region. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePlateModel extends BakedModelWrapper<BakedModel> {
    private record Key(ProgrammableModel.MaterialSnapshot material,Direction side){}
    private final java.util.function.Function<String,TextureAtlasSprite> sprites;
    private final Cache<Key,List<BakedQuad>> meshes=CacheBuilder.newBuilder().maximumWeight(2*1024*1024).weigher((Key key,List<BakedQuad> quads)->128+quads.stream().mapToInt(q->96+q.getVertices().length*4).sum()).build();
    private NativePlateModel(BakedModel source,java.util.function.Function<String,TextureAtlasSprite> sprites){super(source);this.sprites=sprites;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){if(!NativePlateBindings.enabled())return;
        java.util.function.Function<String,TextureAtlasSprite> sprites=name->event.getTextureGetter().apply(new Material(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,ResourceLocation.parse(name)));
        var wrappers=new IdentityHashMap<BakedModel,NativePlateModel>();
        for(var state:NativePlateBindings.BLOCK.getStateDefinition().getPossibleStates()){var id=BlockModelShaper.stateToModelLocation(state);var base=Objects.requireNonNull(event.getModels().get(id));event.getModels().put(id,wrappers.computeIfAbsent(base,b->new NativePlateModel(b,sprites)));}
        var id=new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_pressure_plate"),"inventory");var source=Objects.requireNonNull(event.getModels().get(id));
        event.getModels().put(id,new NativeTrapdoorItemModel(source,stack->{var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);return data==null?NativePlateEntity.DEFAULT_TEXTURE:data.copyTag().getCompound(NativePlateEntity.ITEM_SETTINGS).contains(com.vandorlabs.persistence.PressurePlateSettings.TEXTURE,net.minecraft.nbt.Tag.TAG_INT)?data.copyTag().getCompound(NativePlateEntity.ITEM_SETTINGS).getInt(com.vandorlabs.persistence.PressurePlateSettings.TEXTURE):NativePlateEntity.DEFAULT_TEXTURE;},sprites));
    }
    private ProgrammableModel.MaterialSnapshot material(int choice){var custom=com.vandorlabs.neoforge.client.NativeBlockMaterials.material(choice);var entry=ModernFinishCatalog.entry(choice);return new ProgrammableModel.MaterialSnapshot(custom==null?sprites.apply(entry.texture()):custom.lower(),custom==null?entry.u():1,custom==null?entry.v():1);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        Integer choice=data.get(NativePlateEntity.TEXTURE);var key=new Key(material(choice==null?NativePlateEntity.DEFAULT_TEXTURE:choice),side);var found=meshes.getIfPresent(key);if(found!=null)return found;
        var result=new ArrayList<BakedQuad>();for(var quad:originalModel.getQuads(state,side,random,ModelData.EMPTY,layer))result.add(NativeItemQuads.retexture(quad,key.material.sprite(),key.material.usedU(),key.material.usedV()));var immutable=List.copyOf(result);meshes.put(key,immutable);return immutable;
    }
}
