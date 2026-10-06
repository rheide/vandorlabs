package com.vandorlabs.dynmap;

import com.vandorlabs.render.*;
import org.dynmap.renderer.*;
import java.util.*;

/** Use the loaded world's saved opening basis for grouped and diagonal split geometry. */
final class TrapdoorPanelMap {
    static String[] fields(String... original) {
        List<String> fields=new ArrayList<>(Arrays.asList(original));fields.add("TrapdoorSlideMode");
        for(String axis:new String[]{"U","V"})for(int i=0;i<3;i++)fields.add("TrapdoorPanel"+axis+i);
        for(String value:new String[]{"LowU","LowV","Width","Height"})fields.add("TrapdoorPanel"+value);
        return fields.toArray(new String[0]);
    }
    private static double number(MapDataContext context,String key,double fallback){Object value=context.getBlockTileEntityField(key);return value instanceof Number?((Number)value).doubleValue():fallback;}
    static RenderPatch[] render(MapDataContext context,double[][] corners,boolean tall,double pose,int texture) {
        int mode=(int)number(context,"TrapdoorSlideMode",0);if(!PanelMotion.valid(mode) || mode==0)return null;
        double[] u=axis(corners,1),v=axis(corners,tall?2:4);
        for(int i=0;i<3;i++){u[i]=number(context,"TrapdoorPanelU"+i,u[i]);v[i]=number(context,"TrapdoorPanelV"+i,v[i]);}
        double lowU=Double.POSITIVE_INFINITY,lowV=lowU,highU=Double.NEGATIVE_INFINITY,highV=highU;
        for(double[] point:corners){double a=PanelPolyhedron.dot(point,u),b=PanelPolyhedron.dot(point,v);lowU=Math.min(lowU,a);highU=Math.max(highU,a);lowV=Math.min(lowV,b);highV=Math.max(highV,b);}
        PanelMotion motion=new PanelMotion(u,v,number(context,"TrapdoorPanelLowU",lowU-.002),number(context,"TrapdoorPanelLowV",lowV-.002),number(context,"TrapdoorPanelWidth",highU-lowU+.004),number(context,"TrapdoorPanelHeight",highV-lowV+.004),mode);
        List<RenderPatch> patches=new ArrayList<>();
        for(int panel=0;panel<motion.count();panel++)for(double[][] face:PanelPolyhedron.faces(corners,motion,panel,pose))for(int i=1;i+1<face.length;i++) {
            double[] a=face[0],b=face[i],c=face[i+1];double[] cross=PanelPolyhedron.cross(new double[]{b[0]-a[0],b[1]-a[1],b[2]-a[2]},new double[]{c[0]-a[0],c[1]-a[1],c[2]-a[2]});
            if(PanelPolyhedron.dot(cross,cross)<1e-16)continue;
            RenderPatch patch=context.getPatchFactory().getPatch(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],1,RenderPatchFactory.SideVisible.TOP,texture);if(patch!=null)patches.add(patch);
        }
        return patches.toArray(new RenderPatch[0]);
    }
    private static double[] axis(double[][] corners,int end){return PanelPolyhedron.unit(new double[]{corners[end][0]-corners[0][0],corners[end][1]-corners[0][1],corners[end][2]-corners[0][2]});}
}
