package com.vandorlabs.client;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;

/** Immutable, world-free item geometry captured from the existing surface emitters. */
final class ItemAppearanceMesh implements IBakedModel {
    private final IBakedModel base;
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particle;
    private final ItemCameraTransforms transforms;
    final long bytes;

    ItemAppearanceMesh(IBakedModel base,List<BakedQuad> quads,TextureAtlasSprite particle,boolean pad) {
        this.base=base;this.quads=Collections.unmodifiableList(new ArrayList<>(quads));this.particle=particle;
        transforms=pad?ControlItemPadding.fit(this,base.getItemCameraTransforms()):base.getItemCameraTransforms();
        bytes=512+ItemVariantModels.quadBytes(quads);
    }
    static List<BakedQuad> all(IBakedModel model) {
        List<BakedQuad> out=new ArrayList<>();
        for(int side=-1;side<6;side++)out.addAll(model.getQuads(null,side<0?null:EnumFacing.getFront(side),0));
        return out;
    }
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?quads:Collections.emptyList();}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return true;}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return particle;}
    public ItemCameraTransforms getItemCameraTransforms(){return transforms;}
    public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}

    /** Emits ordinary ITEM records, with independent atlas sprites for split door artwork. */
    static final class Capture extends BufferBuilder {
        private final List<BakedQuad> quads=new ArrayList<>();
        private final List<TextureAtlasSprite> sprites=new ArrayList<>();
        private final float[][] points=new float[4][12];
        private final double scaleX,scaleY,scaleZ,offsetX,offsetZ;
        private int vertex;
        Capture(double scaleX,double scaleY,double scaleZ,double offsetX,double offsetZ,TextureAtlasSprite... materials) {
            super(16);this.scaleX=scaleX;this.scaleY=scaleY;this.scaleZ=scaleZ;this.offsetX=offsetX;this.offsetZ=offsetZ;
            for(TextureAtlasSprite material:materials)if(material!=null) {
                if(!sprites.contains(material))sprites.add(material);
                for(int edge=0;edge<4;edge++){TextureAtlasSprite cut=FrameEdgeTextures.edge(material,edge);if(!sprites.contains(cut))sprites.add(cut);}
            }
        }
        @Override public BufferBuilder pos(double x,double y,double z){float[] p=points[vertex];p[0]=(float)(x*scaleX+offsetX);p[1]=(float)(y*scaleY);p[2]=(float)(z*scaleZ+offsetZ);Arrays.fill(p,8,12,1F);return this;}
        @Override public BufferBuilder tex(double u,double v){points[vertex][3]=(float)u;points[vertex][4]=(float)v;return this;}
        @Override public BufferBuilder normal(float x,float y,float z){double nx=x/scaleX,ny=y/scaleY,nz=z/scaleZ,len=Math.sqrt(nx*nx+ny*ny+nz*nz);points[vertex][5]=(float)(nx/len);points[vertex][6]=(float)(ny/len);points[vertex][7]=(float)(nz/len);return this;}
        @Override public BufferBuilder color(int r,int g,int b,int a){return color(r/255F,g/255F,b/255F,a/255F);}
        @Override public BufferBuilder color(float r,float g,float b,float a){float[] p=points[vertex];p[8]=r;p[9]=g;p[10]=b;p[11]=a;return this;}
        @Override public BufferBuilder lightmap(int a,int b){return this;}
        @Override public void endVertex(){
            if(++vertex<4)return;
            TextureAtlasSprite sprite=null;
            for(TextureAtlasSprite candidate:sprites){boolean contains=true;for(float[] p:points)contains&=p[3]>=candidate.getMinU()-1e-6&&p[3]<=candidate.getMaxU()+1e-6&&p[4]>=candidate.getMinV()-1e-6&&p[4]<=candidate.getMaxV()+1e-6;if(contains){sprite=candidate;break;}}
            if(sprite==null)throw new IllegalStateException("Item surface samples outside its material sprites");
            VertexFormat format=new VertexFormat(DefaultVertexFormats.ITEM);
            UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);
            builder.setTexture(sprite);builder.setQuadTint(-1);builder.setApplyDiffuseLighting(false);
            builder.setQuadOrientation(EnumFacing.getFacingFromVector(points[0][5],points[0][6],points[0][7]));
            for(float[] p:points)for(int element=0;element<format.getElementCount();element++)switch(format.getElement(element).getUsage()){
                case POSITION:builder.put(element,p[0],p[1],p[2],1);break;
                case COLOR:builder.put(element,p[8],p[9],p[10],p[11]);break;
                case NORMAL:builder.put(element,p[5],p[6],p[7],0);break;
                case UV:if(format.getElement(element).getIndex()==0)builder.put(element,p[3],p[4],0,1);else builder.put(element,0,0,0,1);break;
                default:builder.put(element);break;
            }
            BakedQuad packed=builder.build();quads.add(new BakedQuad(packed.getVertexData(),-1,packed.getFace(),sprite,false,format));vertex=0;
        }
        List<BakedQuad> finish(){if(vertex!=0)throw new IllegalStateException("Incomplete item surface");return quads;}
    }
}
