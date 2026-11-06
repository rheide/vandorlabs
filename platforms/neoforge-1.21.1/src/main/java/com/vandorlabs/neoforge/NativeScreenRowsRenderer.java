package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.screen.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Prepared standalone row canvas. Cached font geometry contains display choices, never channel banks or hosts. */
public final class NativeScreenRowsRenderer implements NativeScreenSurfaceConsumer {
    private static final long TEXT_BUDGET=4L*1024*1024;
    private record Choice(String label,boolean slider,boolean pulse){}
    private record Key(String title,List<Choice> rows,int height,int active){}
    private static final class Selector {
        final String title;final List<Choice> rows;final int bytes;Key last;
        Selector(NativeScreenControls controls){title=controls.title();var choices=new ArrayList<Choice>();long size=512L+title.length()*2L;for(var row:controls.rows()){choices.add(new Choice(row.label(),row.slider(),row.pulse()));size+=96L+row.label().length()*2L;}rows=List.copyOf(choices);bytes=(int)Math.min(Integer.MAX_VALUE,size);}
        Key key(int height,int active){if(last==null||last.height!=height||last.active!=active)last=new Key(title,rows,height,active);return last;}
    }
    private record Glyphs(RenderType type,int[] words){}
    private record TextMesh(List<Glyphs> layers,long bytes){}
    private static final class Types extends RenderType {
        private Types(){super("vandorlabs_screen_rows",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
        static final RenderType SOLID=RenderType.create("vandorlabs_screen_rows",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
    }
    private static final class Recorder implements VertexConsumer {
        int[] words=new int[128];int vertices,at;
        public VertexConsumer addVertex(float x,float y,float z){if(vertices>=4096)throw new IllegalArgumentException("Screen glyph vertex bound");at=vertices++*7;if(at+7>words.length)words=Arrays.copyOf(words,Math.min(4096*7,Math.max(at+7,words.length*2)));words[at]=Float.floatToRawIntBits(x);words[at+1]=Float.floatToRawIntBits(y);words[at+2]=Float.floatToRawIntBits(z);words[at+3]=-1;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){words[at+3]=a<<24|r<<16|g<<8|b;return this;}public VertexConsumer setUv(float u,float v){words[at+4]=Float.floatToRawIntBits(u);words[at+5]=Float.floatToRawIntBits(v);return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){words[at+6]=(v<<16)|u;return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private final Font font;
    private static final class WeakKey extends java.lang.ref.WeakReference<NativeScreenControls> {
        final int hash;
        WeakKey(NativeScreenControls controls,java.lang.ref.ReferenceQueue<NativeScreenControls> queue){super(controls,queue);hash=System.identityHashCode(controls);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||get()!=null&&other instanceof WeakKey key&&get()==key.get();}
    }
    private static final class Lookup {
        NativeScreenControls controls;
        @Override public int hashCode(){return System.identityHashCode(controls);}
        @Override public boolean equals(Object other){return other instanceof WeakKey key&&controls==key.get();}
    }
    private final java.lang.ref.ReferenceQueue<NativeScreenControls> retired=new java.lang.ref.ReferenceQueue<>();
    private final LinkedHashMap<WeakKey,Selector> selectors=new LinkedHashMap<>();
    private final Lookup lookup=new Lookup();private long selectorBytes;
    private Selector selector(NativeScreenControls controls){
        WeakKey key;while((key=(WeakKey)retired.poll())!=null){var value=selectors.remove(key);if(value!=null)selectorBytes-=value.bytes;}
        lookup.controls=controls;Selector found;try{found=selectors.get(lookup);}finally{lookup.controls=null;}if(found!=null)return found;
        found=new Selector(controls);if(found.bytes<=512*1024){selectors.put(new WeakKey(controls,retired),found);selectorBytes+=found.bytes;while(selectorBytes>512*1024){var iterator=selectors.values().iterator();selectorBytes-=iterator.next().bytes;iterator.remove();}}return found;
    }
    private LinkedHashMap<Key,TextMesh> text=new LinkedHashMap<>(32,.75F,true);private long bytes,builds;
    private final Matrix4f ui=new Matrix4f(),matrix=new Matrix4f();private VertexConsumer solid;
    private final ScreenControlOverlay.Sink rectangles=new ScreenControlOverlay.Sink(){
        public void rectangle(int l,int t,int r,int b,double z,int color){vertex(solid,matrix,l,b,(float)z,color,0,0,0,false);vertex(solid,matrix,r,b,(float)z,color,0,0,0,false);vertex(solid,matrix,r,t,(float)z,color,0,0,0,false);vertex(solid,matrix,l,t,(float)z,color,0,0,0,false);}
        public void text(String value,int x,int y,int color,int width){}
    };
    public NativeScreenRowsRenderer(Font font){this.font=Objects.requireNonNull(font);}
    /** Source's exact float UI affine, before its owning role matrix and caller transform. */
    public static void uiMatrix(ScreenSurface.Quad q,int height,Matrix4f target){
        if(height!=64&&height!=128)throw new IllegalArgumentException("Screen row height");target.zero().m00((float)(q.topLeft.x-q.topRight.x)/128).m11((float)(q.bottomRight.y-q.topRight.y)/height).m12((float)(q.bottomRight.z-q.topRight.z)/height).m21(q.ny).m22(q.nz).m30((float)q.topRight.x).m31((float)q.topRight.y+q.ny*.02F).m32((float)q.topRight.z+q.nz*.02F).m33(1);
    }
    private static void vertex(VertexConsumer consumer,Matrix4fc pose,float x,float y,float z,int color,float u,float v,int light,boolean glyph){
        float px=pose.m00()*x+pose.m10()*y+pose.m20()*z+pose.m30(),py=pose.m01()*x+pose.m11()*y+pose.m21()*z+pose.m31(),pz=pose.m02()*x+pose.m12()*y+pose.m22()*z+pose.m32();consumer.addVertex(px,py,pz).setColor(color);if(glyph)consumer.setUv(u,v).setUv2(light&65535,light>>>16);
    }
    private TextMesh build(NativeScreenControls controls,int height,long now,Key key){
        var captured=new LinkedHashMap<RenderType,Recorder>();MultiBufferSource target=type->captured.computeIfAbsent(type,ignored->new Recorder());var depth=new Matrix4f().translation(0,0,.04F);
        ScreenControlOverlay.emit(controls,height,height==64?4:8,now,new ScreenControlOverlay.Sink(){public void rectangle(int l,int t,int r,int b,double z,int color){}public void text(String value,int x,int y,int color,int width){font.drawInBatch(width<0?value:font.plainSubstrByWidth(value,width),x,y,0xff000000|color,false,depth,target,Font.DisplayMode.NORMAL,0,0x00f000f0);}});
        var layers=new ArrayList<Glyphs>();long retained=512L+key.title.length()*2L;for(var choice:key.rows)retained+=96L+choice.label.length()*2L;for(var layer:captured.entrySet()){int[] words=Arrays.copyOf(layer.getValue().words,layer.getValue().vertices*7);retained+=96L+words.length*4L;layers.add(new Glyphs(layer.getKey(),words));}builds++;return new TextMesh(List.copyOf(layers),retained);
    }
    private TextMesh text(NativeScreenControls controls,int height,long now){
        var selector=selector(controls);int active=0;for(int row=0;row<Math.min(controls.rows().size(),height==64?4:8);row++)if(controls.rows().get(row).active(now))active|=1<<row;Key key=selector.key(height,active);var found=text.get(key);if(found!=null)return found;found=build(controls,height,now,key);if(found.bytes<=TEXT_BUDGET){text.put(key,found);bytes+=found.bytes;while(bytes>TEXT_BUDGET){var iterator=text.values().iterator();bytes-=iterator.next().bytes;iterator.remove();}}return found;
    }
    @Override public void emit(NativeScreenRenderPlan.Binding binding,NativeScreenEntity.Visual visual,ScreenFamily.State state,int slot,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int packedLight,int overlay,long controlTime){
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();if(binding!=NativeScreenRenderPlan.Binding.ROWS)throw new IllegalArgumentException("Standalone row consumer only");int height=visual.family().half(slot)?64:128;var controls=visual.controls().get(slot);var glyphs=text(controls,height,controlTime);uiMatrix(content,height,ui);matrix.set(ownerPoses.last().pose()).mul(pixelToOwner).mul(ui);solid=buffers.getBuffer(Types.SOLID);try{ScreenControlOverlay.emit(controls,height,visual.family().standaloneControlRows(slot),controlTime,rectangles);}finally{solid=null;}
        for(int index=0;index<glyphs.layers.size();index++){var layer=glyphs.layers.get(index);var target=buffers.getBuffer(layer.type);for(int at=0;at<layer.words.length;at+=7)vertex(target,matrix,Float.intBitsToFloat(layer.words[at]),Float.intBitsToFloat(layer.words[at+1]),Float.intBitsToFloat(layer.words[at+2]),layer.words[at+3],Float.intBitsToFloat(layer.words[at+4]),Float.intBitsToFloat(layer.words[at+5]),packedLight,true);}
    }
    public long builds(){return builds;}public long retainedBytes(){return bytes+selectorBytes;}
    /** A new bake/font generation owns a new consumer; explicit clears also retire peak table capacity. */
    public void clear(){selectors.clear();selectorBytes=0;while(retired.poll()!=null){}text=new LinkedHashMap<>(32,.75F,true);bytes=0;}
}
