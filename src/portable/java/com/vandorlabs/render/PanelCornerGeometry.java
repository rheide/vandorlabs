package com.vandorlabs.render;

/** Local numeric corner widths; neighbour discovery remains platform-owned. */
public final class PanelCornerGeometry {
    private PanelCornerGeometry() { }
    public static class Corner {
        public final Boolean frontRight;
        public final Boolean backRight;
        public final boolean straightLeft;
        public final boolean straightRight;
        public Double frontFlat,backFlat;
        public Corner(Boolean frontRight, Boolean backRight,
                boolean straightLeft, boolean straightRight) {
            this.frontRight = frontRight;
            this.backRight = backRight;
            this.straightLeft = straightLeft;
            this.straightRight = straightRight;
        }
        public double left(double near) {
            double front=frontRight==null?Double.NaN:armLeft(near,frontRight,true);
            double back=backRight==null?Double.NaN:armLeft(near,backRight,false);
            double edge=16;
            if(frontRight!=null)edge=Math.min(edge,front);
            if(backRight!=null)edge=Math.min(edge,back);
            if(straightLeft || !straightRight && (frontRight==null || backRight==null) && (Boolean.FALSE.equals(frontRight)||Boolean.FALSE.equals(backRight)))edge=0;
            return com.vandorlabs.render.DiagonalCornerOutline.left(edge,near,front,back);
        }
        public double right(double near) {
            double front=frontRight==null?Double.NaN:armLeft(near,frontRight,true);
            double back=backRight==null?Double.NaN:armLeft(near,backRight,false);
            double edge=0;
            if(frontRight!=null)edge=Math.max(edge,front+4);
            if(backRight!=null)edge=Math.max(edge,back+4);
            if(straightRight || !straightLeft && (frontRight==null || backRight==null) && (Boolean.TRUE.equals(frontRight)||Boolean.TRUE.equals(backRight)))edge=16;
            return com.vandorlabs.render.DiagonalCornerOutline.right(edge,near,front,back);
        }
        public double armLeft(double near,boolean right,boolean front) {
            Double fixed=front?frontFlat:backFlat;
            return fixed==null?armLeft(near,right):fixed;
        }
        public double armLeft(double near, boolean right) {
            return right ? 12 - near : near;
        }
    }

    /** A level panel bends into a perpendicular neighbor within this block. */
    public static class FlatCorner {
        public final Double frontArm;
        public final Double backArm;
        public final Boolean frontRight;
        public final Boolean backRight;
        public final boolean straightLeft;
        public final boolean straightRight;

        public FlatCorner(Double frontArm, Double backArm,
                Boolean frontRight, Boolean backRight,
                boolean straightLeft, boolean straightRight) {
            this.frontArm = frontArm;
            this.backArm = backArm;
            this.frontRight = frontRight;
            this.backRight = backRight;
            this.straightLeft = straightLeft;
            this.straightRight = straightRight;
        }
        public double left() {
            // Two perpendicular arms already define both ends of this bridge.
            // A free tail here turns a three-way meeting into a false cross.
            if (!straightLeft && !straightRight
                    && frontArm != null && backArm != null)
                return Math.min(frontArm, backArm);
            if (straightLeft || !straightRight && (Boolean.FALSE.equals(frontRight)
                    || Boolean.FALSE.equals(backRight))) return 0;
            return Math.min(frontArm == null ? 16 : frontArm,
                    backArm == null ? 16 : backArm);
        }
        public double right() {
            if (!straightLeft && !straightRight
                    && frontArm != null && backArm != null)
                return Math.max(frontArm, backArm) + 4;
            if (straightRight || !straightLeft && (Boolean.TRUE.equals(frontRight)
                    || Boolean.TRUE.equals(backRight))) return 16;
            return Math.max(frontArm == null ? 0 : frontArm + 4,
                    backArm == null ? 0 : backArm + 4);
        }
    }

}
