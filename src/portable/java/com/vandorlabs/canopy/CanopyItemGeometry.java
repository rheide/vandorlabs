package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;
import java.util.List;
import java.util.function.IntFunction;

/** Inventory geometry follows the released authored defaults independently of placed contact masks. */
public final class CanopyItemGeometry {
    private CanopyItemGeometry(){}
    public static List<CanopyFace> faces(CanopyShapes.Models models,String kind,CanopySettingsData settings,double aspect,IntFunction<String> material){
        List<CanopyFace> faces;
        if(CanopySettingsData.supports(kind))faces=CanopyShapes.front(models,kind.equals("large_front_canopy_glass")?5:3,settings,true);
        else if(kind.equals("canopy")||kind.equals("long_canopy")){
            int depth=kind.equals("long_canopy")?3:2;
            faces=CanopyShapes.opening(models,1,depth,Math.min(depth,settings.angle),false);
        }else faces=models.model(kind.equals("regular_canopy_glass")?"regular_1x1_00":kind);
        return CanopySurfaceUv.drawable(CanopySurfaceTopology.visible(CanopyShapes.finish(faces,settings,aspect,material)));
    }
}
