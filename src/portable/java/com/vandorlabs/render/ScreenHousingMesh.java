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
        private Face(Vertex... vertices) { this.vertices=vertices; }
    }
    public final Face[] quads,triangles;

    private ScreenHousingMesh(Face[] quads,Face[] triangles) {
        this.quads=quads; this.triangles=triangles;
    }

    private static final ScreenHousingMesh CONSOLE=buildConsole();
    private static final ScreenHousingMesh HALF_CONSOLE=buildHalfConsole();
    private static final ScreenHousingMesh DIAGONAL=buildDiagonal(false);
    private static final ScreenHousingMesh DIAGONAL_INVERTED=buildDiagonal(true);

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
