package com.vandorlabs.coupler.client;

import com.vandorlabs.coupler.*;
import com.vandorlabs.geometry.CardinalFrame;
import java.util.*;
import javax.vecmath.Matrix4f;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.*;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.*;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;

/** Fixed atlas models for 24 full frames and the eight representable horizontal slab frames. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class CouplerFrameModels {
    private static final IBakedModel[][][][] MODELS=new IBakedModel[6][6][2][3];
    private static long bytes;
    private static int bakes;
    private CouplerFrameModels(){}
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        for(IBakedModel[][][] a:MODELS)for(IBakedModel[][] b:a)for(IBakedModel[] c:b)Arrays.fill(c,null);bytes=0;bakes=0;
        List<ModelResourceLocation> keys=new ArrayList<>(event.getModelRegistry().getKeys());
        for(ModelResourceLocation key:keys){
            if(!key.getResourceDomain().equals("vandorlabs")||!(key.getResourcePath().equals("square_coupler")||key.getResourcePath().equals("vehicle_coupler_slab"))||key.getVariant().equals("inventory"))continue;
            IBakedModel base=event.getModelRegistry().getObject(key);VertexFormat format=format(base);
            if(bakes==0)build(format);
            event.getModelRegistry().putObject(key,new Framed(base));
        }
    }
    private static VertexFormat format(IBakedModel model){
        List<BakedQuad> quads=model.getQuads(null,null,0);if(quads.isEmpty())for(EnumFacing side:EnumFacing.values()){quads=model.getQuads(null,side,0);if(!quads.isEmpty())break;}
        if(quads.isEmpty())throw new IllegalStateException("Coupler authored model is empty");return new VertexFormat(quads.get(0).getFormat());
    }
    private static void build(VertexFormat format){
        try{
            for(int locked=0;locked<2;locked++){
                IModel full=ModelLoaderRegistry.getModel(new ResourceLocation("vandorlabs:block/square_coupler_"+(locked==0?"released":"locked")));
                IModel slab=ModelLoaderRegistry.getModel(new ResourceLocation("vandorlabs:block/vehicle_coupler_slab_"+(locked==0?"released":"locked")+"_bottom"));
                for(EnumFacing forward:EnumFacing.values())for(EnumFacing up:EnumFacing.values()){
                    if(forward.getAxis()==up.getAxis())continue;CardinalFrame frame=CardinalFrame.of(forward.getIndex(),up.getIndex());
                    MODELS[forward.getIndex()][up.getIndex()][locked][2]=bake(full,format,frame,.5,.5);
                    if(forward.getAxis().isHorizontal()&&up.getAxis()==EnumFacing.Axis.Y)for(int half=0;half<2;half++)MODELS[forward.getIndex()][up.getIndex()][locked][half]=bake(slab,format,frame,.25,.25+half*.5);
                }
            }
        }catch(Exception failure){throw new IllegalStateException("Could not bake Coupler frames",failure);}
    }
    private static IBakedModel bake(IModel source,VertexFormat format,CardinalFrame frame,double sourceY,double targetY){
        Matrix4f matrix=new Matrix4f();matrix.setIdentity();for(int row=0;row<3;row++)for(int col=0;col<3;col++)matrix.setElement(row,col,frame.component(row,col));
        matrix.m03=(float)(.5-frame.transform(0,.5,sourceY,.5));matrix.m13=(float)(targetY-frame.transform(1,.5,sourceY,.5));matrix.m23=(float)(.5-frame.transform(2,.5,sourceY,.5));
        IBakedModel model=source.bake(new TRSRTransformation(matrix),format,texture->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));bakes++;
        for(int side=-1;side<6;side++)for(BakedQuad quad:model.getQuads(null,side<0?null:EnumFacing.getFront(side),0))bytes+=(long)quad.getVertexData().length*4;
        return model;
    }
    public static int bakeCount(){return bakes;}
    public static long vertexBytes(){return bytes;}
    private static final class Framed implements IBakedModel {
        private final IBakedModel base;
        Framed(IBakedModel base){this.base=base;}
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
            if(!(state instanceof IExtendedBlockState))return base.getQuads(state,side,seed);
            IExtendedBlockState extended=(IExtendedBlockState)state;Integer up=extended.getValue(BlockCoupler.FRAME_UP);IBlockState clean=extended.getClean();
            if(up==null)return base.getQuads(clean,side,seed);
            int forward=clean.getValue(BlockCoupler.FACING).getIndex(),locked=clean.getValue(BlockCoupler.LOCKED)?1:0,part=clean.getBlock() instanceof BlockCouplerSlab?(clean.getValue(BlockCouplerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP?1:0):2;
            IBakedModel selected=MODELS[forward][up][locked][part];return selected==null?base.getQuads(clean,side,seed):selected.getQuads(clean,side,seed);
        }
        @Override public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
        @Override public boolean isGui3d(){return base.isGui3d();}
        @Override public boolean isBuiltInRenderer(){return false;}
        @Override public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
        @Override public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
        @Override public ItemOverrideList getOverrides(){return base.getOverrides();}
    }
}
