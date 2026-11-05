package com.vandorlabs.render;
/** Current aperture face/rim polygons, independent of atlas and target surface transform. */
public final class PanelApertureEmitter {
    public interface Sink<C,M,T>{void quad(C context,M material,T transform,boolean reverse,double[] a,double[] b,double[] c,double[] d);}
    private PanelApertureEmitter(){}
    public static <C,M,T> void panelQuad(Sink<C,M,T> sink,C buf,M sprite, T mesh,
            double x0, double y0, double x1, double y1, double x2, double y2,
            double x3, double y3) {
        for (int z : new int[] {6, 10}) {
            sink.quad(buf, sprite, mesh, z==6,
                    new double[]{x0, y0, z, x0, 16 - y0},
                    new double[]{x1, y1, z, x1, 16 - y1},
                    new double[]{x2, y2, z, x2, 16 - y2},
                    new double[]{x3, y3, z, x3, 16 - y3});
        }
    }
    public static <C,M,T> void topRim(Sink<C,M,T> sink,C buf,M metal, T mesh,
            double y, double x0, double x1, double depthUv) {
        if (x1 - x0 < 1.0E-7) return;
        sink.quad(buf, metal, mesh, y==16,
                new double[]{x0, y, 6, x0, 0},
                new double[]{x1, y, 6, x1, 0},
                new double[]{x1, y, 10, x1, depthUv},
                new double[]{x0, y, 10, x0, depthUv});
    }
    public static <C,M,T> void sideRim(Sink<C,M,T> sink,C buf,M metal, T mesh,
            double x, double y0, double y1, double depthUv) {
        if (y1 - y0 < 1.0E-7) return;
        sink.quad(buf, metal, mesh, x==16,
                new double[]{x, y0, 6, 0, 16 - y0},
                new double[]{x, y0, 10, depthUv, 16 - y0},
                new double[]{x, y1, 10, depthUv, 16 - y1},
                new double[]{x, y1, 6, 0, 16 - y1});
    }
    public static <C,M,T> void rimSegment(Sink<C,M,T> sink,C buf,M metal, T mesh,
            double x0, double y0, double x1, double y1, double depthUv) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        // Atlas coordinates must stay inside this sprite, including diagonal
        // edges longer than 16 pixels. Tile the metal along those edges.
        for (double start = 0; start < length - 1.0E-7; start += 16) {
            double end = Math.min(length, start + 16);
            double ax = x0 + (x1 - x0) * start / length;
            double ay = y0 + (y1 - y0) * start / length;
            double bx = x0 + (x1 - x0) * end / length;
            double by = y0 + (y1 - y0) * end / length;
            sink.quad(buf, metal, mesh, true,
                    new double[]{ax, ay, 6, 0, 0},
                    new double[]{bx, by, 6, end - start, 0},
                    new double[]{bx, by, 10, end - start, depthUv},
                    new double[]{ax, ay, 10, 0, depthUv});
        }
    }
}
