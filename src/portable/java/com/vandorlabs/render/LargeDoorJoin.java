package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/** Complete rectangular groups of independently installed three-cell door modules. */
public final class LargeDoorJoin {
    public static final int PITCH = 3;
    public static final String TAG = "LargeDoorJoin";

    public static final class Group<T> {
        public final T anchor;
        public final List<T> members;
        public final int columns,rows,column,row;
        private Group(T anchor,List<T> members,int columns,int rows,int column,int row){
            this.anchor=anchor;
            this.members=members;
            this.columns=columns;
            this.rows=rows;
            this.column=column;
            this.row=row;
        }
        public boolean joined(){return columns!=1||rows!=1;}
    }

    public static <T> Group<T> singleton(T start){
        return new Group<>(start,Collections.singletonList(start),1,1,0,0);
    }

    /** Largest face-connected cluster considered before every module stays single. */
    public static final int MAX_COMPONENT=32;

    /**
     * Walk the face-connected component of compatible modules, then tile it the same way
     * from every member: in row-major order from the lowest row, each free module starts a
     * 2x2 square if it can, otherwise a horizontal pair, otherwise a vertical pair, otherwise
     * it stays single. An L of three keeps its pair; the fourth module completes the square.
     * The lookup accepts module offsets relative to start and returns only installed
     * compatible modules.
     */
    public static <T> Group<T> resolve(T start,BiFunction<Integer,Integer,T> lookup){
        java.util.Map<Long,T> cells=new java.util.HashMap<>();
        java.util.ArrayDeque<int[]> pending=new java.util.ArrayDeque<>();
        cells.put(key(0,0),start);pending.add(new int[]{0,0});
        while(!pending.isEmpty()){
            int[] at=pending.removeFirst();
            for(int side=0;side<4;side++){
                int x=at[0]+(side==0?-1:side==1?1:0),y=at[1]+(side==2?-1:side==3?1:0);
                if(cells.containsKey(key(x,y)))continue;
                T next=lookup.apply(x,y);
                if(next==null)continue;
                if(cells.size()>=MAX_COMPONENT)return singleton(start);
                cells.put(key(x,y),next);pending.add(new int[]{x,y});
            }
        }
        if(cells.size()==1)return singleton(start);
        List<int[]> order=new ArrayList<>();
        for(Long k:cells.keySet())order.add(new int[]{(int)(k>>32),(int)(long)k});
        order.sort((a,b)->a[1]!=b[1]?Integer.compare(a[1],b[1]):Integer.compare(a[0],b[0]));
        java.util.Set<Long> taken=new java.util.HashSet<>();
        int[][] shapes={{2,2},{2,1},{1,2}};
        for(int[] c:order){
            if(taken.contains(key(c[0],c[1])))continue;
            int w=1,h=1;
            for(int[] shape:shapes){
                boolean free=true;
                for(int dx=0;dx<shape[0]&&free;dx++)for(int dy=0;dy<shape[1]&&free;dy++){
                    long k=key(c[0]+dx,c[1]+dy);free=cells.containsKey(k)&&!taken.contains(k);
                }
                if(free){w=shape[0];h=shape[1];break;}
            }
            for(int dx=0;dx<w;dx++)for(int dy=0;dy<h;dy++)taken.add(key(c[0]+dx,c[1]+dy));
            if(0>=c[0]&&0<c[0]+w&&0>=c[1]&&0<c[1]+h){
                if(w==1&&h==1)return singleton(start);
                List<T> members=new ArrayList<>(w*h);
                for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++)members.add(cells.get(key(c[0]+dx,c[1]+dy)));
                return new Group<>(cells.get(key(c[0],c[1])),Collections.unmodifiableList(members),w,h,-c[0],-c[1]);
            }
        }
        throw new IllegalStateException("Joined door tiling missed its start module");
    }
    private static long key(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}

    /** Project an oriented box from the canonical anchor cell in its installed plane. */
    public static <T> T project(SpaceDoorBounds.Factory<T> box,int facing,boolean horizontal,int columns,int rows,
            double x0,double y0,double z0,double x1,double y1,double z1){
        double sx=(facing&1)==0?columns:horizontal?rows:1;
        double sy=horizontal?1:rows;
        double sz=(facing&1)!=0?columns:horizontal?rows:1;
        double ox=(facing==2||horizontal&&facing==1)?1:0;
        double oz=(facing==3||horizontal&&facing==2)?1:0;
        return box.create(ox+(x0-ox)*sx,y0*sy,oz+(z0-oz)*sz,
                ox+(x1-ox)*sx,y1*sy,oz+(z1-oz)*sz);
    }

    public interface Point<T> { T create(double x,double y,double z); }
    public static <T> T unproject(Point<T> point,int facing,boolean horizontal,int columns,int rows,double x,double y,double z){
        double sx=(facing&1)==0?columns:horizontal?rows:1;
        double sy=horizontal?1:rows;
        double sz=(facing&1)!=0?columns:horizontal?rows:1;
        double ox=(facing==2||horizontal&&facing==1)?1:0;
        double oz=(facing==3||horizontal&&facing==2)?1:0;
        return point.create(ox+(x-ox)/sx,y/sy,oz+(z-oz)/sz);
    }

    private LargeDoorJoin(){}
}
