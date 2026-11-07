package com.vandorlabs.neoforge;

import com.vandorlabs.render.*;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Native records from current shared housing/tread geometry; material snapshots are bake-owned. */
final class NativeHousingQuads {
    private static final int[] LOCAL={1,0,4,5,2,3};
    static BakedQuad slab(boolean upper,Direction facing,int meshFace,int layout,ProgrammableModel.MaterialSnapshot material){
        return box(ScreenHousingMesh.slab(upper,true),facing,meshFace,layout,material);
    }
    static BakedQuad cube(Direction facing,int meshFace,int layout,ProgrammableModel.MaterialSnapshot material){
        return box(ScreenHousingMesh.cube(),facing,meshFace,layout,material);
    }
    private static BakedQuad box(ScreenHousingMesh source,Direction facing,int meshFace,int layout,ProgrammableModel.MaterialSnapshot material){
        var mesh=source.textureLayout(layout,material.usedU()/material.usedV());
        var points=mesh.quads[meshFace].vertices;int[] data=new int[32];int turns=facing==Direction.NORTH?0:facing==Direction.SOUTH?2:facing==Direction.WEST?1:3;
        Direction normal=Direction.from3DDataValue(HousingTexturePolicy.housingWorldFace(LOCAL[meshFace],facing.get3DDataValue()));
        for(int j=0;j<4;j++){
            var p=points[(4-j)&3];double a=p.x/16,b=p.y/16,c=p.z/16,x=a,z=c;
            switch(turns){case 1->{x=c;z=1-a;}case 2->{x=1-a;z=1-c;}case 3->{x=1-c;z=a;}default->{}}
            vertex(data,j,x,b,z,p.u,p.v,material,normal);
        }
        return new BakedQuad(data,-1,normal,material.sprite(),false,false);
    }
    static List<BakedQuad> stairs(Direction facing,boolean upper,StairGeometry.Shape shape,int settings,TextureAtlasSprite source){
        return StairGeometry.mesh(facing.get3DDataValue(),upper,shape,settings,NativeStairGeometry.POINTS,(a,b,c,d,ta,tb,tc,td,face,textureFace)->stairFace(source,a,b,c,d,ta,tb,tc,td,Direction.from3DDataValue(face),Direction.from3DDataValue(textureFace)));
    }
    private static BakedQuad stairFace(TextureAtlasSprite sprite,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 ta,Vec3 tb,Vec3 tc,Vec3 td,Direction face,Direction textureFace){
        int[] data=new int[32];var material=new ProgrammableModel.MaterialSnapshot(sprite,1,1);
        for(int i=0;i<4;i++){
            Vec3 p=i==0?a:i==1?b:i==2?c:d,uv=i==0?ta:i==1?tb:i==2?tc:td;double u=textureFace.getAxis()==Direction.Axis.X?uv.z:uv.x,v=textureFace.getAxis()==Direction.Axis.Y?uv.z:1-uv.y;
            if(textureFace==Direction.EAST||textureFace==Direction.NORTH)u=1-u;if(textureFace==Direction.DOWN)v=1-v;vertex(data,i,p.x,p.y,p.z,u*16,v*16,material,face);
        }
        return new BakedQuad(data,-1,face,sprite,true,true);
    }
    static BakedQuad retextureStair(BakedQuad source,int layout,Direction textureFace,ProgrammableModel.MaterialSnapshot material){
        TextureAtlasSprite old=source.getSprite();int[] data=source.getVertices().clone();int stride=data.length/4;float[] u=new float[4],v=new float[4];float lo=16,hi=0,ulo=16,uhi=0;
        for(int i=0;i<4;i++){u[i]=(Float.intBitsToFloat(data[i*stride+4])-old.getU0())/(old.getU1()-old.getU0())*16;v[i]=(Float.intBitsToFloat(data[i*stride+5])-old.getV0())/(old.getV1()-old.getV0())*16;lo=Math.min(lo,v[i]);hi=Math.max(hi,v[i]);ulo=Math.min(ulo,u[i]);uhi=Math.max(uhi,u[i]);}
        double unitU=1D/16,unitV=1D/16,aspect=material.usedU()/material.usedV();
        for(int a=0;a<4;a++)for(int b=0;b<4;b++){
            double length=0;for(int axis=0;axis<3;axis++){double delta=Float.intBitsToFloat(data[a*stride+axis])-Float.intBitsToFloat(data[b*stride+axis]);length+=delta*delta;}length=Math.sqrt(length);
            if(Math.abs(u[a]-u[b])>1e-6&&Math.abs(v[a]-v[b])<1e-6)unitU=length/Math.abs(u[a]-u[b]);if(Math.abs(v[a]-v[b])>1e-6&&Math.abs(u[a]-u[b])<1e-6)unitV=length/Math.abs(v[a]-v[b]);
        }
        boolean fit=TextureLayoutMode.fit(layout,(uhi-ulo)*unitU,(hi-lo)*unitV,aspect);
        for(int i=0;i<4;i++){data[i*stride+4]=Float.floatToRawIntBits(atlas(material,HousingTexturePolicy.stairU(layout,fit,u[i],ulo,uhi,aspect),true));data[i*stride+5]=Float.floatToRawIntBits(atlas(material,HousingTexturePolicy.stairV(layout,fit,v[i],lo,hi,textureFace.get3DDataValue(),aspect),false));}
        return new BakedQuad(data,source.getTintIndex(),source.getDirection(),material.sprite(),source.isShade(),source.hasAmbientOcclusion());
    }
    static float atlas(ProgrammableModel.MaterialSnapshot material,double value,boolean u){
        var sprite=material.sprite();float low=u?sprite.getU0():sprite.getV0(),high=u?sprite.getU1():sprite.getV1(),used=u?material.usedU():material.usedV();
        float contentHigh=low+(high-low)*used;return low+(contentHigh-low)*(float)value/16;
    }
    private static void vertex(int[] data,int i,double x,double y,double z,double u,double v,ProgrammableModel.MaterialSnapshot material,Direction normal){
        int at=i*8;data[at]=Float.floatToRawIntBits((float)x);data[at+1]=Float.floatToRawIntBits((float)y);data[at+2]=Float.floatToRawIntBits((float)z);data[at+3]=-1;data[at+4]=Float.floatToRawIntBits(atlas(material,u,true));data[at+5]=Float.floatToRawIntBits(atlas(material,v,false));data[at+7]=((normal.getStepX()*127)&255)|(((normal.getStepY()*127)&255)<<8)|(((normal.getStepZ()*127)&255)<<16);
    }
    private NativeHousingQuads(){}
}
