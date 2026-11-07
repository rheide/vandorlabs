package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.credentials.CredentialCatalog;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.screen.NativeScreenChipView;
import com.vandorlabs.programmer.*;
import com.vandorlabs.render.ScreenSurface;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Prepared non-Vehicle chip display; consumes bounded immutable projections, never physical inventory. */
public final class NativeScreenJournalRenderer {
 private static final long BUDGET=4L*1024*1024;
 private record Choice(boolean occupied,boolean master,int company,String name,String title,String source,List<String> lines,int page,int pages){
  JournalOverlay.Contents contents(){return new JournalOverlay.Contents(occupied,master,company,name,title,source,lines,page,pages);}
  int bytes(){long bytes=512L+2L*(name.length()+title.length()+source.length());for(var line:lines)bytes+=48L+line.length()*2L;return (int)Math.min(Integer.MAX_VALUE,bytes);}
 }
 private record Key(Choice choice,int height){}
 private static final class Selector {
  final Choice choice;final JournalOverlay.Contents contents;final int bytes;Key last;
  Selector(NativeScreenChipView view){var journal=view.journal;String name=view.displayName!=null?view.displayName:view.reader.occupied()?JournalText.bounded(NativeCredentialItems.dataChip(view.reader.company()).getHoverName().getString(),NativeScreenChipView.NAME_LIMIT):"";choice=new Choice(view.reader.occupied(),view.master,view.reader.company(),name,journal==null?"":journal.title,journal==null?"":journal.source,journal==null?List.of():journal.pages.get(view.page).lines,journal==null?0:view.page,journal==null?0:journal.pages.size());contents=choice.contents();bytes=choice.bytes();}
  Selector(JournalOverlay.Contents contents){this.contents=contents;choice=new Choice(contents.occupied,contents.master,contents.company,contents.displayName,contents.title,contents.source,contents.lines,contents.page,contents.pages);bytes=choice.bytes();}
  Key key(int height){if(last==null||last.height!=height)last=new Key(choice,height);return last;}
 }
 private record Logo(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float usedU,float usedV,double aspect){}
 private record Prepared(Key key,NativeOverlayCanvas.Mesh text,long bytes){}
 private final NativeOverlayCanvas canvas;
 private final Logo[] logos=new Logo[7];
 private final NativeOverlayCanvas.Selectors<Object,Selector> selectors=new NativeOverlayCanvas.Selectors<>(512*1024,value->value.bytes);
 private LinkedHashMap<Key,Prepared> cache=new LinkedHashMap<>(32,.75F,true);private long bytes,builds;
 private final Matrix4f ui=new Matrix4f(),matrix=new Matrix4f();private VertexConsumer solids;
 private final JournalOverlay.Sink rectangles=new JournalOverlay.Sink(){
  public void rectangle(int l,int t,int r,int b,double z,int color){NativeOverlayCanvas.rectangle(solids,matrix,l,t,r,b,z,color);}
  public void fitted(String value,int x,int y,int width,int color){}public void text(String value,int x,int y,int color){}public void centered(String value,int center,int y,int color){}public boolean companyLogo(int company,int height){return logos[company]!=null;}
 };
 public NativeScreenJournalRenderer(Font font,NativeFlatWallModel.Bake bake){canvas=new NativeOverlayCanvas(font);Objects.requireNonNull(bake);for(int company=1;company<7;company++){String id="corp_"+CredentialCatalog.companyName(company)+"_logo";int choice=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals(id)){choice=i;break;}var material=choice<0?null:bake.surfaces.get(ModernFinishCatalog.storedChoice(choice));if(choice>0&&material!=null)logos[company]=new Logo(material.sprite(),material.usedU(),material.usedV(),material.usedU()/material.usedV());}}
 public static int height(ScreenSurface.Quad content){return Math.max(JournalOverlay.MIN_HEIGHT,(int)Math.round(JournalOverlay.WIDTH*Math.hypot(content.bottomRight.y-content.topRight.y,content.bottomRight.z-content.topRight.z)/(content.topRight.x-content.topLeft.x)));}
 public static void uiMatrix(ScreenSurface.Quad content,int height,Matrix4f target){if(height<JournalOverlay.MIN_HEIGHT)throw new IllegalArgumentException("Journal canvas height");NativeOverlayCanvas.uiMatrix(content,JournalOverlay.WIDTH,height,target);}
 /** Joined Source canvases use a double-precision translation/scale before GL float publication. */
 public static int joinedHeight(ScreenSurface.Quad content,int columns,int rows){if(columns<1||rows<1||columns>1024||rows>1024)throw new IllegalArgumentException("Journal rectangle dimensions");double span=content.topRight.x-content.topLeft.x+16*(columns-1),topY=content.topRight.y+16*(rows-1);return Math.max(JournalOverlay.MIN_HEIGHT,(int)Math.round(JournalOverlay.WIDTH*Math.hypot(content.bottomRight.y-topY,content.bottomRight.z-content.topRight.z)/span));}
 public static void joinedUiMatrix(ScreenSurface.Quad content,int columns,int rows,int height,Matrix4f target){if(height<JournalOverlay.MIN_HEIGHT)throw new IllegalArgumentException("Journal canvas height");double span=content.topRight.x-content.topLeft.x+16*(columns-1),topY=content.topRight.y+16*(rows-1);target.zero().m00((float)(-span/JournalOverlay.WIDTH)).m11((float)((content.bottomRight.y-topY)/height)).m22(content.nz).m30((float)(content.topRight.x+16*(columns-1))).m31((float)(topY+content.ny*.02F)).m32((float)(content.topRight.z+content.nz*.02F)).m33(1);}
 private Prepared prepared(Selector selector,int height){var key=selector.key(height);var found=cache.get(key);if(found!=null){selector.last=found.key;return found;}var mesh=canvas.build(target->JournalOverlay.emit(selector.contents,height,new JournalOverlay.Sink(){public void rectangle(int l,int t,int r,int b,double z,int color){}public void fitted(String value,int x,int y,int width,int color){target.fitted(value,x,y,width,color);}public void text(String value,int x,int y,int color){target.plain(value,x,y,color);}public void centered(String value,int center,int y,int color){target.centered(value,center,y,color);}public boolean companyLogo(int company,int height){return logos[company]!=null;}}));found=new Prepared(key,mesh,mesh.bytes()+selector.bytes+32);builds++;if(found.bytes<=BUDGET){cache.put(key,found);bytes+=found.bytes;while(bytes>BUDGET){var iterator=cache.values().iterator();bytes-=iterator.next().bytes;iterator.remove();}}return found;}
 /** Caller supplies the exact Source content plane and owning role/camera pose. */
 public void render(NativeScreenChipView view,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int light){
  render(view,content,pixelToOwner,ownerPoses,buffers,light,0,0);
 }
 /** Positive dimensions select the released joined-flat canvas, including singleton groups. */
 public void render(NativeScreenChipView view,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int light,int columns,int rows){
  com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();Objects.requireNonNull(view);var selector=selectors.get(view);if(selector==null){selector=new Selector(view);selectors.put(view,selector);}int height=columns>0?joinedHeight(content,columns,rows):height(content);var prepared=prepared(selector,height);if(columns>0)joinedUiMatrix(content,columns,rows,height,ui);else uiMatrix(content,height,ui);matrix.set(ownerPoses.last().pose()).mul(pixelToOwner).mul(ui);draw(selector,prepared,height,buffers,light);
 }
 /** Caller-prepared journal contents and complete pixel plane; no Screen or physical item is required. */
 public void render(JournalOverlay.Contents contents,int height,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int light){
  com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();Objects.requireNonNull(contents);if(height<JournalOverlay.MIN_HEIGHT)throw new IllegalArgumentException("Journal canvas height");var selector=selectors.get(contents);if(selector==null){selector=new Selector(contents);selectors.put(contents,selector);}var prepared=prepared(selector,height);matrix.set(ownerPoses.last().pose()).mul(pixelToOwner);draw(selector,prepared,height,buffers,light);
 }
 private void draw(Selector selector,Prepared prepared,int height,MultiBufferSource buffers,int light){solids=NativeOverlayCanvas.solid(buffers);try{JournalOverlay.emit(selector.contents,height,rectangles);}finally{solids=null;}if(selector.choice.master&&logos[selector.choice.company]!=null)logo(logos[selector.choice.company],height,buffers,light);NativeOverlayCanvas.draw(prepared.text,matrix,buffers,light);}
 /** Source logo sizing uses actual native crop aspect, not the square atlas canvas. */
 public double logoAspect(int company){return logos[company]==null?0:logos[company].aspect;}
 private void logo(Logo logo,int height,MultiBufferSource buffers,int light){double width=Math.min(304,(height-16)*logo.aspect),h=width/logo.aspect,x=(320-width)/2,y=(height-h)/2;var target=buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType());float u0=logo.sprite.getU0(),v0=logo.sprite.getV0(),u1=u0+(logo.sprite.getU1()-u0)*logo.usedU,v1=v0+(logo.sprite.getV1()-v0)*logo.usedV;point(target,(float)x,(float)(y+h),u0,v1,light);point(target,(float)(x+width),(float)(y+h),u1,v1,light);point(target,(float)(x+width),(float)y,u1,v0,light);point(target,(float)x,(float)y,u0,v0,light);}
 private void point(VertexConsumer target,float x,float y,float u,float v,int light){NativeOverlayCanvas.vertex(target,matrix,x,y,.02F,-1,u,v,light,true);target.setNormal(0,0,1);}
 public long builds(){return builds;}public long retainedBytes(){return bytes+selectors.bytes();}
 public void clear(){selectors.clear();cache=new LinkedHashMap<>(32,.75F,true);bytes=0;}
}
