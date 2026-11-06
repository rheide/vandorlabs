package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.render.ScreenSurface;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Shared prepared pixel canvas for standalone controls and journal displays. No worlds, hosts or item stacks. */
final class NativeOverlayCanvas {
 interface Text {void trimmed(String value,int x,int y,int color,int width);void fitted(String value,int x,int y,int width,int color);void plain(String value,int x,int y,int color);void centered(String value,int center,int y,int color);}
 record Glyphs(RenderType type,int[] words){}
 record Mesh(List<Glyphs> layers,long bytes){}
 private static final class Types extends RenderType {
  private Types(){super("vandorlabs_overlay_canvas",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
  static final RenderType SOLID=RenderType.create("vandorlabs_overlay_canvas",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
 }
 private static final class Recorder implements VertexConsumer {
  int[] words=new int[128];int vertices,at;
  public VertexConsumer addVertex(float x,float y,float z){if(vertices>=8192)throw new IllegalArgumentException("Canvas glyph vertex bound");at=vertices++*7;if(at+7>words.length)words=Arrays.copyOf(words,Math.min(8192*7,Math.max(at+7,words.length*2)));words[at]=Float.floatToRawIntBits(x);words[at+1]=Float.floatToRawIntBits(y);words[at+2]=Float.floatToRawIntBits(z);words[at+3]=-1;return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){words[at+3]=a<<24|r<<16|g<<8|b;return this;}public VertexConsumer setUv(float u,float v){words[at+4]=Float.floatToRawIntBits(u);words[at+5]=Float.floatToRawIntBits(v);return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){words[at+6]=(v<<16)|u;return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
 }
 /** Main-render-thread weak identity selectors; values must contain display scalars only. */
 static final class Selectors<K,V> {
  private static final class WeakKey extends java.lang.ref.WeakReference<Object> {final int hash;WeakKey(Object key,java.lang.ref.ReferenceQueue<Object> queue){super(key,queue);hash=System.identityHashCode(key);}@Override public int hashCode(){return hash;}@Override public boolean equals(Object other){return this==other||get()!=null&&other instanceof WeakKey key&&get()==key.get();}}
  private static final class Lookup {Object key;@Override public int hashCode(){return System.identityHashCode(key);}@Override public boolean equals(Object other){return other instanceof WeakKey weak&&key==weak.get();}}
  private final java.lang.ref.ReferenceQueue<Object> retired=new java.lang.ref.ReferenceQueue<>();private final Lookup lookup=new Lookup();private final java.util.function.ToIntFunction<V> weigh;private final long budget;private LinkedHashMap<WeakKey,V> values=new LinkedHashMap<>();private long bytes;
  Selectors(long budget,java.util.function.ToIntFunction<V> weigh){this.budget=budget;this.weigh=weigh;}
  V get(K key){WeakKey dead;while((dead=(WeakKey)retired.poll())!=null){var old=values.remove(dead);if(old!=null)bytes-=weigh.applyAsInt(old);}lookup.key=key;try{return values.get(lookup);}finally{lookup.key=null;}}
  void put(K key,V value){int size=weigh.applyAsInt(value);if(size<0||size>budget)return;var old=values.put(new WeakKey(key,retired),value);if(old!=null)bytes-=weigh.applyAsInt(old);bytes+=size;while(bytes>budget){var iterator=values.values().iterator();bytes-=weigh.applyAsInt(iterator.next());iterator.remove();}}
  long bytes(){return bytes;}
  void clear(){values=new LinkedHashMap<>();bytes=0;while(retired.poll()!=null){}}
 }
 private final Font font;
 NativeOverlayCanvas(Font font){this.font=Objects.requireNonNull(font);}
 Mesh build(java.util.function.Consumer<Text> commands){
  var captured=new LinkedHashMap<RenderType,Recorder>();MultiBufferSource target=type->captured.computeIfAbsent(type,ignored->new Recorder());var depth=new Matrix4f().translation(0,0,.04F);var fit=new Matrix4f();
  commands.accept(new Text(){
   public void plain(String value,int x,int y,int color){font.drawInBatch(value,x,y,0xff000000|color,false,depth,target,Font.DisplayMode.NORMAL,0,0x00f000f0);}
   public void trimmed(String value,int x,int y,int color,int width){plain(width<0?value:font.plainSubstrByWidth(value,width),x,y,color);}
   public void fitted(String value,int x,int y,int width,int color){int measured=font.width(value);if(measured<=width){plain(value,x,y,color);return;}float scale=width/(float)measured;fit.translation(x,y,.04F).scale(scale,scale,1);font.drawInBatch(value,0,0,0xff000000|color,false,fit,target,Font.DisplayMode.NORMAL,0,0x00f000f0);}
   public void centered(String value,int center,int y,int color){plain(value,(center*2-font.width(value))/2,y,color);}
  });
  var layers=new ArrayList<Glyphs>();long retained=96;for(var layer:captured.entrySet()){int[] words=Arrays.copyOf(layer.getValue().words,layer.getValue().vertices*7);retained+=96L+words.length*4L;layers.add(new Glyphs(layer.getKey(),words));}return new Mesh(List.copyOf(layers),retained);
 }
 static VertexConsumer solid(MultiBufferSource buffers){return buffers.getBuffer(Types.SOLID);}
 static void uiMatrix(ScreenSurface.Quad q,int width,int height,Matrix4f target){target.zero().m00((float)(q.topLeft.x-q.topRight.x)/width).m11((float)(q.bottomRight.y-q.topRight.y)/height).m12((float)(q.bottomRight.z-q.topRight.z)/height).m21(q.ny).m22(q.nz).m30((float)q.topRight.x).m31((float)q.topRight.y+q.ny*.02F).m32((float)q.topRight.z+q.nz*.02F).m33(1);}
 static void vertex(VertexConsumer consumer,Matrix4fc pose,float x,float y,float z,int color,float u,float v,int light,boolean glyph){float px=pose.m00()*x+pose.m10()*y+pose.m20()*z+pose.m30(),py=pose.m01()*x+pose.m11()*y+pose.m21()*z+pose.m31(),pz=pose.m02()*x+pose.m12()*y+pose.m22()*z+pose.m32();consumer.addVertex(px,py,pz).setColor(color);if(glyph)consumer.setUv(u,v).setUv2(light&65535,light>>>16);}
 static void rectangle(VertexConsumer target,Matrix4fc matrix,int l,int t,int r,int b,double z,int color){vertex(target,matrix,l,b,(float)z,color,0,0,0,false);vertex(target,matrix,r,b,(float)z,color,0,0,0,false);vertex(target,matrix,r,t,(float)z,color,0,0,0,false);vertex(target,matrix,l,t,(float)z,color,0,0,0,false);}
 static void draw(Mesh mesh,Matrix4fc matrix,MultiBufferSource buffers,int light){for(int index=0;index<mesh.layers.size();index++){var layer=mesh.layers.get(index);var target=buffers.getBuffer(layer.type);for(int at=0;at<layer.words.length;at+=7)vertex(target,matrix,Float.intBitsToFloat(layer.words[at]),Float.intBitsToFloat(layer.words[at+1]),Float.intBitsToFloat(layer.words[at+2]),layer.words[at+3],Float.intBitsToFloat(layer.words[at+4]),Float.intBitsToFloat(layer.words[at+5]),light,true);}}
}
