package com.vandorlabs.client;

import com.vandorlabs.render.TrapdoorGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Texture clipping is independent of a rigid leaf's pose, lighting and atlas placement. */
final class TrapdoorSurfaceMesh {
    static final int MAX_LAYOUTS=512,MAX_POINTS=65536;
    private static final LinkedHashMap<Key,Layout> LAYOUTS=new LinkedHashMap<>(64,.75F,true);
    private static int points;

    static void draw(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] uv,int light,int choice,int first,int end,boolean tiled,boolean mirror) {
        boolean customDoor=com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice) && CustomBlockTextures.isDoor(choice);
        // Resolve sprites at draw time: a resource reload cannot leave stale atlas UVs.
        TextureAtlasSprite upper=customDoor?CustomBlockTextures.sprite(choice,true):sprite;
        TextureAtlasSprite lower=customDoor?CustomBlockTextures.sprite(choice,false):sprite;
        draw(buffer,upper,lower,vertices,uv,light,first,end,tiled,mirror,customDoor);
    }
    static void draw(BufferBuilder buffer,TextureAtlasSprite upper,TextureAtlasSprite lower,double[][] vertices,double[][] uv,int light,int first,int end,boolean tiled,boolean mirror,boolean customDoor) {
        Layout layout=layout(uv,first,end,tiled,mirror,customDoor);
        for(Face face:layout.faces) {
            int[] indices=TrapdoorGeometry.FACES[face.index];
            double[] a=vertices[indices[0]],b=vertices[indices[1]],c=vertices[indices[2]];
            double nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            float normalX=(float)(nx/length),normalY=(float)(ny/length),normalZ=(float)(nz/length);
            // Clipped coordinates are affine weights in the leaf's corner basis.
            double[] origin=vertices[0],x=vertices[1],y=vertices[2],z=vertices[4];
            for(int i=0;i<face.data.length;i+=18) {
                double[] data=face.data;
                double ax=coordinate(data,i,origin[0],x[0],y[0],z[0]),ay=coordinate(data,i,origin[1],x[1],y[1],z[1]),az=coordinate(data,i,origin[2],x[2],y[2],z[2]);
                double bx=coordinate(data,i+6,origin[0],x[0],y[0],z[0]),by=coordinate(data,i+6,origin[1],x[1],y[1],z[1]),bz=coordinate(data,i+6,origin[2],x[2],y[2],z[2]);
                double cx=coordinate(data,i+12,origin[0],x[0],y[0],z[0]),cy=coordinate(data,i+12,origin[1],x[1],y[1],z[1]),cz=coordinate(data,i+12,origin[2],x[2],y[2],z[2]);
                double crossX=(by-ay)*(cz-az)-(bz-az)*(cy-ay),crossY=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),crossZ=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
                if(crossX*crossX+crossY*crossY+crossZ*crossZ<1e-18)continue;
                TextureAtlasSprite part=data[i+5]==0?upper:lower;
                emit(buffer,part,data,i,ax,ay,az,light,normalX,normalY,normalZ);
                emit(buffer,part,data,i+6,bx,by,bz,light,normalX,normalY,normalZ);
                emit(buffer,part,data,i+12,cx,cy,cz,light,normalX,normalY,normalZ);
                emit(buffer,part,data,i+12,cx,cy,cz,light,normalX,normalY,normalZ);
            }
        }
    }
    private static double coordinate(double[] point,int i,double origin,double x,double y,double z) {
        return origin+point[i]*(x-origin)+point[i+1]*(y-origin)+point[i+2]*(z-origin);
    }
    private static void emit(BufferBuilder buffer,TextureAtlasSprite sprite,double[] data,int i,double x,double y,double z,int light,float nx,float ny,float nz) {
        buffer.pos(x,y,z).color(255,255,255,255).tex(sprite.getInterpolatedU(data[i+3]*16),sprite.getInterpolatedV(data[i+4]*16))
                .lightmap(light>>>16,light&65535).normal(nx,ny,nz).endVertex();
    }
    private static Layout layout(double[][] uv,int first,int end,boolean tiled,boolean mirror,boolean customDoor) {
        Key key=new Key(uv,first,end,tiled,mirror,customDoor);
        Layout found=LAYOUTS.get(key);
        if(found!=null)return found;
        Face[] faces=new Face[end-first];int count=0;
        for(int face=first;face<end;face++) {
            List<double[]> polygon=new ArrayList<>();
            for(int index:TrapdoorGeometry.FACES[face])polygon.add(new double[]{index&1,(index>>1)&1,(index>>2)&1,uv[index][0],face<2?uv[index][2]:uv[index][1]});
            double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
            for(double[] point:polygon){minU=Math.min(minU,point[3]);maxU=Math.max(maxU,point[3]);minV=Math.min(minV,point[4]);maxV=Math.max(maxV,point[4]);}
            int u0=tiled?(int)Math.floor(minU):0,u1=tiled?(int)Math.ceil(maxU)-1:0;
            int v0=tiled?(int)Math.floor(minV):0,v1=tiled?(int)Math.ceil(maxV)-1:0;
            List<double[]> triangles=new ArrayList<>();
            for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++)for(int half=0;half<(customDoor?2:1);half++) {
                double low=v+(customDoor?half*.5:0),high=v+(customDoor?(half+1)*.5:1);
                List<double[]> clipped=clip(clip(clip(clip(polygon,3,u,true),3,u+1,false),4,low,true),4,high,false);
                for(int j=1;j+1<clipped.size();j++)for(double[] point:new double[][]{clipped.get(0),clipped.get(j),clipped.get(j+1)}) {
                    double pu=point[3]-u,pv=point[4]-v;
                    if(mirror && (u&1)!=0)pu=1-pu;
                    if(customDoor)pv=(pv-half*.5)*2;
                    triangles.add(new double[]{point[0],point[1],point[2],pu,pv,half});
                }
            }
            double[] data=new double[triangles.size()*6];
            for(int i=0;i<triangles.size();i++)System.arraycopy(triangles.get(i),0,data,i*6,6);
            faces[face-first]=new Face(face,data);count+=triangles.size();
        }
        Layout built=new Layout(faces,count);
        if(count<=MAX_POINTS) {
            // Callers may reuse or mutate their UV array after submission.
            key.freeze();LAYOUTS.put(key,built);points+=count;
            while(LAYOUTS.size()>MAX_LAYOUTS || points>MAX_POINTS) {
                Iterator<Layout> it=LAYOUTS.values().iterator();points-=it.next().points;it.remove();
            }
        }
        return built;
    }
    static void clear(){LAYOUTS.clear();points=0;}
    static int layoutCount(){return LAYOUTS.size();}
    static int pointCount(){return points;}
    private static final class Face {
        final int index;final double[] data;
        Face(int index,double[] data){this.index=index;this.data=data;}
    }
    private static final class Layout {
        final Face[] faces;final int points;
        Layout(Face[] faces,int points){this.faces=faces;this.points=points;}
    }
    private static final class Key {
        double[][] uv;final int flags,hash;
        Key(double[][] uv,int first,int end,boolean tiled,boolean mirror,boolean customDoor) {
            this.uv=uv;flags=first|(end<<3)|(tiled?64:0)|(mirror?128:0)|(customDoor?256:0);
            hash=31*Arrays.deepHashCode(uv)+flags;
        }
        void freeze(){double[][] copy=new double[uv.length][];for(int i=0;i<uv.length;i++)copy[i]=uv[i].clone();uv=copy;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object raw){if(!(raw instanceof Key))return false;Key other=(Key)raw;return flags==other.flags && Arrays.deepEquals(uv,other.uv);}
    }
    private static List<double[]> clip(List<double[]> polygon,int axis,double edge,boolean greater) {
        List<double[]> out=new ArrayList<>();
        for(int j=0;j<polygon.size();j++) {
            double[] before=polygon.get((j+polygon.size()-1)%polygon.size()),after=polygon.get(j);
            boolean a=greater?before[axis]>=edge:before[axis]<=edge,b=greater?after[axis]>=edge:after[axis]<=edge;
            if(a!=b){double t=(edge-before[axis])/(after[axis]-before[axis]);double[] point=new double[5];for(int k=0;k<5;k++)point[k]=before[k]+t*(after[k]-before[k]);point[axis]=edge;out.add(point);}
            if(b)out.add(after);
        }
        return out;
    }
}
