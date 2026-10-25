package com.vandorlabs.client;

import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class PressurePlateModels {
    private static String[] variants(){String[] result=new String[17];for(int i=0;i<16;i++)result[i]="power="+i;result[16]="inventory";return result;}
    @SubscribeEvent public static void bake(ModelBakeEvent event){for(String variant:variants()){ModelResourceLocation key=new ModelResourceLocation("vandorlabs:programmable_pressure_plate",variant);IBakedModel source=event.getModelRegistry().getObject(key);if(source!=null)event.getModelRegistry().putObject(key,new Model(source));}}
    private static final class Model implements IBakedModel {
        private final IBakedModel source;
        private final com.google.common.cache.Cache<String,List<BakedQuad>> cache=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(512).build();
        Model(IBakedModel source){this.source=source;}
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){
            int choice=ScreenHousingTextures.screenIndex("door_inner");if(state instanceof IExtendedBlockState){Integer value=((IExtendedBlockState)state).getValue(ProgrammableHousingState.FINISH);if(value!=null)choice=value;}
            String key=choice+":"+side;List<BakedQuad> existing=cache.getIfPresent(key);if(existing!=null)return existing;
            TextureAtlasSprite target=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(choice));List<BakedQuad> result=new ArrayList<>();
            for(BakedQuad quad:source.getQuads(state,side,random)){
                int[] data=quad.getVertexData().clone();int stride=data.length/4,uv=quad.getFormat().getUvOffsetById(0)/4;TextureAtlasSprite old=quad.getSprite();
                for(int i=0;i<4;i++){int v=i*stride+uv;float u=(Float.intBitsToFloat(data[v])-old.getMinU())/(old.getMaxU()-old.getMinU()),w=(Float.intBitsToFloat(data[v+1])-old.getMinV())/(old.getMaxV()-old.getMinV());data[v]=Float.floatToRawIntBits(target.getInterpolatedU(u*16));data[v+1]=Float.floatToRawIntBits(target.getInterpolatedV(w*16));}
                result.add(new BakedQuad(data,quad.getTintIndex(),quad.getFace(),target,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
            }
            result=Collections.unmodifiableList(result);cache.put(key,result);return result;
        }
        @Override public boolean isAmbientOcclusion(){return source.isAmbientOcclusion();}
        @Override public boolean isGui3d(){return true;}
        @Override public boolean isBuiltInRenderer(){return false;}
        @Override public TextureAtlasSprite getParticleTexture(){return source.getParticleTexture();}
        @Override public ItemCameraTransforms getItemCameraTransforms(){return source.getItemCameraTransforms();}
        @Override public ItemOverrideList getOverrides(){return source.getOverrides();}
    }
}
