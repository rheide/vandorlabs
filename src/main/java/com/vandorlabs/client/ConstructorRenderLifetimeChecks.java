package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.client.constructors.TEConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Exercise both Forge renderer entry points with retained tiles after removal. */
final class ConstructorRenderLifetimeChecks {
    static void run(Minecraft mc){
        BlockPos pos=new BlockPos(mc.player.posX+2,90,mc.player.posZ+2);int cases=0;
        if(!mc.world.isBlockLoaded(pos))throw new IllegalStateException("Constructor lifetime fixture chunk");
        try{for(FrameMachineDesign spec:FrameMachineDesign.all())for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockConstructorCore block=(BlockConstructorCore)net.minecraft.block.Block.getBlockFromName("vandorlabs:"+spec.block);
            mc.world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing).withProperty(BlockConstructorCore.FORMED,true),2);
            TileEntityConstructor tile=(TileEntityConstructor)mc.world.getTileEntity(pos);tile.getRenderBoundingBox();tile.hasFastRenderer();
            TEConstructor renderer=(TEConstructor)net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.<TileEntityConstructor>getRenderer(tile);
            mc.world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);
            renderer.render(tile,0,0,0,0,-1,1); // Part removal first leaves an unformed core.
            mc.world.setBlockToAir(pos);
            for(int pass=0;pass<2;pass++){
                if(!tile.getRenderBoundingBox().equals(new AxisAlignedBB(pos))||tile.formed())throw new IllegalStateException("Retained core bounds in main/shadow query");
                tile.hasFastRenderer();renderer.render(tile,0,0,0,0,-1,1);
                BufferBuilder buffer=new BufferBuilder(256);buffer.begin(7,DefaultVertexFormats.BLOCK);renderer.renderTileEntityFast(tile,0,0,0,0,-1,1,buffer);if(buffer.getVertexCount()!=0)throw new IllegalStateException("Removed core emitted geometry");buffer.finishDrawing();
            }
            mc.world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);tile.getRenderBoundingBox();renderer.render(tile,0,0,0,0,-1,1);cases++;
        }}finally{mc.world.setBlockToAir(pos);}
        System.out.println("[vandorlabs] constructor-render-lifetime PASS cases="+cases+" rapid teardown retained tiles main/shadow bounds no stale fast/regular geometry");
    }
}
