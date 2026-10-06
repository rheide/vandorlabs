package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import java.util.Arrays;

/** Static positions, atlas UVs and normals; lighting and shader vertex generation stay live. */
final class StaticSurfaceMesh {
    private final float[] vertices;
    private StaticSurfaceMesh(float[] vertices) { this.vertices=vertices; }
    private OpaqueDoorBatch.Mesh batch;
    OpaqueDoorBatch.Mesh batchMesh() {
        if(batch!=null)return batch;
        int[] data=new int[vertexCount()*7];
        for(int v=0;v<vertexCount();v++) {
            for(int axis=0;axis<3;axis++)data[v*7+axis]=Float.floatToRawIntBits(vertices[v*8+axis]);
            data[v*7+3]=-1;data[v*7+4]=Float.floatToRawIntBits(vertices[v*8+3]);
            data[v*7+5]=Float.floatToRawIntBits(vertices[v*8+4]);
        }
        batch=new OpaqueDoorBatch.Mesh(data,false);return batch;
    }
    int vertexCount() { return vertices.length/8; }

    void draw(BufferBuilder buffer,int lightmapA,int lightmapB) {
        for(int i=0;i<vertices.length;i+=8)
            buffer.pos(vertices[i],vertices[i+1],vertices[i+2]).color(255,255,255,255)
                    .tex(vertices[i+3],vertices[i+4]).lightmap(lightmapA,lightmapB)
                    .normal(vertices[i+5],vertices[i+6],vertices[i+7]).endVertex();
    }
    void drawColored(BufferBuilder buffer,int lightmapA,int lightmapB,float r,float g,float b,float a) {
        for(int i=0;i<vertices.length;i+=8)
            buffer.pos(vertices[i],vertices[i+1],vertices[i+2]).color(r,g,b,a)
                    .tex(vertices[i+3],vertices[i+4]).lightmap(lightmapA,lightmapB)
                    .normal(vertices[i+5],vertices[i+6],vertices[i+7]).endVertex();
    }

    /** Split source quads once, retaining atlas coordinates across the diagonal cuts. */
    StaticSurfaceMesh[] xPanels(int hand,boolean caps) {
        StaticSurfaceMesh[] panels=new StaticSurfaceMesh[4];
        for(int panel=0;panel<4;panel++) {
            Capture capture=capture();
            for(int offset=0;offset<vertices.length;offset+=32) {
                java.util.List<float[]> polygon=new java.util.ArrayList<>();
                for(int v=0;v<4;v++)polygon.add(Arrays.copyOfRange(vertices,offset+v*8,offset+(v+1)*8));
                polygon=com.vandorlabs.render.XDoorPanel.clip(polygon,hand,panel);
                for(int v=1;v+1<polygon.size();v++) {
                    emit(capture,polygon.get(0));emit(capture,polygon.get(v));emit(capture,polygon.get(v+1));emit(capture,polygon.get(v+1));
                }
                // Seal the new diagonal edges of the two-pixel-thick sliding slab.
                // Reuse front-face alpha/UVs, so cutout windows stay open at the edge.
                if(caps && vertices[offset+7]>.5F && Math.abs(vertices[offset+2]-9F/16)<1e-6) {
                    for(int v=0;v<polygon.size();v++) {
                        float[] a=polygon.get(v),b=polygon.get((v+1)%polygon.size());
                        double length=Math.hypot(a[0]-b[0],a[1]-b[1]);
                        if(length<1e-6)continue;
                        for(int plane=0;plane<2;plane++) {
                            if(Math.abs(com.vandorlabs.render.XDoorPanel.distance(panel,plane,a[0]+hand,a[1]))>1e-6
                                    || Math.abs(com.vandorlabs.render.XDoorPanel.distance(panel,plane,b[0]+hand,b[1]))>1e-6)continue;
                            float[] c=b.clone(),d=a.clone();c[2]=d[2]=7F/16;
                            float nx=(float)((b[1]-a[1])/length),ny=(float)((a[0]-b[0])/length);
                            for(float[] point:new float[][]{a.clone(),d,c,b.clone()}) {
                                point[5]=nx;point[6]=ny;point[7]=0;emit(capture,point);
                            }
                        }
                    }
                }
            }
            panels[panel]=capture.finish();
        }
        return panels;
    }
    private static void emit(Capture capture,float[] v) {
        capture.pos(v[0],v[1],v[2]).color(255,255,255,255).tex(v[3],v[4]).normal(v[5],v[6],v[7]).endVertex();
    }

    private static final ThreadLocal<Capture> CAPTURE=ThreadLocal.withInitial(Capture::new);
    static Capture capture() { Capture result=CAPTURE.get();result.used=0;return result; }

    /** Captures existing geometry emitters without packing a shader-dependent vertex layout. */
    static final class Capture extends BufferBuilder {
        private float[] data=new float[1024];
        private int used;
        private float x,y,z,u,v,nx,ny,nz;
        private Capture() { super(16); }
        @Override public BufferBuilder pos(double x,double y,double z) { this.x=(float)x;this.y=(float)y;this.z=(float)z;return this; }
        @Override public BufferBuilder tex(double u,double v) { this.u=(float)u;this.v=(float)v;return this; }
        @Override public BufferBuilder normal(float x,float y,float z) { nx=x;ny=y;nz=z;return this; }
        @Override public BufferBuilder color(int r,int g,int b,int a) {
            if(r!=255 || g!=255 || b!=255 || a!=255)throw new IllegalArgumentException("Static surface must use white vertex color");
            return this;
        }
        @Override public BufferBuilder lightmap(int a,int b) { return this; }
        @Override public void endVertex() {
            if(used+8>data.length)data=Arrays.copyOf(data,data.length*2);
            data[used++]=x;data[used++]=y;data[used++]=z;data[used++]=u;data[used++]=v;
            data[used++]=nx;data[used++]=ny;data[used++]=nz;
        }
        StaticSurfaceMesh finish() {
            if((used/8)%4!=0)throw new IllegalStateException("Incomplete static surface quad");
            return new StaticSurfaceMesh(Arrays.copyOf(data,used));
        }
    }
}
