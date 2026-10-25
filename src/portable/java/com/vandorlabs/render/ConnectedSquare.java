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

    /** Partition a complete bounded component into non-overlapping squares, or join none of it.
     * The lowest row/column and largest-first exact cover are invariant under the queried member. */
    public static Part findPartitioned(int maxSize,Matcher matcher){
        java.util.Map<Long,Boolean> samples=new java.util.HashMap<>();Matcher cached=(x,y)->samples.computeIfAbsent(key(x,y),ignored->matcher.matches(x,y));
        Part isolated=find(maxSize,cached);if(!isolated.isSingle())return isolated;if(!cached.matches(0,0))return Part.SINGLE;
        java.util.Comparator<Long> order=(a,b)->{int y=Integer.compare((int)(long)a,(int)(long)b);return y!=0?y:Integer.compare((int)(a>>32),(int)(b>>32));};
        java.util.TreeSet<Long> cells=new java.util.TreeSet<>(order);java.util.ArrayDeque<Long> queue=new java.util.ArrayDeque<>();cells.add(key(0,0));queue.add(key(0,0));int minX=0,maxX=0,minY=0,maxY=0,span=Math.max(16,maxSize*2);
        while(!queue.isEmpty()){long current=queue.removeFirst();int x=(int)(current>>32),y=(int)current;for(int direction=0;direction<4;direction++){int nx=x+(direction==0?1:direction==1?-1:0),ny=y+(direction==2?1:direction==3?-1:0);long cell=key(nx,ny);if(cells.contains(cell)||!cached.matches(nx,ny))continue;cells.add(cell);queue.add(cell);minX=Math.min(minX,nx);maxX=Math.max(maxX,nx);minY=Math.min(minY,ny);maxY=Math.max(maxY,ny);if(cells.size()>Math.max(256,4*maxSize*maxSize)||maxX-minX>=span||maxY-minY>=span)return Part.SINGLE;}}
        java.util.List<int[]> squares=new java.util.ArrayList<>();if(!cover(cells,maxSize,squares,new int[]{0}))return Part.SINGLE;
        for(int[] square:squares)if(square[0]<=0&&square[1]<=0&&square[0]+square[2]>0&&square[1]+square[2]>0)return new Part(square[2],-square[0],-square[1]);return Part.SINGLE;
    }
    private static long key(int x,int y){return (long)x<<32|(y&0xffffffffL);}
    private static boolean cover(java.util.TreeSet<Long> cells,int maxSize,java.util.List<int[]> squares,int[] work){
        if(cells.isEmpty())return true;if(++work[0]>4096)return false;long first=cells.first();int x=(int)(first>>32),y=(int)first;
        for(int size=maxSize;size>=2;size--){boolean full=true;for(int dx=0;dx<size&&full;dx++)for(int dy=0;dy<size;dy++)if(!cells.contains(key(x+dx,y+dy))){full=false;break;}if(!full)continue;
            for(int dx=0;dx<size;dx++)for(int dy=0;dy<size;dy++)cells.remove(key(x+dx,y+dy));squares.add(new int[]{x,y,size});if(cover(cells,maxSize,squares,work))return true;squares.remove(squares.size()-1);for(int dx=0;dx<size;dx++)for(int dy=0;dy<size;dy++)cells.add(key(x+dx,y+dy));
        }return false;
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
