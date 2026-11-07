package com.vandorlabs.canopy;

/** The released Icon triangulates every polygon, including quads, and retains packed double normals. */
public final class CanopyItemEmission {
    public interface Sink {
        void quad(int direction,int packedNormal);
        void vertex(float x,float y,float z,float u,float v,int color);
    }
    private CanopyItemEmission(){}
    public static void emit(CanopyFace face,float opacity,CanopyEmission.Sprite sprite,Sink sink){
        for(int triangle=1;triangle<face.points()-1;triangle++){
            quad(face,opacity,sprite,sink,0,triangle,triangle+1,triangle+1);
            if(face.doubleSided)quad(face,opacity,sprite,sink,triangle+1,triangle,0,0);
        }
    }
    private static void quad(CanopyFace face,float opacity,CanopyEmission.Sprite sprite,Sink sink,int a,int b,int c,int d){
        CanopyPoint normal=face.vertex(b).subtract(face.vertex(a)).crossProduct(face.vertex(c).subtract(face.vertex(a))).normalize();
        int packed=((int)(normal.x*127)&255)|(((int)(normal.y*127)&255)<<8)|(((int)(normal.z*127)&255)<<16);
        sink.quad(CanopyEmission.direction((float)normal.x,(float)normal.y,(float)normal.z),packed);
        int color=(Math.round(opacity*255)<<24)|0xffffff;
        vertex(face,sprite,sink,a,color);vertex(face,sprite,sink,b,color);vertex(face,sprite,sink,c,color);vertex(face,sprite,sink,d,color);
    }
    private static void vertex(CanopyFace face,CanopyEmission.Sprite sprite,Sink sink,int index,int color){
        CanopyPoint p=face.vertex(index);sink.vertex((float)p.x,(float)p.y,(float)p.z,sprite.u(face.u(index)),sprite.v(face.v(index)),color);
    }
}
