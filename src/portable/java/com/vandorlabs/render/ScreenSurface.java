package com.vandorlabs.render;

/** Positions for the programmable screen face; texture and GPU details belong to adapters. */
public final class ScreenSurface {
    public enum Kind { FLAT, CONSOLE, DIAGONAL }
    public static final class Vertex {
        public final double x,y,z;
        private Vertex(double x,double y,double z) { this.x=x; this.y=y; this.z=z; }
    }
    public static final class Quad {
        public final Vertex topLeft,topRight,bottomRight,bottomLeft;
        private Quad(double x0,double x1,double bottomY,double bottomZ,double topY,double topZ) {
            topLeft=new Vertex(x0,topY,topZ); topRight=new Vertex(x1,topY,topZ);
            bottomRight=new Vertex(x1,bottomY,bottomZ); bottomLeft=new Vertex(x0,bottomY,bottomZ);
        }
    }
    private static final Quad FLAT=new Quad(0,16,0,-.1,16,-.1);
    private static final Quad CONSOLE=new Quad(.5,15.5,1.46,7.70,15.59,14.75);
    // Keep both images exactly on their housing planes. Moving them toward
    // the camera opened a visible slit along the side of the ceiling wedge.
    // The renderer gives the image a depth bias instead of a geometric gap.
    private static final Quad DIAGONAL=new Quad(.5,15.5,4.25,4.25,14.85,14.85);
    private static final Quad DIAGONAL_INVERTED=new Quad(.5,15.5,1.15,14.85,11.75,4.25);
    private ScreenSurface() { }
    public static Quad quad(Kind kind,boolean inverted) {
        if (kind==Kind.CONSOLE) return CONSOLE;
        if (kind==Kind.DIAGONAL) return inverted?DIAGONAL_INVERTED:DIAGONAL;
        return FLAT;
    }
}
