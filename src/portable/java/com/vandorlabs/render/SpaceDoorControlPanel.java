package com.vandorlabs.render;

/** Fixed jamb control pad, measured in model pixels. */
public final class SpaceDoorControlPanel {
    public enum Side { LEFT, RIGHT, NONE }
    public static final double Y0=16.5, Y1=19.5;
    /** Keep the pad's center at regular-door height when the door is enlarged. */
    public static double verticalOffset(double scale) { return (1-scale)*(Y0+Y1)/32; }
    /** Local coordinates in blocks, matching the scaled renderer and its vertical offset. */
    public static boolean containsScaled(double localZ,double localY,boolean sliding,boolean farEdge,double scale) {
        return contains(localZ*16,(localY-verticalOffset(scale))*16/scale,sliding,farEdge);
    }
    /** Exact configured hit regions, including security-reader height and mirrored rear pad. */
    public static boolean containsConfigured(boolean sliding,boolean farEdge,int panelMode,int securityMode,double offset,double localZ,double localY,double size){
        double y=(localY-verticalOffset(size))*16/size;
        double lo=securityMode>0?15.8:Y0,hi=securityMode>0?20.2:Y1;
        if(y<lo||y>hi)return false;
        double start=z0(sliding,farEdge),end=start+3;
        if(securityMode>0){double extra=1.5*(size-1);start-=extra;end+=extra;}
        double z=(localZ-offset)*16;
        if(z>=start&&z<=end)return true;
        z=(1-localZ-offset)*16;return panelMode==2&&z>=start&&z<=end;
    }
    public static <B> B bounds(SpaceDoorBounds.Factory<B> boxes,int facing,Side side,double offset,boolean sliding,boolean farEdge){
        double x0=x0(side)/16,x1=x1(side)/16;
        double z0=z0(sliding,farEdge)/16,
                z1=z1(sliding,farEdge)/16;
        double y0=Y0/16,y1=Y1/16;
        switch (facing) {
            case 2: return boxes.create(1-x1,y0,1-z1-offset,1-x0,y1,1-z0-offset);
            case 5: return boxes.create(z0+offset,y0,1-x1,z1+offset,y1,1-x0);
            case 4: return boxes.create(1-z1-offset,y0,x0,1-z0-offset,y1,x1);
            default: return boxes.create(x0,y0,z0+offset,x1,y1,z1+offset);
        }
    }
    public static <B> B mirror(SpaceDoorBounds.Factory<B> boxes,double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int facing){
        return facing==4||facing==5?boxes.create(1-maxX,minY,minZ,1-minX,maxY,maxZ):boxes.create(minX,minY,1-maxZ,maxX,maxY,1-minZ);
    }
    private SpaceDoorControlPanel() { }
    public static Side side(boolean hingeOnVisualLeft,boolean sliding,boolean paired,
            boolean neighborLeft,boolean neighborRight) {
        if (neighborLeft && neighborRight) return Side.NONE;
        if (sliding) {
            if (paired) return hingeOnVisualLeft?Side.NONE:Side.RIGHT;
            return hingeOnVisualLeft?Side.RIGHT:Side.LEFT;
        }
        if (paired && !hingeOnVisualLeft) return Side.NONE;
        return hingeOnVisualLeft?Side.LEFT:Side.RIGHT;
    }
    public static double x0(Side side) { return side==Side.RIGHT?15:0; }
    public static double x1(Side side) { return side==Side.RIGHT?16:1; }
    // Both models face local +Z. Mount the pad against the rear of the jamb,
    // clear of the moving leaf and within the placed block after offsets.
    public static double z0(boolean sliding) { return sliding?3:8.24; }
    public static double z1(boolean sliding) { return z0(sliding)+3; }
    /** Only far-edge doors mount the pad on the opposite depth face of the jamb. */
    public static double z0(boolean sliding, boolean farEdge) {
        return farEdge ? (sliding ? 10 : 15.24) : z0(sliding);
    }
    public static double z1(boolean sliding, boolean farEdge) { return z0(sliding,farEdge)+3; }
    public static boolean contains(double localZ,double localY,boolean sliding) {
        return contains(localZ,localY,sliding,false);
    }
    public static boolean contains(double localZ,double localY,boolean sliding,boolean farEdge) {
        return localZ>=z0(sliding,farEdge) && localZ<=z1(sliding,farEdge)
                && localY>=Y0 && localY<=Y1;
    }
}
