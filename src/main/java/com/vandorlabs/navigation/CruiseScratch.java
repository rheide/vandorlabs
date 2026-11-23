package com.vandorlabs.navigation;

import java.util.ArrayList;

/** Only a seed worker borrows these arrays. The server's vanilla IntCache never owns them. */
public final class CruiseScratch {
    private static final ThreadLocal<CruiseScratch> CURRENT=new ThreadLocal<>();
    private final ArrayList<int[]> arrays=new ArrayList<>();
    private int cursor;
    void reset(){cursor=0;CURRENT.set(this);}
    void release(){CURRENT.remove();}
    public static int[] allocate(int size){
        CruiseScratch pool=CURRENT.get();
        if(pool==null)return null;
        int i=pool.cursor++;
        if(i==pool.arrays.size())pool.arrays.add(new int[Math.max(256,size)]);
        else if(pool.arrays.get(i).length<size)pool.arrays.set(i,new int[size]);
        return pool.arrays.get(i);
    }
}
