package com.vandorlabs.client;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Client-only surface thumbnail: no tile factories, world construction or saved-data changes. */
final class VehiclePreviewSampler {
    static final int VOXELS_PER_BLOCK=8,MAX_VOXELS=131072,MAX_AXIS=512,MAX_FACES=65536;
    private static final int POINT_BUDGET=2000000,GEOMETRY_BUDGET=4*1024*1024;
    static final class Sample {
        final int[] colors;final int width,height,depth;final float centerX,centerY,centerZ,span;
        Sample(int[] colors,int width,int height,int depth){this.colors=colors;this.width=width;this.height=height;this.depth=depth;int minX=width,minY=height,minZ=depth,maxX=-1,maxY=-1,maxZ=-1;for(int y=0;y<height;y++)for(int z=0;z<depth;z++)for(int x=0;x<width;x++)if(occupied(x,y,z)){minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}centerX=maxX<0?width/2F:(minX+maxX+1)/2F;centerY=maxY<0?height/2F:(minY+maxY+1)/2F;centerZ=maxZ<0?depth/2F:(minZ+maxZ+1)/2F;span=maxX<0?1:Math.max(1,Math.max(maxY-minY+1,(float)Math.hypot(maxX-minX+1,maxZ-minZ+1))*1.5F);}
        boolean occupied(int x,int y,int z){return x>=0&&x<width&&y>=0&&y<height&&z>=0&&z<depth&&colors[(y*depth+z)*width+x]!=0;}
        int color(int x,int y,int z){return colors[(y*depth+z)*width+x]&0xffffff;}
        int bytes(){return 64+colors.length*4;}
        boolean excessiveFaces(){int faces=0;for(int y=0;y<height;y++)for(int z=0;z<depth;z++)for(int x=0;x<width;x++)if(occupied(x,y,z))for(EnumFacing side:EnumFacing.VALUES)if(!occupied(x+side.getFrontOffsetX(),y+side.getFrontOffsetY(),z+side.getFrontOffsetZ())&&++faces>MAX_FACES)return true;return false;}
        Sample reduced(){int w=(width+1)/2,h=(height+1)/2,d=(depth+1)/2;int[] next=new int[w*h*d];for(int y=0;y<height;y++)for(int z=0;z<depth;z++)for(int x=0;x<width;x++){int color=colors[(y*depth+z)*width+x];if(color!=0)next[((y/2)*d+z/2)*w+x/2]=color;}return new Sample(next,w,h,d);}
    }
    private static final class Face {
        final float[] x=new float[4],y=new float[4],z=new float[4],u=new float[4],v=new float[4],red=new float[4],green=new float[4],blue=new float[4];final int[] pixels;final int width,height,tint;final double edgeU,edgeV;
        Face(BakedQuad quad,int tint){this.tint=tint;int[] raw=quad.getVertexData();int stride=raw.length/4;TextureAtlasSprite sprite=quad.getSprite();width=sprite.getIconWidth();height=sprite.getIconHeight();int[][] levels=sprite.getFrameCount()==0?null:sprite.getFrameTextureData(0);pixels=levels==null||levels.length==0?null:levels[0];float du=sprite.getMaxU()-sprite.getMinU(),dv=sprite.getMaxV()-sprite.getMinV();for(int i=0;i<4;i++){int at=i*stride;int vertex=raw[at+3];red[i]=(vertex&255)/255F;green[i]=(vertex>>>8&255)/255F;blue[i]=(vertex>>>16&255)/255F;x[i]=Float.intBitsToFloat(raw[at]);y[i]=Float.intBitsToFloat(raw[at+1]);z[i]=Float.intBitsToFloat(raw[at+2]);u[i]=(Float.intBitsToFloat(raw[at+4])-sprite.getMinU())/du;v[i]=(Float.intBitsToFloat(raw[at+5])-sprite.getMinV())/dv;}edgeU=Math.max(edge(0,1),edge(3,2));edgeV=Math.max(edge(0,3),edge(1,2));}
        float interpolate(float[] values,double a,double b){return (float)((values[0]*(1-a)+values[1]*a)*(1-b)+(values[3]*(1-a)+values[2]*a)*b);}
        double edge(int a,int b){return Math.sqrt(Math.pow(x[a]-x[b],2)+Math.pow(y[a]-y[b],2)+Math.pow(z[a]-z[b],2));}
        int color(double a,double b){int color=0xffaaaaaa;if(pixels!=null&&width>0&&height>0){int px=Math.max(0,Math.min(width-1,(int)(interpolate(u,a,b)*width))),py=Math.max(0,Math.min(height-1,(int)(interpolate(v,a,b)*height)));int at=py*width+px;if(at<pixels.length)color=pixels[at];}if((color>>>24)<128)return 0;return 0xff000000|((int)((color>>16&255)*(tint>>16&255)/255F*interpolate(red,a,b)))<<16|((int)((color>>8&255)*(tint>>8&255)/255F*interpolate(green,a,b)))<<8|(int)((color&255)*(tint&255)/255F*interpolate(blue,a,b));}
    }
    private static final class Geometry {final List<Face> faces;final AxisAlignedBB bounds;final int fallback,bytes;final boolean solid;Geometry(List<Face> faces,AxisAlignedBB bounds,int fallback,boolean solid){this.faces=faces;this.bounds=bounds;this.fallback=fallback;this.solid=solid;bytes=128+faces.size()*256;}}
    private static final LinkedHashMap<IBlockState,Geometry> GEOMETRY=new LinkedHashMap<>(64,.75F,true);private static int geometryBytes;
    static void clear(){GEOMETRY.clear();geometryBytes=0;}
    static Sample read(NBTTagCompound tag){
        if(!tag.hasKey("VehicleDesign",7))return null;
        try{
            byte[] raw=com.vandorlabs.copier.core.BlueprintCompression.decode(tag.getInteger("VehicleDesignCodec"),tag.getByteArray("VehicleDesign"));VehicleStructure structure=VehicleStructure.decode(raw);
            double sx=Math.max(1,structure.bounds.maxX)+2,sy=Math.max(1,structure.bounds.maxY)+2,sz=Math.max(1,structure.bounds.maxZ)+2;
            double density=Math.min(VOXELS_PER_BLOCK,Math.min(MAX_AXIS/Math.max(sx,Math.max(sy,sz)),Math.cbrt(MAX_VOXELS/(sx*sy*sz))));int w,h,d;
            do{w=Math.max(1,(int)Math.ceil(sx*density));h=Math.max(1,(int)Math.ceil(sy*density));d=Math.max(1,(int)Math.ceil(sz*density));if((long)w*h*d>MAX_VOXELS)density*=.97;else break;}while(true);
            int[] colors=new int[w*h*d];int cellBudget=Math.max(1,POINT_BUDGET/Math.max(1,structure.cells.size()));
            for(VehicleStructure.Cell cell:structure.cells){Geometry model=geometry(cell.state);if(model.solid)box(colors,w,h,d,cell.pos,model.bounds,density,model.fallback,true);if(model.faces.isEmpty()){box(colors,w,h,d,cell.pos,model.bounds,density,model.fallback,false);continue;}int stride=Math.max(1,(model.faces.size()+cellBudget-1)/cellBudget),count=(model.faces.size()+stride-1)/stride,limit=Math.max(0,(int)Math.sqrt(Math.max(1,cellBudget/count))-1);boolean wrote=false;
                for(int f=0;f<model.faces.size();f+=stride){Face face=model.faces.get(f);int across=Math.max(0,Math.min(limit,(int)Math.ceil(face.edgeU*density*1.5))),down=Math.max(0,Math.min(limit,(int)Math.ceil(face.edgeV*density*1.5)));
                    for(int b=0;b<=down;b++)for(int a=0;a<=across;a++){double u=across==0?.5:a/(double)across,v=down==0?.5:b/(double)down;int color=face.color(u,v);if(color==0)continue;double x=face.interpolate(face.x,u,v),y=face.interpolate(face.y,u,v),z=face.interpolate(face.z,u,v);put(colors,w,h,d,(cell.pos.getX()+1+inset(x))*density,(cell.pos.getY()+1+inset(y))*density,(cell.pos.getZ()+1+inset(z))*density,color);wrote=true;}
                }
                if(!wrote)box(colors,w,h,d,cell.pos,model.bounds,density,model.fallback,false);
            }
            Sample sample=new Sample(colors,w,h,d);while(sample.excessiveFaces()&&(sample.width>1||sample.height>1||sample.depth>1))sample=sample.reduced();return sample;
        }catch(java.io.IOException|RuntimeException invalid){return null;}
    }
    private static double inset(double value){return value==0?1e-6:value==1?1-1e-6:value;}
    private static void put(int[] colors,int w,int h,int d,double x,double y,double z,int color){int xx=(int)Math.floor(x),yy=(int)Math.floor(y),zz=(int)Math.floor(z);if(xx>=0&&xx<w&&yy>=0&&yy<h&&zz>=0&&zz<d)colors[(yy*d+zz)*w+xx]=color;}
    private static void box(int[] colors,int w,int h,int d,BlockPos at,AxisAlignedBB bounds,double density,int color,boolean solid){int x0=Math.max(0,(int)Math.floor((at.getX()+1+bounds.minX)*density)),y0=Math.max(0,(int)Math.floor((at.getY()+1+bounds.minY)*density)),z0=Math.max(0,(int)Math.floor((at.getZ()+1+bounds.minZ)*density)),x1=Math.min(w,(int)Math.ceil((at.getX()+1+bounds.maxX)*density)),y1=Math.min(h,(int)Math.ceil((at.getY()+1+bounds.maxY)*density)),z1=Math.min(d,(int)Math.ceil((at.getZ()+1+bounds.maxZ)*density));for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++)if(solid||x==x0||x==x1-1||y==y0||y==y1-1||z==z0||z==z1-1)colors[(y*d+z)*w+x]=0xff000000|color;}
    private static Geometry geometry(IBlockState state){Geometry cached=GEOMETRY.get(state);if(cached!=null)return cached;List<Face> faces=new ArrayList<>();AxisAlignedBB bounds=new AxisAlignedBB(0,0,0,1,1,1);int fallback=0xaaaaaa;
        try{fallback=state.getMapColor(null,BlockPos.ORIGIN).colorValue;}catch(RuntimeException ignored){}
        try{bounds=state.getBoundingBox(null,BlockPos.ORIGIN);}catch(RuntimeException ignored){}
        try{Minecraft mc=Minecraft.getMinecraft();IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);outer:for(int side=-1;side<6;side++)for(BakedQuad quad:model.getQuads(state,side<0?null:EnumFacing.VALUES[side],0)){if(faces.size()>=64)break outer;int tint=quad.hasTintIndex()?mc.getBlockColors().colorMultiplier(state,null,null,quad.getTintIndex()):0xffffff;if(tint<0)tint=0xffffff;faces.add(new Face(quad,tint));}}catch(RuntimeException unsupported){faces.clear();}
        cached=new Geometry(Collections.unmodifiableList(faces),bounds==null?new AxisAlignedBB(0,0,0,1,1,1):bounds,fallback,state.isFullCube());while(!GEOMETRY.isEmpty()&&(geometryBytes+cached.bytes>GEOMETRY_BUDGET||GEOMETRY.size()>=512)){Iterator<Geometry> it=GEOMETRY.values().iterator();geometryBytes-=it.next().bytes;it.remove();}GEOMETRY.put(state,cached);geometryBytes+=cached.bytes;return cached;
    }
    private VehiclePreviewSampler(){}
}
