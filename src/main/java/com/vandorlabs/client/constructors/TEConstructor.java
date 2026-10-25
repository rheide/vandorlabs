package com.vandorlabs.client.constructors;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.blocks.FrameMachineDesign;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class TEConstructor extends TileEntitySpecialRenderer<TileEntityConstructor> {
    private final UniversalConstructorFastTESR<TileEntityConstructor> body=new UniversalConstructorFastTESR<TileEntityConstructor>(){
        @Override protected boolean isController(TileEntityConstructor tile){return tile.renderReady();}
        @Override protected String machineId(TileEntityConstructor tile){return tile.core().design;}
        @Override protected EnumFacing facing(TileEntityConstructor tile){return tile.facing();}
        @Override protected BlockPos originOffset(TileEntityConstructor tile){return BlockPos.ORIGIN.subtract(tile.pivot());}
        @Override protected void drawMachine(TileEntityConstructor tile,double x,double y,double z,float partial,BufferBuilder buffer){
            FrameMachineDesign spec=tile.core().specification();
            int state=tile.screenOn()?tile.activity():0;
            if(tile.isDrive()&&(spec.design.equals("longburn_drive")||tile.screenOn()&&spec.design.equals("meridian_core"))){
                String driveFolder=tile.screenOn()?"active/":"";drawPart(tile,driveFolder+"drive_body.obj",buffer,x,y,z,0,0,0);
                float brightness=tile.screenOn()?(tile.driveSettings().levels?tile.driveLevel()/15F:1):.35F;
                if(spec.design.equals("meridian_core")){double now=MachineVisualMotion.sample(tile,true,10).now;brightness*=.55F+.45F*(float)(.5-.5*Math.cos(now*Math.PI/20));}
                UniversalConstructorModelCache.get(spec.design,driveFolder+"drive_lights.obj").emitTinted(buffer,x,y,z,tile.facing(),spec.width,spec.depth,0,0,0,15728880,brightness);
            }else if("shutters".equals(spec.motion)){
                MachineVisualMotion.Pose pose=MachineVisualMotion.sample(tile,state==2,20);
                int appearance=pose.progress>0&&pose.progress<1?1:state;
                String folder=spec.state_models[appearance].split("/")[0]+"/parts/";
                drawPart(tile,folder+"static.obj",buffer,x,y,z,0,0,0);
                double travel=.5125*MachineVisualMotion.eased(pose.progress);
                drawPart(tile,folder+"shutter_upper.obj",buffer,x,y,z,0,travel,0);
                drawPart(tile,folder+"shutter_lower.obj",buffer,x,y,z,0,-travel,0);
                UniversalConstructorModelCache.get(spec.design,folder+"field.obj").emitTinted(buffer,x,y,z,tile.facing(),spec.width,spec.depth,0,0,0,appearance==0?packedLight(tile):15728880,tile.isDrive()&&tile.driveSettings().levels?tile.driveLevel()/15F:1);
            }else if("dish".equals(spec.motion)){
                MachineVisualMotion.Pose pose=MachineVisualMotion.sample(tile,state==2,10);
                String folder=spec.state_models[state].split("/")[0]+"/parts/";
                drawPart(tile,folder+"static.obj",buffer,x,y,z,0,0,0);
                double yaw=Math.toRadians(55)*Math.sin((pose.now-pose.phase)*Math.PI/80)*MachineVisualMotion.eased(pose.progress);
                UniversalConstructorModelCache.get(spec.design,folder+"rotor.obj").emitTransformed(buffer,x,y,z,tile.facing(),spec.width,spec.depth,0,0,0,packedLight(tile),yaw,spec.motion_pivot[0],spec.motion_pivot[2]);
            }else{
                String path=spec.state_models!=null?spec.state_models[state]:spec.hasChip()?(tile.screenOn()?"active/static.obj":"static.obj"):"complete.obj";
                drawPart(tile,path,buffer,x,y,z,0,0,0);
            }
        }
    };
    @Override public void renderTileEntityFast(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha,BufferBuilder buffer){body.renderTileEntityFast(tile,x,y,z,partial,stage,alpha,buffer);}
    @Override public void render(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!tile.renderReady())return;
        body.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
        body.render(tile,x,y,z,partial,stage,alpha);
        com.vandorlabs.client.MachineDisplayRenderer.render(tile,x,y,z);
        com.vandorlabs.blocks.MachineMountSurface mount=tile.mount();if(mount!=null)com.vandorlabs.client.TETokenSlot.nativeMount(tile,mount,x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ(),partial);
    }
}
