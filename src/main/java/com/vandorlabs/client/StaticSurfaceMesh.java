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

    /** Remove a shared cap once when caching joined transparent panel geometry. */
    StaticSurfaceMesh withoutPlane(int axis,float plane) {
        float[] result=new float[vertices.length];int used=0;
        for(int offset=0;offset<vertices.length;offset+=32) {
            boolean onPlane=true;
            for(int v=0;v<4;v++)onPlane &= Math.abs(vertices[offset+v*8+axis]-plane)<1E-5;
            if(!onPlane){System.arraycopy(vertices,offset,result,used,32);used+=32;}
        }
        return used==vertices.length?this:new StaticSurfaceMesh(Arrays.copyOf(result,used));
    }

    /** One-pixel rails on the pane faces, using the regular glass frame artwork. */
    StaticSurfaceMesh outlineFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            boolean hideLeft,boolean hideRight,boolean hideFront,boolean hideBack,boolean hideLower,boolean hideUpper,int rowAxis) {
        java.util.Map<String,Integer> edges=new java.util.HashMap<>();
        for(int q=0;q<vertices.length;q+=32)if(paneFace(q))for(int e=0;e<4;e++) {
            String key=edgeKey(q+e*8,q+((e+1)&3)*8);edges.put(key,edges.getOrDefault(key,0)+1);
        }
        Capture result=capture();
        for(int q=0;q<vertices.length;q+=32)if(paneFace(q))for(int e=0;e<4;e++) {
            int ai=q+e*8,bi=q+((e+1)&3)*8;
            if(edges.get(edgeKey(ai,bi))!=1)continue;
            if(hideLeft && edgePlane(ai,bi,0,0) || hideRight && edgePlane(ai,bi,0,16)
                    || hideFront && edgePlane(ai,bi,2,0) || hideBack && edgePlane(ai,bi,2,16)
                    || hideLower && edgePlane(ai,bi,rowAxis,0) || hideUpper && edgePlane(ai,bi,rowAxis,16))continue;
            double dx=vertices[bi]-vertices[ai],dy=vertices[bi+1]-vertices[ai+1],dz=vertices[bi+2]-vertices[ai+2];
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1E-5)continue;
            float nx=vertices[ai+5],ny=vertices[ai+6],nz=vertices[ai+7];
            double ix=(ny*dz-nz*dy)/length,iy=(nz*dx-nx*dz)/length,iz=(nx*dy-ny*dx)/length;
            double cx=0,cy=0,cz=0;
            for(int v=0;v<4;v++){cx+=vertices[q+v*8];cy+=vertices[q+v*8+1];cz+=vertices[q+v*8+2];}
            if(ix*(cx/4-vertices[ai])+iy*(cy/4-vertices[ai+1])+iz*(cz/4-vertices[ai+2])<0){ix=-ix;iy=-iy;iz=-iz;}
            // A tiny outward offset keeps rails separate from the underlying transparent pane.
            double ox=nx*.01,oy=ny*.01,oz=nz*.01;
            for(int v=0;v<4;v++) {
                int source=v==0 || v==3?ai:bi;boolean inset=v>=2;
                result.pos(vertices[source]+ox+(inset?ix:0),vertices[source+1]+oy+(inset?iy:0),vertices[source+2]+oz+(inset?iz:0))
                        .color(255,255,255,255).tex(sprite.getInterpolatedU(inset?.5:0),sprite.getInterpolatedV(v==0 || v==3?0:Math.min(8,length/2)))
                        .normal(nx,ny,nz).endVertex();
            }
        }
        // The transparent mesh already contains the exact thickness caps, including
        // clipped corner arms. Close each exposed rim with opaque outer and inner walls.
        for(int q=0;q<vertices.length;q+=32)if(!paneFace(q) && !hiddenCap(q,hideLeft,hideRight,hideFront,hideBack,hideLower,hideUpper,rowAxis)) {
            double[] inset=capInset(q,edges);if(inset==null)continue;
            for(int side=0;side<2;side++)for(int v=0;v<4;v++) {
                int source=q+(side==0?v:3-v)*8;float sign=side==0?1:-1;
                result.pos(vertices[source]+(side==0?0:inset[0])+vertices[q+5]*sign*.01,
                        vertices[source+1]+(side==0?0:inset[1])+vertices[q+6]*sign*.01,
                        vertices[source+2]+(side==0?0:inset[2])+vertices[q+7]*sign*.01)
                        .color(255,255,255,255).tex(sprite.getInterpolatedU(v==0 || v==3?.16:.28),sprite.getInterpolatedV(v<2?4:4.12))
                        .normal(vertices[q+5]*sign,vertices[q+6]*sign,vertices[q+7]*sign).endVertex();
            }
        }
        return result.finish();
    }
    private boolean hiddenCap(int q,boolean left,boolean right,boolean front,boolean back,boolean lower,boolean upper,int axis) {
        return left && capPlane(q,0,0) || right && capPlane(q,0,16) || front && capPlane(q,2,0) || back && capPlane(q,2,16) || lower && capPlane(q,axis,0) || upper && capPlane(q,axis,16);
    }
    private boolean capPlane(int q,int axis,float value){for(int v=0;v<4;v++)if(Math.abs(vertices[q+v*8+axis]-value)>1E-4)return false;return true;}
    private boolean capContains(int q,int point){for(int v=0;v<4;v++){int p=q+v*8;if(Math.abs(vertices[p]-vertices[point])+Math.abs(vertices[p+1]-vertices[point+1])+Math.abs(vertices[p+2]-vertices[point+2])<1E-4)return true;}return false;}
    private double[] capInset(int cap,java.util.Map<String,Integer> edges){
        for(int q=0;q<vertices.length;q+=32)if(paneFace(q))for(int e=0;e<4;e++){
            int a=q+e*8,b=q+((e+1)&3)*8;if(edges.get(edgeKey(a,b))!=1 || !capContains(cap,a) || !capContains(cap,b))continue;
            double dx=vertices[b]-vertices[a],dy=vertices[b+1]-vertices[a+1],dz=vertices[b+2]-vertices[a+2],length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1E-5)continue;
            double x=(vertices[a+6]*dz-vertices[a+7]*dy)/length,y=(vertices[a+7]*dx-vertices[a+5]*dz)/length,z=(vertices[a+5]*dy-vertices[a+6]*dx)/length;
            double cx=0,cy=0,cz=0;for(int v=0;v<4;v++){cx+=vertices[q+v*8];cy+=vertices[q+v*8+1];cz+=vertices[q+v*8+2];}
            if(x*(cx/4-vertices[a])+y*(cy/4-vertices[a+1])+z*(cz/4-vertices[a+2])<0){x=-x;y=-y;z=-z;}return new double[]{x,y,z};
        }return null;
    }
    private boolean paneFace(int q) {
        // Sloping face normals distinguish pane faces from the narrow end/depth caps.
        return Math.abs(vertices[q+6])>1E-5 && Math.abs(vertices[q+6])<.99999;
    }
    private boolean edgePlane(int a,int b,int axis,float plane) {
        return Math.abs(vertices[a+axis]-plane)<1E-4 && Math.abs(vertices[b+axis]-plane)<1E-4;
    }
    private String edgeKey(int a,int b) {
        String aa=Math.round(vertices[a]*10000)+","+Math.round(vertices[a+1]*10000)+","+Math.round(vertices[a+2]*10000);
        String bb=Math.round(vertices[b]*10000)+","+Math.round(vertices[b+1]*10000)+","+Math.round(vertices[b+2]*10000);
        return aa.compareTo(bb)<0?aa+"/"+bb:bb+"/"+aa;
    }

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
