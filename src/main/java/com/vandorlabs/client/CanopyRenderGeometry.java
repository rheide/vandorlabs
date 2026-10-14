package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import java.util.*;

/** Clip each rigid part once; only contacts between moving parts need fresh topology. */
final class CanopyRenderGeometry {
    private final List<CanopyMesh.Face> source;
    private List<CanopyMesh.Face> closed,moving;
    private int[][] movingPlanes;
    private List<CanopyMesh.Face> posed;private double lastProgress=Double.NaN;
    CanopyRenderGeometry(List<CanopyMesh.Face> source){this.source=source;}
    List<CanopyMesh.Face> faces(boolean separated){
        if(!separated){if(closed==null)closed=CanopyMaterialUv.drawable(CanopySurfaces.visible(source));return closed;}
        if(moving==null){
            Map<String,List<CanopyMesh.Face>> groups=new LinkedHashMap<>();
            for(CanopyMesh.Face face:source)groups.computeIfAbsent(face.group,k->new ArrayList<>()).add(face);
            moving=new ArrayList<>();for(List<CanopyMesh.Face> part:groups.values())moving.addAll(CanopyMaterialUv.drawable(CanopySurfaces.visible(part)));
            movingPlanes=CanopySurfaces.rigidPlanes(moving);
        }
        return moving;
    }
    List<CanopyMesh.Face> posed(String kind,int width,LargeCanopySettings settings,double progress){
        if(posed==null || progress!=lastProgress){
            boolean separated=(kind.equals("canopy") || kind.equals("long_canopy")) && progress>0;
            posed=CanopyPose.faces(faces(separated),kind,width,settings,progress);
            if(separated)posed=CanopySurfaces.visibleRigidParts(posed,movingPlanes);
            lastProgress=progress;
        }
        return posed;
    }
}
