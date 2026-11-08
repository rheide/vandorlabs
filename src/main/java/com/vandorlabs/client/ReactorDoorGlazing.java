package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Bounded, atlas-local Reactor frame and pane meshes, shared by ordinary and Large leaves. */
final class ReactorDoorGlazing {
 private static final com.google.common.cache.Cache<Key,StaticSurfaceMesh> CACHE=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(2*1024*1024).weigher((Key k,StaticSurfaceMesh v)->256+v.vertexCount()*32).build();
 private static final class Key {
  final SelectedDoorGeometry geometry;final DoorGlassAppearance glass;final boolean pane,mirror;final double scale;
  Key(SelectedDoorGeometry g,DoorGlassAppearance a,boolean p,boolean m,double s){geometry=g;glass=a;pane=p;mirror=m;scale=s;}
  public int hashCode(){return java.util.Objects.hash(System.identityHashCode(geometry),glass,pane,mirror,scale);}
  public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return geometry==k.geometry&&glass.equals(k.glass)&&pane==k.pane&&mirror==k.mirror&&scale==k.scale;}
 }
 static void clear(){CACHE.invalidateAll();}
 private static com.vandorlabs.segmented.SegmentedPanelMesh.Texture texture(TextureAtlasSprite sprite){return new com.vandorlabs.segmented.SegmentedPanelMesh.Texture(){public float u(double p){return sprite.getInterpolatedU(p);}public float v(double p){return sprite.getInterpolatedV(p);}};}
 static StaticSurfaceMesh get(SelectedDoorGeometry geometry,DoorGlassAppearance glass,boolean pane,boolean mirror){return get(geometry,glass,pane,mirror,1);}
 static StaticSurfaceMesh get(SelectedDoorGeometry geometry,DoorGlassAppearance glass,boolean pane,boolean mirror,double scale){
  Key key=new Key(geometry,glass,pane,mirror,scale);StaticSurfaceMesh mesh=CACHE.getIfPresent(key);if(mesh!=null)return mesh;
  String image;if(pane)image=GlassMaterials.texture(glass.material==DoorGlassAppearance.ORIGINAL?-1:glass.material,glass.shade,1,glass.tint);else{int choice=0;while(choice<ScreenHousingTextures.IDS.length&&!ScreenHousingTextures.reactorArtwork(choice))choice++;if(choice==ScreenHousingTextures.IDS.length)throw new IllegalStateException("Missing Reactor artwork identity");image=ScreenHousingTextures.fullTexture(choice);}
  TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(image),edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
  StaticSurfaceMesh.Capture out=StaticSurfaceMesh.capture();com.vandorlabs.segmented.SegmentedPanelMesh.Sink sink=(x,y,z,u,v,nx,ny,nz)->out.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex();if(pane){com.vandorlabs.segmented.SegmentedPanelMesh.Texture imageTexture=texture(sprite);com.vandorlabs.render.ReactorArtworkMesh.pane(geometry.bounds,new com.vandorlabs.segmented.SegmentedPanelMesh.Surface(imageTexture,imageTexture,UnifiedTextureSprites.aspect(sprite),false),glass.layout,scale,sink);}else com.vandorlabs.render.ReactorArtworkMesh.build(geometry.bounds,false,texture(sprite),texture(edge),sink);mesh=out.finish();if(mirror)mesh=mesh.mirrorX(geometry.bounds[0]+geometry.bounds[3]);CACHE.put(key,mesh);return mesh;
 }
 private ReactorDoorGlazing(){}
}
