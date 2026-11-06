package com.vandorlabs.light;

import com.vandorlabs.render.ScreenHousingMesh;

/** Frozen released housing/face records in local pixel units. Build on change; emission allocates nothing. */
public final class ProgrammableLightMesh {
    public interface Sink {void vertex(double x,double y,double z,double u,double v,float nx,float ny,float nz);}
    private final double[] records;
    private ProgrammableLightMesh(double[] records){this.records=records;}
    public static ProgrammableLightMesh housing(double[] bounds,int visible,int layout,double aspect){
        if(bounds==null||bounds.length!=6||(visible&~63)!=0)throw new IllegalArgumentException("Light mesh bounds/mask");
        ScreenHousingMesh mesh=ScreenHousingMesh.box(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]).textureLayout(layout,aspect).boxFaces(visible&~4);
        double[] records=new double[mesh.quads.length*4*8];int at=0;
        for(ScreenHousingMesh.Face face:mesh.quads)for(int i=0;i<4;i++){ScreenHousingMesh.Vertex v=face.vertices[(4-i)%4];records[at++]=v.x;records[at++]=v.y;records[at++]=v.z;records[at++]=v.u;records[at++]=v.v;records[at++]=face.nx;records[at++]=face.ny;records[at++]=face.nz;}
        return new ProgrammableLightMesh(records);
    }
    public static ProgrammableLightMesh face(double[] bounds,double left,double top,double right,double bottom){
        if(bounds==null||bounds.length!=6||!Double.isFinite(left)||!Double.isFinite(top)||!Double.isFinite(right)||!Double.isFinite(bottom)||left>=right||top>=bottom)throw new IllegalArgumentException("Light face UV");
        double x0=bounds[0],y0=bounds[1],z=bounds[2],x1=bounds[3],y1=bounds[4];
        return new ProgrammableLightMesh(new double[]{x1,y1,z,right,top,0,0,-1,x1,y0,z,right,bottom,0,0,-1,x0,y0,z,left,bottom,0,0,-1,x0,y1,z,left,top,0,0,-1});
    }
    /** Invert the shared authoritative world box to the source's pre-GL local pixel box. */
    public static double[] localBounds(int family,int facing,boolean upper,boolean small){
        double[] w=ProgrammableLightShape.bounds(family,facing,upper,small),b;
        switch(facing){
            case 0:b=new double[]{w[0],1-w[5],w[1],w[3],1-w[2],w[4]};break;
            case 1:b=new double[]{w[0],w[2],1-w[4],w[3],w[5],1-w[1]};break;
            case 3:b=new double[]{1-w[3],w[1],1-w[5],1-w[0],w[4],1-w[2]};break;
            case 4:b=new double[]{1-w[5],w[1],w[0],1-w[2],w[4],w[3]};break;
            case 5:b=new double[]{w[2],w[1],1-w[3],w[5],w[4],1-w[0]};break;
            default:b=w;
        }
        for(int i=0;i<6;i++)b[i]*=16;return b;
    }
    public void emit(Sink sink){for(int at=0;at<records.length;at+=8)sink.vertex(records[at],records[at+1],records[at+2],records[at+3],records[at+4],(float)records[at+5],(float)records[at+6],(float)records[at+7]);}
    public int vertices(){return records.length/8;}public int retainedBytes(){return 48+records.length*8;}
}
