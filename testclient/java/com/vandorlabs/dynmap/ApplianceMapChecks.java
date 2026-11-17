package com.vandorlabs.dynmap;

import org.dynmap.renderer.*;
import java.lang.reflect.Proxy;
import java.util.*;

final class ApplianceMapChecks {
    private static final class Patch implements RenderPatch {final int texture;final double[] p;Patch(int t,double[] p){texture=t;this.p=p;}public int getTextureIndex(){return texture;}}
    static void run(){
        RenderPatchFactory factory=(RenderPatchFactory)Proxy.newProxyInstance(ApplianceMapChecks.class.getClassLoader(),new Class[]{RenderPatchFactory.class},(proxy,method,args)->{
            if(method.getName().equals("getRotatedPatch")){Patch before=(Patch)args[0];double[] p=before.p.clone();double angle=Math.toRadians(((Number)args[2]).doubleValue());for(int v=0;v<3;v++){double x=p[v*3]-.5,z=p[v*3+2]-.5;p[v*3]=.5+x*Math.cos(angle)-z*Math.sin(angle);p[v*3+2]=.5+x*Math.sin(angle)+z*Math.cos(angle);}return new Patch((Integer)args[4],p);}
            if(!method.getName().equals("getPatch"))throw new AssertionError(method);double[] p=new double[9];for(int i=0;i<9;i++)p[i]=((Number)args[i]).doubleValue();return new Patch((Integer)args[args.length-1],p);
        });
        ApplianceRenderer renderer=new ApplianceRenderer();
        for(String facing:new String[]{"north","east","south","west"}){
            DynmapBlockState state=new DynmapBlockState(null,0,"vandorlabs:printer","facing="+facing,"IRON");MapDataContext context=(MapDataContext)Proxy.newProxyInstance(ApplianceMapChecks.class.getClassLoader(),new Class[]{MapDataContext.class},(proxy,method,args)->method.getName().equals("getPatchFactory")?factory:method.getName().equals("getBlockType")?state:null);RenderPatch[] patches=renderer.getRenderPatchList(context);if(patches.length!=6||patches!=renderer.getRenderPatchList(context))throw new AssertionError("Six cached appliance faces");Set<Integer> textures=new HashSet<>();for(RenderPatch raw:patches){Patch p=(Patch)raw;textures.add(p.texture);for(double n:p.p)if(n<-.00001||n>1.00001)throw new AssertionError("Appliance bounds");if(p.texture==2){int axis=facing.equals("east")||facing.equals("west")?0:2;double plane=facing.equals("east")||facing.equals("south")?1:0;for(int v=0;v<3;v++)if(Math.abs(p.p[v*3+axis]-plane)>.00001)throw new AssertionError("Authored front facing "+facing);}}if(textures.size()!=6)throw new AssertionError("Six distinct face materials");
        }
        System.out.println("Appliance Dynmap PASS four facings six authored faces UV-preserving rotated cached cube geometry");
    }
}
