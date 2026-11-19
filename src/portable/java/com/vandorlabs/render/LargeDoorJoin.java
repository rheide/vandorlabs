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

    /**
     * Walk a face-connected component. The lookup accepts module offsets relative to
     * start and returns only installed compatible modules. Oversize and incomplete
     * components resolve to single doors, independent of which member is queried.
     */
    public static <T> Group<T> resolve(T start,BiFunction<Integer,Integer,T> lookup){
        List<T> members=new ArrayList<>(4);
        int[] xs=new int[4],ys=new int[4];
        members.add(start);
        int minX=0,maxX=0,minY=0,maxY=0;
        for(int i=0;i<members.size();i++){
            for(int side=0;side<4;side++){
                int x=xs[i]+(side==0?-1:side==1?1:0);
                int y=ys[i]+(side==2?-1:side==3?1:0);
                boolean seen=false;
                for(int j=0;j<members.size();j++)if(xs[j]==x&&ys[j]==y){seen=true;break;}
                if(seen)continue;
                T next=lookup.apply(x,y);
                if(next==null)continue;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                int count=members.size();
                if(count==4||maxX-minX>1||maxY-minY>1)return singleton(start);
                xs[count]=x;ys[count]=y;
                members.add(next);
            }
        }
        int columns=maxX-minX+1,rows=maxY-minY+1;
        if(members.size()==1||members.size()!=columns*rows)return singleton(start);
        for(int i=0;i<members.size();i++)if(xs[i]==minX&&ys[i]==minY)
            return new Group<>(members.get(i),Collections.unmodifiableList(members),columns,rows,-minX,-minY);
        throw new IllegalStateException("Missing joined door anchor");
    }

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
