package com.vandorlabs.client;

import com.vandorlabs.garage.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Reuses porthole outlines and bounded atlas meshes; no world or tile references are retained. */
public final class GarageDoorPanels {
    private static final int MAX_BYTES=4*1024*1024;
    private static final Map<Key,StaticSurfaceMesh> CACHE=new LinkedHashMap<>(128,.75F,true);
    private static int retained;
    private static final class Key {
        final GarageDoorSettings settings;final int texture,column,row,width,height,pass;
        Key(GarageDoorTile t,int row,int pass){settings=t.panel(row).settings;texture=t.texture;column=t.column;this.row=row;width=t.columns;height=t.height;this.pass=pass;}
        public int hashCode(){return Objects.hash(settings,texture,column,row,width,height,pass);}
        public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return settings.equals(k.settings)&&texture==k.texture&&column==k.column&&row==k.row&&width==k.width&&height==k.height&&pass==k.pass;}
    }
    public static void clear(){CACHE.clear();retained=0;}
    public static int retainedBytes(){return retained;}
    public static void emit(GarageDoorTile tile,int row,int pass,BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light){
        Key key=new Key(tile,row,pass);StaticSurfaceMesh mesh=CACHE.get(key);if(mesh==null){mesh=build(key);int bytes=mesh.vertexCount()*32+128;if(bytes<=MAX_BYTES){while(!CACHE.isEmpty()&&retained+bytes>MAX_BYTES){Iterator<StaticSurfaceMesh> it=CACHE.values().iterator();retained-=it.next().vertexCount()*32+128;it.remove();}CACHE.put(key,mesh);retained+=bytes;}}
        int alpha=pass==1?Math.round(255*GlassMaterials.opacity(key.settings.glass.material,key.settings.glass.tint)):255;mesh.drawSection(buffer,x,y,z,pose,rotation,light,alpha);
    }
    private static TextureAtlasSprite sprite(int choice,boolean upper){return CustomBlockMaterials.isCustom(choice)?CustomBlockTextures.sprite(choice,upper):Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(choice<0?"vandorlabs:blocks/garage/garage_panel":ScreenHousingTextures.fullTexture(choice));}
    private static StaticSurfaceMesh build(Key k){
        StaticSurfaceMesh.Capture out=StaticSurfaceMesh.capture();GarageDoorSettings s=k.settings;boolean solidGlass=GlassMaterials.opaque(s.glass.material,s.glass.tint);boolean glass=s.window&&(k.pass==1&&!solidGlass||k.pass==0&&solidGlass);
        PortholeHex.Slice slice=null;if(s.window){int w=s.join?k.width:1,h=s.join?k.height:1,c=s.join?k.column:0,r=s.join?k.height-1-k.row:0;PortholeHex outline=PortholeGeometryCache.outline(w,h,s.shape,0);slice=PortholeGeometryCache.slice(w,h,s.shape,0,c,r,outline);}
        if(k.pass==0){List<double[]> panels=s.window?slice.frameQuads:Collections.singletonList(new double[]{0,0,16,0,16,16,0,16});for(double[] q:panels){front(out,q,GarageDoorGeometry.HALF_THICKNESS*16,1,k.texture,s.layout,k.column,k.row,k.width,k.height);front(out,q,-GarageDoorGeometry.HALF_THICKNESS*16,-1,k.texture,s.layout,k.column,k.row,k.width,k.height);}edges(out,slice);}
        if(glass){int choice=s.glass.material;TextureAtlasSprite nativeSprite=choice<0?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(-1,s.glass.shade,1,s.glass.tint)):null;for(double[] q:slice.glassQuads)front(out,q,0,1,choice,s.glass.layout,k.column,k.row,k.width,k.height,nativeSprite);}
        return out.finish();
    }
    private static void front(StaticSurfaceMesh.Capture out,double[] q,double depth,int side,int choice,int mode,int column,int row,int width,int height){front(out,q,depth,side,choice,mode,column,row,width,height,null);}
    private static void front(StaticSurfaceMesh.Capture out,double[] q,double depth,int side,int choice,int mode,int column,int row,int width,int height,TextureAtlasSprite override){
        TextureAtlasSprite source=override!=null?override:sprite(choice,false);boolean door=override==null&&CustomBlockMaterials.isCustom(choice)&&CustomBlockTextures.isDoor(choice);double aspect=door?.5:UnifiedTextureSprites.aspect(source);boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(mode,width,height,aspect);List<double[]> polygon=new ArrayList<>();double uMin=Double.POSITIVE_INFINITY,uMax=Double.NEGATIVE_INFINITY,vMin=Double.POSITIVE_INFINITY,vMax=Double.NEGATIVE_INFINITY;
        for(int n=0;n<4;n++){int i=side>0?n:3-n;double x=q[i*2],y=q[i*2+1],u=fit?(column+x/16)/width:com.vandorlabs.render.TextureLayoutMode.tileU(column+x/16,aspect),v=fit?(row+1-y/16)/height:com.vandorlabs.render.TextureLayoutMode.tileV(row+1-y/16,aspect);polygon.add(new double[]{x,y,depth,u,v});uMin=Math.min(uMin,u);uMax=Math.max(uMax,u);vMin=Math.min(vMin,v);vMax=Math.max(vMax,v);}
        int u0=fit?0:(int)Math.floor(uMin+1e-8),u1=fit?0:(int)Math.ceil(uMax-1e-8)-1,v0=fit?0:(int)Math.floor(vMin+1e-8),v1=fit?0:(int)Math.ceil(vMax-1e-8)-1;
        for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++)for(int half=0;half<(door?2:1);half++){
            double low=v+(door?half*.5:0),high=v+(door?(half+1)*.5:1);List<double[]> clipped=TrapdoorSurfaceMesh.clip(TrapdoorSurfaceMesh.clip(TrapdoorSurfaceMesh.clip(TrapdoorSurfaceMesh.clip(polygon,3,u,true),3,u+1,false),4,low,true),4,high,false);TextureAtlasSprite image=door?sprite(choice,half==0):source;
            for(int i=1;i+1<clipped.size();i++)for(double[] p:new double[][]{clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1)})out.pos(p[0],p[1],p[2]).tex(image.getInterpolatedU(Math.max(0,Math.min(1,p[3]-u))*16),image.getInterpolatedV(Math.max(0,Math.min(1,(p[4]-low)/(high-low)))*16)).normal(0,0,side).endVertex();
        }
    }
    private static void edges(StaticSurfaceMesh.Capture out,PortholeHex.Slice slice){
        if(slice!=null)for(double[] edge:slice.hexEdges)edge(out,edge[2],edge[3],edge[0],edge[1]);
        for(int side=0;side<4;side++){int axis=side<2?0:1;double boundary=(side&1)==0?0:16;double[] opening=slice==null?null:slice.edgeOpening(axis,boundary);double lo=opening==null?16:opening[0],hi=opening==null?16:opening[1];outer(out,side,0,lo);if(hi<16)outer(out,side,hi,16);}
    }
    private static void outer(StaticSurfaceMesh.Capture out,int side,double lo,double hi){if(hi-lo<1e-7)return;if(side==0)edge(out,0,hi,0,lo);if(side==1)edge(out,16,lo,16,hi);if(side==2)edge(out,lo,0,hi,0);if(side==3)edge(out,hi,16,lo,16);}
    private static void edge(StaticSurfaceMesh.Capture out,double ax,double ay,double bx,double by){double length=Math.hypot(bx-ax,by-ay);if(length<1e-8)return;TextureAtlasSprite edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");double h=GarageDoorGeometry.HALF_THICKNESS*16;double[][] points={{ax,ay,-h},{bx,by,-h},{bx,by,h},{ax,ay,h}};for(int i=0;i<4;i++)out.pos(points[i][0],points[i][1],points[i][2]).tex(edge.getInterpolatedU(i==1||i==2?16:0),edge.getInterpolatedV(i<2?16:0)).normal((float)((by-ay)/length),(float)((ax-bx)/length),0).endVertex();}
    private GarageDoorPanels(){}
}
