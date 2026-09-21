package com.vandorlabs.render;

/** Finds an isolated square around local coordinate 0,0 without world/API types. */
public final class ConnectedSquare {
    private ConnectedSquare() { }

    public interface Matcher { boolean matches(int x,int y); }

    public static final class Part {
        public static final Part SINGLE=new Part(1,0,0);
        public final int size,x,y;
        public Part(int size,int x,int y) { this.size=size; this.x=x; this.y=y; }
        public boolean isSingle() { return size==1; }
    }

    public static Part find(int maxSize,Matcher matcher) {
        int negativeX=distance(maxSize,matcher,-1,0);
        int positiveX=distance(maxSize,matcher,1,0);
        int negativeY=distance(maxSize,matcher,0,-1);
        int positiveY=distance(maxSize,matcher,0,1);
        if (negativeX<0||positiveX<0||negativeY<0||positiveY<0) return Part.SINGLE;
        int width=negativeX+1+positiveX;
        int height=negativeY+1+positiveY;
        if (width!=height||width<2||width>maxSize) return Part.SINGLE;
        for (int x=-negativeX-1;x<=positiveX+1;x++) {
            for (int y=-negativeY-1;y<=positiveY+1;y++) {
                boolean inside=x>=-negativeX&&x<=positiveX&&y>=-negativeY&&y<=positiveY;
                if (matcher.matches(x,y)!=inside) return Part.SINGLE;
            }
        }
        return new Part(width,negativeX,negativeY);
    }

    /** -1 means matching cells continue past the supported extent. */
    private static int distance(int maxSize,Matcher matcher,int dx,int dy) {
        int distance=0;
        for (int step=1;step<=maxSize;step++) {
            if (!matcher.matches(dx*step,dy*step)) break;
            if (step==maxSize) return -1;
            distance=step;
        }
        return distance;
    }
}
