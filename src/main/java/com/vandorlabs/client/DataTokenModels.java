package com.vandorlabs.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

/** Transparent artwork faces bounded by continuous opaque silhouette walls. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class DataTokenModels {
    @SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){
        for(String kind:new String[]{"card","chip"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:items/data_"+kind+"_edge"));
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        for(String kind:new String[]{"card","chip"})for(String design:new String[]{"unbranded","pinnacle","ashiro","paladyne","vektor","seyr","vandor"}){
            ModelResourceLocation key=new ModelResourceLocation("vandorlabs:data_"+kind+"_"+design,"inventory");
            IBakedModel raw=event.getModelRegistry().getObject(key);
            if(raw!=null)event.getModelRegistry().putObject(key,new Outline(raw,kind));
        }
    }
    private static long cross(int[] a,int[] b,int[] c){return (long)(b[0]-a[0])*(c[1]-a[1])-(long)(b[1]-a[1])*(c[0]-a[0]);}
    private static final class Outline implements IBakedModel {
        final IBakedModel raw;final List<BakedQuad> quads;
        Outline(IBakedModel raw,String kind){
            this.raw=raw;List<BakedQuad> result=new ArrayList<>(raw.getQuads(null,null,0));
            TextureAtlasSprite art=raw.getParticleTexture(),edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:items/data_"+kind+"_edge");
            int w=art.getIconWidth(),h=art.getIconHeight();int[] pixels=art.getFrameTextureData(0)[0];
            // A convex outline removes one-pixel stair steps from the authored perspective.
            // Only side walls use it: both broad faces retain the original alpha mask.
            SortedSet<Integer> corners=new TreeSet<>();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((pixels[y*w+x]>>>24)>=128)for(int dx=0;dx<=1;dx++)for(int dy=0;dy<=1;dy++)corners.add((x+dx)*(h+1)+y+dy);
            List<int[]> points=new ArrayList<>();for(int p:corners)points.add(new int[]{p/(h+1),p%(h+1)});
            List<int[]> hull=new ArrayList<>();for(int[] p:points){while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
            int lower=hull.size();for(int i=points.size()-2;i>=0;i--){int[] p=points.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}hull.remove(hull.size()-1);
            for(int i=0;i<hull.size();i++){
                int[] a=hull.get(i),b=hull.get((i+1)%hull.size());float nx=b[1]-a[1],ny=b[0]-a[0],length=(float)Math.sqrt(nx*nx+ny*ny);nx/=length;ny/=length;
                float[][] vertices={{a[0]/(float)w,1-a[1]/(float)h,7/16F},{a[0]/(float)w,1-a[1]/(float)h,9/16F},{b[0]/(float)w,1-b[1]/(float)h,9/16F},{b[0]/(float)w,1-b[1]/(float)h,7/16F}};
                VertexFormat format=DefaultVertexFormats.ITEM;UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);builder.setTexture(edge);builder.setApplyDiffuseLighting(false);builder.setQuadOrientation(EnumFacing.getFacingFromVector(nx,ny,0));
                for(float[] v:vertices)for(int e=0;e<format.getElementCount();e++)switch(format.getElement(e).getUsage()){
                    case POSITION:builder.put(e,v[0],v[1],v[2],1);break;
                    case COLOR:builder.put(e,1,1,1,1);break;
                    case UV:builder.put(e,edge.getInterpolatedU(8),edge.getInterpolatedV(8),0,1);break;
                    case NORMAL:builder.put(e,nx,ny,0,0);break;
                    default:builder.put(e);break;
                }
                result.add(builder.build());
            }
            quads=Collections.unmodifiableList(result);
        }
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return raw.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return raw.getItemCameraTransforms();}public ItemOverrideList getOverrides(){return raw.getOverrides();}
    }
}
