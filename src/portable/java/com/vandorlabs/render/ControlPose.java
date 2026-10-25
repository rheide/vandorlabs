package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.render.ControlMount.Box;

/** Canonical element/variant rotation and supporting-face discovery for binary controls. */
public final class ControlPose {
    public static final class Element {
        public final Box bounds;public final Point origin;public final int axis;public final double degrees;
        public Element(Box bounds,Point origin,int axis,double degrees){this.bounds=bounds;this.origin=origin;this.axis=axis;this.degrees=degrees;}
    }
    public final List<List<Point>> components;public final List<Box> supports;public final Box support;
    public ControlPose(List<Element> elements,int facing,double variantX,double variantY){
        List<List<Point>> components=new ArrayList<>();List<Box> supports=new ArrayList<>();Point normal=ControlMount.normal(facing),origin=new Point(.5,.5,.5).subtract(normal.scale(.5));
        for(Element element:elements){List<Point> points=new ArrayList<>(),bottom=new ArrayList<>();for(Point start:ControlMount.corners(element.bounds)){Point p=start;if(element.origin!=null)p=ControlMount.rotate(p,element.origin,element.axis,element.degrees);if(variantX!=0)p=ControlMount.rotate(p,new Point(.5,.5,.5),0,-variantX);if(variantY!=0)p=ControlMount.rotate(p,new Point(.5,.5,.5),1,-variantY);points.add(p);if(Math.abs(p.subtract(origin).dot(normal))<1e-6)bottom.add(p);}components.add(Collections.unmodifiableList(points));if(bottom.size()>=4)supports.add(ControlMount.hull(bottom));}
        if(supports.isEmpty())throw new IllegalStateException("Control has no supporting face");List<Point> all=new ArrayList<>();for(Box box:supports)all.addAll(ControlMount.corners(box));this.components=Collections.unmodifiableList(components);this.supports=Collections.unmodifiableList(supports);support=ControlMount.hull(all);
    }
}
