package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;
import com.vandorlabs.tiles.GlassTextures;
import java.util.*;
import java.util.function.IntFunction;

/** Stateless authored-kit assembly. Callers own the byte-bounded caches of resulting immutable faces. */
public final class CanopyShapes {
    public interface Models { List<CanopyFace> model(String name); }
    private CanopyShapes(){}
    private static CanopyFace copy(CanopyFace f,CanopyPoint[] points,double[][] uv,String material,String group) {
        return new CanopyFace(points,uv,material,f.baseMaterial,group,f.doubleSided,false,false);
    }
    private static String variant(String id,int mask){return mask==0?id:String.format(Locale.ROOT,"%s_%02x",id,mask);}
    private static List<CanopyFace> regular(Models models,int mask){return models.model(String.format(Locale.ROOT,"regular_1x1_%02x",mask));}
    private static List<CanopyFace> rearJoined(Models models,String id,int mask,boolean rear) {
        List<CanopyFace> closed=id.equals("regular_1x1")?regular(models,mask):models.model(variant(id,mask));
        if(!rear)return closed;double back=id.equals("angled_front_1x2")?2:1;List<CanopyFace> result=new ArrayList<>();
        for(CanopyFace face:closed){boolean glass=face.baseMaterial.equals("glass");for(CanopyPoint p:face.vertices)if(p.z<back-1D/8)glass=false;if(!glass)result.add(face);}
        return result;
    }
    private static void append(List<CanopyFace> result,List<CanopyFace> source,int x,int z,boolean raised) {
        for(CanopyFace f:source){CanopyPoint[] points=new CanopyPoint[f.points()];for(int i=0;i<points.length;i++){CanopyPoint p=f.vertex(i);points[i]=p.addVector(x,raised&&p.y<.5?1D/16:0,z);}result.add(copy(f,points,f.uv,f.material,"shell"));}
    }
    public static List<CanopyFace> opening(Models models,int width,int depth,int angle,boolean rear) {
        if(width<1||width>9||depth<2||depth>3||angle<0||angle>(depth==3?3:2))throw new IllegalArgumentException("Opening canopy dimensions");
        List<CanopyFace> result=new ArrayList<>();
        for(int x=0;x<width;x++) {
            int sides=(x>0?1:0)|(x+1<width?2:0);
            if(angle==3){List<CanopyFace> diagonal=new ArrayList<>();for(CanopyFace f:rearJoined(models,"angled_front_1x2",sides,rear)){CanopyPoint[] p=new CanopyPoint[f.points()];for(int i=0;i<p.length;i++){CanopyPoint v=f.vertex(i);p[i]=new CanopyPoint(v.x,v.y,v.z*1.5);}diagonal.add(copy(f,p,f.uv,f.material,f.group));}append(result,diagonal,x,0,false);}
            else if(angle==2){append(result,rearJoined(models,"angled_front_1x2",sides,depth==3||rear),x,0,false);if(depth==3)append(result,rearJoined(models,"regular_1x1",sides|4,rear),x,2,false);}
            else{append(result,angle==0?regular(models,sides|8):models.model(variant("angled_front_1x1",sides|8)),x,0,false);for(int z=1;z<depth;z++)append(result,rearJoined(models,"regular_1x1",sides|4|(z+1<depth?8:0),z+1<depth||rear),x,z,angle==1);}
        }
        for(CanopyFace f:models.model(width==1?"canopy":"canopy_mounts_double"))if(f.group.equals("mount")) {
            CanopyPoint[] p=new CanopyPoint[f.points()];for(int i=0;i<p.length;i++){CanopyPoint v=f.vertex(i);double x=v.x;if(width>=3)x=x<=.125?x:x>=1.875?x+width-2:.125+(x-.125)*(width-.25)/1.75;p[i]=new CanopyPoint(x,v.y,v.z*depth/2D);}result.add(copy(f,p,f.uv,f.material,f.group));
        }
        return Collections.unmodifiableList(result);
    }
    public static List<CanopyFace> front(Models models,int width,CanopySettingsData settings,boolean backAnchored) {
        List<CanopyFace> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(CanopyFace f:models.model("front_canopy_glass")) {
            CanopyPoint[] p=new CanopyPoint[f.points()];double[][] uv=f.uv;List<String> keys=new ArrayList<>();
            for(int i=0;i<p.length;i++){CanopyPoint v=f.vertex(i);double t=1-Math.min(1,v.z/.96875),factor=width/3D*(1-t+t*settings.scale/100D/(2.125/3D));double x=Math.max(0,Math.min(width,width/2D+(v.x-1.5)*factor)),y=Math.max(0,Math.min(width,width/2D+(v.y-1.5)*factor));p[i]=new CanopyPoint(x,y,(backAnchored?1-v.z:v.z)*Math.max(1D/64,settings.depth/16D));keys.add(Math.round(p[i].x*1048576)+","+Math.round(p[i].y*1048576)+","+Math.round(p[i].z*1048576));}
            if(backAnchored){CanopyPoint[] reversed=new CanopyPoint[p.length];uv=new double[p.length][];for(int i=0;i<p.length;i++){reversed[i]=p[p.length-1-i];uv[i]=f.uv[p.length-1-i];}p=reversed;}
            double area=0;for(int i=1;i<p.length-1;i++)area+=p[i].subtract(p[0]).crossProduct(p[i+1].subtract(p[0])).lengthSquared();Collections.sort(keys);
            if(area>1E-16&&seen.add(String.join(";",keys)))result.add(copy(f,p,uv,f.baseMaterial.equals("glass")?GlassTextures.texture(settings.shade,1,settings.tint):f.material,f.group));
        }
        return Collections.unmodifiableList(result);
    }
    private static boolean roof(CanopyFace f){for(CanopyPoint p:f.vertices)if(p.y<14D/16)return false;return true;}
    public static List<CanopyFace> fixed(Models models,int mask,int roof) {
        mask&=15;roof&=mask;List<CanopyFace> aperture=regular(models,mask);if(mask==roof)return aperture;List<CanopyFace> result=new ArrayList<>();for(CanopyFace f:aperture)if(!roof(f))result.add(f);for(CanopyFace f:regular(models,roof))if(roof(f))result.add(f);return Collections.unmodifiableList(result);
    }
    public static List<CanopyFace> shift(List<CanopyFace> source,double x,double z) {
        List<CanopyFace> result=new ArrayList<>();for(CanopyFace f:source){CanopyPoint[] p=new CanopyPoint[f.points()];for(int i=0;i<p.length;i++)p[i]=f.vertex(i).addVector(x,0,z);result.add(copy(f,p,f.uv,f.material,f.group));}return Collections.unmodifiableList(result);
    }
    public static List<CanopyFace> visorRails(List<CanopyFace> source,double width,double height) {
        List<CanopyFace> result=new ArrayList<>(source);
        for(CanopyFace f:source)if(f.baseMaterial.equals("glass")) {
            double top=0;for(CanopyPoint p:f.vertices)top=Math.max(top,p.y);if(Math.abs(top-height)>1E-8)continue;List<CanopyPoint> edge=new ArrayList<>();for(CanopyPoint p:f.vertices)if(Math.abs(p.y-top)<1E-8)edge.add(p);
            if(edge.size()==2)rail(result,edge.get(0),edge.get(1),width);
        }
        return Collections.unmodifiableList(result);
    }
    private static void rail(List<CanopyFace> result,CanopyPoint a,CanopyPoint b,double width) {
        CanopyPoint direction=b.subtract(a);double length=Math.hypot(direction.x,direction.z);if(length<1E-8)return;double nx=-direction.z/length/32,nz=direction.x/length/32;CanopyPoint[] v=new CanopyPoint[8];
        for(int y=0;y<2;y++)for(int end=0;end<2;end++)for(int side=0;side<2;side++){CanopyPoint p=end==0?a:b;double sign=side==0?-1:1;v[y*4+end*2+side]=new CanopyPoint(Math.max(0,Math.min(width,p.x+sign*nx)),p.y-(y==0?1D/16:0),Math.max(0,Math.min(1,p.z+sign*nz)));}
        for(int[] indices:new int[][]{{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}}){CanopyPoint[] p=new CanopyPoint[4];for(int i=0;i<4;i++)p[i]=v[indices[i]];result.add(new CanopyFace(p,new double[][]{{0,0},{1,0},{1,1},{0,1}},"canopy_canopy_steel","steel","top_border",true,false,false));}
    }
    public static List<CanopyFace> finish(List<CanopyFace> source,CanopySettingsData settings,double aspect,IntFunction<String> material) {
        List<CanopyFace> result=new ArrayList<>(source.size());for(CanopyFace f:source){if(!f.baseMaterial.equals("glass")){result.add(f);continue;}
            double[][] uv=settings.material<0?f.uv:CanopySurfaceUv.coordinates(f,settings.textureLayout(),aspect);boolean tiled=settings.material>=0&&!CanopySurfaceUv.fits(f,settings.textureLayout(),aspect);
            result.add(new CanopyFace(f.vertices,uv,settings.material<0?GlassTextures.texture(settings.shade,1,settings.tint):material.apply(settings.material),settings.material<0?f.baseMaterial:"panel",f.group,f.doubleSided,true,tiled));
        }return Collections.unmodifiableList(result);
    }
}
