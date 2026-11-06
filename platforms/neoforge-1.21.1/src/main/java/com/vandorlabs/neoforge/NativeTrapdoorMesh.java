package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.render.TrapdoorSurfaceLayout;
import com.vandorlabs.render.TrapdoorFrameLayout;
import com.vandorlabs.segmented.SegmentedPanelMesh;

/** Immutable affine leaf layout; the active consumer owns its native or extended vertex format. */
public final class NativeTrapdoorMesh {
    private static final ThreadLocal<Emission> EMISSION=ThreadLocal.withInitial(Emission::new);
    private final TrapdoorSurfaceLayout surface;
    private final TrapdoorFrameLayout frame;
    private final SegmentedPanelMesh.Texture upper,lower,edge;
    private NativeTrapdoorMesh(TrapdoorSurfaceLayout surface,TrapdoorFrameLayout frame,
                               SegmentedPanelMesh.Texture upper,SegmentedPanelMesh.Texture lower,
                               SegmentedPanelMesh.Texture edge){
        if(upper==null||lower==null||edge==null)throw new IllegalArgumentException("Trapdoor material");
        this.surface=surface;this.frame=frame;this.upper=upper;this.lower=lower;this.edge=edge;
    }
    public static NativeTrapdoorMesh surface(TrapdoorSurfaceLayout layout,SegmentedPanelMesh.Texture upper,SegmentedPanelMesh.Texture lower){
        if(layout==null)throw new IllegalArgumentException("Trapdoor surface");return new NativeTrapdoorMesh(layout,null,upper,lower,lower);
    }
    public static NativeTrapdoorMesh frame(TrapdoorFrameLayout layout,SegmentedPanelMesh.Texture artwork,SegmentedPanelMesh.Texture edge){
        if(layout==null)throw new IllegalArgumentException("Trapdoor frame");return new NativeTrapdoorMesh(null,layout,artwork,artwork,edge);
    }
    public long retainedBytes(){return 96+(surface==null?frame.retainedBytes():surface.retainedBytes());}
    /** Caller-owned 8x3 corner/scratch arrays are reused; no cached object retains live frame state. */
    public void emit(double[][] corners,double[][] frameScratch,PoseStack.Pose matrix,VertexConsumer target,int light,int overlay){
        Emission emission=EMISSION.get();if(emission.target!=null)throw new IllegalStateException("Reentrant Trapdoor emission");
        emission.matrix=matrix;emission.target=target;emission.light=light;emission.overlay=overlay;emission.upper=upper;emission.lower=lower;emission.edge=edge;
        try{if(surface!=null)surface.emit(corners,emission.artwork);else frame.emit(corners,frameScratch,emission.artwork,emission.edges);}
        finally{emission.matrix=null;emission.target=null;emission.upper=null;emission.lower=null;emission.edge=null;}
    }
    public static void emitEdges(double[][] corners,boolean diagonal,boolean tall,SegmentedPanelMesh.Texture edge,
                                 PoseStack.Pose matrix,VertexConsumer target,int light,int overlay){
        Emission emission=EMISSION.get();if(emission.target!=null)throw new IllegalStateException("Reentrant Trapdoor edges");
        emission.matrix=matrix;emission.target=target;emission.light=light;emission.overlay=overlay;emission.edge=edge;
        try{com.vandorlabs.render.TrapdoorRigidEdges.emit(corners,diagonal,tall,emission.edges);}
        finally{emission.matrix=null;emission.target=null;emission.edge=null;}
    }
    private static final class Emission {
        final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();
        PoseStack.Pose matrix;VertexConsumer target;int light,overlay;SegmentedPanelMesh.Texture upper,lower,edge;
        final TrapdoorSurfaceLayout.Sink artwork=(x,y,z,u,v,half,nx,ny,nz)->vertex(half?lower:upper,x,y,z,u,v,nx,ny,nz);
        final TrapdoorSurfaceLayout.Sink edges=(x,y,z,u,v,half,nx,ny,nz)->vertex(edge,x,y,z,u,v,nx,ny,nz);
        void vertex(SegmentedPanelMesh.Texture sprite,double x,double y,double z,double u,double v,float nx,float ny,float nz){
            matrix.pose().transformPosition((float)x,(float)y,(float)z,position);matrix.transformNormal(nx,ny,nz,normal);
            target.addVertex(position.x,position.y,position.z).setColor(255,255,255,255).setUv(sprite.u(u*16),sprite.v(v*16))
                    .setOverlay(overlay).setLight(light).setNormal(normal.x,normal.y,normal.z);
        }
    }
}
