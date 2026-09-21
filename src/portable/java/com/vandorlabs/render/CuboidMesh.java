package com.vandorlabs.render;

import com.vandorlabs.ramp.RampGeometry;

/** Renderer-neutral faces and source-texture coordinates for a ramp slice. */
public final class CuboidMesh {
    public enum Face { DOWN, UP, NORTH, SOUTH, WEST, EAST }
    public interface VertexConsumer { void vertex(double x,double y,double z,double u,double v); }
    private static final int[][] CORNERS={{4,5,1,0},{2,3,7,6},{0,1,3,2},
            {5,4,6,7},{4,0,2,6},{1,5,7,3}};
    public static final class Vertex {
        public final double x,y,z,u,v;
        private Vertex(double x,double y,double z,double u,double v) {
            this.x=x; this.y=y; this.z=z; this.u=u; this.v=v;
        }
    }
    private CuboidMesh() { }

    public static Vertex[] face(RampGeometry.Box a,Face face,double vBottom,double vTop) {
        Vertex[] result=new Vertex[4];
        int[] index={0};
        emitFace(a,face,vBottom,vTop,(x,y,z,u,v)->result[index[0]++]=new Vertex(x,y,z,u,v));
        return result;
    }

    public static void emitFace(RampGeometry.Box a,Face face,double vBottom,double vTop,
            VertexConsumer consumer) {
        for (int corner:CORNERS[face.ordinal()]) {
            double x=(corner&1)==0?a.minX:a.maxX;
            double y=(corner&2)==0?a.minY:a.maxY;
            double z=(corner&4)==0?a.minZ:a.maxZ;
            double u=face==Face.WEST||face==Face.EAST?z:x;
            double v=face==Face.UP||face==Face.DOWN?z:(y==a.minY?vBottom:vTop);
            consumer.vertex(x,y,z,u,v);
        }
    }
}
