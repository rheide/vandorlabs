package com.vandorlabs.client.constructors;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.blocks.FrameMachineDesign;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class TEConstructor extends TileEntitySpecialRenderer<TileEntityConstructor> {
    private final UniversalConstructorFastTESR<TileEntityConstructor> body=new UniversalConstructorFastTESR<TileEntityConstructor>(){
        @Override protected boolean isController(TileEntityConstructor tile){return tile.formed();}
        @Override protected String machineId(TileEntityConstructor tile){return tile.core().design;}
        @Override protected EnumFacing facing(TileEntityConstructor tile){return tile.facing();}
        @Override protected BlockPos originOffset(TileEntityConstructor tile){return BlockPos.ORIGIN.subtract(tile.pivot());}
        @Override protected void drawMachine(TileEntityConstructor tile,double x,double y,double z,float partial,BufferBuilder buffer){
            FrameMachineDesign spec=tile.core().specification();
            String path=spec.state_models!=null?spec.state_models[tile.activity()]:spec.hasChip()&&tile.getOutputLevel()>0?"active/complete.obj":"complete.obj";
            drawPart(tile,path,buffer,x,y,z,0,0,0);
        }
    };
    @Override public void renderTileEntityFast(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha,BufferBuilder buffer){body.renderTileEntityFast(tile,x,y,z,partial,stage,alpha,buffer);}
    @Override public void render(TileEntityConstructor tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!tile.formed())return;
        body.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
        body.render(tile,x,y,z,partial,stage,alpha);
        com.vandorlabs.blocks.MachineMountSurface mount=tile.mount();if(mount!=null)com.vandorlabs.client.TETokenSlot.embedded(tile,mount,x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ(),partial);
    }
}
