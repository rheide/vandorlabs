package com.vandorlabs.render;

/** Eight-neighbor frame decisions for a connected glass panel. */
public final class GlassConnections {
    public interface Neighbors { boolean connected(int horizontal,int vertical); }
    public final boolean top,bottom,left,right,innerTopLeft,innerTopRight,innerBottomLeft,innerBottomRight;

    private GlassConnections(boolean top,boolean bottom,boolean left,boolean right,
            boolean innerTopLeft,boolean innerTopRight,boolean innerBottomLeft,boolean innerBottomRight) {
        this.top=top; this.bottom=bottom; this.left=left; this.right=right;
        this.innerTopLeft=innerTopLeft; this.innerTopRight=innerTopRight;
        this.innerBottomLeft=innerBottomLeft; this.innerBottomRight=innerBottomRight;
    }

    public static GlassConnections calculate(Neighbors n) {
        boolean up=n.connected(0,1),down=n.connected(0,-1);
        boolean left=n.connected(-1,0),right=n.connected(1,0);
        return new GlassConnections(!up,!down,!left,!right,
                up&&left&&!n.connected(-1,1),up&&right&&!n.connected(1,1),
                down&&left&&!n.connected(-1,-1),down&&right&&!n.connected(1,-1));
    }
}
