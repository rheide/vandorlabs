package com.vandorlabs.gear.core;

import java.util.*;

/** A bounded platform plane. Air holes are legal; all carried cells and supports must connect. */
public final class JoinedPlatform {
    public static final int MAX_RANGE=32,MAX_SPAN=64;
    public static final class Cell {
        public final int u,v;
        public Cell(int u,int v){this.u=u;this.v=v;}
        @Override public boolean equals(Object o){return o instanceof Cell&&((Cell)o).u==u&&((Cell)o).v==v;}
        @Override public int hashCode(){return 31*u+v;}
    }
    public static Set<Cell> connected(Collection<Cell> supports,Collection<Cell> occupied){
        if(supports.size()<2||supports.size()>64)throw new IllegalArgumentException("Join between two and 64 arms");
        int minU=Integer.MAX_VALUE,minV=minU,maxU=Integer.MIN_VALUE,maxV=maxU;
        for(Cell c:supports){minU=Math.min(minU,c.u);minV=Math.min(minV,c.v);maxU=Math.max(maxU,c.u);maxV=Math.max(maxV,c.v);}
        if((long)maxU-minU>MAX_SPAN||(long)maxV-minV>MAX_SPAN)throw new IllegalArgumentException("Joined platform span exceeds 64 blocks");
        Set<Cell> anchors=new HashSet<>(supports);
        for(Cell corner:Arrays.asList(new Cell(minU,minV),new Cell(minU,maxV),new Cell(maxU,minV),new Cell(maxU,maxV)))
            if(!anchors.contains(corner))throw new IllegalArgumentException("Place an enabled arm at every platform corner");
        Set<Cell> cells=new HashSet<>();for(Cell c:occupied){if(c.u<minU||c.u>maxU||c.v<minV||c.v>maxV)throw new IllegalArgumentException("Platform cell outside the supported rectangle");cells.add(c);}
        if(!cells.containsAll(anchors))throw new IllegalArgumentException("Connect the platform to every arm tip");
        Set<Cell> seen=new HashSet<>();ArrayDeque<Cell> pending=new ArrayDeque<>();Cell first=supports.iterator().next();seen.add(first);pending.add(first);
        while(!pending.isEmpty()){Cell c=pending.removeFirst();for(Cell next:Arrays.asList(new Cell(c.u-1,c.v),new Cell(c.u+1,c.v),new Cell(c.u,c.v-1),new Cell(c.u,c.v+1)))if(cells.contains(next)&&seen.add(next))pending.addLast(next);}
        if(!seen.containsAll(anchors))throw new IllegalArgumentException("Connect all platform corners with face-adjacent blocks");
        if(seen.size()!=cells.size())throw new IllegalArgumentException("Connect loose platform blocks before moving");
        return Collections.unmodifiableSet(seen);
    }
    private JoinedPlatform(){}
}
