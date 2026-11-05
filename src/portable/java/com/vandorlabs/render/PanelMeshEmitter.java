package com.vandorlabs.render;

/** Current local wall polygons. Hosts retain sprite mapping, normals, clipping and buffer ownership. */
public final class PanelMeshEmitter {
    public interface Sink<C,M> {
        void quad(C context,M material,boolean reverse,double[] a,double[] b,double[] c,double[] d);
        void normal(C context,M material,int sign,double halfOffset,double[] clip,double[] a,double[] b,double[] c,double[] d);
    }
    public static <C,M> void diagonal(Sink<C,M> sink,C buf,
            M wall, M metal, boolean inverted,
            PanelCornerGeometry.Corner corner, double span, int fill, double fillLow, double fillHigh,
            double halfOffset, double lowerEnd, double upperEnd, boolean hideLower, boolean hideUpper, double[] clip) {
        double bottom = (inverted ? span : 0)-2, top = (inverted ? 0 : span)-2;
        // Opposite-handed arms exchange the outer bridge edge at mid-height.
        // Interpolating across that kink adds a phantom triangular face.
        boolean crossingArms=span==16 && Double.isNaN(halfOffset) && corner!=null && corner.frontRight!=null && corner.backRight!=null
                && !corner.frontRight.equals(corner.backRight);
        double[] heights=crossingArms?new double[]{0,2,4,8,12,14,16}:new double[]{0,2,4,12,14,16};
        for(int segment=0;segment<heights.length-1;segment++) {
            double y0=heights[segment],y1=heights[segment+1];
            double near0=diagonalSection(bottom,top,y0,lowerEnd,upperEnd);
            double near1=diagonalSection(bottom,top,y1,lowerEnd,upperEnd);

            double[][] lower = filledDiagonalOutline(near0, corner, fill, fillLow, fillHigh);
            double[][] upper = filledDiagonalOutline(near1, corner, fill, fillLow, fillHigh);
            for (int i = 0; i < lower.length; i++) {
                int next = (i + 1) % lower.length;
                double[] a = lower[i], b = lower[next];
                double[] c = upper[next], d = upper[i];
                if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) < 1.0E-7
                        && Math.abs(c[0] - d[0]) + Math.abs(c[1] - d[1]) < 1.0E-7)
                    continue;
                boolean endCap = Math.abs(a[0] - b[0]) < 1.0E-7
                        && Math.abs(c[0] - d[0]) < 1.0E-7
                        // A moving edge is the sloped outside of a bend, not an
                        // exposed panel end. Its narrow strip continues the wall.
                        && Math.abs(a[0] - d[0]) < 1.0E-7
                        && Math.abs(Math.abs(a[1] - b[1]) - 4) < 1.0E-7
                        && Math.abs(Math.abs(c[1] - d[1]) - 4) < 1.0E-7;
                M sprite = endCap && fill == 0 ? metal : wall;
                double uvOrigin = fill == 0 ? 0 : fillLow;
                boolean acrossX = Math.abs(a[0] - b[0]) + Math.abs(c[0] - d[0])
                        > Math.abs(a[1] - b[1]) + Math.abs(c[1] - d[1]);
                sink.normal(buf, sprite, -1, halfOffset, clip,
                        new double[]{a[0], y0, a[1], acrossX ? a[0] : a[1] - uvOrigin, 16-y0},
                        new double[]{b[0], y0, b[1], acrossX ? b[0] : b[1] - uvOrigin, 16-y0},
                        new double[]{c[0], y1, c[1], acrossX ? c[0] : c[1] - uvOrigin, 16-y1},
                        new double[]{d[0], y1, d[1], acrossX ? d[0] : d[1] - uvOrigin, 16-y1});
            }
        }
        for (int y : new int[] {0, 16}) {
            if (y==0?hideLower:hideUpper) continue;
            double near = diagonalSection(bottom,top,y,lowerEnd,upperEnd);
            roofRect(sink,buf, fill == 0 ? metal : wall, y, corner == null ? 0 : corner.left(near),
                    corner == null ? 16 : corner.right(near), (fill&1)!=0?Math.min(fillLow,near):near, (fill&2)!=0?Math.max(fillHigh,near+4):near + 4, fill == 0 ? 0 : fillLow, true, halfOffset, clip);
            if (corner != null) {
                if (corner.frontRight != null && (fill&1)==0) {
                    double armX = corner.armLeft(near, corner.frontRight,true);
                    roofRect(sink,buf, metal, y, armX, armX + 4, 0, near, 0, true, halfOffset, clip);
                }
                if (corner.backRight != null && (fill&2)==0) {
                    double armX = corner.armLeft(near, corner.backRight,false);
                    roofRect(sink,buf, metal, y, armX, armX + 4, near + 4, 16, 0, true, halfOffset, clip);
                }
            }
        }
    }

    private static double diagonalSection(double bottom,double top,double y,double lower,double upper) {
        double near=bottom+(top-bottom)*y/16;
        if(!Double.isNaN(lower) && y<4)near+=(lower-bottom)*(1-y/4);
        if(!Double.isNaN(upper) && y>12)near+=(upper-top)*(y/4-3);
        return near;
    }

    public static <C,M> void flat(Sink<C,M> sink,C buf,
            M wall, M metal, double near,
            PanelCornerGeometry.FlatCorner corner) {
        double[][] outline = corner==null?new double[][]{{0,near},{16,near},{16,near+4},{0,near+4}}:flatOutline(near, corner);
        for (int i = 0; i < outline.length; i++) {
            double[] a = outline[i], b = outline[(i + 1) % outline.length];
            if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) < 1.0E-7) continue;
            boolean endCap = Math.abs(a[0] - b[0]) < 1.0E-7
                    && Math.abs(Math.abs(a[1] - b[1]) - 4) < 1.0E-7;
            // Exposed ends of a connecting arm are also four-pixel metal caps.
            endCap |= Math.abs(a[1] - b[1]) < 1.0E-7
                    && Math.abs(Math.abs(a[0] - b[0]) - 4) < 1.0E-7
                    && corner!=null && ((corner.frontArm != null && near > 0 && a[1] == 0)
                    || (corner.backArm != null && near + 4 < 16 && a[1] == 16));
            M sprite = endCap ? metal : wall;
            boolean acrossX = Math.abs(a[0] - b[0]) > Math.abs(a[1] - b[1]);
            double u0 = acrossX ? a[0] : a[1];
            double u1 = acrossX ? b[0] : b[1];
            sink.quad(buf,sprite,true,
                    new double[]{a[0],0,a[1],u0,16},new double[]{b[0],0,b[1],u1,16},
                    new double[]{b[0],16,b[1],u1,0},new double[]{a[0],16,a[1],u0,0});
        }
        for (int y : new int[] {0, 16}) {
            roofRect(sink,buf, metal, y, corner==null?0:corner.left(), corner==null?16:corner.right(), near, near + 4);
            if (corner!=null && corner.frontArm != null)
                roofRect(sink,buf, metal, y, corner.frontArm,
                        corner.frontArm + 4, 0, near);
            if (corner!=null && corner.backArm != null)
                roofRect(sink,buf, metal, y, corner.backArm,
                        corner.backArm + 4, near + 4, 16);
        }
    }

    private static double[][] flatOutline(double near,
            PanelCornerGeometry.FlatCorner corner) {
        java.util.List<double[]> points = new java.util.ArrayList<>();
        points.add(new double[] {corner.left(), near});
        if (corner.frontArm != null) {
            points.add(new double[] {corner.frontArm, near});
            points.add(new double[] {corner.frontArm, 0});
            points.add(new double[] {corner.frontArm + 4, 0});
            points.add(new double[] {corner.frontArm + 4, near});
        }
        points.add(new double[] {corner.right(), near});
        points.add(new double[] {corner.right(), near + 4});
        if (corner.backArm != null) {
            points.add(new double[] {corner.backArm + 4, near + 4});
            points.add(new double[] {corner.backArm + 4, 16});
            points.add(new double[] {corner.backArm, 16});
            points.add(new double[] {corner.backArm, near + 4});
        }
        points.add(new double[] {corner.left(), near + 4});
        return points.toArray(new double[points.size()][]);
    }

    private static double[][] filledDiagonalOutline(double near, PanelCornerGeometry.Corner corner,
            int fill, double low, double high) {
        double front=(fill&1)!=0?Math.min(low,near):near,back=(fill&2)!=0?Math.max(high,near+4):near+4;
        double left=corner==null?0:corner.left(near),right=corner==null?16:corner.right(near);
        double frontArm=corner==null||corner.frontRight==null||(fill&1)!=0?Double.NaN:corner.armLeft(near,corner.frontRight,true);
        double backArm=corner==null||corner.backRight==null||(fill&2)!=0?Double.NaN:corner.armLeft(near,corner.backRight,false);
        return com.vandorlabs.render.DiagonalCornerOutline.polygon(left,right,front,back,frontArm,backArm);
    }

    private static double[][] diagonalOutline(double near,PanelCornerGeometry.Corner corner) {
        return filledDiagonalOutline(near,corner,0,0,16);
    }

    private static <C,M> void roofRect(Sink<C,M> sink,C buf, M metal,
            double y, double x0, double x1, double z0, double z1) {
        roofRect(sink,buf, metal, y, x0, x1, z0, z1, 0);
    }

    private static <C,M> void roofRect(Sink<C,M> sink,C buf, M sprite,
            double y, double x0, double x1, double z0, double z1, double uvOrigin) {
        roofRect(sink,buf,sprite,y,x0,x1,z0,z1,uvOrigin,false,Double.NaN);
    }

    private static <C,M> void roofRect(Sink<C,M> sink,C buf, M sprite,
            double y, double x0, double x1, double z0, double z1, double uvOrigin,
            boolean normal, double halfOffset) {
        roofRect(sink,buf,sprite,y,x0,x1,z0,z1,uvOrigin,normal,halfOffset,null);
    }

    private static <C,M> void roofRect(Sink<C,M> sink,C buf, M sprite,
            double y, double x0, double x1, double z0, double z1, double uvOrigin,
            boolean normal, double halfOffset, double[] clip) {
        if (z1 - z0 < 1.0E-7) return;
        if (normal) {
            sink.normal(buf,sprite,y == 16 ? -1 : 1, halfOffset, clip,
                    new double[]{x0,y,z0,x0,z0-uvOrigin},
                    new double[]{x1,y,z0,x1,z0-uvOrigin},
                    new double[]{x1,y,z1,x1,z1-uvOrigin},
                    new double[]{x0,y,z1,x0,z1-uvOrigin});
            return;
        }
        sink.quad(buf,sprite,y==16,
                new double[]{x0,y,z0,x0,z0-uvOrigin},new double[]{x1,y,z0,x1,z0-uvOrigin},
                new double[]{x1,y,z1,x1,z1-uvOrigin},new double[]{x0,y,z1,x0,z1-uvOrigin});
    }

    private PanelMeshEmitter(){}
}
