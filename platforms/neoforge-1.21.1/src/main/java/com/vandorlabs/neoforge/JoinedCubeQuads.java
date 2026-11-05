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
        for(int vertex=0;vertex<4;vertex++)for(int uv=0;uv<2;uv++){
            double value=(Float.intBitsToFloat(data[vertex*stride+axes[uv]])-plane.min(axes[uv]))/plane.size(axes[uv]);
            if(signs[uv]<0)value=1-value;
            if(layout==2){double fraction=uv==0?Math.min(1,surface/aspect):Math.min(1,aspect/surface);value=.5+(value-.5)*fraction;}
            data[vertex*stride+4+uv]=Float.floatToRawIntBits(uv==0?sprite.getU((float)value*usedU):sprite.getV((float)value*usedV));
        }
        return List.of(new BakedQuad(data,source.getTintIndex(),source.getDirection(),sprite,false,false));
    }
    private JoinedCubeQuads(){}
}
