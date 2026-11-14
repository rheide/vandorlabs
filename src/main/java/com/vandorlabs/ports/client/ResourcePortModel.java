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
    private ResourcePortModel(IBakedModel fallback,List<BakedQuad>[][] faces){this.fallback=fallback;this.faces=faces;}
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
            for(EnumFacing side:EnumFacing.values()){
                ModelResourceLocation location=new ModelResourceLocation(block.getRegistryName(),"facing="+side.getName());IBakedModel fallback=event.getModelRegistry().getObject(location);if(fallback==null)throw new IllegalStateException("Missing resource port blockstate "+location);event.getModelRegistry().putObject(location,new ResourcePortModel(fallback,faces));
            }
        }
    }
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long rand){
        if(side==null)return Collections.emptyList();if(!(state instanceof IExtendedBlockState))return fallback.getQuads(state,side,rand);
        Integer packed=((IExtendedBlockState)state).getValue(ResourcePortBlock.PORT_FACES),lit=((IExtendedBlockState)state).getValue(ResourcePortBlock.LIT);if(packed==null)return fallback.getQuads(state,side,rand);
        int mode=(packed>>(side.getIndex()*2))&3;int variant=mode==1?1:mode==2?3:0;if(variant!=0&&lit!=null&&(lit&(1<<side.getIndex()))!=0)variant++;return faces[variant][side.getIndex()];
    }
    @Override public boolean isAmbientOcclusion(){return true;}
    @Override public boolean isGui3d(){return fallback.isGui3d();}
    @Override public boolean isBuiltInRenderer(){return false;}
    @Override public TextureAtlasSprite getParticleTexture(){return fallback.getParticleTexture();}
    @Override public ItemCameraTransforms getItemCameraTransforms(){return fallback.getItemCameraTransforms();}
    @Override public ItemOverrideList getOverrides(){return fallback.getOverrides();}
}
