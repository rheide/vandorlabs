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
    /** Reflect the artwork about the leaf centre, retaining face winding and outward normals. */
    StaticSurfaceMesh mirrorX(double centerSum){
        float[] data=new float[vertices.length];for(int q=0;q<vertices.length;q+=32)for(int v=0;v<4;v++){int from=q+(3-v)*8,to=q+v*8;System.arraycopy(vertices,from,data,to,8);data[to]=(float)(centerSum-data[to]);data[to+5]=-data[to+5];}return new StaticSurfaceMesh(data);
    }
    int vertexCount() { return vertices.length/8; }
    /** Include float records and reserve both lazy packed batch arrays on the render thread. */
    long cacheRetainedBytes() { return 256L+vertexCount()*88L; }
    StaticSurfaceMesh glassTexture(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double borderX,double borderY){
        float[] data=vertices.clone();double width=layout.columns-2*borderX,height=layout.rows-2*borderY;
        for(int i=0;i<data.length;i+=8){
            double u=raw?data[i+3]:(data[i+3]-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU());
            double v=raw?data[i+4]:(data[i+4]-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV());
            if(layout.flipU)u=1-u;if(layout.flipV)v=1-v;
            u=com.vandorlabs.render.PanelTextureMapping.stretch(u,layout.column,borderX,width);v=com.vandorlabs.render.PanelTextureMapping.stretch(v,layout.rows-1-layout.row,borderY,height);
            data[i+3]=sprite.getInterpolatedU(u*16);data[i+4]=sprite.getInterpolatedV(v*16);
        }
        return new StaticSurfaceMesh(data);
    }
    private static final com.vandorlabs.render.PanelTextureMesh.Sink<Capture,net.minecraft.client.renderer.texture.TextureAtlasSprite> TEXTURE_VERTEX=new com.vandorlabs.render.PanelTextureMesh.Sink<Capture,net.minecraft.client.renderer.texture.TextureAtlasSprite>(){
        public void atlas(Capture capture,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,double x,double y,double z,double u,double v,float nx,float ny,float nz){capture.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex();}
        public void pixel(Capture capture,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,double x,double y,double z,double u,double v,float nx,float ny,float nz){capture.pos(x,y,z).tex(sprite.getInterpolatedU(u),sprite.getInterpolatedV(v)).normal(nx,ny,nz).endVertex();}
    };
    /** Subdivide at repeat boundaries so rectangular images never sample neighboring atlas slots. */
    StaticSurfaceMesh textureLayout(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean raw,double borderX,double borderY,int mode){
        Capture out=capture();
        com.vandorlabs.render.PanelTextureMesh.map(TEXTURE_VERTEX,out,sprite,vertices,sprite.getMinU(),sprite.getMaxU(),sprite.getMinV(),sprite.getMaxV(),UnifiedTextureSprites.aspect(sprite),layout.column,layout.row,layout.columns,layout.rows,layout.flipU,layout.flipV,raw,borderX,borderY,mode);
        return out.finish();
    }
    /** Keep only the connector surfaces around a sloped porthole's open central body. */
    StaticSurfaceMesh diagonalArms(int mode,boolean inverted,double lower,double upper) {
        return new StaticSurfaceMesh(com.vandorlabs.render.PanelMeshTransform.diagonalArms(vertices,mode,inverted,lower,upper));
    }
    boolean samplesOnly(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        for(int i=0;i<vertices.length;i+=8)
            if(vertices[i+3]<sprite.getMinU()-1E-6 || vertices[i+3]>sprite.getMaxU()+1E-6
                    || vertices[i+4]<sprite.getMinV()-1E-6 || vertices[i+4]>sprite.getMaxV()+1E-6)return false;
        return true;
    }

    private static final com.vandorlabs.render.PanelVertexEmitter.RawSink<Capture> MESH_VERTEX=(capture,x,y,z,u,v,nx,ny,nz)->capture.pos(x,y,z).color(255,255,255,255).tex(u,v).normal(nx,ny,nz).endVertex();

    /** Rotate local panel coordinates and normals together; UVs retain donor orientation. */
    StaticSurfaceMesh panelTransform(int turns,double depth,double left,double width) {
        return new StaticSurfaceMesh(com.vandorlabs.render.PanelMeshTransform.transform(vertices,turns,depth,left,width));
    }
    /** Close the opaque stem at a material boundary, retaining porthole openings in its section. */
    StaticSurfaceMesh panelEnd(double cut,double near,net.minecraft.client.renderer.texture.TextureAtlasSprite metal,boolean right) {
        Capture result=capture();com.vandorlabs.render.PanelMeshTransform.end(TEXTURE_VERTEX,result,metal,vertices,cut,near,right);return result.finish();
    }
    boolean hasPlaneFace(int axis,double plane,double normal) {
        for(int q=0;q<vertices.length;q+=32) {
            boolean match=true;
            for(int v=0;v<4;v++)match&=Math.abs(vertices[q+v*8+axis]-plane)<1E-4 && vertices[q+v*8+5+axis]*normal>.5;
            if(match)return true;
        }
        return false;
    }
    boolean inside(double x0,double y0,double z0,double x1,double y1,double z1) {
        for(int i=0;i<vertices.length;i+=8)if(vertices[i]<x0-1E-4 || vertices[i]>x1+1E-4
                || vertices[i+1]<y0-1E-4 || vertices[i+1]>y1+1E-4 || vertices[i+2]<z0-1E-4 || vertices[i+2]>z1+1E-4)return false;
        return true;
    }
    /** Clip original surfaces, interpolating atlas UVs at each new edge. No interior seam caps. */
    StaticSurfaceMesh box(double x0,double y0,double z0,double x1,double y1,double z1,boolean outside) {
        Capture result=capture();com.vandorlabs.render.PanelMeshTransform.box(MESH_VERTEX,result,vertices,0,vertices.length,new double[]{x0,x1,y0,y1,z0,z1},outside);return result.finish();
    }

    /** Remove a shared cap once when caching joined transparent panel geometry. */
    StaticSurfaceMesh withoutPlane(int axis,float plane) {
        float[] result=com.vandorlabs.render.PanelMeshTransform.withoutPlane(vertices,axis,plane);return result==vertices?this:new StaticSurfaceMesh(result);
    }

    /** One-pixel rails on the pane faces, using the regular glass frame artwork. */
    StaticSurfaceMesh outlineFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            boolean hideLeft,boolean hideRight,boolean hideFront,boolean hideBack,boolean hideLower,boolean hideUpper,int rowAxis) {
        return outlineFrame(sprite,hideLeft,hideRight,hideFront,hideBack,hideLower,hideUpper,rowAxis,false,null,null,null);
    }
    StaticSurfaceMesh outlineFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,net.minecraft.client.renderer.texture.TextureAtlasSprite pane,net.minecraft.client.renderer.texture.TextureAtlasSprite interior,
            boolean hideLeft,boolean hideRight,boolean hideFront,boolean hideBack,boolean hideLower,boolean hideUpper,int rowAxis){return outlineFrame(sprite,hideLeft,hideRight,hideFront,hideBack,hideLower,hideUpper,rowAxis,false,pane,interior,interior);}
    StaticSurfaceMesh outlineFlatFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        return outlineFrame(sprite,false,false,false,false,false,false,1,true,null,null,null);
    }
    StaticSurfaceMesh outlineFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,net.minecraft.client.renderer.texture.TextureAtlasSprite pane,net.minecraft.client.renderer.texture.TextureAtlasSprite interior,net.minecraft.client.renderer.texture.TextureAtlasSprite exterior,
            boolean hideLeft,boolean hideRight,boolean hideFront,boolean hideBack,boolean hideLower,boolean hideUpper,int rowAxis){return outlineFrame(sprite,hideLeft,hideRight,hideFront,hideBack,hideLower,hideUpper,rowAxis,false,pane,interior,exterior);}
    StaticSurfaceMesh outlineFlatFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,net.minecraft.client.renderer.texture.TextureAtlasSprite pane,net.minecraft.client.renderer.texture.TextureAtlasSprite interior,net.minecraft.client.renderer.texture.TextureAtlasSprite exterior){return outlineFrame(sprite,false,false,false,false,false,false,1,true,pane,interior,exterior);}
    private static final com.vandorlabs.render.PanelOutlineFrame.Textures<net.minecraft.client.renderer.texture.TextureAtlasSprite> FRAME_TEXTURES=new com.vandorlabs.render.PanelOutlineFrame.Textures<net.minecraft.client.renderer.texture.TextureAtlasSprite>(){
        public net.minecraft.client.renderer.texture.TextureAtlasSprite edge(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,double[] a,double[] b){return FrameEdgeTextures.edge(sprite,a,b);}
        public boolean authored(net.minecraft.client.renderer.texture.TextureAtlasSprite original,net.minecraft.client.renderer.texture.TextureAtlasSprite edge){return edge!=original||original.getIconName().contains("/frames/");}
        public net.minecraft.client.renderer.texture.TextureAtlasSprite map(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,double[][] points){return FrameEdgeTextures.map(sprite,points);}
        public double unU(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float u){return sprite.getUnInterpolatedU(u);}
        public double unV(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float v){return sprite.getUnInterpolatedV(v);}
    };
    private StaticSurfaceMesh outlineFrame(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            boolean hideLeft,boolean hideRight,boolean hideFront,boolean hideBack,boolean hideLower,boolean hideUpper,int rowAxis,boolean flat,net.minecraft.client.renderer.texture.TextureAtlasSprite pane,net.minecraft.client.renderer.texture.TextureAtlasSprite interior,net.minecraft.client.renderer.texture.TextureAtlasSprite exterior) {
        Capture result=capture();com.vandorlabs.render.PanelOutlineFrame.emit(TEXTURE_VERTEX,result,FRAME_TEXTURES,vertices,sprite,pane,interior,exterior,hideLeft,hideRight,hideFront,hideBack,hideLower,hideUpper,rowAxis,flat);return result.finish();
    }

    /** Apply the standard horizontal housing transform while appending to Forge's shared buffer. */
    void drawPlaced(BufferBuilder buffer,double x,double y,double z,int rotation,int light){
        boolean normals=buffer.getVertexFormat().hasNormal();
        for(int i=0;i<vertices.length;i+=8){
            double a=vertices[i]/16D,b=vertices[i+1]/16D,c=vertices[i+2]/16D,rx=a,rz=c;
            float nx=vertices[i+5],ny=vertices[i+6],nz=vertices[i+7],fx=nx,fz=nz;
            switch(rotation){case 1:rx=c;rz=1-a;fx=nz;fz=-nx;break;case 2:rx=1-a;rz=1-c;fx=-nx;fz=-nz;break;case 3:rx=1-c;rz=a;fx=-nz;fz=nx;break;default:break;}
            buffer.pos(x+rx,y+b,z+rz).color(255,255,255,255).tex(vertices[i+3],vertices[i+4]).lightmap(light>>>16,light&65535);
            if(normals)buffer.normal(fx,ny,fz);buffer.endVertex();
        }
    }
    /** Rigid Segmented Door panel transform; cached atlas vertices stay unchanged. */
    private static final ThreadLocal<double[]> SECTION_VERTEX=ThreadLocal.withInitial(()->new double[6]);
    void drawSection(BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light,int alpha){
        boolean normals=buffer.getVertexFormat().hasNormal();double[] out=SECTION_VERTEX.get();
        for(int i=0;i<vertices.length;i+=8){
            com.vandorlabs.segmented.SegmentedPanelTransform.vertex(vertices,i,pose,rotation,x,y,z,out);
            buffer.pos(out[0],out[1],out[2]).color(255,255,255,alpha).tex(vertices[i+3],vertices[i+4]).lightmap(light>>>16,light&65535);
            if(normals)buffer.normal((float)out[3],(float)out[4],(float)out[5]);buffer.endVertex();
        }
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

    /** Transform atlas UVs at emission time without copying the cached mesh. */
    void drawColoredMapped(BufferBuilder buffer,int sky,int block,float alpha,net.minecraft.client.renderer.texture.TextureAtlasSprite source,double[] uv){
        double us=(uv[3]-uv[2])/(source.getMaxU()-source.getMinU()),vs=(uv[1]-uv[0])/(source.getMaxV()-source.getMinV());
        for(int i=0;i<vertices.length;i+=8)buffer.pos(vertices[i],vertices[i+1],vertices[i+2]).color(1F,1F,1F,alpha)
            .tex(uv[2]+(vertices[i+3]-source.getMinU())*us,uv[0]+(vertices[i+4]-source.getMinV())*vs).lightmap(sky,block).normal(vertices[i+5],vertices[i+6],vertices[i+7]).endVertex();
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
    /** Nested mesh emitters must not reset a caller's thread-local capture. */
    static Capture independentCapture() { return new Capture(); }

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
