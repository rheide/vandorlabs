package com.vandorlabs.client;

import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.render.ScreenSurface;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/** Inventory silhouette uses exactly the same solid wedge as the placed screen. */
public final class DiagonalScreenItemModel implements IBakedModel {
    private final IBakedModel delegate;
    private final List<BakedQuad> quads;
    public DiagonalScreenItemModel(IBakedModel delegate) {
        this(delegate,false);
    }
    public DiagonalScreenItemModel(IBakedModel delegate,boolean redstone) {
        this.delegate=delegate;
        TextureAtlasSprite wall=sprite("dark_wall_panel"),screen=redstone?atlas("minecraft:blocks/concrete_black"):sprite("engineering_screen_static");
        List<BakedQuad> built=new ArrayList<>();
        for(ScreenHousingMesh.Face face:ScreenHousingMesh.diagonal(false).quads) {
            double[][] points=new double[4][5];
            for(int i=0;i<4;i++) {
                ScreenHousingMesh.Vertex v=face.vertices[(4-i)&3];
                points[i]=new double[]{v.x,v.y,v.z,v.u,v.v};
            }
            built.add(quad(points,wall));
        }
        ScreenSurface.Quad s=ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false);
        ScreenSurface.Vertex[] vertices={s.topLeft,s.topRight,s.bottomRight,s.bottomLeft};
        double[][] points=new double[4][5];
        for(int i=0;i<4;i++) {
            ScreenSurface.Vertex v=vertices[i];
            // Small normal offset gives the item image the same depth separation as the TESR.
            points[i]=new double[]{v.x,v.y+.01,v.z-.01,i==0||i==3?16:0,i<2?0:16};
        }
        built.add(quad(points,screen));
        if(redstone){
            built.add(panel(s,3,3,125,20,.03,"concrete_cyan"));
            for(int row=0;row<3;row++){
                int y=24+row*12;
                built.add(panel(s,8,y+3,70-row*10,y+7,.04,"concrete_light_blue"));
                built.add(panel(s,98,y,120,y+10,.04,row==0?"concrete_lime":"concrete_cyan"));
            }
        }
        quads=Collections.unmodifiableList(built);
    }
    private static BakedQuad panel(ScreenSurface.Quad q,int left,int top,int right,int bottom,double offset,String texture){
        double[][] points=new double[4][5];int[] xs={right,left,left,right},ys={top,top,bottom,bottom};
        for(int i=0;i<4;i++)points[i]=new double[]{q.topRight.x+(q.topLeft.x-q.topRight.x)*xs[i]/128,
                q.topRight.y+(q.bottomRight.y-q.topRight.y)*ys[i]/128+q.ny*offset,
                q.topRight.z+(q.bottomRight.z-q.topRight.z)*ys[i]/128+q.nz*offset,
                i==0||i==3?0:16,i<2?0:16};
        return quad(points,atlas("minecraft:blocks/"+texture));
    }
    private static TextureAtlasSprite atlas(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/"+name);}
    private static BakedQuad quad(double[][] points,TextureAtlasSprite sprite) {
        int[] data=new int[28];
        double[] a=points[0],b=points[1],c=points[2];
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
        EnumFacing face=EnumFacing.getFacingFromVector((float)(uy*vz-uz*vy),(float)(uz*vx-ux*vz),(float)(ux*vy-uy*vx));
        for(int i=0;i<4;i++) {
            int k=i*7;double[] v=points[i];
            for(int j=0;j<3;j++)data[k+j]=Float.floatToRawIntBits((float)(v[j]/16));
            data[k+3]=-1;data[k+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(v[3]));
            data[k+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(v[4]));
        }
        return new BakedQuad(data,-1,face,sprite,true,DefaultVertexFormats.ITEM);
    }
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?quads:Collections.emptyList();}
    public boolean isAmbientOcclusion(){return false;}
    public boolean isGui3d(){return true;}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return delegate.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return delegate.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return delegate.getOverrides();}
}
