package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import java.util.*;

/** Clip each rigid part once; only contacts between moving parts need fresh topology. */
final class CanopyRenderGeometry {
    private final List<CanopyMesh.Face> source;
    private List<CanopyMesh.Face> closed,moving;
    private int[][] movingPlanes;
    private List<CanopyMesh.Face> posed;private double lastProgress=Double.NaN;
    private List<CanopyMesh.Face> frontFrame,frontSource;private boolean frontBackAnchored;private int frontDepth;
    private boolean lastBackAnchored;
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
        return posed(kind,width,settings,progress,false);
    }
    List<CanopyMesh.Face> posed(String kind,int width,LargeCanopySettings settings,double progress,boolean backAnchored){
        if(posed==null || progress!=lastProgress || backAnchored!=lastBackAnchored){
            boolean separated=(kind.equals("canopy") || kind.equals("long_canopy")) && progress>0;
            List<CanopyMesh.Face> drawing=faces(separated);
            if(LargeCanopySettings.supports(kind))drawing=rearClearance(drawing,settings.depth,backAnchored);
            posed=CanopyPose.faces(drawing,kind,width,settings,progress);
            if(separated)posed=CanopySurfaces.visibleRigidParts(posed,movingPlanes);
            lastProgress=progress;
            lastBackAnchored=backAnchored;
        }
        return posed;
    }
    private List<CanopyMesh.Face> rearClearance(List<CanopyMesh.Face> drawing,int depth,boolean backAnchored){
        if(frontFrame!=null&&frontSource==drawing&&frontDepth==depth&&frontBackAnchored==backAnchored)return frontFrame;
        frontSource=drawing;frontDepth=depth;frontBackAnchored=backAnchored;frontFrame=insetRear(drawing,depth,backAnchored);return frontFrame;
    }
    static List<CanopyMesh.Face> insetRear(List<CanopyMesh.Face> drawing,int depth,boolean backAnchored){
        List<CanopyMesh.Face> result=new ArrayList<>(drawing.size());
        for(CanopyMesh.Face original:drawing){
            CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=new net.minecraft.util.math.Vec3d[original.vertices.length];copy.uv=original.uv;
            copy.material=original.material;copy.baseMaterial=original.baseMaterial;copy.group=original.group;copy.doubleSided=original.doubleSided;copy.glazing=original.glazing;copy.tiled=original.tiled;
            for(int i=0;i<copy.vertices.length;i++){net.minecraft.util.math.Vec3d vertex=original.vertices[i];copy.vertices[i]=new net.minecraft.util.math.Vec3d(vertex.x,vertex.y,com.vandorlabs.render.FrontCanopyRenderDepth.coordinate(vertex.z,depth/16D,backAnchored));}
            result.add(copy);
        }
        return result;
    }
}
