package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.model.BakedQuad;
import java.util.*;
import java.util.function.Supplier;

/** Projection keys must match fresh output and retain descriptors, never owner snapshots. */
final class NativeWallProjectionCacheChecks {
 private static int checks;
 private static void need(boolean condition,String text){checks++;if(!condition)throw new AssertionError(text);}
 private static NativeWallEntity.WallAppearance view(int profile,double clip){
  int main=profile%4,layout=(profile/3)%3;
  var state=new com.vandorlabs.ui.ProgrammableBlockDialog.State(main,layout,false,new int[]{-1,-1,-1,-1,-1,-1},-1);
  var base=new ProgrammableShapeEntity.Appearance(state,(profile/4)%4,new StairState(8,0));
  var frames=new FrameSettings(profile%3==0?-1:profile%3,profile%3,(profile/2)%3,0,-1,-1,false,false,false,false);
  var glass=new PanelGlassSettings(profile%3,profile%4==0?-1:profile%3,profile%3,false,profile%4,profile%5!=0,true);
  var pose=new NativeDiagonalWallGeometry.Pose(Direction.from2DDataValue(profile%4),(profile&4)!=0,profile%3,(profile/2)%4,null,Double.NaN,Double.NaN,false,false,new NativeDiagonalWallGeometry.Clip(-2+clip,-2,-2,3,3,3),NativePanelLayout.UNIT);
  var geometry=NativeDiagonalWallGeometry.build(pose);
  var group=new NativePanelLayout(1,0,3,2,(profile&1)!=0,(profile&2)!=0);
  var aperture=new NativePortholeLayout(group,profile%4,0,(profile&1)!=0,false,false,false,profile%11==0);
  return new NativeWallEntity.WallAppearance(base,new WallSettings(pose.mode()==1,pose.mode()==2,pose.fill(),0,false,profile%4),frames,glass,geometry,aperture,PanelPlayback.DEFAULT,false,profile%2==0?NativePanelKind.GLASS:NativePanelKind.PORTHOLE,new NativeDiagonalGlassLayout(group,profile%16,pose.mode()==2?2:1));
 }
 private static void equal(List<BakedQuad> expected,List<BakedQuad> actual){
  need(expected.size()==actual.size(),"Projection changed quad count");
  for(int i=0;i<expected.size();i++){var a=expected.get(i);var b=actual.get(i);need(Arrays.equals(a.getVertices(),b.getVertices())&&a.getDirection()==b.getDirection()&&a.getSprite()==b.getSprite()&&a.isShade()==b.isShade()&&a.hasAmbientOcclusion()==b.hasAmbientOcclusion(),"Projection changed defined records/material/flags");}
 }
 private static void emptyProbe(NativeFlatWallModel.Bake bake)throws Exception{
  var field=NativeFlatWallModel.Bake.class.getDeclaredField("lookup");field.setAccessible(true);var key=field.get(bake);
  for(String name:List.of("geometry","aperture","diagonal","kind")){var value=key.getClass().getDeclaredField(name);value.setAccessible(true);need(value.get(key)==null,"Projection probe retained "+name);}
 }
 static void run(Supplier<NativeFlatWallModel.Bake> factory)throws Exception{
  var bake=factory.get();var stable=view(2,0);var body=bake.bodyProjection(stable);var pane=bake.paneProjection(stable);
  for(int i=0;i<512;i++){var equivalent=view(2,0);need(bake.bodyProjection(equivalent)==body&&bake.paneProjection(equivalent)==pane,"Equivalent independent publications must share projections");}
  for(int profile=0;profile<96;profile++){
   var snapshot=view(profile,0);var fresh=factory.get();
   equal(fresh.bodyProjection(snapshot),bake.bodyProjection(snapshot));equal(fresh.paneProjection(snapshot).quads(),bake.paneProjection(snapshot).quads());emptyProbe(bake);
  }
  // Distinct valid finite clip descriptors exercise FIFO pressure without retaining Geometry's lazy tables.
  long maxBody=0,maxPane=0;var bodyBytes=NativeFlatWallModel.Bake.class.getDeclaredField("bodyBytes");bodyBytes.setAccessible(true);var paneBytes=NativeFlatWallModel.Bake.class.getDeclaredField("playbackBytes");paneBytes.setAccessible(true);
  for(int i=0;i<2048;i++){
   var snapshot=view(2,i/8192D);bake.bodyProjection(snapshot);bake.paneProjection(snapshot);
   int retainedBody=bodyBytes.getInt(bake),retainedPane=paneBytes.getInt(bake);need(retainedBody<=4*1024*1024&&retainedPane<=4*1024*1024,"Projection retained-byte bound");maxBody=Math.max(maxBody,retainedBody);maxPane=Math.max(maxPane,retainedPane);
  }
  need(maxBody>3*1024*1024&&maxPane>3*1024*1024,"Pressure reaches both cache limits");
  equal(body,bake.bodyProjection(stable));equal(pane.quads(),bake.paneProjection(stable).quads());emptyProbe(bake);
  var invalid=new NativeWallEntity.WallAppearance(stable.base(),stable.walls(),stable.frames(),stable.glass(),null,stable.aperture(),stable.playback(),false,stable.kind(),stable.diagonalGlass());
  boolean failed=false;try{bake.bodyProjection(invalid);}catch(RuntimeException expected){failed=true;}need(failed,"Invalid descriptor must fail");emptyProbe(bake);
  for(int i=0;i<32768;i++){bake.bodyProjection(stable);bake.paneProjection(stable);}
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
  for(int i=0;i<8192;i++){bake.bodyProjection(stable);bake.paneProjection(stable);}
  long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes==0,"Warmed projection lookup allocated "+bytes);
  System.out.println("[vandorlabs][wall-projection-cache] CPU PASS profiles=96 independentOwners=512 pressureDescriptors=2048 checks="+checks+" bodyPeak="+maxBody+" panePeak="+maxPane+" warm16384Bytes="+bytes+" exactFreshRecords/rebuild/probeRetirement");
 }
 private NativeWallProjectionCacheChecks(){}
}
