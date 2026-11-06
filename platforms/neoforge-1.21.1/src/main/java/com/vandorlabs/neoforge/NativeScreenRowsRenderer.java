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
    private record TextMesh(NativeOverlayCanvas.Mesh mesh,long bytes){}
    private final NativeOverlayCanvas canvas;
    private final NativeOverlayCanvas.Selectors<NativeScreenControls,Selector> selectors=new NativeOverlayCanvas.Selectors<>(512*1024,value->value.bytes);
    private Selector selector(NativeScreenControls controls){var found=selectors.get(controls);if(found==null){found=new Selector(controls);selectors.put(controls,found);}return found;}
    private LinkedHashMap<Key,TextMesh> text=new LinkedHashMap<>(32,.75F,true);private long bytes,builds;
    private final Matrix4f ui=new Matrix4f(),matrix=new Matrix4f();private VertexConsumer solid;
    private final ScreenControlOverlay.Sink rectangles=new ScreenControlOverlay.Sink(){
        public void rectangle(int l,int t,int r,int b,double z,int color){NativeOverlayCanvas.rectangle(solid,matrix,l,t,r,b,z,color);}
        public void text(String value,int x,int y,int color,int width){}
    };
    public NativeScreenRowsRenderer(Font font){canvas=new NativeOverlayCanvas(font);}
    /** Source's exact float UI affine, before its owning role matrix and caller transform. */
    public static void uiMatrix(ScreenSurface.Quad q,int height,Matrix4f target){
        if(height!=64&&height!=128)throw new IllegalArgumentException("Screen row height");NativeOverlayCanvas.uiMatrix(q,128,height,target);
    }
    private TextMesh build(NativeScreenControls controls,int height,long now,Key key){
        var mesh=canvas.build(target->ScreenControlOverlay.emit(controls,height,height==64?4:8,now,new ScreenControlOverlay.Sink(){public void rectangle(int l,int t,int r,int b,double z,int color){}public void text(String value,int x,int y,int color,int width){target.trimmed(value,x,y,color,width);}}));
        long retained=512L+key.title.length()*2L+mesh.bytes();for(var choice:key.rows)retained+=96L+choice.label.length()*2L;builds++;return new TextMesh(mesh,retained);
    }
    private TextMesh text(NativeScreenControls controls,int height,long now){
        var selector=selector(controls);int active=0;for(int row=0;row<Math.min(controls.rows().size(),height==64?4:8);row++)if(controls.rows().get(row).active(now))active|=1<<row;Key key=selector.key(height,active);var found=text.get(key);if(found!=null)return found;found=build(controls,height,now,key);if(found.bytes<=TEXT_BUDGET){text.put(key,found);bytes+=found.bytes;while(bytes>TEXT_BUDGET){var iterator=text.values().iterator();bytes-=iterator.next().bytes;iterator.remove();}}return found;
    }
    @Override public void emit(NativeScreenRenderPlan.Binding binding,NativeScreenEntity.Visual visual,ScreenFamily.State state,int slot,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int packedLight,int overlay,long controlTime){
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();if(binding!=NativeScreenRenderPlan.Binding.ROWS)throw new IllegalArgumentException("Standalone row consumer only");int height=visual.family().half(slot)?64:128;var controls=visual.controls().get(slot);var glyphs=text(controls,height,controlTime);uiMatrix(content,height,ui);matrix.set(ownerPoses.last().pose()).mul(pixelToOwner).mul(ui);solid=NativeOverlayCanvas.solid(buffers);try{ScreenControlOverlay.emit(controls,height,visual.family().standaloneControlRows(slot),controlTime,rectangles);}finally{solid=null;}
        NativeOverlayCanvas.draw(glyphs.mesh,matrix,buffers,packedLight);
    }
    public long builds(){return builds;}public long retainedBytes(){return bytes+selectors.bytes();}
    /** A new bake/font generation owns a new consumer; explicit clears also retire peak table capacity. */
    public void clear(){selectors.clear();text=new LinkedHashMap<>(32,.75F,true);bytes=0;}
}
