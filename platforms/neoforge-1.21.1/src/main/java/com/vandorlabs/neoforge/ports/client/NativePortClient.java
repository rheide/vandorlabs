package com.vandorlabs.neoforge.ports.client;

import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.ports.ResourcePortSettings;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Fixed face-quad caches are rebuilt with the atlas, never allocated during block drawing. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePortClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativePortBindings.enabled())event.register(NativePortBindings.MENU,NativePortScreen::new);}
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){if(!NativePortBindings.enabled())return;var models=new EnumMap<NativePortKind,BakedModel>(NativePortKind.class);event.getModels().replaceAll((id,model)->{if(id.getVariant().equals("inventory")||!id.id().getNamespace().equals("vandorlabs"))return model;for(var kind:NativePortKind.values())if(id.id().getPath().equals(kind.id))return models.computeIfAbsent(kind,key->new PortModel(model,key,event));return model;});}
    public static BakedQuad retextureQuad(BakedQuad quad,TextureAtlasSprite sprite){var old=quad.getSprite();int[] data=quad.getVertices().clone();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++){int index=vertex*stride;float u=(Float.intBitsToFloat(data[index+4])-old.getU0())/(old.getU1()-old.getU0()),v=(Float.intBitsToFloat(data[index+5])-old.getV0())/(old.getV1()-old.getV0());data[index+4]=Float.floatToRawIntBits(sprite.getU0()+u*(sprite.getU1()-sprite.getU0()));data[index+5]=Float.floatToRawIntBits(sprite.getV0()+v*(sprite.getV1()-sprite.getV0()));}return new BakedQuad(data,quad.getTintIndex(),quad.getDirection(),sprite,quad.isShade(),quad.hasAmbientOcclusion());}
    private static final class PortModel extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad>[][] faces;
        private final LinkedHashMap<Integer,List<BakedQuad>[]> housings=new LinkedHashMap<>(16,.75F,true);private long housingBytes;
        @SuppressWarnings("unchecked") PortModel(BakedModel original,NativePortKind kind,ModelEvent.ModifyBakingResult event){super(original);faces=(List<BakedQuad>[][])new List<?>[6][5];for(var side:Direction.values()){var source=original.getQuads(null,side,RandomSource.create(0));for(int mode=0;mode<5;mode++){String name=mode==0?"port_hull":kind.texture+"_"+(mode<=2?"in":"out")+"_"+(mode%2==0?"on":"off");var sprite=event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS,ResourceLocation.fromNamespaceAndPath("vandorlabs","blocks/resource_ports/"+name)));var quads=new ArrayList<BakedQuad>(source.size());for(var quad:source)quads.add(retextureQuad(quad,sprite));faces[side.get3DDataValue()][mode]=List.copyOf(quads);}}}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(side==null)return List.of();int face=side.get3DDataValue();var visual=data.get(NativeResourcePortEntity.VISUAL);ResourcePortSettings settings=visual==null?ResourcePortSettings.placed(state==null?Direction.NORTH.get3DDataValue():state.getValue(NativePortBlock.FACING).get3DDataValue()):visual.settings();if(settings.face(face)==ResourcePortSettings.Face.DISABLED&&settings.housingChoice>=0)return housing(settings.housingChoice,side);int mode=switch(settings.face(face)){case DISABLED->0;case INPUT->1;case OUTPUT->3;};if(mode!=0&&visual!=null&&!visual.inert()&&(visual.lit()&(1<<face))!=0)mode++;return faces[face][mode];}
        private synchronized List<BakedQuad> housing(int choice,Direction side){var found=housings.get(choice);if(found!=null)return found[side.get3DDataValue()];var sprite=net.minecraft.client.Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(com.vandorlabs.neoforge.ModernFinishCatalog.option(choice).texture()));@SuppressWarnings("unchecked") List<BakedQuad>[] result=(List<BakedQuad>[])new List<?>[6];long bytes=128;for(var face:Direction.values()){var quads=new ArrayList<BakedQuad>();for(var original:faces[face.get3DDataValue()][0]){var quad=retextureQuad(original,sprite);quads.add(quad);bytes+=128L+quad.getVertices().length*4L;}result[face.get3DDataValue()]=List.copyOf(quads);bytes+=32;}if(housings.size()>=128||housingBytes+bytes>2*1024*1024){housings.clear();housingBytes=0;}if(bytes<=2*1024*1024){housings.put(choice,result);housingBytes+=bytes;}return result[side.get3DDataValue()];}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    }
    private NativePortClient(){}
}
