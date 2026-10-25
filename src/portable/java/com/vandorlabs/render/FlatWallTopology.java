package com.vandorlabs.render;

/** Four-neighbour flat panel joins, independent of world and renderer access. */
public final class FlatWallTopology {
    public static final class Panel {
        public final boolean flat,cornerPanel,enabled,cornerEnabled;
        public final int facing,effectiveFacing;
        public final double near,effectiveNear;
        public Panel(boolean flat,boolean cornerPanel,boolean enabled,boolean cornerEnabled,
                int facing,int effectiveFacing,double near,double effectiveNear){
            this.flat=flat;this.cornerPanel=cornerPanel;this.enabled=enabled;this.cornerEnabled=cornerEnabled;
            this.facing=facing;this.effectiveFacing=effectiveFacing;this.near=near;this.effectiveNear=effectiveNear;
        }
    }
    public interface Neighbors {Panel at(int facing);}
    public static final class Corner {
        public final Double frontArm,backArm;
        public final Boolean frontRight,backRight;
        public final boolean straightLeft,straightRight;
        public Corner(Double frontArm,Double backArm,Boolean frontRight,Boolean backRight,boolean straightLeft,boolean straightRight){
            this.frontArm=frontArm;this.backArm=backArm;this.frontRight=frontRight;this.backRight=backRight;
            this.straightLeft=straightLeft;this.straightRight=straightRight;
        }
        public double left(){
            if(!straightLeft&&!straightRight&&frontArm!=null&&backArm!=null)return Math.min(frontArm,backArm);
            if(straightLeft||!straightRight&&(Boolean.FALSE.equals(frontRight)||Boolean.FALSE.equals(backRight)))return 0;
            return Math.min(frontArm==null?16:frontArm,backArm==null?16:backArm);
        }
        public double right(){
            if(!straightLeft&&!straightRight&&frontArm!=null&&backArm!=null)return Math.max(frontArm,backArm)+4;
            if(straightRight||!straightLeft&&(Boolean.TRUE.equals(frontRight)||Boolean.TRUE.equals(backRight)))return 16;
            return Math.max(frontArm==null?0:frontArm+4,backArm==null?0:backArm+4);
        }
    }
    private FlatWallTopology(){}
    public static int clockwise(int facing){return StairJoinPolicy.opposite(StairJoinPolicy.counterClockwise(facing));}
    private static Boolean armRight(int facing,Panel other){
        if(other==null||!other.cornerPanel||!other.cornerEnabled)return null;
        return other.effectiveFacing==clockwise(facing)?Boolean.TRUE:
                other.effectiveFacing==StairJoinPolicy.counterClockwise(facing)?Boolean.FALSE:null;
    }
    /** Lateral neighbours have no displacement along the panel's normal. */
    public static boolean straight(Panel owner,Panel other){
        if(other==null||!owner.enabled||!other.enabled||!other.flat)return false;
        if((owner.facing>=4)!=(other.facing>=4))return false;
        double near=other.facing==owner.facing?other.near:12-other.near;
        return near==owner.near;
    }
    public static Corner corner(Panel owner,Neighbors neighbors){
        if(!owner.cornerPanel||!owner.cornerEnabled)return null;
        int facing=owner.effectiveFacing;
        Panel front=neighbors.at(facing),back=neighbors.at(StairJoinPolicy.opposite(facing));
        Boolean frontRight=armRight(facing,front),backRight=armRight(facing,back);
        if(frontRight==null&&backRight==null)return null;
        Double frontArm=frontRight==null?null:frontRight?12-front.effectiveNear:front.effectiveNear;
        Double backArm=backRight==null?null:backRight?12-back.effectiveNear:back.effectiveNear;
        return new Corner(frontArm,backArm,frontRight,backRight,
                straight(owner,neighbors.at(StairJoinPolicy.counterClockwise(facing))),
                straight(owner,neighbors.at(clockwise(facing))));
    }
    /** Ordered X/Z boundary in authored pixels; duplicate vertices retain legacy UV seams. */
    public static double[][] outline(double near,Corner corner){
        if(corner==null)return new double[][]{{0,near},{16,near},{16,near+4},{0,near+4}};
        java.util.List<double[]> points=new java.util.ArrayList<double[]>();
        points.add(new double[]{corner.left(),near});
        if(corner.frontArm!=null){points.add(new double[]{corner.frontArm,near});points.add(new double[]{corner.frontArm,0});points.add(new double[]{corner.frontArm+4,0});points.add(new double[]{corner.frontArm+4,near});}
        points.add(new double[]{corner.right(),near});points.add(new double[]{corner.right(),near+4});
        if(corner.backArm!=null){points.add(new double[]{corner.backArm+4,near+4});points.add(new double[]{corner.backArm+4,16});points.add(new double[]{corner.backArm,16});points.add(new double[]{corner.backArm,near+4});}
        points.add(new double[]{corner.left(),near+4});return points.toArray(new double[points.size()][]);
    }
    /** Unrotated collision rectangles [x0,z0,x1,z1] in authored pixels. */
    public static double[][] boxes(double near,Corner corner){
        java.util.List<double[]> result=new java.util.ArrayList<double[]>();
        result.add(new double[]{corner==null?0:corner.left(),near,corner==null?16:corner.right(),near+4});
        if(corner!=null&&corner.frontArm!=null)result.add(new double[]{corner.frontArm,0,corner.frontArm+4,near});
        if(corner!=null&&corner.backArm!=null)result.add(new double[]{corner.backArm,near+4,corner.backArm+4,16});
        return result.toArray(new double[result.size()][]);
    }
}
