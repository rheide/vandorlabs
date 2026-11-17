package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.navigation.*;
import com.vandorlabs.neoforge.navigation.NativeNavigationImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import java.lang.reflect.*;
import java.util.*;

/** Real client state/action/receive/draw checks; isolated key, no polling or packets. */
public final class NativeNavigationLoadingChecks {
 private static int checks;
 public static void run()throws Exception{
  var client=NativeNavigationClient.class;var mc=Minecraft.getInstance();var world=field(client,"world");Object oldWorld=world.get(null);world.set(null,mc.level);var states=(Map<Object,Object>)field(client,"STATES").get(null);var pos=new BlockPos(29000123,111,29000456);var keyClass=Class.forName(client.getName()+"$Key");var keyCtor=keyClass.getDeclaredConstructors()[0];keyCtor.setAccessible(true);Object key=keyCtor.newInstance(pos,0);Object previous=states.get(key);Class<?> type=Class.forName(client.getName()+"$State");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object state=ctor.newInstance();states.put(key,state);
  try{
   var canvas=new NavigationCanvas(256,256,true);long tick=field(client,"tick").getLong(null);field(type,"serial").setInt(state,17);field(type,"request").setLong(state,tick);var motion=(NavigationMotion)field(type,"motion").get(state);double scale=NavigationMap.SPANS[4]/384.0;motion.snap(0,0,scale);var image=image(pos,canvas,17);field(type,"image").set(state,image);
   NativeNavigationClient.action(pos,0,2);need(NativeNavigationClient.loading(pos,0)&&field(type,"waiting").getBoolean(state)&&motion.targetX>0,"pan begins loading immediately before response");field(type,"request").setLong(state,tick);var pan=draw(pos,canvas);need(pan.firstColor()>>>24==255&&(pan.firstColor()&255)>=86,"local pan begins bright pulse phase");fullCanvas(pan,canvas);
   motion.snap(0,0,scale);field(type,"loading").setBoolean(state,false);NativeNavigationClient.action(pos,0,5);need(NativeNavigationClient.loading(pos,0)&&motion.targetScale<scale,"zoom begins loading immediately before response");field(type,"request").setLong(state,tick);
   motion.snap(0,0,scale);field(type,"loading").setBoolean(state,false);field(type,"waiting").setBoolean(state,true);double start=field(type,"loadingStart").getDouble(state);var waiting=draw(pos,canvas);need(!NativeNavigationClient.loading(pos,0)&&waiting.firstColor()==0xff09121c,"periodic waiting without movement never pulses");var receive=client.getDeclaredMethod("receive",NativeNavigationImage.class);receive.setAccessible(true);receive.invoke(null,image);need(!NativeNavigationClient.loading(pos,0)&&!field(type,"waiting").getBoolean(state)&&field(type,"loadingStart").getDouble(state)==start,"unchanged ACK neither restarts pulse nor movement");need(draw(pos,canvas).firstColor()==0xff09121c,"unchanged ACK keeps settled background");
   field(type,"image").set(state,null);field(type,"loading").setBoolean(state,true);var now=client.getDeclaredMethod("now");now.setAccessible(true);field(type,"loadingStart").setDouble(state,(double)now.invoke(null));var initial=draw(pos,canvas);need((initial.firstColor()&255)>=86,"initial load starts bright rather than black");fullCanvas(initial,canvas);
   var clock=(VisualClock)field(client,"CLOCK").get(null);var saved=new HashMap<Field,Object>();for(Field f:VisualClock.class.getDeclaredFields()){f.setAccessible(true);saved.put(f,f.get(clock));}try{double a=clock.sample(8000000000000000L,true),b=clock.sample(8000005000000000L,true);need(a==b&&NavigationMotion.pulse(a-start+20)==NavigationMotion.pulse(b-start+20),"actual shared client clock freezes pulse while paused");}finally{for(var entry:saved.entrySet())entry.getKey().set(clock,entry.getValue());}
   System.out.println("[vandorlabs][navigation-loading] PASS checks="+checks+" localPanZoom/initialBright/periodicACKNoPulse/pausedClock/fullCanvasSolids/noPackets");
  }finally{if(previous==null)states.remove(key);else states.put(key,previous);world.set(null,oldWorld);}
 }
 private static NativeNavigationImage image(BlockPos pos,NavigationCanvas c,int serial){return new NativeNavigationImage(pos,0,serial,0,0,4,c.width,c.height,c.mapTop,c.mapBottom,c.gridWidth(4),c.gridHeight(4),1,false,false,true,false,0,0,null);}
 private static Recorder draw(BlockPos pos,NavigationCanvas canvas){var recorders=new LinkedHashMap<RenderType,Recorder>();MultiBufferSource buffers=type->recorders.computeIfAbsent(type,k->new Recorder());NativeNavigationClient.draw(pos,0,canvas,new Matrix4f(),buffers);return (Recorder)NativeOverlayCanvas.solid(buffers);}
 private static void fullCanvas(Recorder r,NavigationCanvas c){need(r.points.size()>=8,"actual solid background rectangles emitted");float[][] expected={{0,c.height},{c.width,c.height},{c.width,0},{0,0}};for(int i=0;i<4;i++)need(r.points.get(i)[0]==expected[i][0]&&r.points.get(i)[1]==expected[i][1]&&r.points.get(i)[2]==0,"pulsing base covers entire header/map/footer beneath foreground");need(r.points.get(4)[1]==c.mapBottom&&r.points.get(6)[1]==c.mapTop&&r.points.get(4)[2]>0,"map inset remains separate forward layer");}
 private static final class Recorder implements VertexConsumer{final List<float[]> points=new ArrayList<>();final List<Integer> colors=new ArrayList<>();public VertexConsumer addVertex(float x,float y,float z){points.add(new float[]{x,y,z});return this;}public VertexConsumer setColor(int r,int g,int b,int a){colors.add(a<<24|r<<16|g<<8|b);return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}int firstColor(){return colors.getFirst();}}
 private static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static void need(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 private NativeNavigationLoadingChecks(){}
}
