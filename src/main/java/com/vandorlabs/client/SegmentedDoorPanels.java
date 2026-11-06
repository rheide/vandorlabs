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
    private static final class Key {
        final SegmentedDoorSettings settings;final int texture,column,row,width,height,pass,wc,wr,ww,wh;
        Key(SegmentedDoorTile t,int row,int pass){settings=t.panel(row).settings;texture=t.texture;column=t.column;this.row=row;width=t.columns;height=t.height;this.pass=pass;SegmentedDoorWindows.Layout window=SegmentedDoorWindows.layout(t.panel(row));wc=window.column;wr=window.row;ww=window.width;wh=window.height;}
        public int hashCode(){return Objects.hash(settings,texture,column,row,width,height,pass,wc,wr,ww,wh);}
        public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return settings.equals(k.settings)&&texture==k.texture&&column==k.column&&row==k.row&&width==k.width&&height==k.height&&pass==k.pass&&wc==k.wc&&wr==k.wr&&ww==k.ww&&wh==k.wh;}
    }
    public static void clear(){CACHE.clear();retained=0;}
    public static int retainedBytes(){return retained;}
    public static void emit(SegmentedDoorTile tile,int row,int pass,BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light){
        Key key=new Key(tile,row,pass);StaticSurfaceMesh mesh=CACHE.get(key);if(mesh==null){mesh=build(key);int bytes=mesh.vertexCount()*32+128;if(bytes<=MAX_BYTES){while(!CACHE.isEmpty()&&retained+bytes>MAX_BYTES){Iterator<StaticSurfaceMesh> it=CACHE.values().iterator();retained-=it.next().vertexCount()*32+128;it.remove();}CACHE.put(key,mesh);retained+=bytes;}}
        int alpha=pass==1?Math.round(255*GlassMaterials.opacity(key.settings.glass.material,key.settings.glass.tint)):255;mesh.drawSection(buffer,x,y,z,pose,rotation,light,alpha);
    }
    private static TextureAtlasSprite sprite(int choice,boolean upper){return CustomBlockMaterials.isCustom(choice)?CustomBlockTextures.sprite(choice,upper):Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(choice<0?"vandorlabs:blocks/segmented/segmented_panel":ScreenHousingTextures.fullTexture(choice));}
    private static com.vandorlabs.segmented.SegmentedPanelMesh.Texture texture(final TextureAtlasSprite sprite){return new com.vandorlabs.segmented.SegmentedPanelMesh.Texture(){public float u(double pixel){return sprite.getInterpolatedU(pixel);}public float v(double pixel){return sprite.getInterpolatedV(pixel);}};}
    private static com.vandorlabs.segmented.SegmentedPanelMesh.Surface surface(int choice,TextureAtlasSprite override){TextureAtlasSprite image=override!=null?override:sprite(choice,false);boolean door=override==null&&CustomBlockMaterials.isCustom(choice)&&CustomBlockTextures.isDoor(choice);return new com.vandorlabs.segmented.SegmentedPanelMesh.Surface(texture(image),door?texture(sprite(choice,true)):texture(image),door?.5:UnifiedTextureSprites.aspect(image),door);}
    private static StaticSurfaceMesh build(Key k){
        final StaticSurfaceMesh.Capture out=StaticSurfaceMesh.capture();SegmentedDoorSettings s=k.settings;boolean opaque=GlassMaterials.opaque(s.glass.material,s.glass.tint),glass=s.window&&(k.pass==1&&!opaque||k.pass==0&&opaque);
        TextureAtlasSprite nativeSprite=glass&&s.glass.material<0?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(-1,s.glass.shade,1,s.glass.tint)):null;
        com.vandorlabs.segmented.SegmentedPanelMesh.build(s.window,s.shape,s.layout,k.column,k.row,k.width,k.height,k.wc,k.wr,k.ww,k.wh,s.glass.layout,opaque,k.pass,k.pass==0?surface(k.texture,null):null,glass?surface(s.glass.material,nativeSprite):null,k.pass==0?texture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side")):null,(x,y,z,u,v,nx,ny,nz)->out.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex());
        return out.finish();
    }
    private SegmentedDoorPanels(){}
}
