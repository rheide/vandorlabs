package com.vandorlabs.client.constructors;

import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/** Baked-model occupancy and atlas pixels, printed in stable bottom-up Minecraft-pixel order. */
final class BuilderFabricationVoxels {
    private static final int WORLD_GRID=32;
    private static final class Hit {final double x;final int delta,surface;Hit(double x,int delta,int surface){this.x=x;this.delta=delta;this.surface=surface;}}
    private static final int[][] POSITIVE={{0,0},{1,0},{1,1},{0,1}},NEGATIVE={{0,1},{1,1},{1,0},{0,0}};
    private static final class Vertices {int[] data=new int[4096];int size;void add(double[] p,int color){if(size+4>data.length)data=Arrays.copyOf(data,data.length*2);for(int a=0;a<3;a++)data[size++]=Float.floatToRawIntBits((float)p[a]);data[size++]=color;}int[] finish(){return Arrays.copyOf(data,size);}}
    private static final int[] MATTER={0x51c7d6,0xb23d48,0xa8b0b8,0x38424c};
    private static final LinkedList<Model> CACHE=new LinkedList<>();
    private static long retained;
    private static final long BUDGET=12L*1024*1024;
    static void clear(){CACHE.clear();retained=0;}
    static Model get(ItemStack stack,boolean flat){BuilderItemGeometry source=BuilderItemGeometry.get(stack);for(Iterator<Model> it=CACHE.iterator();it.hasNext();){Model old=it.next();if(old.source==source&&old.flat==flat){it.remove();CACHE.addFirst(old);return old;}}Model model=new Model(source,flat);while(!CACHE.isEmpty()&&(CACHE.size()>=16||retained+model.bytes>BUDGET)){Model old=CACHE.removeLast();retained-=old.memory();}CACHE.addFirst(model);retained+=model.memory();return model;}
    static final class Model {
        final BuilderItemGeometry source;final boolean flat;final int resolution,nx,ny,nz;final double originX,originZ,minX,minY,minZ,maxX,maxY,maxZ;final boolean[] solid;final int[] paint,rank,order,material;final long bytes;int lastCount=-1;int[] vertices;
        long memory(){return bytes+(vertices==null?0:vertices.length*4L);}
        Model(BuilderItemGeometry source,boolean flat){
            this.source=source;this.flat=flat;
            boolean module=source.stack.getItem()==com.vandorlabs.items.ModItems.NAVIGATION_DATA_MODULE||source.stack.getItem()==com.vandorlabs.items.ModItems.BATTERY_MODULE||source.stack.getItem()==com.vandorlabs.items.ModItems.BLANK_MODULE||source.stack.getItem()==com.vandorlabs.items.ModItems.AUTOPILOT_MODULE;
            boolean flatten=flat&&!module;
            List<BuilderItemGeometry.Triangle> triangles=new ArrayList<>();BuilderItemGeometry.Bounds bounds=new BuilderItemGeometry.Bounds();
            for(BuilderItemGeometry.Triangle triangle:source.triangles){double[] p=triangle.p.clone();for(int v=0;v<3;v++){double y=p[v*3+1],z=p[v*3+2];if(flatten){p[v*3+1]=-z;p[v*3+2]=y;}bounds.add(p[v*3],p[v*3+1],p[v*3+2]);}triangles.add(new BuilderItemGeometry.Triangle(p,triangle.uv,triangle.sprite,triangle.tint,triangle.surface));}
            double scale=flatten?Math.min(1,source.fixedScale):1,limit=flat?1:2;
            if(bounds.valid())scale=Math.min(scale,Math.min(limit/Math.max(.0001,bounds.maxX-bounds.minX),Math.min(limit/Math.max(.0001,bounds.maxY-bounds.minY),limit/Math.max(.0001,bounds.maxZ-bounds.minZ))));
            double width=bounds.valid()?(bounds.maxX-bounds.minX)*scale:0,height=bounds.valid()?(bounds.maxY-bounds.minY)*scale:0,depth=bounds.valid()?(bounds.maxZ-bounds.minZ)*scale:0;
            minX=-width/2;maxX=width/2;minY=0;maxY=height;minZ=-depth/2;maxZ=depth/2;
            resolution=flat?64:WORLD_GRID;originX=Math.floor(minX*resolution)/resolution;originZ=Math.floor(minZ*resolution)/resolution;
            nx=Math.max(1,(int)Math.ceil(maxX*resolution)-(int)Math.floor(minX*resolution));ny=Math.max(1,(int)Math.ceil(maxY*resolution));nz=Math.max(1,(int)Math.ceil(maxZ*resolution)-(int)Math.floor(minZ*resolution));
            int volume=nx*ny*nz;solid=new boolean[volume];paint=new int[volume*6];rank=new int[volume];material=flat?null:new int[volume];
            if(bounds.valid()){
                for(BuilderItemGeometry.Triangle triangle:triangles)for(int v=0;v<3;v++){triangle.p[v*3]=(triangle.p[v*3]-bounds.center(0))*scale-originX;triangle.p[v*3+1]=(triangle.p[v*3+1]-bounds.minY)*scale;triangle.p[v*3+2]=(triangle.p[v*3+2]-bounds.center(2))*scale-originZ;}
                // Fill the actual closed mesh, using alpha-tested ray intersections rather than its bounding cube.
                List<Hit> intersections=new ArrayList<>();double[] bary=new double[3];Set<Integer> surfaces=new HashSet<>();
                for(int y=0;y<ny;y++)for(int z=0;z<nz;z++){
                    intersections.clear();double py=(y+.5)/resolution,pz=(z+.5)/resolution;
                    for(BuilderItemGeometry.Triangle triangle:triangles)if(project(triangle.p,0,py,pz,bary)){
                        int pixel=triangle.pixel(bary[0],bary[1],bary[2]);if((pixel>>>24)<128)continue;
                        double normal=(triangle.p[4]-triangle.p[1])*(triangle.p[8]-triangle.p[2])-(triangle.p[5]-triangle.p[2])*(triangle.p[7]-triangle.p[1]);
                        intersections.add(new Hit(triangle.p[0]*bary[0]+triangle.p[3]*bary[1]+triangle.p[6]*bary[2],normal<0?1:-1,triangle.surface));
                    }
                    Collections.sort(intersections,(a,b)->Double.compare(a.x,b.x));int winding=0,next=0;
                    for(int x=0;x<nx;x++){double px=(x+.5)/resolution;while(next<intersections.size()&&intersections.get(next).x<=px){double plane=intersections.get(next).x;surfaces.clear();while(next<intersections.size()&&Math.abs(intersections.get(next).x-plane)<1e-6){Hit hit=intersections.get(next++);if(surfaces.add(hit.surface))winding+=hit.delta;}}if(winding!=0)solid[index(x,y,z)]=true;}
                }

                // Rasterize original surface pixels as well, retaining thin/open item-model surfaces.
                for(BuilderItemGeometry.Triangle triangle:triangles){double[] p=triangle.p;double[] normal={ (p[4]-p[1])*(p[8]-p[2])-(p[5]-p[2])*(p[7]-p[1]),(p[5]-p[2])*(p[6]-p[0])-(p[3]-p[0])*(p[8]-p[2]),(p[3]-p[0])*(p[7]-p[1])-(p[4]-p[1])*(p[6]-p[0])};int axis=0;for(int a=1;a<3;a++)if(Math.abs(normal[a])>Math.abs(normal[axis]))axis=a;if(Math.abs(normal[axis])<1e-12)continue;int ua=(axis+1)%3,va=(axis+2)%3;double minU=Math.min(p[ua],Math.min(p[3+ua],p[6+ua])),maxU=Math.max(p[ua],Math.max(p[3+ua],p[6+ua])),minV=Math.min(p[va],Math.min(p[3+va],p[6+va])),maxV=Math.max(p[va],Math.max(p[3+va],p[6+va]));
                    int umax=size(ua),vmax=size(va);for(int u=Math.max(0,(int)Math.floor(minU*resolution));u<Math.min(umax,(int)Math.ceil(maxU*resolution));u++)for(int v=Math.max(0,(int)Math.floor(minV*resolution));v<Math.min(vmax,(int)Math.ceil(maxV*resolution));v++)if(project(p,axis,(u+.5)/resolution,(v+.5)/resolution,bary)){
                        int pixel=triangle.pixel(bary[0],bary[1],bary[2]);if((pixel>>>24)<128)continue;double at=p[axis]*bary[0]+p[3+axis]*bary[1]+p[6+axis]*bary[2];int cell=(int)Math.floor(at*resolution-(normal[axis]>0?1e-5:-1e-5));int x=axis==0?cell:ua==0?u:v,y=axis==1?cell:ua==1?u:v,z=axis==2?cell:ua==2?u:v;if(x<0||x>=nx||y<0||y>=ny||z<0||z>=nz)continue;int index=index(x,y,z);solid[index]=true;for(int a=0;a<3;a++)if(Math.abs(normal[a])>Math.abs(normal[axis])*.1)paint[index*6+a*2+(normal[a]>0?1:0)]=pixel;
                    }
                }
            }
            if(!flat){
                // Newly exposed Gantry cut faces inherit the nearest real surface material.
                // Programmable Matter's seeded interior colors belong only to Press items.
                int[] queue=new int[volume];int head=0,tail=0;
                for(int cell=0;cell<volume;cell++)if(solid[cell])for(int side=0;side<6;side++)if(paint[cell*6+side]!=0){material[cell]=paint[cell*6+side];queue[tail++]=cell;break;}
                while(head<tail){int cell=queue[head++],x=cell%nx,z=cell/nx%nz,y=cell/(nx*nz);for(int axis=0;axis<3;axis++)for(int sign=-1;sign<=1;sign+=2){int px=x+(axis==0?sign:0),py=y+(axis==1?sign:0),pz=z+(axis==2?sign:0);if(px<0||px>=nx||py<0||py>=ny||pz<0||pz>=nz)continue;int next=index(px,py,pz);if(solid[next]&&material[next]==0){material[next]=material[cell];queue[tail++]=next;}}}
            }
            int count=0;for(boolean occupied:solid)if(occupied)count++;order=new int[count];int at=0;for(int y=0;y<ny;y++)for(int x=0;x<nx;x++)for(int z=0;z<nz;z++)if(solid[index(x,y,z)]){int index=index(x,y,z);order[at]=index;rank[index]=++at;}bytes=128L+volume*(flat?29L:33L)+order.length*4L+source.triangles.size()*256L;
        }
        int size(int axis){return axis==0?nx:axis==1?ny:nz;}
        int index(int x,int y,int z){return (y*nz+z)*nx+x;}
        int count(double progress){return (int)Math.floor(Math.max(0,Math.min(1,progress))*order.length);}
        double height(double progress){int count=count(progress);return count==0?0:(order[count-1]/(nx*nz)+1)/(double)resolution;}
        boolean occupied(int x,int y,int z,int count){return x>=0&&x<nx&&y>=0&&y<ny&&z>=0&&z<nz&&rank[index(x,y,z)]>0&&rank[index(x,y,z)]<=count;}
        void emit(BufferBuilder buffer,double progress){int count=count(progress);int[] data=vertices;if(lastCount!=count||data==null){data=build(count);if(vertices!=null){retained-=vertices.length*4L;vertices=null;}while(CACHE.size()>1&&retained+data.length*4L>BUDGET){Model old=CACHE.removeLast();retained-=old.memory();}if(data.length*4L+bytes<=BUDGET){vertices=data;lastCount=count;retained+=data.length*4L;}}
            TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/core_flat");double u=white.getInterpolatedU(8),v=white.getInterpolatedV(8);for(int at=0;at<data.length;at+=4){int color=data[at+3];buffer.pos(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2])).color(color>>16&255,color>>8&255,color&255,255).tex(u,v).lightmap(240,240).endVertex();}}
        private int[] build(int count){Vertices out=new Vertices();double[] p=new double[3];int[] base=new int[3];for(int i=0;i<count;i++){int cell=order[i],x=cell%nx,z=cell/nx%nz,y=cell/(nx*nz);base[0]=x;base[1]=y;base[2]=z;for(int axis=0;axis<3;axis++)for(int sign=-1;sign<=1;sign+=2){if(occupied(x+(axis==0?sign:0),y+(axis==1?sign:0),z+(axis==2?sign:0),count))continue;int side=axis*2+(sign>0?1:0),color=paint[cell*6+side];if(color==0)color=flat?MATTER[(cell*1103515245+12345>>>16)&3]:(material[cell]!=0?material[cell]:0xffa8b0b8);double shade=axis==1?(sign>0?1:.60):axis==0?.78:.9;int rgb=(int)((color>>16&255)*shade)<<16|(int)((color>>8&255)*shade)<<8|(int)((color&255)*shade),ua=(axis+1)%3,va=(axis+2)%3;int[][] corners=sign>0?POSITIVE:NEGATIVE;for(int[] corner:corners){p[axis]=(base[axis]+(sign>0?1:0))/(double)resolution;p[ua]=(base[ua]+corner[0])/(double)resolution;p[va]=(base[va]+corner[1])/(double)resolution;p[0]+=originX;p[2]+=originZ;out.add(p,rgb);}}}return out.finish();}

    }
    /** Barycentrics in the plane perpendicular to axis; the two supplied coordinates follow cyclic axes. */
    private static boolean project(double[] p,int axis,double u,double v,double[] bary){int ua=(axis+1)%3,va=(axis+2)%3;double ax=p[ua],ay=p[va],bx=p[3+ua],by=p[3+va],cx=p[6+ua],cy=p[6+va],den=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy);if(Math.abs(den)<1e-12)return false;bary[0]=((by-cy)*(u-cx)+(cx-bx)*(v-cy))/den;bary[1]=((cy-ay)*(u-cx)+(ax-cx)*(v-cy))/den;bary[2]=1-bary[0]-bary[1];return bary[0]>=-1e-7&&bary[1]>=-1e-7&&bary[2]>=-1e-7;}
    private BuilderFabricationVoxels(){}
}
