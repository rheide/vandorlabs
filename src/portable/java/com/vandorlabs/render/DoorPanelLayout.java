package com.vandorlabs.render;

/** Geometry and UV decisions for legacy textured door panels. */
public final class DoorPanelLayout {
    public enum Kind { HINGED, HINGE_SPLIT, SLIDING_SPLIT, SLIDING_SINGLE }
    public static final class Panel {
        public final double x,width,u0,u1,z0,z1,angle,pivotX,pivotZ;
        public final boolean swung,usesWindow;
        private Panel(double x,double width,double u0,double u1,double z0,double z1,
                double angle,double pivotX,double pivotZ,boolean swung,boolean usesWindow) {
            this.x=x; this.width=width; this.u0=u0; this.u1=u1; this.z0=z0; this.z1=z1;
            this.angle=angle; this.pivotX=pivotX; this.pivotZ=pivotZ;
            this.swung=swung; this.usesWindow=usesWindow;
        }
    }
    private DoorPanelLayout() { }

    public static Panel[] calculate(Kind kind,boolean hingeLeft,boolean reversePaint,double progress) {
        double p=Math.max(0,Math.min(1,progress));
        if (kind==Kind.HINGE_SPLIT) return new Panel[]{
                panel(0,8,8,0,7,9,-90*p,1,8,true,false),
                panel(8,8,16,8,7,9,90*p,15,8,true,false)};
        if (kind==Kind.SLIDING_SPLIT) return new Panel[]{
                panel(-7*p,8,8,16,7,9,0,0,0,false,false),
                panel(8+7*p,8,0,8,7,9,0,0,0,false,false)};
        double u0=hingeLeft?0:16,u1=hingeLeft?16:0;
        if (reversePaint) { double swap=u0; u0=u1; u1=swap; }
        if (kind==Kind.SLIDING_SINGLE)
            return new Panel[]{panel((hingeLeft?-15:15)*p,16,u0,u1,7,9,0,0,0,false,true)};
        return new Panel[]{panel(0,16,u0,u1,0,2,(hingeLeft?-90:90)*p,
                hingeLeft?1:15,1,true,true)};
    }

    private static Panel panel(double x,double width,double u0,double u1,double z0,double z1,
            double angle,double pivotX,double pivotZ,boolean swung,boolean usesWindow) {
        return new Panel(x,width,u0,u1,z0,z1,angle,pivotX,pivotZ,swung,usesWindow);
    }
}
