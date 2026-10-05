package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

/** Compare CPU batching with the renderer's matrix stack without a GL context. */
final class OpaqueDoorBatchChecks {
    static void run() {
        int[] source=new int[28];
        float[][] points={{0,0,.75F},{1,0,.875F},{1,2,.875F},{0,2,.75F}};
        for(int v=0;v<4;v++) {
            for(int axis=0;axis<3;axis++)source[v*7+axis]=Float.floatToRawIntBits(points[v][axis]);
            source[v*7+3]=0xFF7193AB;source[v*7+4]=Float.floatToRawIntBits(.25F);
            source[v*7+5]=Float.floatToRawIntBits(.75F);source[v*7+6]=0x11223344;
        }
        int assertions=0;
        for(boolean panel:new boolean[]{false,true})for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)
            for(float scale:new float[]{1,1.5F})for(float leaf:new float[]{0,1.5F})
                for(float angle:new float[]{-90,-45,0,45,90})for(float slide:new float[]{-1.5F,0,1.5F}) {
                    float px=.09375F*scale,pz=.7025F,depth=.125F,vertical=slide*2;
                    Matrix4f matrix=new Matrix4f();
                    matrix.translate(new Vector3f(2,3,4));matrix.translate(new Vector3f(.5F,0,.5F));
                    float facingAngle=face==EnumFacing.WEST?-90:face==EnumFacing.NORTH?180:face==EnumFacing.EAST?90:0;
                    matrix.rotate((float)Math.toRadians(facingAngle),new Vector3f(0,1,0));
                    matrix.translate(new Vector3f(-.5F,0,-.5F));matrix.translate(new Vector3f(leaf,panel?(float)com.vandorlabs.render.SpaceDoorControlPanel.verticalOffset(scale):0,depth));
                    matrix.translate(new Vector3f(slide,vertical,0));matrix.translate(new Vector3f(px,0,pz));
                    matrix.rotate((float)Math.toRadians(angle),new Vector3f(0,1,0));
                    matrix.translate(new Vector3f(-px,0,-pz));matrix.scale(new Vector3f(scale,scale,1));
                    OpaqueDoorBatch.Mesh mesh=new OpaqueDoorBatch.Mesh(source,panel);
                    BufferBuilder buffer=new BufferBuilder(256);buffer.begin(7,DefaultVertexFormats.BLOCK);
                    int light=0x00C00050;
                    mesh.draw(buffer,face,2,3,4,depth,light,slide,vertical,px,pz,angle,scale,leaf);
                    buffer.finishDrawing();
                    require(buffer.getVertexCount()==4,"vertex count");
                    for(int v=0;v<4;v++) {
                        Vector4f expected=Matrix4f.transform(matrix,new Vector4f(points[v][0],points[v][1],points[v][2],1),null);
                        for(int axis=0;axis<3;axis++) {
                            float want=axis==0?expected.x:axis==1?expected.y:expected.z;
                            require(Math.abs(buffer.getByteBuffer().getFloat(v*28+axis*4)-want)<2e-6,"matrix pose differs");
                        }
                        for(int slot=3;slot<6;slot++)require(buffer.getByteBuffer().getInt(v*28+slot*4)==source[v*7+slot],"color/UV changed");
                        require(buffer.getByteBuffer().getInt(v*28+24)==(panel?(light>>>16)|(light<<16):light),"lightmap differs");
                        assertions+=7;
                    }
                    buffer.reset();
                }
        System.out.println("PASS: opaque door batch matrix parity, regular/large leaves, every facing, swing/slide poses, colors, UVs and panel lightmaps ("+assertions+" assertions; no GL)");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("door batch: "+message);}
}
