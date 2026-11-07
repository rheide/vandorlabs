package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;

import java.util.*;

/** Render-thread-owned preparation cache; contains immutable world-free faces and no tile references. */
public final class CanopyDrawingGeometry {
    private final List<CanopyFace> source;
    private final String kind;
    private final int width;
    private final CanopySettingsData settings;
    private List<CanopyFace> closed,moving,fixedClearance;
    private int[][] movingPlanes;
    private List<CanopyFace> posed;private double lastProgress=Double.NaN;
    private List<CanopyFace> frontFrame,frontSource;private boolean frontBackAnchored;private int frontDepth;
    private boolean lastBackAnchored;
    public CanopyDrawingGeometry(List<CanopyFace> source,String kind,int width,CanopySettingsData settings){this.source=Collections.unmodifiableList(new ArrayList<>(source));this.kind=java.util.Objects.requireNonNull(kind);this.width=width;this.settings=java.util.Objects.requireNonNull(settings);}
    public List<CanopyFace> faces(boolean separated){
        if(!separated){if(closed==null)closed=Collections.unmodifiableList(CanopySurfaceUv.drawable(CanopySurfaceTopology.visible(source)));return closed;}
        if(moving==null){
            Map<String,List<CanopyFace>> groups=new LinkedHashMap<>();
            for(CanopyFace face:source)groups.computeIfAbsent(face.group,k->new ArrayList<>()).add(face);
            moving=new ArrayList<>();for(List<CanopyFace> part:groups.values())moving.addAll(CanopySurfaceUv.drawable(CanopySurfaceTopology.visible(part)));
            movingPlanes=CanopySurfaceTopology.rigidPlanes(moving);
            moving=Collections.unmodifiableList(moving);
        }
        return moving;
    }
    public List<CanopyFace> posed(double progress){
        return posed(progress,false);
    }
    public List<CanopyFace> posed(double progress,boolean backAnchored){
        if(posed==null || progress!=lastProgress || backAnchored!=lastBackAnchored){
            boolean separated=(kind.equals("canopy") || kind.equals("long_canopy")) && progress>0;
            List<CanopyFace> drawing=faces(separated);
            if(kind.equals("regular_canopy_glass")){
                if(fixedClearance==null)fixedClearance=insetFixedSides(drawing);
                drawing=fixedClearance;
            }
            if(CanopySettingsData.supports(kind))drawing=rearClearance(drawing,settings.depth,backAnchored);
            posed=pose(drawing,kind,width,settings,progress);
            if(separated)posed=Collections.unmodifiableList(CanopySurfaceTopology.visibleRigidParts(posed,movingPlanes));
            lastProgress=progress;
            lastBackAnchored=backAnchored;
        }
        return posed;
    }
    /** Move the authored outer sides inside their cell, using one affine map
     * for every face so glass, rails and shared corner vertices stay sealed.
     * The join clearance is render-only, including carried copies. */
    static final double SIDE_CLEARANCE=1D/4096;
    public static List<CanopyFace> insetFixedSides(List<CanopyFace> drawing){
        List<CanopyFace> result=new ArrayList<>(drawing.size());
        for(CanopyFace original:drawing){
            CanopyPoint[] vertices=new CanopyPoint[original.vertices.length];
            for(int i=0;i<vertices.length;i++){CanopyPoint v=original.vertices[i];vertices[i]=new CanopyPoint(SIDE_CLEARANCE+v.x*(1-2*SIDE_CLEARANCE),v.y,SIDE_CLEARANCE+v.z*(1-2*SIDE_CLEARANCE));}
            result.add(original.geometryOwned(vertices,original.uv));
        }
        return result;
    }
    private List<CanopyFace> rearClearance(List<CanopyFace> drawing,int depth,boolean backAnchored){
        if(frontFrame!=null&&frontSource==drawing&&frontDepth==depth&&frontBackAnchored==backAnchored)return frontFrame;
        frontSource=drawing;frontDepth=depth;frontBackAnchored=backAnchored;frontFrame=insetRear(drawing,depth,backAnchored);return frontFrame;
    }
    public static List<CanopyFace> insetRear(List<CanopyFace> drawing,int depth,boolean backAnchored){
        List<CanopyFace> result=new ArrayList<>(drawing.size());
        for(CanopyFace original:drawing){
            CanopyPoint[] vertices=new CanopyPoint[original.vertices.length];
            for(int i=0;i<vertices.length;i++){CanopyPoint vertex=original.vertices[i];vertices[i]=new CanopyPoint(vertex.x,vertex.y,com.vandorlabs.render.FrontCanopyRenderDepth.coordinate(vertex.z,depth/16D,backAnchored));}
            result.add(original.geometryOwned(vertices,original.uv));
        }
        return result;
    }
    private static List<CanopyFace> pose(List<CanopyFace> source,String kind,int width,CanopySettingsData settings,double progress){
        CanopyTransform transform=new CanopyTransform(kind,width,settings,progress);
        List<CanopyFace> result=new ArrayList<>(source.size());
        for(CanopyFace original:source){
            CanopyPoint[] vertices=new CanopyPoint[original.vertices.length];double[][] uv=original.uv;
            for(int i=0;i<vertices.length;i++)vertices[i]=transform.apply(original.vertices[i],original.group);
            if(settings.mount==1){CanopyPoint[] transformed=vertices.clone();uv=new double[vertices.length][];for(int i=0;i<vertices.length;i++){int j=vertices.length-1-i;vertices[i]=transformed[j];uv[i]=original.uv[j];}}
            result.add(original.posedOwned(vertices,uv));
        }
        return Collections.unmodifiableList(result);
    }
}
