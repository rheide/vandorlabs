package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;

/** Small immutable frames; no per-frame mesh or chip-design decoding. */
final class ScreenChipRenderer {
    private static final Map<IBlockState,FloatBuffer> BASES=new HashMap<>();
    private static final Map<ScreenSurface.Quad,Canvas> DISPLAYS=new WeakHashMap<>();
    static void reader(TileEntityAnimatedScreenSelector tile,IBlockState state,double x,double y,double z,float partial){
        if(!ScreenChipSupport.selected(tile))return;MachineMountSurface surface=ScreenChipSupport.mount(state,tile);
        FloatBuffer matrix=BASES.computeIfAbsent(state,key->{FloatBuffer b=BufferUtils.createFloatBuffer(16);b.put(new float[]{(float)surface.u.x,(float)surface.u.y,(float)surface.u.z,0,(float)surface.v.x,(float)surface.v.y,(float)surface.v.z,0,(float)-surface.normal.x,(float)-surface.normal.y,(float)-surface.normal.z,0,0,0,0,1});b.flip();return b;});
        TETokenSlot.orientedMount(tile.ownedChipReader(),surface,matrix,x,y,z,partial);
    }
    private static final class Canvas{final FloatBuffer matrix;final int height;Canvas(FloatBuffer matrix,int height){this.matrix=matrix;this.height=height;}}
    static int height(ScreenSurface.Quad q){return Math.max(136,(int)Math.round(320*Math.hypot(q.bottomRight.y-q.topRight.y,q.bottomRight.z-q.topRight.z)/(q.topRight.x-q.topLeft.x)));}
    static void display(TileEntityAnimatedScreenSelector tile,IBlockState state,int slot){
        ScreenSurface.Quad q=FrameSurfaceRenderer.content(tile,RedstoneScreenInteractions.surface(state,tile,slot));
        Canvas canvas=DISPLAYS.computeIfAbsent(q,key->{int height=height(q);FloatBuffer b=BufferUtils.createFloatBuffer(16);b.put(new float[]{(float)(q.topLeft.x-q.topRight.x)/320,0,0,0,0,(float)(q.bottomRight.y-q.topRight.y)/height,(float)(q.bottomRight.z-q.topRight.z)/height,0,0,q.ny,q.nz,0,(float)q.topRight.x,(float)q.topRight.y+q.ny*.02F,(float)q.topRight.z+q.nz*.02F,1});b.flip();return new Canvas(b,height);});
        GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){canvas.matrix.rewind();GlStateManager.multMatrix(canvas.matrix);ProgrammerJournalRenderer.contents(ScreenChipSupport.stack(tile),tile.chipDisplay(),tile.chipPage,canvas.height);}finally{GlStateManager.popMatrix();}
    }
}
