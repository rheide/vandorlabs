package com.vandorlabs.client.garage;

import com.vandorlabs.garage.GarageDoorGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import java.util.*;

/** Settled closed segments belong to terrain, not the per-frame tile dispatcher. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GarageDoorModels {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void bake(ModelBakeEvent event){
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean top:new boolean[]{false,true})for(boolean bottom:new boolean[]{false,true}){
            String variant="baked=true,bottom="+bottom+",facing="+facing.getName()+",top="+top;
            ModelResourceLocation key=new ModelResourceLocation("vandorlabs:garage_door",variant);IBakedModel original=event.getModelRegistry().getObject(key);
            if(original!=null)event.getModelRegistry().putObject(key,new Model(original,facing,top,bottom));
        }
        GarageDoorChunks.reload();
    }
    private static final class Model implements IBakedModel {
        final IBakedModel original;final List<BakedQuad> quads;
        Model(IBakedModel original,EnumFacing facing,boolean top,boolean bottom){
            this.original=original;List<BakedQuad> result=new ArrayList<>(original.getQuads(null,null,0));
            double lo=.5-GarageDoorGeometry.HALF_THICKNESS,hi=.5+GarageDoorGeometry.HALF_THICKNESS;
            double[][][] faces={{{0,0,hi},{0,0,lo},{1,0,lo},{1,0,hi}},{{0,1,lo},{0,1,hi},{1,1,hi},{1,1,lo}},{{1,0,lo},{0,0,lo},{0,1,lo},{1,1,lo}},{{0,0,hi},{1,0,hi},{1,1,hi},{0,1,hi}},{{0,0,lo},{0,0,hi},{0,1,hi},{0,1,lo}},{{1,0,hi},{1,0,lo},{1,1,lo},{1,1,hi}}};
            int[][] normals={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};int rotation=((int)(180-facing.getHorizontalAngle())/90)&3;
            VertexFormat format=new VertexFormat(DefaultVertexFormats.BLOCK);
            for(int face=0;face<6;face++){
                if(face==0&&!bottom||face==1&&!top)continue;
                TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(face==2||face==3?"vandorlabs:blocks/garage/garage_panel":"vandorlabs:blocks/programmable_glass/metal_side");
                double nx=normals[face][0],ny=normals[face][1],nz=normals[face][2];double rnx=rotation==1?nz:rotation==2?-nx:rotation==3?-nz:nx,rnz=rotation==1?-nx:rotation==2?-nz:rotation==3?nx:nz;
                UnpackedBakedQuad.Builder b=new UnpackedBakedQuad.Builder(format);b.setTexture(sprite);b.setQuadTint(-1);b.setApplyDiffuseLighting(false);b.setQuadOrientation(EnumFacing.getFacingFromVector((float)rnx,(float)ny,(float)rnz));
                for(int v=0;v<4;v++)for(int e=0;e<format.getElementCount();e++){
                    double x=faces[face][v][0],y=faces[face][v][1],z=faces[face][v][2];double rx=rotation==1?z:rotation==2?1-x:rotation==3?1-z:x,rz=rotation==1?1-x:rotation==2?1-z:rotation==3?x:z;
                    VertexFormatElement element=format.getElement(e);
                    switch(element.getUsage()){
                        case POSITION:b.put(e,(float)rx,(float)y,(float)rz,1);break;
                        case COLOR:b.put(e,1,1,1,1);break;
                        case NORMAL:b.put(e,(float)rnx,(float)ny,(float)rnz,0);break;
                        case UV:if(element.getIndex()==0)b.put(e,sprite.getInterpolatedU(v==1||v==2?16:0),sprite.getInterpolatedV(v<2?16:0),0,1);else b.put(e,0,0,0,1);break;
                        default:b.put(e);break;
                    }
                }
                BakedQuad q=b.build();result.add(new BakedQuad(q.getVertexData(),-1,q.getFace(),sprite,false,format));
            }
            quads=Collections.unmodifiableList(result);
        }
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return side==null?quads:original.getQuads(state,side,random);}
        public boolean isAmbientOcclusion(){return false;}
        public boolean isGui3d(){return true;}
        public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return original.getParticleTexture();}
        public ItemCameraTransforms getItemCameraTransforms(){return original.getItemCameraTransforms();}
        public ItemOverrideList getOverrides(){return original.getOverrides();}
    }
    private GarageDoorModels(){}
}
