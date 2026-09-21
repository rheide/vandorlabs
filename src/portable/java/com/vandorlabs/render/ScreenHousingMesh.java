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
                quad(0,1,7.5,16,1,7.5,16,16,16,0,16,16,0,16,16,0),
                quad(0,16,16,16,16,16,16,1,16,0,1,16,0,16,16,0)},
                new Face[]{triangle(0,1,7.5,0,16,16,0,1,16),
                        triangle(16,1,16,16,16,16,16,1,7.5)});
    }

    private static ScreenHousingMesh buildHalfConsole() {
        return new ScreenHousingMesh(new Face[]{
                quad(0,8,16,16,8,16,16,1,16,0,1,16,0,16,16,0)},
                new Face[]{triangle(0,1,7.5,0,8,16,0,1,16),
                        triangle(16,1,16,16,8,16,16,1,7.5)});
    }

    private static ScreenHousingMesh buildDiagonal(boolean inverted) {
        if (inverted) return new ScreenHousingMesh(new Face[]{
                quad(0,16,0,16,16,0,16,0,16,0,0,16,0,16,16,0),
                quad(0,0,16,16,0,16,16,16,16,0,16,16,0,16,16,0),
                quad(0,16,0,16,16,0,16,16,16,0,16,16,0,0,16,16)},
                new Face[]{triangle(0,16,0,0,0,16,0,16,16),
                        triangle(16,16,16,16,0,16,16,16,0)});
        return new ScreenHousingMesh(new Face[]{
                quad(0,0,0,16,0,0,16,16,16,0,16,16,0,16,16,0),
                quad(0,16,16,16,16,16,16,0,16,0,0,16,0,16,16,0),
                quad(0,0,16,16,0,16,16,0,0,0,0,0,0,0,16,16)},
                new Face[]{triangle(0,0,0,0,16,16,0,0,16),
                        triangle(16,0,16,16,16,16,16,0,0)});
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
