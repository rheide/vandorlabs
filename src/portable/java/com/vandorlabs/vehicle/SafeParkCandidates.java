package com.vandorlabs.vehicle;

import java.util.*;

/** Bounded nearest-grid search; callers supply loaded-only terrain, permission and fit checks. */
public final class SafeParkCandidates {
    public interface Columns {boolean loaded(int originX,int originZ);}
    public static final class Candidate {
        public final int x,y,z;public final long distanceSquared;
        private Candidate(int x,int y,int z,long distanceSquared){this.x=x;this.y=y;this.z=z;this.distanceSquared=distanceSquared;}
    }
    private static final class Column {
        final int dx,dz;int level;boolean loaded;
        Column(int dx,int dz){this.dx=dx;this.dz=dz;}
    }
    private final int x,y,z;private final int[] levels;private final PriorityQueue<Column> queue;
    public SafeParkCandidates(int x,int y,int z,int radius,int minimumY,int maximumY){
        if(Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||radius<0||radius>16||minimumY< -2048||maximumY>2048||maximumY<minimumY||(long)maximumY-minimumY+1>512)throw new IllegalArgumentException("Safe Park search bounds");
        this.x=x;this.y=y;this.z=z;Integer[] sorted=new Integer[maximumY-minimumY+1];for(int i=0;i<sorted.length;i++)sorted[i]=minimumY+i;
        Arrays.sort(sorted,Comparator.comparingLong((Integer value)->Math.abs((long)value-y)).thenComparingInt(Integer::intValue));levels=new int[sorted.length];for(int i=0;i<levels.length;i++)levels[i]=sorted[i];
        queue=new PriorityQueue<>((a,b)->{int order=Long.compare(distance(a),distance(b));if(order!=0)return order;order=Integer.compare(levels[a.level],levels[b.level]);if(order!=0)return order;order=Integer.compare(a.dz,b.dz);return order!=0?order:Integer.compare(a.dx,b.dx);});
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)queue.add(new Column(dx,dz));
    }
    private long distance(Column column){long dy=(long)levels[column.level]-y;return (long)column.dx*column.dx+(long)column.dz*column.dz+dy*dy;}
    /** Null means either exhaustion or this call's new-column budget was consumed. */
    public Candidate next(Columns columns,int columnBudget){
        Objects.requireNonNull(columns);if(columnBudget<1||columnBudget>4096)throw new IllegalArgumentException("Safe Park column budget");
        while(!queue.isEmpty()){
            Column column=queue.peek();if(!column.loaded){if(columnBudget--==0)return null;if(!columns.loaded(x+column.dx,z+column.dz)){queue.remove();continue;}column.loaded=true;}
            queue.remove();Candidate result=new Candidate(x+column.dx,levels[column.level],z+column.dz,distance(column));
            if(++column.level<levels.length)queue.add(column);return result;
        }return null;
    }
    public boolean done(){return queue.isEmpty();}
}
