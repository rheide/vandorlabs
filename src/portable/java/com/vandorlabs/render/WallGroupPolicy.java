package com.vandorlabs.render;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Flat wall aperture and artwork compatibility over platform-supplied loaded members. */
public final class WallGroupPolicy {
    public static final int LIMIT=4096;
    public static final class Surface {
        public final Object family;
        public final boolean porthole,join;
        public final int facing,depth,shape,main,layout;
        public Surface(Object family,boolean porthole,boolean join,int facing,int depth,int shape,int main,int layout){
            this.family=family;this.porthole=porthole;this.join=join;this.facing=facing;this.depth=depth;this.shape=shape;this.main=main;this.layout=layout;
        }
    }
    public static boolean aperture(Surface a,Surface b){return a!=null&&b!=null&&a.porthole&&b.porthole&&a.family==b.family&&a.join&&b.join&&a.facing==b.facing&&a.depth==b.depth&&a.shape==b.shape;}
    /** Housing artwork groups independently of aperture joining or glass material. */
    public static boolean main(Surface a,Surface b){return a!=null&&b!=null&&a.family==b.family&&a.facing==b.facing&&a.depth==b.depth&&a.main==b.main&&a.layout==b.layout;}
    public static final class Layout {
        public final int minAxis,minY,column,row,columns,rows;
        private Layout(int minAxis,int minY,int column,int row,int columns,int rows){this.minAxis=minAxis;this.minY=minY;this.column=column;this.row=row;this.columns=columns;this.rows=rows;}
    }
    private WallGroupPolicy(){}
    /** Hole partition and the legacy inclusive cap; the caller chooses the canonical right/up basis. */
    public static <P> Map<P,Layout> partition(Set<P> members,ToIntFunction<P> horizontal,ToIntFunction<P> vertical,int limit){
        Map<P,Layout> result=new LinkedHashMap<P,Layout>();
        if(members.size()>=limit){for(P member:members)result.put(member,new Layout(horizontal.applyAsInt(member),vertical.applyAsInt(member),0,0,1,1));return Collections.unmodifiableMap(result);}
        Map<Long,P> cells=new HashMap<Long,P>();
        for(P member:members)cells.put(PanelRectangles.cell(horizontal.applyAsInt(member),vertical.applyAsInt(member)),member);
        for(PanelRectangles.Rect rect:PanelRectangles.partition(cells.keySet()))for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++){
            P member=cells.get(PanelRectangles.cell(rect.x+x,rect.y+y));result.put(member,new Layout(rect.x,rect.y,x,y,rect.width,rect.height));
        }
        return Collections.unmodifiableMap(result);
    }
}
