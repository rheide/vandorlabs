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
        return xPanels(hand,caps,2);
    }
    StaticSurfaceMesh[] xPanels(int hand,boolean caps,double width) {return panels(hand,caps,0,width);}
    StaticSurfaceMesh[] splitPanels(boolean vertical,boolean caps) {return panels(0,caps,vertical?2:1,1);}
    private StaticSurfaceMesh[] panels(int hand,boolean caps,int mode,double width) {
        StaticSurfaceMesh[] panels=new StaticSurfaceMesh[mode==0?4:2];
        for(int panel=0;panel<panels.length;panel++) {
            Capture capture=capture();
            for(int offset=0;offset<vertices.length;offset+=32) {
                java.util.List<float[]> polygon=new java.util.ArrayList<>();
                for(int v=0;v<4;v++)polygon.add(Arrays.copyOfRange(vertices,offset+v*8,offset+(v+1)*8));
                polygon=mode==0?com.vandorlabs.render.XDoorPanel.clip(polygon,hand,panel,width):
                        com.vandorlabs.render.SplitDoorPanel.clip(polygon,mode==2,panel,1);
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
                        for(int plane=0;plane<(mode==0?2:1);plane++) {
                            if(Math.abs(cutDistance(mode,panel,plane,hand,a,width))>1e-6
                                    || Math.abs(cutDistance(mode,panel,plane,hand,b,width))>1e-6)continue;
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
    /** Cut arbitrary flat/sloped leaf surfaces in their shared opening plane. */
    StaticSurfaceMesh[] panels(com.vandorlabs.render.PanelMotion motion,double[][] closed,int thicknessEnd) {
        StaticSurfaceMesh[] result=new StaticSurfaceMesh[motion.count()];
        double[] thickness={closed[thicknessEnd][0]-closed[0][0],closed[thicknessEnd][1]-closed[0][1],closed[thicknessEnd][2]-closed[0][2]};
        double thicknessLength=Math.sqrt(com.vandorlabs.render.PanelPolyhedron.dot(thickness,thickness));
        for(int panel=0;panel<result.length;panel++) {
            Capture capture=capture();
            java.util.List<double[][]> caps=panelCaps(closed,motion,panel);
            for(int offset=0;offset<vertices.length;offset+=32) {
                java.util.List<float[]> polygon=new java.util.ArrayList<>();
                for(int v=0;v<4;v++)polygon.add(Arrays.copyOfRange(vertices,offset+v*8,offset+(v+1)*8));
                for(int plane=0;plane<motion.planes();plane++)polygon=clipPanel(polygon,motion.plane(panel,plane));
                emitPolygon(capture,polygon);
                double dot=vertices[offset+5]*thickness[0]+vertices[offset+6]*thickness[1]+vertices[offset+7]*thickness[2];
                if(dot>thicknessLength*.05)for(double[][] cap:caps)emitCap(capture,cap,offset,thickness);
            }
            result[panel]=capture.finish();
        }
        return result;
    }
    private static void emitPolygon(Capture capture,java.util.List<float[]> polygon) {
        for(int v=1;v+1<polygon.size();v++){emit(capture,polygon.get(0));emit(capture,polygon.get(v));emit(capture,polygon.get(v+1));emit(capture,polygon.get(v+1));}
    }
    private static java.util.List<double[][]> panelCaps(double[][] closed,com.vandorlabs.render.PanelMotion motion,int panel) {
        java.util.List<double[][]> result=new java.util.ArrayList<>();
        for(double[][] face:com.vandorlabs.render.PanelPolyhedron.faces(closed,motion,panel,0)) {
            boolean cut=false;
            for(int plane=0;plane<motion.planes();plane++){boolean on=true;for(double[] point:face)on&=Math.abs(com.vandorlabs.render.PanelMotion.distance(motion.plane(panel,plane),point))<1e-7;cut|=on;}
            if(!cut)continue;
            boolean original=false;
            for(int[] indices:com.vandorlabs.render.TrapdoorGeometry.FACES) {
                double[] a=closed[indices[0]],b=closed[indices[1]],c=closed[indices[3]];
                double[] normal=com.vandorlabs.render.PanelPolyhedron.cross(delta(b,a),delta(c,a));
                boolean on=true;for(double[] point:face)on&=Math.abs(com.vandorlabs.render.PanelPolyhedron.dot(delta(point,a),normal))<1e-7;original|=on;
            }
            if(!original)result.add(face);
        }
        return result;
    }
    private void emitCap(Capture capture,double[][] face,int offset,double[] thickness) {
        double[] a=point(offset),b=point(offset+8),c=point(offset+16),e=delta(b,a),f=delta(c,a);
        double[] front=com.vandorlabs.render.PanelPolyhedron.cross(e,f);double frontLength=com.vandorlabs.render.PanelPolyhedron.dot(front,front);
        if(frontLength<1e-16)return;
        double denominator=com.vandorlabs.render.PanelPolyhedron.dot(front,thickness);if(Math.abs(denominator)<1e-12)return;
        double frontDepth=com.vandorlabs.render.PanelPolyhedron.dot(front,a);
        double ee=com.vandorlabs.render.PanelPolyhedron.dot(e,e),ff=com.vandorlabs.render.PanelPolyhedron.dot(f,f),ef=com.vandorlabs.render.PanelPolyhedron.dot(e,f),det=ee*ff-ef*ef;
        double[] normal=com.vandorlabs.render.PanelPolyhedron.cross(delta(face[1],face[0]),delta(face[2],face[0]));
        if(com.vandorlabs.render.PanelPolyhedron.dot(normal,normal)<1e-16)return;
        normal=com.vandorlabs.render.PanelPolyhedron.unit(normal);
        java.util.List<float[]> polygon=new java.util.ArrayList<>();
        for(double[] p:face) {
            double depth=(frontDepth-com.vandorlabs.render.PanelPolyhedron.dot(front,p))/denominator;
            double[] q={p[0]+thickness[0]*depth-a[0],p[1]+thickness[1]*depth-a[1],p[2]+thickness[2]*depth-a[2]};
            double qe=com.vandorlabs.render.PanelPolyhedron.dot(q,e),qf=com.vandorlabs.render.PanelPolyhedron.dot(q,f),s=(qe*ff-qf*ef)/det,t=(qf*ee-qe*ef)/det;
            float u=(float)(vertices[offset+3]+s*(vertices[offset+11]-vertices[offset+3])+t*(vertices[offset+19]-vertices[offset+3]));
            float v=(float)(vertices[offset+4]+s*(vertices[offset+12]-vertices[offset+4])+t*(vertices[offset+20]-vertices[offset+4]));
            polygon.add(new float[]{(float)p[0],(float)p[1],(float)p[2],u,v,(float)normal[0],(float)normal[1],(float)normal[2]});
        }
        // Project each material tile's borders through the original extrusion.
        for(int edge=0;edge<4;edge++) {
            double[] start=point(offset+edge*8),end=point(offset+((edge+1)%4)*8),inward=com.vandorlabs.render.PanelPolyhedron.cross(front,delta(end,start));
            if(com.vandorlabs.render.PanelPolyhedron.dot(inward,inward)<1e-16)continue;
            double adjust=com.vandorlabs.render.PanelPolyhedron.dot(inward,thickness)/denominator;
            double[] plane={inward[0]-front[0]*adjust,inward[1]-front[1]*adjust,inward[2]-front[2]*adjust,-com.vandorlabs.render.PanelPolyhedron.dot(inward,start)+frontDepth*adjust};
            polygon=clipPanel(polygon,plane);
        }
        emitPolygon(capture,polygon);
    }
    private double[] point(int offset){return new double[]{vertices[offset],vertices[offset+1],vertices[offset+2]};}
    private static double[] delta(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double distance(double[] plane,float[] point){return plane[0]*point[0]+plane[1]*point[1]+plane[2]*point[2]+plane[3];}
    private static java.util.List<float[]> clipPanel(java.util.List<float[]> polygon,double[] plane) {
        java.util.List<float[]> out=new java.util.ArrayList<>();if(polygon.isEmpty())return out;
        float[] previous=polygon.get(polygon.size()-1);double before=distance(plane,previous);
        for(float[] current:polygon) {
            double after=distance(plane,current);
            if((before>=0)!=(after>=0)){double t=before/(before-after);float[] cut=new float[current.length];for(int i=0;i<cut.length;i++)cut[i]=(float)(previous[i]+t*(current[i]-previous[i]));out.add(cut);}
            if(after>=0)out.add(current);previous=current;before=after;
        }
        return out;
    }
    private static double cutDistance(int mode,int panel,int plane,int hand,float[] v,double width) {
        return mode==0?com.vandorlabs.render.XDoorPanel.distance(panel,plane,v[0]+hand,v[1],width):
                com.vandorlabs.render.SplitDoorPanel.distance(mode==2,panel,1,v[0],v[1]);
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
