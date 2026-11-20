package com.vandorlabs.client.constructors;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.blocks.FrameMachineDesign;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class TEConstructor extends TileEntitySpecialRenderer<TileEntityConstructor> {
    private static final java.util.Map<FrameMachineDesign,String[][]> OBSERVER_KEYS=new java.util.IdentityHashMap<>();
    private static String[][] observerKeys(FrameMachineDesign spec){
        String[][] keys=OBSERVER_KEYS.get(spec);if(keys!=null)return keys;keys=new String[spec.state_models.length][2];
        for(int state=0;state<keys.length;state++){String model=spec.state_models[state];if("dish".equals(spec.motion)){String folder=spec.design+"/"+model.substring(0,model.lastIndexOf('/')+1)+"parts/";keys[state][0]=folder+"static.obj";keys[state][1]=folder+"rotor.obj";}else keys[state][0]=spec.design+"/"+model;}
        OBSERVER_KEYS.put(spec,keys);return keys;
    }
    private final UniversalConstructorFastTESR<TileEntityConstructor> body=new UniversalConstructorFastTESR<TileEntityConstructor>(){
        @Override protected boolean isController(TileEntityConstructor tile){return tile.renderReady()&&com.vandorlabs.gear.client.CoreArmHandoff.allowed(tile);}
        @Override protected String machineId(TileEntityConstructor tile){return tile.core().design;}
        @Override protected EnumFacing facing(TileEntityConstructor tile){return tile.facing();}
        @Override protected BlockPos originOffset(TileEntityConstructor tile){return tile.renderOriginOffset();}
        @Override protected void drawMachine(TileEntityConstructor tile,double x,double y,double z,float partial,BufferBuilder buffer){
            FrameMachineDesign spec=tile.core().specification();
            int state=tile.screenOn()?tile.activity():0;
            if(com.vandorlabs.navigation.ObserverScale.observer(spec.design)){
                state=tile.activity();
                String[] keys=observerKeys(spec)[state];com.vandorlabs.machines.CoreLayout shape=tile.layout();double scale=shape.width/(double)spec.width;EnumFacing facing=EnumFacing.getFront(shape.facing);boolean inverted=tile.inverted();int light=packedLight(tile);
                observerPort(buffer,x+shape.width/2.0,y-.0005,z+shape.depth/2.0,light,tile.observerPowered());
                if("dish".equals(spec.motion)){
                    MachineVisualMotion.Pose pose=MachineVisualMotion.sample(tile,state==2,10);double yaw=Math.toRadians(55)*Math.sin((pose.now-pose.phase)*Math.PI/80)*MachineVisualMotion.eased(pose.progress);
                    UniversalConstructorModelCache.getByKey(keys[0]).emitObserver(buffer,x,y,z,facing,spec.width,spec.height,spec.depth,light,scale,inverted,0,0,0);
                    UniversalConstructorModelCache.getByKey(keys[1]).emitObserver(buffer,x,y,z,facing,spec.width,spec.height,spec.depth,light,scale,inverted,yaw,spec.motion_pivot[0],spec.motion_pivot[2]);
                }else UniversalConstructorModelCache.getByKey(keys[0]).emitObserver(buffer,x,y,z,facing,spec.width,spec.height,spec.depth,light,scale,inverted,0,0,0);
                return;
            }
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
            }else if("enclosed_gantry".equals(spec.design)){
                BuilderFabricationMotion.Pose pose=BuilderFabricationMotion.sample(tile);
                drawPart(tile,"static.obj",buffer,x,y,z,0,0,0);
                drawPart(tile,"gantry_arm.obj",buffer,x,y,z,0,0,pose.armZ());
                drawPart(tile,"gantry_head.obj",buffer,x,y,z,pose.headX(),0,pose.armZ());
            }else{
                String path=spec.state_models!=null?spec.state_models[state]:spec.hasChip()?(tile.screenOn()?"active/static.obj":"static.obj"):"complete.obj";
                drawPart(tile,path,buffer,x,y,z,0,0,0);
            }
        }
    };
    private static void observerPort(BufferBuilder buffer,double x,double y,double z,int light,boolean powered){
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft()
                .getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/resource_ports/power_in_"+(powered?"on":"off"));
        float u0=sprite.getMinU(),u1=sprite.getMaxU(),v0=sprite.getMinV(),v1=sprite.getMaxV();
        observerPortVertex(buffer,x-.375,y,z-.375,u0,v0,light);
        observerPortVertex(buffer,x+.375,y,z-.375,u1,v0,light);
        observerPortVertex(buffer,x+.375,y,z+.375,u1,v1,light);
        observerPortVertex(buffer,x-.375,y,z+.375,u0,v1,light);
    }
    private static void observerPortVertex(BufferBuilder buffer,double x,double y,double z,float u,float v,int light){
        buffer.pos(x,y,z).color(.5F,.5F,.5F,1F).tex(u,v).lightmap(light&65535,light>>>16&65535).endVertex();
    }
    @Override public void renderTileEntityFast(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha,BufferBuilder buffer){if(com.vandorlabs.gear.client.CoreArmHandoff.allowed(tile)){com.vandorlabs.gear.client.CoreArmHandoff.drawn(tile);body.renderTileEntityFast(tile,x,y,z,partial,stage,alpha,buffer);}}
    @Override public void render(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!tile.renderReady()||!com.vandorlabs.gear.client.CoreArmHandoff.allowed(tile))return;
        com.vandorlabs.gear.client.CoreArmHandoff.drawn(tile);
        if(tile.builder()){
            try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
                // The authored chamber glass is binary-alpha cutout geometry.
                // Blending alone still writes invisible glass depth over the fabrication volume.
                net.minecraft.client.renderer.GlStateManager.enableAlpha();
                net.minecraft.client.renderer.GlStateManager.disableCull();
                net.minecraft.client.renderer.GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,.1F);
                renderMachine(tile,x,y,z,partial,stage,alpha);
            }
        }else renderMachine(tile,x,y,z,partial,stage,alpha);
    }
    private void renderMachine(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha){
        body.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
        body.render(tile,x,y,z,partial,stage,alpha);
        com.vandorlabs.client.MachineDisplayRenderer.render(tile,x,y,z);
        SynthesisTowerRenderer.render(tile,x,y,z,partial);
        com.vandorlabs.blocks.MachineMountSurface mount=tile.mount();if(mount!=null){if(tile.builder())com.vandorlabs.client.TETokenSlot.embedded(tile,mount,x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ(),partial);else com.vandorlabs.client.TETokenSlot.nativeMount(tile,mount,x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ(),partial);}
    }
}
