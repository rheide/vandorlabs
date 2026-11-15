package com.vandorlabs.ports.client;

import com.vandorlabs.ports.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.*;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Five immutable face variants per family. Terrain meshing needs no world reads or combinations cache. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class ResourcePortModel implements IBakedModel {
    private final IBakedModel fallback;
    private final List<BakedQuad>[][] faces;
    private final HousingQuads housing;
    private ResourcePortModel(IBakedModel fallback,List<BakedQuad>[][] faces,HousingQuads housing){this.fallback=fallback;this.faces=faces;this.housing=housing;}
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){
        event.getMap().registerSprite(new ResourceLocation("vandorlabs","blocks/resource_ports/port_hull"));
        for(ResourcePortBlock.Kind kind:ResourcePortBlock.Kind.values())for(String direction:new String[]{"in","out"})for(String state:new String[]{"off","on"})event.getMap().registerSprite(new ResourceLocation("vandorlabs","blocks/resource_ports/"+kind.texture+"_"+direction+"_"+state));
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        for(ResourcePortBlock block:ResourcePorts.BLOCKS){
            @SuppressWarnings("unchecked") List<BakedQuad>[][] faces=(List<BakedQuad>[][])new List<?>[5][6];
            String[] variants={"port_hull",block.kind.texture+"_in_off",block.kind.texture+"_in_on",block.kind.texture+"_out_off",block.kind.texture+"_out_on"};
            for(int variant=0;variant<5;variant++){
                try{IModel source=ModelLoaderRegistry.getModel(new ResourceLocation("vandorlabs","block/resource_ports/"+variants[variant]));IBakedModel baked=source.bake(ModelRotation.X0_Y0,DefaultVertexFormats.ITEM,texture->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
                    for(EnumFacing side:EnumFacing.values())faces[variant][side.getIndex()]=Collections.unmodifiableList(new ArrayList<>(baked.getQuads(null,side,0)));
                }catch(Exception failure){throw new IllegalStateException("Cannot bake resource port face "+variants[variant],failure);}
            }
            HousingQuads housing=new HousingQuads(faces[0]);
            for(EnumFacing side:EnumFacing.values()){
                ModelResourceLocation location=new ModelResourceLocation(block.getRegistryName(),"facing="+side.getName());IBakedModel fallback=event.getModelRegistry().getObject(location);if(fallback==null)throw new IllegalStateException("Missing resource port blockstate "+location);event.getModelRegistry().putObject(location,new ResourcePortModel(fallback,faces,housing));
            }
        }
    }
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long rand){
        if(side==null)return Collections.emptyList();if(!(state instanceof IExtendedBlockState))return fallback.getQuads(state,side,rand);
        Integer packed=((IExtendedBlockState)state).getValue(ResourcePortBlock.PORT_FACES),lit=((IExtendedBlockState)state).getValue(ResourcePortBlock.LIT);if(packed==null)return fallback.getQuads(state,side,rand);
        int mode=(packed>>(side.getIndex()*2))&3;Integer choice=((IExtendedBlockState)state).getValue(ResourcePortBlock.HOUSING);if(mode==0&&choice!=null&&choice>=0)return housing.face(choice,side);int variant=mode==1?1:mode==2?3:0;if(variant!=0&&lit!=null&&(lit&(1<<side.getIndex()))!=0)variant++;return faces[variant][side.getIndex()];
    }
    /** Shared by the six facing wrappers; copied records retain all source vertex components. */
    private static final class HousingQuads {
        private static final long LIMIT=2*1024*1024;private long bytes;
        private final List<BakedQuad>[] originals;
        private final LinkedHashMap<Integer,List<BakedQuad>[]> cache=new LinkedHashMap<>(16,.75F,true);
        HousingQuads(List<BakedQuad>[] originals){this.originals=originals;}
        synchronized List<BakedQuad> face(int choice,EnumFacing side){
            List<BakedQuad>[] found=cache.get(choice);if(found!=null)return found[side.getIndex()];
            TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(choice));
            @SuppressWarnings("unchecked") List<BakedQuad>[] result=(List<BakedQuad>[])new List<?>[6];long size=128;
            for(EnumFacing face:EnumFacing.values()){List<BakedQuad> quads=new ArrayList<>();for(BakedQuad quad:originals[face.getIndex()]){BakedQuad changed=com.vandorlabs.client.RetexturedItemModel.retextureQuad(quad,sprite);quads.add(changed);size+=128L+changed.getVertexData().length*4L;}result[face.getIndex()]=Collections.unmodifiableList(quads);size+=32;}
            if(cache.size()>=128||bytes+size>LIMIT){cache.clear();bytes=0;}if(size<=LIMIT){cache.put(choice,result);bytes+=size;}return result[side.getIndex()];
        }
    }
    @Override public boolean isAmbientOcclusion(){return true;}
    @Override public boolean isGui3d(){return fallback.isGui3d();}
    @Override public boolean isBuiltInRenderer(){return false;}
    @Override public TextureAtlasSprite getParticleTexture(){return fallback.getParticleTexture();}
    @Override public ItemCameraTransforms getItemCameraTransforms(){return fallback.getItemCameraTransforms();}
    @Override public ItemOverrideList getOverrides(){return fallback.getOverrides();}
}
