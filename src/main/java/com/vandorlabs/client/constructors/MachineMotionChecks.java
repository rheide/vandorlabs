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
    /** Small policy proof used by the actual Dish endpoint focus, without another scene matrix. */
    public static void handoff(Minecraft mc){
        TileEntityConstructor source=new TileEntityConstructor();source.setWorld(mc.world);source.setPos(new BlockPos(988,90,988));
        MachineVisualMotion.Pose original=MachineVisualMotion.sampleAt(source,true,10,100);
        TileEntityConstructor unrelated=new TileEntityConstructor();unrelated.setWorld(mc.world);unrelated.setPos(source.getPos().east());
        MachineVisualMotion.Pose independent=MachineVisualMotion.sampleAt(unrelated,true,10,119);
        BlockPos target=source.getPos().up();MachineVisualMotion.Pose owned=MachineVisualMotion.takeObserved(mc.world,source.getPos());require(owned==original,"source pose becomes owned by the carried machine");MachineVisualMotion.installObserved(mc.world,target,owned);
        TileEntityConstructor replacement=new TileEntityConstructor();replacement.setWorld(mc.world);replacement.setPos(target);
        require(MachineVisualMotion.sampleAt(replacement,true,10,125)==original&&original.phase==100,"endpoint replacement retains exact phase object");
        require(MachineVisualMotion.sampleAt(replacement,true,10,125).now==125,"repeated/paused samples do not advance");
        require(MachineVisualMotion.sampleAt(replacement,false,10,130).progress==.5,"activity reversal remains continuous after handoff");
        require(MachineVisualMotion.sampleAt(unrelated,true,10,130)==independent&&independent!=original&&independent.phase==119,"unrelated machine retains its independent phase");
        MachineVisualMotion.Pose reused=MachineVisualMotion.sampleAt(source,true,10,150);require(reused!=original&&reused.phase==150&&original.phase==100,"reusing the vacated original position cannot mutate carried phase");
        MachineVisualMotion.releaseObserved(mc.world,source.getPos(),original);require(MachineVisualMotion.sampleAt(source,true,10,155)==reused,"repeated carried headers cannot detach the unrelated replacement pose");
        require(MachineVisualMotion.takeObserved(mc.world,target.up(10))==null,"unobserved cells never prewarm a pose");
        System.out.println("[vandorlabs] machine-handoff-policy PASS exact pose/phase replacement repeated pause reversal unrelated isolation no prewarm");
    }
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
