package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import java.util.*;

/** Compare cached rigid-part surfaces to full per-pose clipping, including tiled materials. */
final class CanopyPerformanceRuntimeChecks {
    public static void main(String[] args){net.minecraft.init.Bootstrap.register();run();}
    private static double area(List<CanopyMesh.Face> faces){double sum=0;for(CanopyMesh.Face f:faces)for(int i=1;i<f.vertices.length-1;i++)sum+=f.vertices[i].subtract(f.vertices[0]).crossProduct(f.vertices[i+1].subtract(f.vertices[0])).lengthVector()/2;return sum;}
    static void run(){
        CanopySettingsKeyChecks.run();
        int variants=0;long oldTime=0,newTime=0;
        for(int width=1;width<=3;width++)for(int angle=0;angle<3;angle++)for(int motion=0;motion<2;motion++)for(int mount=0;mount<2;mount++)for(int material:new int[]{-1,0})for(boolean forward:new boolean[]{false,true}){
            LargeCanopySettings settings=new LargeCanopySettings();settings.angle=angle;settings.motion=motion;settings.mount=mount;settings.material=material;settings.tileMaterial=material>=0;settings.slideForward=forward;
            List<CanopyMesh.Face> base=CanopyFinishes.faces(OpeningCanopyGeometry.faces(width,angle,false),0,1,material,settings.tileMaterial);
            CanopyRenderGeometry cache=new CanopyRenderGeometry(base);
            for(double progress:new double[]{0,.001,.1,.5,.9,1}){
                long start=System.nanoTime();List<CanopyMesh.Face> old=CanopyMaterialUv.drawable(CanopySurfaces.visible(CanopyPose.faces(base,"canopy",width,settings,progress)));oldTime+=System.nanoTime()-start;
                start=System.nanoTime();List<CanopyMesh.Face> rigid=cache.faces(progress>0);List<CanopyMesh.Face> optimized=cache.posed("canopy",width,settings,progress);newTime+=System.nanoTime()-start;
                if(Math.abs(area(old)-area(optimized))>1e-6)throw new IllegalStateException("cached canopy surfaces differ "+width+":"+angle+":"+motion+":"+mount+":"+material+":"+progress+" area="+area(old)+"/"+area(optimized));
                if(rigid!=cache.faces(progress>0))throw new IllegalStateException("animation rebuilt clipping cache");variants++;
            }
        }
        for(int width:new int[]{3,5})for(int hinge=0;hinge<4;hinge++)for(int mount=0;mount<4;mount++){
            LargeCanopySettings settings=new LargeCanopySettings();settings.openable=true;settings.hinge=hinge;settings.mount=mount;settings.material=0;settings.tileMaterial=true;
            List<CanopyMesh.Face> base=CanopyFinishes.faces(LargeCanopyGeometry.faces(width,settings,true),0,1,0,true);CanopyRenderGeometry cache=new CanopyRenderGeometry(base);
            List<CanopyMesh.Face> renderBase=CanopyRenderGeometry.insetRear(base,settings.depth,true);
            for(double progress:new double[]{0,.1,.5,1}){
                List<CanopyMesh.Face> old=CanopyMaterialUv.drawable(CanopySurfaces.visible(CanopyPose.faces(renderBase,"front_canopy_glass",width,settings,progress)));
                List<CanopyMesh.Face> optimized=cache.posed("front_canopy_glass",width,settings,progress,true);
                if(Math.abs(area(old)-area(optimized))>1e-6)throw new IllegalStateException("cached front canopy surfaces differ");variants++;
            }
        }
        System.out.println("[vandorlabs][reprolab] canopy-animation-cache PASS variants="+variants+" rigid-surfaces UV-subdivision closed-seams mounts hinges slide-directions geometry-ms old="+oldTime/1e6+" cached="+newTime/1e6);
        for(int motion=0;motion<3;motion++){
            LargeCanopySettings settings=new LargeCanopySettings();settings.motion=motion==0?1:0;settings.openable=true;settings.material=0;settings.tileMaterial=true;
            String kind=motion<2?"canopy":"large_front_canopy_glass";int width=motion<2?3:5;
            List<CanopyMesh.Face> base=CanopyFinishes.faces(motion<2?OpeningCanopyGeometry.faces(width,2,false):LargeCanopyGeometry.faces(width,settings,true),0,1,0,true);
            List<CanopyMesh.Face> renderBase=motion<2?base:CanopyRenderGeometry.insetRear(base,settings.depth,true);
            CanopyRenderGeometry cache=new CanopyRenderGeometry(base);cache.faces(false);cache.faces(true);
            for(int round=0;round<2;round++){
                long baseline=0,optimized=0;
                for(int frame=0;frame<120;frame++){
                    double progress=(frame%60+.25)/60;
                    long start=System.nanoTime();double oldArea=area(CanopyMaterialUv.drawable(CanopySurfaces.visible(CanopyPose.faces(renderBase,kind,width,settings,progress))));baseline+=System.nanoTime()-start;
                    start=System.nanoTime();double newArea=area(cache.posed(kind,width,settings,progress,motion>=2));optimized+=System.nanoTime()-start;
                    if(Math.abs(oldArea-newArea)>1e-6)throw new IllegalStateException("animation frame changed visible surfaces: "+kind+":"+progress);
                }
                if(round==1)System.out.println("[vandorlabs][reprolab] canopy-animation-benchmark "+(motion==0?"triple-hinge":motion==1?"triple-slide":"large-front-hinge")+" frames=120 geometry-ms old="+baseline/1e6+" cached="+optimized/1e6);
            }
        }
    }
    private CanopyPerformanceRuntimeChecks(){}
}
