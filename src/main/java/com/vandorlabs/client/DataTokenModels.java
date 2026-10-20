package com.vandorlabs.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.*;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

/** Preserve vanilla alpha-cutout item geometry; only its edge material changes. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class DataTokenModels {
    @SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){
        for(String kind:new String[]{"card","chip"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:items/data_"+kind+"_edge"));
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        for(String kind:new String[]{"card","chip"})for(String design:new String[]{"unbranded","pinnacle","ashiro","paladyne","vektor","seyr","vandor"}){
            ModelResourceLocation key=new ModelResourceLocation("vandorlabs:data_"+kind+"_"+design,"inventory");
            IBakedModel raw=event.getModelRegistry().getObject(key);
            if(raw!=null)event.getModelRegistry().putObject(key,new Edges(raw,kind));
        }
    }
    private static final class Edges implements IBakedModel {
        final IBakedModel raw;final List<BakedQuad> quads;
        Edges(IBakedModel raw,String kind){
            this.raw=raw;List<BakedQuad> result=new ArrayList<>();
            TextureAtlasSprite edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:items/data_"+kind+"_edge");
            for(BakedQuad quad:raw.getQuads(null,null,0)){
                if(quad.getFace().getAxis()==EnumFacing.Axis.Z){result.add(quad);continue;}
                int[] vertices=quad.getVertexData().clone();int stride=vertices.length/4,uv=quad.getFormat().getUvOffsetById(0)/4;
                // The generated side UVs sample the alpha boundary of the artwork.
                // Keep every original vertex, but sample a fully opaque side material.
                for(int v=0;v<4;v++){vertices[v*stride+uv]=Float.floatToRawIntBits(edge.getInterpolatedU(8));vertices[v*stride+uv+1]=Float.floatToRawIntBits(edge.getInterpolatedV(8));}
                result.add(new BakedQuad(vertices,quad.getTintIndex(),quad.getFace(),edge,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
            }
            quads=Collections.unmodifiableList(result);
        }
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return raw.isAmbientOcclusion();}public boolean isGui3d(){return raw.isGui3d();}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return raw.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return raw.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return raw.getOverrides();}
    }
}
