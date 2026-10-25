package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.FrameMachineDesign;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;

/** Exercise moving baked parts independently of the stationary enclosure. */
public final class MachineMotionChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Machine motion: "+why);}
    public static void run(Minecraft mc){
        TileEntityConstructor tile=new TileEntityConstructor();tile.setWorld(mc.world);tile.setPos(new BlockPos(987,90,987));
        require(MachineVisualMotion.sampleAt(tile,false,20,0).progress==0,"closed initialization");
        require(Math.abs(MachineVisualMotion.sampleAt(tile,true,20,10).progress-.5)<1e-9,"half opening");
        require(Math.abs(MachineVisualMotion.sampleAt(tile,true,20,10).progress-.5)<1e-9,"repeat packet/time keeps pose");
        TileEntityConstructor replacement=new TileEntityConstructor();replacement.setWorld(mc.world);replacement.setPos(tile.getPos());
        require(Math.abs(MachineVisualMotion.sampleAt(replacement,false,20,15).progress-.25)<1e-9,"replacement and reversal preserve current pose");
        require(MachineVisualMotion.sampleAt(replacement,false,20,40).progress==0,"closure settles");
        require(MachineVisualMotion.sampleAt(replacement,true,20,60).progress==1,"opening settles");
        int cases=0;
        for(FrameMachineDesign spec:FrameMachineDesign.all())if(spec.motion!=null){
            String folder=spec.state_models[2].split("/")[0]+"/parts/";
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int step=0;step<=20;step++){
                if("shutters".equals(spec.motion))for(String part:new String[]{"shutter_upper","shutter_lower"}){
                    double travel=.5125*MachineVisualMotion.eased(step/20.0)*(part.endsWith("upper")?1:-1);
                    check(spec,folder+part+".obj",facing,travel,0);cases++;
                }else{
                    double angle=Math.toRadians(-55+110*step/20.0);check(spec,folder+"rotor.obj",facing,0,angle);cases++;
                }
            }
        }
        System.out.println("[vandorlabs] machine-motion PASS cases="+cases+" actual baked shutter/dish geometry four facings swept bounds continuous reversal repeated packets tile replacement");
    }
    private static void check(FrameMachineDesign spec,String path,EnumFacing facing,double offsetY,double yaw){
        UniversalConstructorModelCache.Mesh mesh=UniversalConstructorModelCache.get(spec.design,path);
        BufferBuilder buffer=new BufferBuilder(mesh.getQuadCount()*4*32);buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
        double px=spec.motion_pivot==null?0:spec.motion_pivot[0],pz=spec.motion_pivot==null?0:spec.motion_pivot[2];
        mesh.emitTransformed(buffer,0,0,0,facing,spec.width,spec.depth,0,offsetY,0,15728880,yaw,px,pz);buffer.finishDrawing();
        require(buffer.getVertexCount()==mesh.getQuadCount()*4,"part submitted once");
        ByteBuffer vertices=buffer.getByteBuffer();int stride=DefaultVertexFormats.BLOCK.getNextOffset();double width=facing.getAxis()==EnumFacing.Axis.X?spec.depth:spec.width,depth=facing.getAxis()==EnumFacing.Axis.X?spec.width:spec.depth;
        for(int i=0;i<buffer.getVertexCount();i++){
            float x=vertices.getFloat(i*stride),y=vertices.getFloat(i*stride+4),z=vertices.getFloat(i*stride+8);
            require(x>=-.001&&x<=width+.001&&y>=-.001&&y<=spec.height+.001&&z>=-.001&&z<=depth+.001,"animated vertex within reserved envelope "+spec.design+" "+facing);
        }
    }
    private MachineMotionChecks(){}
}
