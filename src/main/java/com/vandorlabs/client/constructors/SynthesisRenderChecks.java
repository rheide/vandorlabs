package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.FrameMachineDesign;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/** Validate actual cached atlas vertices used in both previews, including authored attachments. */
public final class SynthesisRenderChecks {
    public static void run(){
        int cases=0;
        for(String id:new String[]{"synthesis_tower","assembly_press","enclosed_gantry"}){
            FrameMachineDesign spec=FrameMachineDesign.get(id);
            UniversalConstructorModelCache.Mesh mesh=UniversalConstructorModelCache.get(id,"complete.obj");
            if(mesh.getQuadCount()<30)throw new IllegalStateException("Missing complete synthesis preview "+id);
            for(EnumFacing facing:EnumFacing.HORIZONTALS){
                BufferBuilder b=new BufferBuilder(65536);b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.BLOCK);
                mesh.emit(b,0,0,0,facing,spec.width,spec.depth,0,0,0,15728880);
                int count=b.getVertexCount(),stride=DefaultVertexFormats.BLOCK.getNextOffset();
                if(count!=mesh.getQuadCount()*4)throw new IllegalStateException("Preview dropped faces "+id);
                java.nio.ByteBuffer data=b.getByteBuffer();double radius=0;
                double width=facing.getAxis()==EnumFacing.Axis.X?spec.depth:spec.width,depth=facing.getAxis()==EnumFacing.Axis.X?spec.width:spec.depth;
                for(int v=0;v<count;v++){
                    double x=data.getFloat(v*stride),y=data.getFloat(v*stride+4),z=data.getFloat(v*stride+8);
                    if(!Double.isFinite(x+y+z)||x<-.5||x>width+.5||y<-.01||y>spec.height+.01||z<-.5||z>depth+.5)throw new IllegalStateException("Invalid emitted preview bounds "+id+" "+facing);
                    radius=Math.max(radius,Math.sqrt(Math.pow(x-width/2,2)+Math.pow(y-spec.height/2D,2)+Math.pow(z-depth/2,2)));
                    float u=data.getFloat(v*stride+16),w=data.getFloat(v*stride+20);
                    if(!Float.isFinite(u+w)||u<0||u>1||w<0||w>1)throw new IllegalStateException("Invalid atlas UV "+id);
                }
                if(radius*20>75)throw new IllegalStateException("GUI preview crosses panel at authored scale "+id);
                b.finishDrawing();cases++;
            }
        }
        System.out.println("[vandorlabs] synthesis-render PASS cases="+cases+" complete textured atlas geometry all facings finite UVs and rotating GUI fit");
    }
    private SynthesisRenderChecks(){}
}
