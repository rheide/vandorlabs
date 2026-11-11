package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

/** Exercise the real fast X-panel emitter against the ordinary TESR's matrix stack. */
public final class DoorXBatchTravelChecks {
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static final float[][] POINTS={{0,0,.4375F},{1,0,.4375F},{1,2,.5625F},{0,2,.5625F}};
    private static final float[][] UV={{.125F,.25F},{.875F,.25F},{.875F,.75F},{.125F,.75F}};

    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();
        StaticSurfaceMesh[] panels=new StaticSurfaceMesh[4];
        for(int panel=0;panel<4;panel++){
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            for(int v=0;v<4;v++)capture.pos(POINTS[v][0],POINTS[v][1],POINTS[v][2])
                    .tex(UV[v][0],UV[v][1]).normal(0,0,1).endVertex();
            panels[panel]=capture.finish();
        }
        BufferBuilder buffer=new BufferBuilder(1024);int cases=0,baselineFailures=0;
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean framed:new boolean[]{false,true})
        for(int hand=0;hand<2;hand++)for(float progress:new float[]{0,.125F,.5F,.875F,1})
        for(float depth:new float[]{-.3275F,0,.125F})for(int light:new int[]{0,0x005000a0,0x00f000f0}){
            buffer.begin(7,DefaultVertexFormats.BLOCK);
            OpaqueDoorBatch.drawXPanels(panels,framed,face,progress,2,3,4,depth,light,1.5,hand*1.5,buffer);
            buffer.finishDrawing();need(buffer.getVertexCount()==16,"X batch dropped vertices");
            for(int panel=0;panel<4;panel++){
                // The ordinary renderer scales normalized travel; a bare leaf
                // stops 1/16 world block short of the framed endpoint.
                float distance=progress*(framed?1.5F:1.5F-1F/16);
                float shiftX=panel==0?-distance:panel==1?distance:0;
                float shiftY=panel==2?distance:panel==3?-distance:0;
                Matrix4f matrix=new Matrix4f();matrix.translate(new Vector3f(2,3,4));
                matrix.translate(new Vector3f(.5F,0,.5F));
                float facingAngle=face==EnumFacing.WEST?-90:face==EnumFacing.NORTH?180:face==EnumFacing.EAST?90:0;
                matrix.rotate((float)Math.toRadians(facingAngle),new Vector3f(0,1,0));
                matrix.translate(new Vector3f(-.5F,0,-.5F));matrix.translate(new Vector3f(hand*1.5F,0,0));
                matrix.translate(new Vector3f(shiftX,shiftY,depth));matrix.scale(new Vector3f(1.5F,1.5F,1));
                for(int v=0;v<4;v++){
                    Vector4f expected=Matrix4f.transform(matrix,new Vector4f(POINTS[v][0],POINTS[v][1],POINTS[v][2],1),null);
                    int offset=(panel*4+v)*28;
                    need(Math.abs(buffer.getByteBuffer().getFloat(offset)-expected.x)<2e-6,"X batch x differs from ordinary TESR");
                    need(Math.abs(buffer.getByteBuffer().getFloat(offset+4)-expected.y)<2e-6,"X batch y differs from ordinary TESR");
                    need(Math.abs(buffer.getByteBuffer().getFloat(offset+8)-expected.z)<2e-6,"X batch z differs from ordinary TESR");
                    need(buffer.getByteBuffer().getInt(offset+12)==-1,"X batch changed color");
                    need(buffer.getByteBuffer().getFloat(offset+16)==UV[v][0]
                            &&buffer.getByteBuffer().getFloat(offset+20)==UV[v][1],"X batch changed UVs");
                    need(buffer.getByteBuffer().getInt(offset+24)==light,"X batch baked stale light");
                }
            }
            buffer.reset();
            if(!framed&&progress>0){
                // Pinned e8cc78a1 fast loop, retained as a negative control.
                buffer.begin(7,DefaultVertexFormats.BLOCK);
                for(int panel=0;panel<4;panel++)panels[panel].batchMesh().draw(buffer,face,2,3,4,depth,light,
                        1.5*com.vandorlabs.render.XDoorPanel.shiftX(panel,progress),
                        1.5*com.vandorlabs.render.XDoorPanel.shiftY(panel,progress),0,0,0,1.5,hand*1.5);
                buffer.finishDrawing();
                // Panel 0 moves only horizontally; its local displacement
                // becomes X or Z depending on the door's world facing.
                int axis=face.getAxis()==EnumFacing.Axis.Z?0:2;
                float old=buffer.getByteBuffer().getFloat(axis*4);buffer.reset();
                buffer.begin(7,DefaultVertexFormats.BLOCK);
                OpaqueDoorBatch.drawXPanels(panels,false,face,progress,2,3,4,depth,light,1.5,hand*1.5,buffer);
                buffer.finishDrawing();float corrected=buffer.getByteBuffer().getFloat(axis*4);buffer.reset();
                need(Math.abs(Math.abs(old-corrected)-progress/16)<2e-6,"baseline no longer demonstrates bare-panel overshoot");
                baselineFailures++;
            }
            cases++;
        }
        System.out.println("door-X-batch PASS cases="+cases+" baseline-overshoot-cases="+baselineFailures
                +" four facings, both leaves, framed/bare, five poses, three depths and lights; full vertex records");
    }
}
