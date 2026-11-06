package com.vandorlabs.render;

/** Released Screen primitive order and normals, without graphics or world dependencies. */
public final class ScreenMeshEmission {
    public interface Sink {
        void vertex(double x,double y,double z,double u,double v,float nx,float ny,float nz);
    }
    private ScreenMeshEmission(){}
    /** Housing UVs remain in authored pixel units; callers retain the original quad/triangle topology. */
    public static void housing(ScreenHousingMesh.Face[] faces,boolean ceiling,Sink sink){
        for(ScreenHousingMesh.Face face:faces)for(int i=0;i<face.vertices.length;i++){
            ScreenHousingMesh.Vertex vertex=face.vertices[ceiling?i:(face.vertices.length-i)%face.vertices.length];
            sink.vertex(vertex.x,ceiling?32-vertex.y:vertex.y,vertex.z,vertex.u,vertex.v,face.nx,ceiling?-face.ny:face.ny,face.nz);
        }
    }
    /** Display UVs are normalized endpoints supplied by the selected playback/artwork adapter. */
    public static void surface(ScreenSurface.Quad quad,double uMin,double uMax,double vTop,double vBottom,boolean ceiling,Sink sink){
        for(int i=0;i<4;i++){
            int index=ceiling?(4-i)&3:i;ScreenSurface.Vertex vertex=quad.vertices[index];
            sink.vertex(vertex.x,ceiling?32-vertex.y:vertex.y,vertex.z,index==0||index==3?uMax:uMin,index<2?vTop:vBottom,quad.nx,ceiling?-quad.ny:quad.ny,quad.nz);
        }
    }
    /** Front-frame region UVs remain in authored pixel units, including source aspect correction. */
    public static void frameRegion(ScreenSurface.Quad quad,boolean input,boolean ceiling,double aspect,double left,double top,double right,double bottom,double u0,double v0,double u1,double v1,Sink sink){
        for(int i=0;i<4;i++){
            boolean rightVertex=i==1||i==2;double x=rightVertex?right:left,y=i<2?top:bottom;
            double px=quad.topLeft.x+(quad.topRight.x-quad.topLeft.x)*x+quad.nx*.06,py=quad.topLeft.y+(quad.bottomLeft.y-quad.topLeft.y)*y+quad.ny*.06,pz=quad.topLeft.z+(quad.bottomLeft.z-quad.topLeft.z)*y+quad.nz*.06;
            double u=rightVertex?u1:u0,v=i<2?v0:v1;if(!input)u=1-u;
            sink.vertex(px,ceiling?32-py:py,pz,u*16,v*16/aspect,quad.nx,ceiling?-quad.ny:quad.ny,quad.nz);
        }
    }
}
