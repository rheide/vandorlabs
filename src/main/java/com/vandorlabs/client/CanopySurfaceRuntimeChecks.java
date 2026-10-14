package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Posed overlap removal must retain the visible union and keep the rear sill continuous. */
final class CanopySurfaceRuntimeChecks {
    private static double area(List<CanopyMesh.Face> faces){double area=0;for(CanopyMesh.Face f:faces)for(int i=1;i<f.vertices.length-1;i++)area+=f.vertices[i].subtract(f.vertices[0]).crossProduct(f.vertices[i+1].subtract(f.vertices[0])).lengthVector()/2;return area;}
    static void run(){
        int clipped=0;
        for(boolean joined:new boolean[]{false,true})for(int width=1;width<=3;width++)for(int angle=0;angle<3;angle++)for(int motion=0;motion<2;motion++)for(int mount=0;mount<2;mount++)for(double progress:new double[]{0,.1,.5,1}){
            LargeCanopySettings settings=new LargeCanopySettings();settings.angle=angle;settings.motion=motion;settings.mount=mount;
            List<CanopyMesh.Face> source=CanopyPose.faces(OpeningCanopyGeometry.faces(width,angle,joined),"canopy",width,settings,progress);
            List<CanopyMesh.Face> visible=CanopySurfaces.visible(source);double before=area(source),after=area(visible),again=area(CanopySurfaces.visible(visible));
            if(!(after>0 && after<=before+1e-7 && Math.abs(after-again)<1e-7))throw new IllegalStateException("canopy visible union "+width+":"+angle+":"+motion+":"+mount+":"+progress+" areas="+before+","+after+","+again);
            if(after<before-1e-7)clipped++;
        }
        Set<Double> front=new HashSet<>(),rear=new HashSet<>();
        for(CanopyMesh.Face f:OpeningCanopyGeometry.faces(1,1,false))if(f.group.equals("shell"))for(Vec3d v:f.vertices)if(v.x==0 && v.z==1){if(Arrays.stream(f.vertices).anyMatch(p->p.z<1))front.add(v.y);if(Arrays.stream(f.vertices).anyMatch(p->p.z>1))rear.add(v.y);}
        if(!Collections.min(front).equals(Collections.min(rear)) || !Collections.max(front).equals(Collections.max(rear)))throw new IllegalStateException("canopy sill seam front="+front+" rear="+rear);
        if(clipped==0)throw new IllegalStateException("canopy overlap fixture was not exercised");
        int roofs=0;
        for(int mask=0;mask<16;mask++)for(int roof=0;roof<16;roof++)if((roof&mask)==roof){
            List<CanopyMesh.Face> source=CanopyVariants.fixedGlassFaces(mask,roof),visible=CanopySurfaces.visible(source);
            if(Math.abs(area(visible)-area(CanopySurfaces.visible(visible)))>1e-7)throw new IllegalStateException("fixed glass roof overlap mask="+mask+" roof="+roof);
            Set<String> polygons=new HashSet<>();
            for(CanopyMesh.Face face:visible){List<String> vertices=new ArrayList<>();for(Vec3d v:face.vertices)vertices.add(Math.round(v.x*1048576)+":"+Math.round(v.y*1048576)+":"+Math.round(v.z*1048576));Collections.sort(vertices);if(!polygons.add(String.join(";",vertices)))throw new IllegalStateException("duplicate fixed roof surface mask="+mask+" roof="+roof);}
            roofs++;
        }
        System.out.println("[vandorlabs][reprolab] canopy-surfaces PASS widths=3 angles=3 motions=2 mounts=2 poses=4 rear-joins=2 fixed-roofs="+roofs+" no-duplicate-surfaces visible-union sill-seam clipped="+clipped);
    }
}
