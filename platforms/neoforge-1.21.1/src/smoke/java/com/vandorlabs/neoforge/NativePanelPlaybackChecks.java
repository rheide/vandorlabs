package com.vandorlabs.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Actual direct-texture consumer emission, without a client world or graphics context. */
public final class NativePanelPlaybackChecks {
    private static long checks;private static volatile Object consumed;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents contents,int offset){super(TextureAtlas.LOCATION_BLOCKS,contents,64,32,offset,8);}}
    private static NativeWallEntity.WallAppearance appearance(int material,int mode,int speed){
        var base=new ProgrammableShapeEntity.Appearance(new ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,new StairState(8,0));
        return new NativeWallEntity.WallAppearance(base,new WallSettings(false,false,0,0,true,0),new FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false),new PanelGlassSettings(2,material,2,false,1,true,true),NativeFlatWallGeometry.straight(Direction.NORTH,1),NativePortholeLayout.unit(0),new PanelPlayback(mode,speed));
    }
    private static final class Probe implements VertexConsumer{
        NativeFlatWallModel.Bake.PaneProjection pane;PanelPlayback playback;long tick;int vertex;boolean verify=true;long count;
        private int[] data(){return pane.quads().get(vertex/4*2).getVertices();}private int offset(){return vertex%4*8;}
        void start(NativeFlatWallModel.Bake.PaneProjection pane,PanelPlayback playback,long tick){this.pane=pane;this.playback=playback;this.tick=tick;vertex=-1;}
        @Override public VertexConsumer addVertex(float x,float y,float z){vertex++;count++;if(verify){var d=data();int p=offset();need(Math.abs(x-Float.intBitsToFloat(d[p])-.25F)<1e-6&&Math.abs(y-Float.intBitsToFloat(d[p+1])-.5F)<1e-6&&Math.abs(z-Float.intBitsToFloat(d[p+2])-.75F)<1e-6,"pose transformation retained");}return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){if(verify)need(r==255&&g==255&&b==255&&a==(data()[offset()+3]>>>24),"vertex alpha retained");return this;}
        @Override public VertexConsumer setUv(float u,float v){if(verify){var d=data();int p=offset();var source=pane.source();var sprite=source.sprite();double expectedU=Float.intBitsToFloat(d[p+4]),unitV=Float.intBitsToFloat(d[p+5]),low=0,high=1;if(pane.material()!=null){expectedU=(expectedU-sprite.getU0())/((sprite.getU1()-sprite.getU0())*source.usedU());unitV=(unitV-sprite.getV0())/((sprite.getV1()-sprite.getV0())*source.usedV());int frame=pane.material().frame(playback.mode(),playback.speed(),tick);low=pane.material().top(playback.mode(),frame);high=pane.material().bottom(playback.mode(),frame);}need(Math.abs(u-expectedU)<1e-6&&Math.abs(v-(low+(high-low)*unitV))<1e-6,"source normalized before frame selection");need(u>=-1e-6&&u<=1.000001&&v>=low-1e-6&&v<=high+1e-6,"no neighboring raw frame sampled");}return this;}
        @Override public VertexConsumer setUv1(int u,int v){if(verify)need(u==0&&v==10,"overlay retained");return this;}
        @Override public VertexConsumer setUv2(int u,int v){if(verify)need(u==160&&v==240,"current light retained");return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){if(verify){int normal=data()[offset()+7];need(Math.abs(x-(byte)normal/127F)<1e-6&&Math.abs(y-(byte)(normal>>>8)/127F)<1e-6&&Math.abs(z-(byte)(normal>>>16)/127F)<1e-6,"normal retained");}return this;}
    }
    public static void main(String[] ignored)throws Exception{
        Map<ResourceLocation,SpriteContents> contents=new HashMap<>();Map<ResourceLocation,TextureAtlasSprite> sprites=new HashMap<>();java.util.function.Function<ResourceLocation,TextureAtlasSprite> getter=id->sprites.computeIfAbsent(id,key->{var value=new SpriteContents(key,new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);contents.put(key,value);return new Sprite(value,16);});
        try{
            Map<Integer,ProgrammableModel.MaterialSnapshot> materials=new HashMap<>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var entry=ModernFinishCatalog.ENTRIES.get(i);materials.put(i,new ProgrammableModel.MaterialSnapshot(getter.apply(ResourceLocation.parse(entry.texture())),entry.u(),entry.v()));}
            var bake=new NativeFlatWallModel.Bake(Map.copyOf(materials),material->getter.apply(material.texture()));int animated=-1,stationary=-1,hidden=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var entry=ModernFinishCatalog.ENTRIES.get(i);if(entry.playback()!=null){if(entry.playback().frameCount()>1&&animated<0)animated=i;if(entry.playback().frameCount()==1&&stationary<0)stationary=i;if(entry.texture().contains("dark_wall_panel")&&i!=0)hidden=i;}}need(animated>=0&&stationary>=0&&hidden>=0,"animated/static/hidden descriptors available");
            Probe probe=new Probe();PoseStack poses=new PoseStack();poses.translate(.25,.5,.75);int samples=0;
            for(int material:new int[]{animated,stationary,hidden}){
                List<BakedQuad> previous=null;for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){
                    var snapshot=appearance(material,mode,speed);var pane=bake.paneProjection(snapshot);need(pane!=null&&pane==bake.paneProjection(snapshot),"weak snapshot projection reused");need(previous==null||previous==pane.quads(),"mode/speed do not rebuild geometry or restart phase");previous=pane.quads();need(pane.material().aspect()==ModernFinishCatalog.ENTRIES.get(ModernFinishCatalog.storedChoice(material)).playback().aspect(),"original static source aspect including hidden material");
                    for(long tick:new long[]{0,1,5,20,100,1000}){probe.start(pane,snapshot.playback(),tick);NativePanelPlaybackRenderer.emit(pane.quads(),pane.source(),pane.material(),snapshot.playback(),tick,poses.last(),probe,0x00F000A0,0x000A0000);need(probe.vertex+1==pane.quads().size()*2,"all vertices emitted exactly once");samples++;}
                }
            }
            for(int material:new int[]{-1,0,0x40001234})for(int tint=0;tint<4;tint++)for(int mode=0;mode<3;mode++){
                var base=appearance(material,1,1);var snapshot=new NativeWallEntity.WallAppearance(base.base(),base.walls(),base.frames(),new PanelGlassSettings(2,material,mode,false,tint,true,true),base.geometry(),base.aperture(),base.playback());var pane=bake.paneProjection(snapshot);need(pane!=null&&pane.material()==null,"palette/general/unavailable IDs use atlas pane path");need(pane.opaque()==(tint==3),"opaque render pass follows tint, not fully white palette vertices");int expected=material<0||tint==3?255:(int)((tint==0?.35F:tint==1?.65F:.85F)*255);need((pane.quads().getFirst().getVertices()[3]>>>24)==expected,"native palette retains PNG alpha, general tint follows exact floor policy");probe.start(pane,snapshot.playback(),100);NativePanelPlaybackRenderer.emit(pane.quads(),pane.source(),pane.material(),snapshot.playback(),100,poses.last(),probe,0x00F000A0,0x000A0000);need(probe.vertex+1==pane.quads().size()*2,"one uncullled record per physical atlas-pane face");samples++;
            }
            var snapshot=appearance(animated,2,1);var old=bake.paneProjection(snapshot);int[] saved=old.quads().getFirst().getVertices().clone();Map<Integer,ProgrammableModel.MaterialSnapshot> replacement=new HashMap<>();for(var entry:materials.entrySet())replacement.put(entry.getKey(),new ProgrammableModel.MaterialSnapshot(new Sprite(contents.get(entry.getValue().sprite().contents().name()),0),entry.getValue().usedU(),entry.getValue().usedV()));new NativeFlatWallModel.Bake(Map.copyOf(replacement),material->getter.apply(material.texture()));need(old==bake.paneProjection(snapshot)&&Arrays.equals(saved,old.quads().getFirst().getVertices()),"old generation retains own sprite/UV selection");
            probe.verify=false;for(int i=0;i<20000;i++){consumed=bake.paneProjection(snapshot);probe.start(old,snapshot.playback(),100);NativePanelPlaybackRenderer.emit(old.quads(),old.source(),old.material(),snapshot.playback(),100,poses.last(),probe,0x00F000A0,0x000A0000);}var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);for(int i=0;i<10000;i++){consumed=bake.paneProjection(snapshot);probe.start(old,snapshot.playback(),100);NativePanelPlaybackRenderer.emit(old.quads(),old.source(),old.material(),snapshot.playback(),100,poses.last(),probe,0x00F000A0,0x000A0000);}long bytes=bean.getThreadAllocatedBytes(id)-before;
            need(bytes<=1600,"warmed cached projection/emitter allocates no per-frame graph bytesPerEmit="+bytes/10000D+" vertices="+old.quads().size()*4);System.out.println("[vandorlabs][panel-playback-render] CPU PASS samples="+samples+" checks="+checks+" bytesPerCachedEmit="+bytes/10000D+" real VertexConsumer pose/alpha/UV/light/overlay/normal; live binding separate");
        }finally{for(var value:contents.values())value.close();}
    }
}
