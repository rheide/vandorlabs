package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.render.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Native records from the current shared polygon, vertex, frame and repeated-UV kernels. */
final class NativePanelQuads {
    record Material(ProgrammableModel.MaterialSnapshot surface,List<ProgrammableModel.MaterialSnapshot> edges){
        Material(ProgrammableModel.MaterialSnapshot surface){this(surface,List.of());}
        Material{edges=List.copyOf(edges);if(!edges.isEmpty()&&edges.size()!=4)throw new IllegalArgumentException("Four directional frame edges required");}
        Material mapped(double[][] points){if(edges.isEmpty())return this;int edge=FrameEdgeMapping.edge(points);FrameEdgeMapping.map(edge,points);return new Material(edges.get(edge));}
    }
    private static final class Context {
        final Direction facing;final Material main;final int mode;final NativePanelLayout layout;
        final List<BakedQuad> result=new ArrayList<>();final float[] source=new float[32],mapped=new float[32];int used,mappedUsed;Material current;
        Context(Direction facing,Material main,int mode,NativePanelLayout layout){this.facing=facing;this.main=main;this.mode=mode;this.layout=Objects.requireNonNull(layout);}
        void source(Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){
            put(source,used++,x,y,z,u,v,nx,ny,nz);if(used!=4)return;used=0;
            var texture=main.surface;var sprite=texture.sprite();
            if(mode==0||material.surface.sprite()!=sprite){append(source,material);return;}
            float u0=sprite.getU0(),v0=sprite.getV0(),u1=u0+(sprite.getU1()-u0)*texture.usedU(),v1=v0+(sprite.getV1()-v0)*texture.usedV();
            PanelTextureMesh.map(MAPPED,this,material,source,u0,u1,v0,v1,texture.usedU()/texture.usedV(),layout.column(),layout.row(),layout.columns(),layout.rows(),layout.flipU(),layout.flipV(),false,0,0,mode);
        }
        void mapped(Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){put(mapped,mappedUsed++,x,y,z,u,v,nx,ny,nz);if(mappedUsed==4){mappedUsed=0;append(mapped,material);}}
        void append(float[] points,Material material){
            int[] data=new int[32];int turns=facing==Direction.WEST?1:facing==Direction.SOUTH?2:facing==Direction.EAST?3:0;
            int a=(byte)(int)(points[5]*127),b=(byte)(int)(points[6]*127),c=(byte)(int)(points[7]*127),nx=turns==1?c:turns==2?-a:turns==3?-c:a,nz=turns==1?-a:turns==2?-c:turns==3?a:c,normal=(nx&255)|((b&255)<<8)|((nz&255)<<16);
            Direction direction=direction(nx/127F,b/127F,nz/127F);
            for(int v=0;v<4;v++){int at=v*8;double x=points[at]/16D,z=points[at+2]/16D;data[at]=Float.floatToRawIntBits((float)(turns==1?z:turns==2?1-x:turns==3?1-z:x));data[at+1]=Float.floatToRawIntBits(points[at+1]/16);data[at+2]=Float.floatToRawIntBits((float)(turns==1?1-x:turns==2?1-z:turns==3?x:z));data[at+3]=-1;data[at+4]=Float.floatToRawIntBits(points[at+3]);data[at+5]=Float.floatToRawIntBits(points[at+4]);data[at+7]=normal;}
            result.add(new BakedQuad(data,-1,direction,material.surface.sprite(),false,false));int[] back=new int[32];for(int v=0;v<4;v++)System.arraycopy(data,((4-v)&3)*8,back,v*8,8);result.add(new BakedQuad(back,-1,direction,material.surface.sprite(),false,false));
        }
        List<BakedQuad> finish(){if(used!=0||mappedUsed!=0)throw new IllegalStateException("Incomplete native panel quad");return List.copyOf(result);}
    }
    private static void put(float[] data,int vertex,double x,double y,double z,double u,double v,float nx,float ny,float nz){int at=vertex*8;data[at]=(float)x;data[at+1]=(float)y;data[at+2]=(float)z;data[at+3]=(float)u;data[at+4]=(float)v;data[at+5]=nx;data[at+6]=ny;data[at+7]=nz;}
    private static Direction direction(float x,float y,float z){Direction result=Direction.NORTH;float best=Float.MIN_VALUE;for(Direction direction:Direction.values()){float score=x*direction.getStepX()+y*direction.getStepY()+z*direction.getStepZ();if(score>best){best=score;result=direction;}}return result;}
    private static final PanelVertexEmitter.RawSink<Context> RAW=(context,x,y,z,u,v,nx,ny,nz)->context.source(context.current,x,y,z,u,v,nx,ny,nz);
    private static final PanelVertexEmitter.PixelSink<Context,Material> PIXEL=(context,material,x,y,z,u,v,nx,ny,nz)->context.source(material,x,y,z,NativeHousingQuads.atlas(material.surface,u,true),NativeHousingQuads.atlas(material.surface,v,false),nx,ny,nz);
    private static final PanelTextureMesh.Sink<Context,Material> MAPPED=new PanelTextureMesh.Sink<>(){
        public void atlas(Context context,Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){context.mapped(material,x,y,z,u,v,nx,ny,nz);}
        public void pixel(Context context,Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){context.mapped(material,x,y,z,NativeHousingQuads.atlas(context.main.surface,u,true),NativeHousingQuads.atlas(context.main.surface,v,false),nx,ny,nz);}
    };
    private static final PanelMeshEmitter.Sink<Context,Material> EMITTER=new PanelMeshEmitter.Sink<>(){
        public void quad(Context context,Material material,boolean reverse,double[] a,double[] b,double[] c,double[] d){
            material=material.mapped(new double[][]{a,b,c,d});context.current=material;var texture=material.surface;
            PanelVertexEmitter.raw(RAW,context,reverse,a[0],a[1],a[2],NativeHousingQuads.atlas(texture,a[3],true),NativeHousingQuads.atlas(texture,a[4],false),b[0],b[1],b[2],NativeHousingQuads.atlas(texture,b[3],true),NativeHousingQuads.atlas(texture,b[4],false),c[0],c[1],c[2],NativeHousingQuads.atlas(texture,c[3],true),NativeHousingQuads.atlas(texture,c[4],false),d[0],d[1],d[2],NativeHousingQuads.atlas(texture,d[3],true),NativeHousingQuads.atlas(texture,d[4],false));
        }
        public void normal(Context context,Material material,int sign,double halfOffset,double[] clip,double[] a,double[] b,double[] c,double[] d){material=material.mapped(new double[][]{a,b,c,d});PanelVertexEmitter.normal(PIXEL,context,material,sign,halfOffset,clip,a,b,c,d);}
    };
    static List<BakedQuad> flat(Direction facing,double near,PanelCornerGeometry.FlatCorner corner,Material wall,Material metal,int mode,NativePanelLayout group){Context context=new Context(facing,wall,mode,group);PanelMeshEmitter.flat(EMITTER,context,wall,metal,near,corner);return context.finish();}
    static List<BakedQuad> diagonal(Direction facing,boolean inverted,PanelCornerGeometry.Corner corner,double span,int fill,double low,double high,double halfOffset,double lower,double upper,boolean hideLower,boolean hideUpper,double[] clip,Material wall,Material metal,int mode,NativePanelLayout group){Context context=new Context(facing,wall,mode,group);PanelMeshEmitter.diagonal(EMITTER,context,wall,metal,inverted,corner,span,fill,low,high,halfOffset,lower,upper,hideLower,hideUpper,clip);return context.finish();}
    private NativePanelQuads(){}
}
