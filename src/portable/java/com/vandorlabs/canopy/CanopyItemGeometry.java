package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;
import java.util.List;
import java.util.function.IntFunction;

/** Inventory geometry follows the released authored defaults independently of placed contact masks. */
public final class CanopyItemGeometry {
    private CanopyItemGeometry(){}
    public static List<CanopyFace> preview(CanopyShapes.Models models,String kind,CanopySettingsData settings,double aspect,IntFunction<String> material){
        List<CanopyFace> source=source(models,kind,settings);
        CanopyItemDetail bounds=new CanopyItemDetail();
        for(CanopyFace face:source)for(CanopyPoint point:face.vertices)bounds.include(point.x,point.y,point.z);
        java.util.ArrayList<CanopyFace> result=new java.util.ArrayList<>();
        for(CanopyFace face:source){
            double area=0;for(int i=1;i<face.points()-1;i++)area+=Math.sqrt(face.vertex(i).subtract(face.vertex(0)).crossProduct(face.vertex(i+1).subtract(face.vertex(0))).lengthSquared())/2;
            if(!bounds.keep(face.baseMaterial,area))continue;
            result.add(face);
        }
        source=CanopySurfaceUv.drawable(CanopySurfaceTopology.visible(CanopyShapes.finish(result,settings,aspect,material)));
        result.clear();
        for(CanopyFace face:source){CanopyPoint[] points=new CanopyPoint[face.points()];for(int i=0;i<points.length;i++){CanopyPoint p=face.vertex(i);points[i]=new CanopyPoint(bounds.x(p.x),bounds.y(p.y),bounds.z(p.z));}result.add(face.geometryOwned(points,face.uv));}
        return java.util.Collections.unmodifiableList(result);
    }
    public static List<CanopyFace> faces(CanopyShapes.Models models,String kind,CanopySettingsData settings,double aspect,IntFunction<String> material){
        return CanopySurfaceUv.drawable(CanopySurfaceTopology.visible(CanopyShapes.finish(source(models,kind,settings),settings,aspect,material)));
    }
    private static List<CanopyFace> source(CanopyShapes.Models models,String kind,CanopySettingsData settings){
        List<CanopyFace> faces;
        if(CanopySettingsData.supports(kind))faces=CanopyShapes.front(models,kind.equals("large_front_canopy_glass")?5:3,settings,true);
        else if(kind.equals("canopy")||kind.equals("long_canopy")){
            int depth=kind.equals("long_canopy")?3:2;
            faces=CanopyShapes.opening(models,1,depth,Math.min(depth,settings.angle),false);
        }else faces=models.model(kind.equals("regular_canopy_glass")?"regular_1x1_00":kind);
        return faces;
    }
}
