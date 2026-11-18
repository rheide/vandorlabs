package com.vandorlabs.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.navigation.*;
import com.vandorlabs.neoforge.navigation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import java.util.*;

/** One texture quad per map, fixed physical UI pixels and bounded render-thread-owned textures. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeNavigationClient {
 private record Key(BlockPos pos,int slot){}
 /** UI color is depth tested against the scene but does not become an occluder for later batches. */
 private static final class Types extends RenderType {
  private Types(){super("vandorlabs_navigation_ui",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
  static final RenderType UI=RenderType.create("vandorlabs_navigation_ui",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setLayeringState(VIEW_OFFSET_Z_LAYERING).setOutputState(MAIN_TARGET).createCompositeState(false));
 }
 private static VertexConsumer foreground(MultiBufferSource buffers){return buffers.getBuffer(Types.UI);}
 private static final class State {int serial,revision;long request=-100,lastSeen;NativeNavigationImage image,queued;DynamicTexture texture;ResourceLocation id;final NavigationMotion motion=new NavigationMotion();int action;boolean waiting,loading;double loadingStart;}
 private static final Map<Key,State> STATES=new HashMap<>();private static final VisualClock CLOCK=new VisualClock();private static Object world;private static long tick;private static int textureId;
 private static double now(){var m=Minecraft.getInstance();return CLOCK.sample(System.nanoTime(),m.isPaused());}
 public static void install(){NativeNavigationImage.RECEIVER=NativeNavigationClient::receive;NativeNavigationTableHooks.CLIENT_ACTION=(t,a)->action(t.reader()==null?t.getBlockPos():t.reader().getBlockPos(),0,a);com.vandorlabs.neoforge.navigation.NativeNavigationTableClient.MAP=NativeNavigationClient::table;}
 private static State state(BlockPos pos,int slot){var m=Minecraft.getInstance();if(world!=m.level){clear();world=m.level;}return STATES.computeIfAbsent(new Key(pos.immutable(),slot),k->new State());}
 public static void action(BlockPos pos,int slot,int action){var s=state(pos,slot);var image=s.image;if(image!=null){double x=s.motion.targetX,z=s.motion.targetZ,scale=s.motion.targetScale;int dx=Math.max(1,(int)Math.round(image.width()*scale/4)),dz=Math.max(1,(int)Math.round((image.mapBottom()-image.mapTop())*scale/4));switch(action){case 1->x=NavigationMap.bound(Math.round(x)-dx);case 2->x=NavigationMap.bound(Math.round(x)+dx);case 3->z=NavigationMap.bound(Math.round(z)-dz);case 4->z=NavigationMap.bound(Math.round(z)+dz);case 5->scale=Math.max(NavigationMap.SPANS[0]/384.0,scale/2);case 6->scale=Math.min(NavigationMap.SPANS[NavigationMap.SPANS.length-1]/384.0,scale*2);case 8->{if(image.pin()){x=image.pinX();z=image.pinZ();}else if(Minecraft.getInstance().player!=null){x=Math.round(Minecraft.getInstance().player.getX());z=Math.round(Minecraft.getInstance().player.getZ());}}}double time=now();if(x!=s.motion.targetX||z!=s.motion.targetZ||scale!=s.motion.targetScale){s.motion.move(x,z,scale,time);s.loading=true;s.loadingStart=time;}}else{s.loading=true;s.loadingStart=now();}s.waiting=true;s.request=tick-19;}
 private static void request(BlockPos pos,int slot,State s){s.request=tick;s.waiting=true;if(s.image==null&&!s.loading){s.loading=true;s.loadingStart=now();}PacketDistributor.sendToServer(new NativeNavigationPayload(pos,slot,++s.serial,s.action,s.revision));s.action=0;}
 private static void receive(NativeNavigationImage image){var s=STATES.get(new Key(image.pos(),image.slot()));if(s==null||image.serial()!=s.serial)return;double time=now();if(s.image==null)s.motion.snap(image.x(),image.z(),NavigationMap.SPANS[image.zoom()]/384.0);else if(s.motion.targetX!=image.x()||s.motion.targetZ!=image.z()||s.motion.targetScale!=NavigationMap.SPANS[image.zoom()]/384.0)s.motion.move(image.x(),image.z(),NavigationMap.SPANS[image.zoom()]/384.0,time);s.queued=image;s.waiting=false;s.loading=false;if(!s.motion.settling(time))apply(s);}
 private static void apply(State s){var p=s.queued;if(p==null)return;s.queued=null;if(p.pixels()!=null){if(s.texture!=null)Minecraft.getInstance().getTextureManager().release(s.id);var data=new NativeImage(p.columns(),p.rows(),false);for(int y=0;y<p.rows();y++)for(int x=0;x<p.columns();x++){int c=p.pixels()[y*p.columns()+x];data.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16));}s.texture=new DynamicTexture(data);s.id=ResourceLocation.fromNamespaceAndPath("vandorlabs","navigation/dynamic_"+(++textureId));Minecraft.getInstance().getTextureManager().register(s.id,s.texture);}s.image=p;s.revision=p.revision();trim(s);}
 public static void table(NativeNavigationTableEntity t,PoseStack poses,MultiBufferSource buffers,int light,float partial){if(!t.selectedNavigation()||!t.ownsReader())return;var plane=t.plane();var c=t.canvas();var q=plane.overlayPoint(0,0);var u=plane.basisU;var v=plane.basisV;var n=plane.normal;var p=t.getBlockPos();var pixel=new Matrix4f().zero().m00((float)(u.x*plane.widthBlocks/c.width)).m01((float)(u.y*plane.widthBlocks/c.width)).m02((float)(u.z*plane.widthBlocks/c.width)).m10((float)(-v.x*plane.heightBlocks/c.height)).m11((float)(-v.y*plane.heightBlocks/c.height)).m12((float)(-v.z*plane.heightBlocks/c.height)).m20((float)n.x).m21((float)n.y).m22((float)n.z).m30((float)(q.x-p.getX())).m31((float)(q.y-p.getY())).m32((float)(q.z-p.getZ())).m33(1);draw(t.getBlockPos(),0,c,new Matrix4f(poses.last().pose()).mul(pixel),buffers);}
 public static void draw(BlockPos pos,int slot,NavigationCanvas c,Matrix4f matrix,MultiBufferSource buffers){var s=state(pos,slot);s.lastSeen=tick;double time=now();if(s.queued!=null&&!s.motion.settling(time))apply(s);if(tick-s.request>=20&&!Minecraft.getInstance().isPaused())request(pos,slot,s);boolean loading=s.loading||s.motion.settling(time);int pulse=NavigationMotion.pulse(time-s.loadingStart+20);NativeOverlayCanvas.rectangle(NativeOverlayCanvas.solid(buffers),matrix,0,0,c.width,c.height,0,loading?pulse:0xff09121c);NativeOverlayCanvas.rectangle(NativeOverlayCanvas.solid(buffers),matrix,0,c.mapTop,c.width,c.mapBottom,.0004,loading?pulse:NavigationMap.OUTSIDE);
  var image=s.image;if(image!=null&&s.texture!=null){double scale=s.motion.scale(time),cx=s.motion.x(time),cz=s.motion.z(time),oldScale=NavigationMap.SPANS[image.zoom()]/384.0;double left=(image.x()-cx-image.width()*oldScale/2)/scale+c.width/2.0,right=left+image.width()*oldScale/scale,top=(image.z()-cz-(image.mapBottom()-image.mapTop())*oldScale/2)/scale+(c.mapTop+c.mapBottom)/2.0,bottom=top+(image.mapBottom()-image.mapTop())*oldScale/scale;float l=(float)Math.max(0,left),r=(float)Math.min(c.width,right),tt=(float)Math.max(c.mapTop,top),b=(float)Math.min(c.mapBottom,bottom);if(r>l&&b>tt){var target=buffers.getBuffer(RenderType.entityTranslucent(s.id));quad(target,matrix,l,tt,r,b,(float)((l-left)/(right-left)),(float)((tt-top)/(bottom-top)),(float)((r-left)/(right-left)),(float)((b-top)/(bottom-top)));}}
  var text=new Matrix4f(matrix).translate(0,0,.008f);var font=Minecraft.getInstance().font;label(font,buffers,text,image!=null&&image.writable()?NavigationOverlay.zoomLabel(image.zoom()):"NAVIGATION",6,1,0xffa7cfde);if(image!=null){String coordinates=Math.round(s.motion.x(time))+", "+Math.round(s.motion.z(time));label(font,buffers,text,font.plainSubstrByWidth(coordinates,c.width-12),6,11,0xff8eafbe);}String[] labels={"<",">","^","v","+","-","","WRITE"};for(int i=0;i<c.count;i++){int x=c.buttonX(i),y=c.buttonY(i);NativeOverlayCanvas.rectangle(foreground(buffers),matrix,x+1,y+1,x+23,y+23,.006,loading?pulse:0xff23384b);if(c.actionAt(i)==8){NativeOverlayCanvas.rectangle(foreground(buffers),matrix,x+11,y+4,x+13,y+20,.009,0xffb9e4ed);NativeOverlayCanvas.rectangle(foreground(buffers),matrix,x+4,y+11,x+20,y+13,.009,0xffb9e4ed);NativeOverlayCanvas.rectangle(foreground(buffers),matrix,x+8,y+8,x+16,y+16,.010,loading?pulse:0xff23384b);}else label(font,buffers,text,labels[i],x+(24-font.width(labels[i]))/2,y+8,0xffcfe3eb);}
  if(image!=null&&image.empty()){String[] lines=image.unlinked()?new String[]{"Seek a Survey Dish","Or a Sentinel Array","Set redstone channels"}:new String[]{"No scanned coverage"};for(int i=0;i<lines.length;i++)label(font,buffers,text,lines[i],(c.width-font.width(lines[i]))/2,(c.mapTop+c.mapBottom)/2+i*12-12,0xffb2c7cf);}
  if(image!=null){if(image.writable()&&!image.empty()){
   var lines=foreground(buffers);
   double scale=s.motion.scale(time),viewX=s.motion.x(time),viewZ=s.motion.z(time);
   image.overlay().drawCircles(c,viewX,viewZ,scale,(x,y,endX,endY,color)->circleSegment(lines,matrix,x,y,endX,endY,color));
   image.overlay().drawRadar(c,viewX,viewZ,scale,Minecraft.getInstance().level.getGameTime(),
        (x,y,endX,endY,color)->circleSegment(lines,matrix,x,y,endX,endY,color),
        (x,y,endX,endY,color)->circleSegment(lines,matrix,x,y,endX,endY,color));
   for(int i=0;i<image.overlay().players.length;i+=2){
    double x=c.width/2.0+(image.overlay().players[i]-viewX)/scale,y=(c.mapTop+c.mapBottom)/2.0+(image.overlay().players[i+1]-viewZ)/scale;
    if(x>=3&&x<=c.width-3&&y>=c.mapTop+3&&y<=c.mapBottom-3)NativeOverlayCanvas.rectangle(lines,matrix,(int)x-2,(int)y-2,(int)x+3,(int)y+3,.004,0xfffcadff);
   }
  }
  if(c.count==8){int cx=c.width/2,cy=(c.mapTop+c.mapBottom)/2;var target=foreground(buffers);NativeOverlayCanvas.rectangle(target,matrix,cx-9,cy,cx-3,cy+1,.004,0xffb9e4ed);NativeOverlayCanvas.rectangle(target,matrix,cx+4,cy,cx+10,cy+1,.004,0xffb9e4ed);NativeOverlayCanvas.rectangle(target,matrix,cx,cy-9,cx+1,cy-3,.004,0xffb9e4ed);NativeOverlayCanvas.rectangle(target,matrix,cx,cy+4,cx+1,cy+10,.004,0xffb9e4ed);}if(image.pin())marker(s,c,matrix,buffers,image.pinX(),image.pinZ(),0xfff0d25e,0);var player=Minecraft.getInstance().player;if(player!=null)marker(s,c,matrix,buffers,player.getX(),player.getZ(),0xff4ce5e7,player.getYRot());}}
 private static void circleSegment(VertexConsumer vertices,Matrix4f matrix,double x,double y,double endX,double endY,int color){
  double dx=endX-x,dy=endY-y,half=.85/Math.hypot(dx,dy),nx=dy*half,ny=-dx*half;
  NativeOverlayCanvas.vertex(vertices,matrix,(float)(x+nx),(float)(y+ny),.004f,color,0,0,0,false);
  NativeOverlayCanvas.vertex(vertices,matrix,(float)(x-nx),(float)(y-ny),.004f,color,0,0,0,false);
  NativeOverlayCanvas.vertex(vertices,matrix,(float)(endX-nx),(float)(endY-ny),.004f,color,0,0,0,false);
  NativeOverlayCanvas.vertex(vertices,matrix,(float)(endX+nx),(float)(endY+ny),.004f,color,0,0,0,false);
 }
 private static void marker(State s,NavigationCanvas c,Matrix4f m,MultiBufferSource buffers,double x,double z,int color,float yaw){double time=now(),scale=s.motion.scale(time);int px=(int)Math.round(c.width/2.0+(x-s.motion.x(time))/scale),py=(int)Math.round((c.mapTop+c.mapBottom)/2.0+(z-s.motion.z(time))/scale);if(px<4||px>=c.width-4||py<c.mapTop+4||py>=c.mapBottom-4)return;NativeOverlayCanvas.rectangle(foreground(buffers),m,px-2,py-2,px+3,py+3,.004,color);double angle=Math.toRadians(yaw);int dx=(int)Math.round(-Math.sin(angle)*7),dy=(int)Math.round(Math.cos(angle)*7);NativeOverlayCanvas.rectangle(foreground(buffers),m,px+dx-1,py+dy-1,px+dx+2,py+dy+2,.004,color);}
 private static void label(Font f,MultiBufferSource b,Matrix4f m,String text,int x,int y,int color){f.drawInBatch(text,x,y,color,false,m,b,Font.DisplayMode.POLYGON_OFFSET,0,0xf000f0);}
 private static void quad(VertexConsumer v,Matrix4f m,float l,float t,float r,float b,float u0,float v0,float u1,float v1){point(v,m,l,b,u0,v1);point(v,m,r,b,u1,v1);point(v,m,r,t,u1,v0);point(v,m,l,t,u0,v0);}
 private static void point(VertexConsumer v,Matrix4f m,float x,float y,float u,float w){NativeOverlayCanvas.vertex(v,m,x,y,.001f,-1,u,w,0xf000f0,true);v.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setNormal(0,1,0);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){tick++;var it=STATES.values().iterator();while(it.hasNext()){var s=it.next();if(tick-s.lastSeen>200){if(s.id!=null)Minecraft.getInstance().getTextureManager().release(s.id);it.remove();}}}
 private static void trim(State keep){long bytes=0;for(var state:STATES.values())if(state.image!=null&&state.texture!=null)bytes+=(long)state.image.columns()*state.image.rows()*4;while(bytes>32*1024*1024||STATES.size()>128){Key oldest=null;long seen=Long.MAX_VALUE;for(var entry:STATES.entrySet())if(entry.getValue()!=keep&&entry.getValue().lastSeen<seen){oldest=entry.getKey();seen=entry.getValue().lastSeen;}if(oldest==null)break;var removed=STATES.remove(oldest);if(removed.image!=null)bytes-=(long)removed.image.columns()*removed.image.rows()*4;if(removed.id!=null)Minecraft.getInstance().getTextureManager().release(removed.id);}}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clear();world=null;}
 public static boolean loading(BlockPos pos,int slot){var state=STATES.get(new Key(pos,slot));return state!=null&&(state.loading||state.motion.settling(now()));}
 public static NativeNavigationImage image(BlockPos pos,int slot){var state=STATES.get(new Key(pos,slot));return state==null?null:state.image;}
 public static void clear(){for(var s:STATES.values())if(s.id!=null)Minecraft.getInstance().getTextureManager().release(s.id);STATES.clear();}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static class Registration{@SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(NativeNavigationClient::install);}@SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->clear());}}
 private NativeNavigationClient(){}
}
