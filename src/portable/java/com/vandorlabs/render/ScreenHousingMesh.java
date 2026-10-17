package com.vandorlabs.render;

/** Wall-panel meshes for non-cuboid screen housings, in legacy pixel units. */
public final class ScreenHousingMesh {
    public static final class Vertex {
        public final double x,y,z,u,v;
        private Vertex(double x,double y,double z,double u,double v) {
            this.x=x; this.y=y; this.z=z; this.u=u; this.v=v;
        }
    }
    public static final class Face {
        public final Vertex[] vertices;
        public final float nx,ny,nz;
        private Face(Vertex... vertices) {
            this.vertices=vertices;
            Vertex a=vertices[0],b=vertices[1],c=vertices[2];
            double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
            double x=uz*vy-uy*vz,y=ux*vz-uz*vx,z=uy*vx-ux*vy;
            double length=Math.sqrt(x*x+y*y+z*z);
            nx=(float)(x/length);ny=(float)(y/length);nz=(float)(z/length);
        }
    }
    public final Face[] quads,triangles;

    private ScreenHousingMesh(Face[] quads,Face[] triangles) {
        this.quads=quads; this.triangles=triangles;
    }

    /** Fit complete artwork to each housing face; Tile retains the pixel-space UVs. */
    private volatile ScreenHousingMesh fitted;
    public ScreenHousingMesh sideLayout(boolean tile){if(tile)return this;ScreenHousingMesh result=fitted;if(result==null)synchronized(this){if(fitted==null)fitted=new ScreenHousingMesh(fit(quads),fit(triangles));result=fitted;}return result;}
    private static Face[] fit(Face[] faces) {
        Face[] result=new Face[faces.length];
        for(int i=0;i<faces.length;i++) {
            double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
            for(Vertex v:faces[i].vertices){minU=Math.min(minU,v.u);maxU=Math.max(maxU,v.u);minV=Math.min(minV,v.v);maxV=Math.max(maxV,v.v);}
            Vertex[] vertices=new Vertex[faces[i].vertices.length];
            for(int j=0;j<vertices.length;j++){Vertex v=faces[i].vertices[j];vertices[j]=new Vertex(v.x,v.y,v.z,maxU>minU?(v.u-minU)*16/(maxU-minU):v.u,maxV>minV?(v.v-minV)*16/(maxV-minV):v.v);}
            result[i]=new Face(vertices);
        }
        return result;
    }
    private final java.util.Map<String,ScreenHousingMesh> layouts=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer,ScreenHousingMesh> masks=new java.util.concurrent.ConcurrentHashMap<>();
    public ScreenHousingMesh boxFaces(int visible){
        if(quads.length!=6)throw new IllegalStateException("Box faces required");ScreenHousingMesh found=masks.get(visible);if(found!=null)return found;
        int[] direction={1,0,4,5,2,3};java.util.List<Face> faces=new java.util.ArrayList<>();for(int i=0;i<6;i++)if((visible&(1<<direction[i]))!=0)faces.add(quads[i]);
        found=new ScreenHousingMesh(faces.toArray(new Face[0]),new Face[0]);masks.put(visible,found);return found;
    }
    /** Select fitting per physical face; rectangular artwork tiles at its own aspect. */
    public ScreenHousingMesh textureLayout(int mode,double aspect){
        if(!TextureLayoutMode.valid(mode) || !(aspect>0) || !Double.isFinite(aspect))throw new IllegalArgumentException("Invalid texture layout");
        if(mode==TextureLayoutMode.STRETCH)return sideLayout(false);
        if(mode==TextureLayoutMode.TILE && Math.abs(aspect-1)<1e-9)return this;
        String key=mode+":"+aspect;ScreenHousingMesh found=layouts.get(key);if(found!=null)return found;
        found=new ScreenHousingMesh(layoutFaces(quads,mode,aspect),layoutFaces(triangles,mode,aspect));if(layouts.size()>64)layouts.clear();layouts.put(key,found);return found;
    }
    private static Face[] layoutFaces(Face[] faces,int mode,double aspect){
        Face[] result=new Face[faces.length];
        for(int f=0;f<faces.length;f++){
            Face face=faces[f];double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU,unitU=1,unitV=1;
            for(Vertex a:face.vertices){minU=Math.min(minU,a.u);maxU=Math.max(maxU,a.u);minV=Math.min(minV,a.v);maxV=Math.max(maxV,a.v);
                for(Vertex b:face.vertices){double du=Math.abs(a.u-b.u),dv=Math.abs(a.v-b.v),length=Math.sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)+(a.z-b.z)*(a.z-b.z));if(du>1e-8 && dv<1e-8)unitU=length/du;if(dv>1e-8 && du<1e-8)unitV=length/dv;}
            }
            boolean fit=TextureLayoutMode.fit(mode,(maxU-minU)*unitU,(maxV-minV)*unitV,aspect);Vertex[] vertices=new Vertex[face.vertices.length];
            for(int i=0;i<vertices.length;i++){Vertex v=face.vertices[i];double u=fit && maxU>minU?(v.u-minU)*16/(maxU-minU):TextureLayoutMode.tileU(v.u,aspect),vv=fit && maxV>minV?(v.v-minV)*16/(maxV-minV):TextureLayoutMode.tileV(v.v,aspect);vertices[i]=new Vertex(v.x,v.y,v.z,u,vv);}
            result[f]=new Face(vertices);
        }
        return result;
    }

    private static final ScreenHousingMesh CUBE=buildBox(0,16,0,16);
    private static final ScreenHousingMesh[] SLABS = {
            buildBox(0,8,0,16), buildBox(0,8,8,16),
            buildBox(8,16,0,16), buildBox(8,16,0,8)};
    private static final ScreenHousingMesh CONSOLE=buildConsole();
    private static final ScreenHousingMesh HALF_CONSOLE=buildHalfConsole();
    private static final ScreenHousingMesh DIAGONAL=buildDiagonal(false);
    private static final ScreenHousingMesh DIAGONAL_INVERTED=buildDiagonal(true);

    public static ScreenHousingMesh cube() { return CUBE; }
    private static final java.util.Map<String,ScreenHousingMesh> BOXES=new java.util.concurrent.ConcurrentHashMap<>();
    public static ScreenHousingMesh box(double x0,double y0,double z0,double x1,double y1,double z1){
        String key=x0+":"+y0+":"+z0+":"+x1+":"+y1+":"+z1;ScreenHousingMesh found=BOXES.get(key);if(found!=null)return found;
        double w=x1-x0,h=y1-y0,d=z1-z0;
        found=new ScreenHousingMesh(new Face[]{
            quad(x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,0,0,w,d),
            quad(x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,0,0,w,d),
            quad(x0,y1,z0,x0,y1,z1,x0,y0,z1,x0,y0,z0,0,0,d,h),
            quad(x1,y1,z1,x1,y1,z0,x1,y0,z0,x1,y0,z1,0,0,d,h),
            quad(x1,y1,z0,x0,y1,z0,x0,y0,z0,x1,y0,z0,0,0,w,h),
            quad(x0,y1,z1,x1,y1,z1,x1,y0,z1,x0,y0,z1,0,0,w,h)
        },new Face[0]);if(BOXES.size()>128)BOXES.clear();BOXES.put(key,found);return found;
    }
    public static ScreenHousingMesh slab(boolean upper, boolean tileSides) {
        return SLABS[(upper?2:0)+(tileSides?1:0)];
    }
    private static ScreenHousingMesh buildBox(double low, double high, double v0, double v1) {
        return new ScreenHousingMesh(new Face[] {
                quad(0,high,0, 16,high,0, 16,high,16, 0,high,16, 0,0,16,16),
                quad(0,low,16, 16,low,16, 16,low,0, 0,low,0, 0,0,16,16),
                quad(0,high,0, 0,high,16, 0,low,16, 0,low,0, 0,v0,16,v1),
                quad(16,high,16, 16,high,0, 16,low,0, 16,low,16, 0,v0,16,v1),
                quad(16,high,0, 0,high,0, 0,low,0, 16,low,0, 0,v0,16,v1),
                quad(0,high,16, 16,high,16, 16,low,16, 0,low,16, 0,v0,16,v1)
        }, new Face[0]);
    }

    public static ScreenHousingMesh console() { return CONSOLE; }
    public static ScreenHousingMesh halfConsole() { return HALF_CONSOLE; }
    public static ScreenHousingMesh diagonal(boolean inverted) {
        return inverted?DIAGONAL_INVERTED:DIAGONAL;
    }

    private static ScreenHousingMesh buildConsole() {
        return new ScreenHousingMesh(new Face[]{
                quad(0,1,7.5,16,1,7.5,16,16,15,0,16,15,0,16,16,0),
                quad(0,16,15,16,16,15,16,16,16,0,16,16,0,15,16,16),
                quad(0,16,16,16,16,16,16,1,16,0,1,16,0,16,16,0),
                sideQuad(0,1,7.5,16,15,16,16,1,16),
                sideQuad(16,1,16,16,16,16,15,1,7.5)},
                new Face[0]);
    }

    private static ScreenHousingMesh buildHalfConsole() {
        return new ScreenHousingMesh(new Face[]{
                quad(0,8,16,16,8,16,16,1,16,0,1,16,0,16,16,0),
                // The diagonal ends one model pixel before the back edge.
                quad(0,8,15,16,8,15,16,8,16,0,8,16,0,15,16,16),
                sideQuad(0,1,7.5,8,15,8,16,1,16),
                sideQuad(16,1,16,8,16,8,15,1,7.5)},
                new Face[0]);
    }

    private static ScreenHousingMesh buildDiagonal(boolean inverted) {
        if (inverted) return new ScreenHousingMesh(new Face[]{
                quad(0,16,0,16,16,0,16,16,16,0,16,16,0,16,16,0),
                quad(0,0,16,16,0,16,16,16,16,0,16,16,0,16,16,0),
                quad(0,15,0,16,15,0,16,16,0,0,16,0,0,15,16,16),
                // One continuous inset slope. The image shares this plane
                // and uses a rendering depth bias rather than a physical gap.
                quad(0,15,1,16,15,1,16,1,15,0,1,15,0,1,16,15),
                quad(0,15,0,16,15,0,16,15,1,0,15,1,0,15,16,16),
                // A vertical one-pixel end cap connects the diagonal to the
                // full-height rear block instead of extending the underside
                // diagonally to the far corner.
                quad(0,1,15,16,1,15,16,0,15,0,0,15,0,15,16,16),
                quad(0,0,15,16,0,15,16,0,16,0,0,16,0,15,16,16),
                sideQuad(0,15,0,15,1,16,1,16,0),
                sideQuad(0,15,1,1,15,16,15,16,1),
                sideQuad(0,0,15,0,16,16,16,16,15),
                sideQuad(16,15,0,16,0,16,1,15,1),
                sideQuad(16,15,1,16,1,16,15,1,15),
                sideQuad(16,0,15,16,15,16,16,0,16)},
                new Face[0]);
        return new ScreenHousingMesh(new Face[]{
                quad(0,0,0,16,0,0,16,0,16,0,0,16,0,16,16,0),
                quad(0,0,16,16,0,16,16,16,16,0,16,16,0,16,16,0),
                quad(0,0,0,16,0,0,16,1,0,0,1,0,0,15,16,16),
                // The visible diagonal terminates at the same one-pixel
                // corner inset as the side profile, rather than running into
                // the top edge while its side texture stops short.
                quad(0,1,1,16,1,1,16,15,15,0,15,15,0,15,16,1),
                quad(0,15,15,16,15,15,16,16,15,0,16,15,0,1,16,0),
                quad(0,1,0,16,1,0,16,1,1,0,1,1,0,15,16,16),
                quad(0,16,15,16,16,15,16,16,16,0,16,16,0,15,16,16),
                sideQuad(0,0,0,1,0,1,1,0,1),
                sideQuad(0,0,1,1,1,15,15,0,15),
                sideQuad(0,0,15,16,15,16,16,0,16),
                sideQuad(16,0,0,0,1,1,1,1,0),
                sideQuad(16,0,1,0,15,15,15,1,1),
                sideQuad(16,0,15,0,16,16,16,16,15)},
                new Face[0]);
    }

    private static Face sideQuad(double x,double y0,double z0,double y1,double z1,
            double y2,double z2,double y3,double z3) {
        return new Face(new Vertex(x,y0,z0,z0,16-y0),new Vertex(x,y1,z1,z1,16-y1),
                new Vertex(x,y2,z2,z2,16-y2),new Vertex(x,y3,z3,z3,16-y3));
    }

    private static Face quad(double x0,double y0,double z0,double x1,double y1,double z1,
            double x2,double y2,double z2,double x3,double y3,double z3,
            double u0,double v0,double u1,double v1) {
        return new Face(new Vertex(x0,y0,z0,u0,v0),new Vertex(x1,y1,z1,u1,v0),
                new Vertex(x2,y2,z2,u1,v1),new Vertex(x3,y3,z3,u0,v1));
    }

    private static Face triangle(double x0,double y0,double z0,double x1,double y1,double z1,
            double x2,double y2,double z2) {
        return new Face(new Vertex(x0,y0,z0,0,16),new Vertex(x1,y1,z1,16,0),
                new Vertex(x2,y2,z2,16,16));
    }
}
