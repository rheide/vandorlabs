package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Reuses porthole outlines and bounded atlas meshes; no world or tile references are retained. */
public final class SegmentedDoorPanels {
    private static final int MAX_BYTES=4*1024*1024;
    private static final Map<Key,StaticSurfaceMesh> CACHE=new LinkedHashMap<>(128,.75F,true);
    private static int retained;
    private static final SegmentedPanelMesh.Surface[] MATERIALS=new SegmentedPanelMesh.Surface[ScreenHousingTextures.BUILTIN_COUNT+1];
    private static final int MATERIAL_BASE=16+MATERIALS.length*8;
    private static int materialBytes=MATERIAL_BASE;
    private static final class Key {
        final SegmentedDoorSettings settings;final int texture,column,row,width,height,pass,wc,wr,ww,wh,nativeKind,components,hash;
        Key(SegmentedDoorTile t,int row,int pass){this(t,row,pass,0);}
        Key(SegmentedDoorTile t,int row,int pass,int nativeKind){this(t.panel(row).settings,t.panel(row).effectiveTexture(),t.column,row,t.columns,t.height,pass,SegmentedDoorWindows.layout(t.panel(row)),nativeKind,3);}
        Key(SegmentedDoorSettings settings,int texture,int column,int row,int width,int height,int pass,SegmentedDoorWindows.Layout window,int nativeKind,int components){this.nativeKind=nativeKind;this.settings=settings;this.texture=texture;this.column=column;this.row=row;this.width=width;this.height=height;this.pass=pass;this.components=components;wc=window.column;wr=window.row;ww=window.width;wh=window.height;hash=Objects.hash(settings,texture,column,row,width,height,pass,wc,wr,ww,wh,nativeKind,components);}
        public int hashCode(){return hash;}
        public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return settings.equals(k.settings)&&texture==k.texture&&column==k.column&&row==k.row&&width==k.width&&height==k.height&&pass==k.pass&&wc==k.wc&&wr==k.wr&&ww==k.ww&&wh==k.wh&&nativeKind==k.nativeKind&&components==k.components;}
    }
    /** World-free row keys, prepared once; mesh ownership remains in the byte-bounded shared cache. */
    public static final class Prepared {
        private final Key solid,body,pane;private final int alpha;
        private Prepared(SegmentedDoorSettings settings,int texture,int column,int row,int width,int height,SegmentedDoorWindows.Layout window){boolean translucent=SegmentedArtwork.translucent(texture);solid=new Key(settings,texture,column,row,width,height,0,window,0,3);body=translucent?new Key(settings,texture,column,row,width,height,1,window,0,1):null;pane=new Key(settings,texture,column,row,width,height,1,window,0,translucent?2:3);alpha=Math.round(255*GlassMaterials.opacity(settings.glass.material,settings.glass.tint));}
        public int retainedBytes(){return 512;}
        public void emit(int pass,BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light){if(pass==0)mesh(solid).drawSection(buffer,x,y,z,pose,rotation,light,255);else{if(body!=null)mesh(body).drawSection(buffer,x,y,z,pose,rotation,light,255);mesh(pane).drawSection(buffer,x,y,z,pose,rotation,light,alpha);}}
    }
    public static Prepared prepare(SegmentedDoorSettings settings,int texture,int column,int row,int width,int height,SegmentedDoorWindows.Layout window){return new Prepared(settings,texture,column,row,width,height,window);}
    public static void clear(){StaticSurfaceMesh.clearTreads();CACHE.clear();Arrays.fill(MATERIALS,null);retained=0;materialBytes=MATERIAL_BASE;}
    public static int retainedBytes(){return retained+materialBytes+StaticSurfaceMesh.treadBytes();}
    public static void emit(SegmentedDoorTile tile,int row,int pass,BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light){
        Key key=new Key(tile,row,pass);StaticSurfaceMesh mesh=mesh(key);
        int alpha=pass==1?Math.round(255*GlassMaterials.opacity(key.settings.glass.material,key.settings.glass.tint)):255;mesh.drawSection(buffer,x,y,z,pose,rotation,light,alpha);
    }
    public static void emitNative(SegmentedDoorTile tile,int row,boolean endpoint,BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light){mesh(new Key(tile,row,0,endpoint?2:1)).drawSection(buffer,x,y,z,pose,rotation,light,255);}
    private static StaticSurfaceMesh mesh(Key key){StaticSurfaceMesh mesh=CACHE.get(key);if(mesh==null){mesh=build(key);int bytes=mesh.vertexCount()*32+128;if(bytes+materialBytes<=MAX_BYTES){trim(bytes);CACHE.put(key,mesh);retained+=bytes;}}return mesh;}
    private static void trim(int bytes){while(!CACHE.isEmpty()&&retained+materialBytes+bytes>MAX_BYTES){Iterator<StaticSurfaceMesh> it=CACHE.values().iterator();retained-=it.next().vertexCount()*32+128;it.remove();}}
    public static double aspect(int choice){return surface(choice,null).aspect;}
    public static SegmentedPanelMesh.Surface bodySurface(int choice){return surface(choice,null);}
    private static TextureAtlasSprite sprite(int choice,boolean upper){return CustomBlockMaterials.isCustom(choice)?CustomBlockTextures.sprite(choice,upper):Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(choice<0?"vandorlabs:blocks/segmented/segmented_panel":ScreenHousingTextures.fullTexture(choice));}
    private static com.vandorlabs.segmented.SegmentedPanelMesh.Texture texture(final TextureAtlasSprite sprite){return new com.vandorlabs.segmented.SegmentedPanelMesh.Texture(){public float u(double pixel){return sprite.getInterpolatedU(pixel);}public float v(double pixel){return sprite.getInterpolatedV(pixel);}};}
    private static com.vandorlabs.segmented.SegmentedPanelMesh.Surface surface(int choice,TextureAtlasSprite override){int index=choice+1;boolean cached=override==null&&index>=0&&index<MATERIALS.length;if(cached&&MATERIALS[index]!=null)return MATERIALS[index];TextureAtlasSprite image=override!=null?override:sprite(choice,false);boolean door=override==null&&CustomBlockMaterials.isCustom(choice)&&CustomBlockTextures.isDoor(choice);SegmentedPanelMesh.Surface result=new SegmentedPanelMesh.Surface(texture(image),door?texture(sprite(choice,true)):texture(image),door?.5:UnifiedTextureSprites.aspect(image),door);if(cached){MATERIALS[index]=result;materialBytes+=128;trim(0);}return result;}
    private static StaticSurfaceMesh build(Key k){
        final StaticSurfaceMesh.Capture out=StaticSurfaceMesh.capture();SegmentedDoorSettings s=k.settings;boolean opaque=GlassMaterials.opaque(s.glass.material,s.glass.tint),glass=s.window&&(k.pass==1&&!opaque||k.pass==0&&opaque);
        if(k.nativeKind!=0){SegmentedNativePanelMesh.build(k.row,k.height,k.column,k.nativeKind==2,surface(k.texture,null),texture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side")),(x,y,z,u,v,nx,ny,nz)->out.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex());return out.finish();}
        if(SegmentedArtwork.translucent(k.texture)){
            TextureAtlasSprite paneSprite=s.glass.material<0?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(-1,s.glass.shade,1,s.glass.tint)):null;
            SegmentedPanelMesh.spiral(s.layout,k.column,k.row,k.width,k.height,k.pass,k.components,s.glass.layout,opaque,surface(k.texture,null),surface(s.glass.material,paneSprite),texture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side")),(x,y,z,u,v,nx,ny,nz)->out.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex());return out.finish();
        }
        PortholeHex.Slice slice=null;if(s.window){PortholeHex outline=PortholeGeometryCache.outline(k.ww,k.wh,s.shape,0);slice=PortholeGeometryCache.slice(k.ww,k.wh,s.shape,0,k.wc,k.wr,outline);}
        TextureAtlasSprite nativeSprite=glass&&s.glass.material<0?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(-1,s.glass.shade,1,s.glass.tint)):null;
        com.vandorlabs.segmented.SegmentedPanelMesh.buildLayered(s.window,s.shape,s.layout,k.column,k.row,k.width,k.height,k.wc,k.wr,k.ww,k.wh,s.glass.layout,opaque,k.pass,k.pass==0||SegmentedArtwork.translucent(k.texture)?surface(k.texture,null):null,glass?surface(s.glass.material,nativeSprite):null,k.pass==0?texture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side")):null,(x,y,z,u,v,nx,ny,nz)->out.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex(),slice,SegmentedArtwork.translucent(k.texture),k.components);
        return out.finish();
    }
    private SegmentedDoorPanels(){}
}
