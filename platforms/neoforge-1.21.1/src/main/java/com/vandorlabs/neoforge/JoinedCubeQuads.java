package com.vandorlabs.neoforge;

import com.vandorlabs.render.HousingFacePlane;
import com.vandorlabs.render.ScreenHousingMesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** Native records retain their complete vertex payload while sharing legacy authored orientation. */
public final class JoinedCubeQuads {
    private static int rotation(Direction facing){return switch(facing){case EAST,UP,DOWN->3;case SOUTH->2;case WEST->1;default->0;};}
    private static int meshFace(int local){return switch(local){case 0->1;case 1->0;case 2->4;case 3->5;case 4->2;case 5->3;default->throw new IllegalArgumentException();};}
    public static List<BakedQuad> map(BakedQuad source,Direction facing,HousingFacePlane plane,int layout,TextureAtlasSprite sprite,float usedU,float usedV,TextureAtlasSprite margin){
        int[] data=source.getVertices().clone();int stride=data.length/4;
        var points=ScreenHousingMesh.cube().quads[meshFace(ProgrammableModel.localFace(source.getDirection(),facing))].vertices;
        for(int i=0;i<4;i++){
            var p=points[(4-i)&3];double a=p.x/16,b=p.y/16,c=p.z/16,x=a,z=c;
            switch(rotation(facing)){case 1-> {x=c;z=1-a;}case 2->{x=1-a;z=1-c;}case 3->{x=1-c;z=a;}default->{}}
            data[i*stride]=Float.floatToRawIntBits((float)x);data[i*stride+1]=Float.floatToRawIntBits((float)b);data[i*stride+2]=Float.floatToRawIntBits((float)z);
            data[i*stride+4]=Float.floatToRawIntBits((float)p.u/16);data[i*stride+5]=Float.floatToRawIntBits((float)p.v/16);
        }
        int[] axes=new int[2];double[] signs=new double[2];
        for(int uv=0;uv<2;uv++)for(int vertex=1;vertex<4;vertex++){
            double delta=Float.intBitsToFloat(data[vertex*stride+4+uv])-Float.intBitsToFloat(data[4+uv]);
            double other=Float.intBitsToFloat(data[vertex*stride+5-uv])-Float.intBitsToFloat(data[5-uv]);
            if(Math.abs(delta)<1e-8||Math.abs(other)>1e-8)continue;
            for(int axis=0;axis<3;axis++){double change=Float.intBitsToFloat(data[vertex*stride+axis])-Float.intBitsToFloat(data[axis]);if(Math.abs(change)>.5){axes[uv]=axis;signs[uv]=delta/change;}}
        }
        double aspect=usedU/usedV,surface=plane.size(axes[0])/(double)plane.size(axes[1]);
        double[] slopes={(signs[0]<0?-1:1)/(double)plane.size(axes[0]),(signs[1]<0?-1:1)/(double)plane.size(axes[1])};
        if(layout==2){slopes[0]/=Math.min(1,aspect/surface);slopes[1]/=Math.min(1,surface/aspect);}
        double[][] coordinates=new double[4][2];
        for(int vertex=0;vertex<4;vertex++)for(int uv=0;uv<2;uv++){
            double value=(Float.intBitsToFloat(data[vertex*stride+axes[uv]])-plane.min(axes[uv]))/plane.size(axes[uv]);if(signs[uv]<0)value=1-value;
            if(layout==2){double fraction=uv==0?Math.min(1,aspect/surface):Math.min(1,surface/aspect);value=.5+(value-.5)/fraction;}
            coordinates[vertex][uv]=value;
        }
        BakedQuad authored=new BakedQuad(data,source.getTintIndex(),source.getDirection(),sprite,false,false);
        if(layout!=2){for(int vertex=0;vertex<4;vertex++){data[vertex*stride+4]=Float.floatToRawIntBits(sprite.getU((float)coordinates[vertex][0]*usedU));data[vertex*stride+5]=Float.floatToRawIntBits(sprite.getV((float)coordinates[vertex][1]*usedV));}return List.of(authored);}
        List<BakedQuad> result=new ArrayList<>(5);
        region(result,authored,coordinates,axes,slopes,0,1,0,1,sprite,usedU,usedV,false);
        region(result,authored,coordinates,axes,slopes,-Double.MAX_VALUE,0,-Double.MAX_VALUE,Double.MAX_VALUE,margin,1,1,true);
        region(result,authored,coordinates,axes,slopes,1,Double.MAX_VALUE,-Double.MAX_VALUE,Double.MAX_VALUE,margin,1,1,true);
        region(result,authored,coordinates,axes,slopes,0,1,-Double.MAX_VALUE,0,margin,1,1,true);
        region(result,authored,coordinates,axes,slopes,0,1,1,Double.MAX_VALUE,margin,1,1,true);
        return List.copyOf(result);
    }
    private static void region(List<BakedQuad> result,BakedQuad source,double[][] uv,int[] axes,double[] slopes,double lowU,double highU,double lowV,double highV,TextureAtlasSprite sprite,float usedU,float usedV,boolean margin){
        double u0=Double.POSITIVE_INFINITY,v0=u0,u1=Double.NEGATIVE_INFINITY,v1=u1;
        for(double[] p:uv){u0=Math.min(u0,p[0]);u1=Math.max(u1,p[0]);v0=Math.min(v0,p[1]);v1=Math.max(v1,p[1]);}
        u0=Math.max(u0,lowU);u1=Math.min(u1,highU);v0=Math.max(v0,lowV);v1=Math.min(v1,highV);if(u1-u0<1e-8||v1-v0<1e-8)return;
        int[] data=source.getVertices().clone();int stride=data.length/4;
        for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<2;axis++){
            double value=Math.max(axis==0?u0:v0,Math.min(axis==0?u1:v1,uv[vertex][axis]));int pos=vertex*stride+axes[axis];
            data[pos]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(data[pos])+(value-uv[vertex][axis])/slopes[axis]));
            float texture=(float)(margin?.5:Math.max(0,Math.min(1,value)));
            data[vertex*stride+4+axis]=Float.floatToRawIntBits(axis==0?sprite.getU(texture*usedU):sprite.getV(texture*usedV));
        }
        result.add(new BakedQuad(data,source.getTintIndex(),source.getDirection(),sprite,false,false));
    }
    private JoinedCubeQuads(){}
}
