package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Legacy renderer API over the platform-neutral rectangle partition policy. */
public final class PortholeRectangles {
    public static long cell(int x,int y){return com.vandorlabs.render.PanelRectangles.cell(x,y);}
    public static final class Rect extends com.vandorlabs.render.PanelRectangles.Rect {
        Rect(int x,int y,int width,int height){super(x,y,width,height);}
    }
    public static List<Rect> partition(Set<Long> cells){
        List<Rect> result=new ArrayList<Rect>();
        for(com.vandorlabs.render.PanelRectangles.Rect rect:com.vandorlabs.render.PanelRectangles.partition(cells))result.add(new Rect(rect.x,rect.y,rect.width,rect.height));
        return result;
    }
    private PortholeRectangles(){}
}
