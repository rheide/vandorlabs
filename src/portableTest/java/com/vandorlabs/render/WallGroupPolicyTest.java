package com.vandorlabs.render;

import java.util.LinkedHashSet;
import java.util.Set;

public final class WallGroupPolicyTest {
    private static void need(boolean value){if(!value)throw new AssertionError("Flat wall group policy");}
    private static WallGroupPolicy.Surface surface(Object family,boolean join,int shape,int main,int layout){return new WallGroupPolicy.Surface(family,true,join,2,0,shape,main,layout);}
    public static void main(String[] args){
        Object family=new Object();WallGroupPolicy.Surface a=surface(family,true,0,4,1);
        need(WallGroupPolicy.aperture(a,surface(family,true,0,99,2)));
        need(!WallGroupPolicy.aperture(a,surface(family,false,0,4,1)));
        need(!WallGroupPolicy.aperture(a,surface(family,true,1,4,1)));
        need(!WallGroupPolicy.aperture(a,surface(new Object(),true,0,4,1)));
        need(WallGroupPolicy.main(a,surface(family,false,3,4,1)));
        need(!WallGroupPolicy.main(a,surface(family,true,0,5,1)));
        Set<Long> cells=new LinkedHashSet<Long>();for(int y=-3;y<0;y++)for(int x=-2;x<1;x++)if(x!=-1||y!=-2)cells.add(com.vandorlabs.client.PortholeRectangles.cell(x,y));
        java.util.Map<Long,WallGroupPolicy.Layout> layouts=WallGroupPolicy.partition(cells,p->(int)(p>>32),p->(int)(long)p,WallGroupPolicy.LIMIT);need(layouts.size()==8);
        for(long cell:cells){WallGroupPolicy.Layout l=layouts.get(cell);need(l.minAxis+l.column==(int)(cell>>32)&&l.minY+l.row==(int)cell);for(int y=0;y<l.rows;y++)for(int x=0;x<l.columns;x++)need(cells.contains(com.vandorlabs.client.PortholeRectangles.cell(l.minAxis+x,l.minY+y)));}
        java.util.Map<Long,WallGroupPolicy.Layout> capped=WallGroupPolicy.partition(cells,p->(int)(p>>32),p->(int)(long)p,8);for(WallGroupPolicy.Layout l:capped.values())need(l.columns==1&&l.rows==1&&l.column==0&&l.row==0);
        try{layouts.clear();throw new AssertionError("mutable layouts");}catch(UnsupportedOperationException expected){}
        System.out.println("WallGroupPolicy PASS aperture/material independence, exact family/shape/join boundaries, holed negative coordinates, inclusive cap and immutable layouts");
    }
}
