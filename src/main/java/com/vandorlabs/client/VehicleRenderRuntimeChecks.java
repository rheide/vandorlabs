package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Paired renderer CPU benchmark; offscreen placement excludes fill rate and frame limiting. */
final class VehicleRenderRuntimeChecks {
    @SuppressWarnings("unchecked") static void run(Minecraft mc){
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(new BlockPos(16,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null));
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(new BlockPos(x,y,0));tile.setHousingTexture(14);
            cells.add(new VehicleStructure.Cell(tile.getPos(),ModBlocks.PROGRAMMABLE_WALL.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));
        }
        VehicleStructure structure=new VehicleStructure(cells,new BlockPos(16,0,0),EnumFacing.NORTH,1);VehicleWorld view=new VehicleWorld(mc.world,structure);
        VehicleOpaqueTileCache cache=new VehicleOpaqueTileCache();long[][] samples=new long[2][24];
        GlStateManager.pushMatrix();GlStateManager.translate(0,-10000,0);mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        try{
            for(int mode=0;mode<2;mode++)for(int round=-8;round<24;round++){
                VehicleOpaqueTileCache.enabled=mode==1;long began=System.nanoTime();
                for(TileEntity tile:view.tiles.values()){
                    TileEntitySpecialRenderer<TileEntity> renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);
                    if(renderer==null || !VehicleOpaqueTileCache.eligible(tile,renderer))throw new IllegalStateException("opaque vehicle wall fixture is not eligible");
                    cache.draw(tile,renderer,structure,0);
                }
                if(round>=0)samples[mode][round]=System.nanoTime()-began;
            }
            if(cache.size()!=64 || cache.builds!=64 || cache.hits<64*24)throw new IllegalStateException("vehicle wall cache does not reuse stable geometry");
            for(long[] sample:samples)Arrays.sort(sample);
            System.out.println("[vandorlabs][reprolab] vehicle-wall-render PASS walls=64 uncached-us="+samples[0][12]/1000+" cached-us="+samples[1][12]/1000+" builds="+cache.builds+" hits="+cache.hits);
        }finally{VehicleOpaqueTileCache.enabled=true;cache.delete();GlStateManager.popMatrix();}
        if(BlockSurfaceFormat.get().getNextOffset()==32){
            VehicleOpaqueBatchChecks.run(mc,structure,view);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++){
                if(facing==EnumFacing.NORTH&&depth==0)continue;
                List<VehicleStructure.Cell> configured=new ArrayList<>();
                for(VehicleStructure.Cell cell:cells)configured.add(cell.pos.equals(structure.seat)?cell:new VehicleStructure.Cell(cell.pos,cell.state.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing).withProperty(com.vandorlabs.blocks.BlockProgrammableWall.DEPTH,depth),cell.tileData()));
                VehicleStructure variant=new VehicleStructure(configured,structure.seat,EnumFacing.NORTH,1);
                VehicleOpaqueBatchChecks.run(mc,variant,new VehicleWorld(mc.world,variant));
            }
            System.out.println("[vandorlabs][reprolab] vehicle-opaque-depths PASS all-four-facings three-depths");
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(structure);craft.setPosition(0,200,0);
        RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());long[][] ordering=new long[2][24];
        try{
            for(int mode=0;mode<2;mode++)for(int round=-12;round<24;round++){
                RenderGroundVehicle.reuseTileOrder=mode==1;RenderGroundVehicle.batchOpaqueTiles=false;RenderGroundVehicle.beginFrame();long start=System.nanoTime();
                renderer.doRender(craft,0,-10000,0,0,0);
                if(round>=0)ordering[mode][round]=System.nanoTime()-start;
            }
            for(long[] sample:ordering)Arrays.sort(sample);
            System.out.println("[vandorlabs][reprolab] vehicle-tile-order PASS walls=64 samples=24 previous-us="+ordering[0][12]/1000+" retained-us="+ordering[1][12]/1000+" opaque-list-copies/frame=64->0 opaque-sort-items/frame=64->0");
        }finally{RenderGroundVehicle.reuseTileOrder=true;RenderGroundVehicle.batchOpaqueTiles=true;RenderGroundVehicle.reload();}
        invalidation(mc,structure);
        if(cache.size()!=0)throw new IllegalStateException("vehicle wall cache retains GPU resources after deletion");
    }    @SuppressWarnings("unchecked") private static void invalidation(Minecraft mc,VehicleStructure structure){
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(structure);craft.setPosition(0,200,0);
        RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());
        GlStateManager.pushMatrix();GlStateManager.translate(0,-10000,0);
        try{
            renderer.doRender(craft,0,0,0,0,0);
            java.lang.reflect.Field meshes=RenderGroundVehicle.class.getDeclaredField("meshes");meshes.setAccessible(true);Object regions=((Map<?,?>)meshes.get(null)).get(craft);
            java.lang.reflect.Field batches=regions.getClass().getDeclaredField("opaqueBatches"),cacheField=regions.getClass().getDeclaredField("opaqueTiles");batches.setAccessible(true);cacheField.setAccessible(true);
            VehicleOpaqueTileBatch batch=((Map<BlockPos,VehicleOpaqueTileBatch>)batches.get(regions)).get(BlockPos.ORIGIN);VehicleOpaqueTileCache cache=(VehicleOpaqueTileCache)cacheField.get(regions);
            batch.draw(cache,craft.structure,0);int builds=batch.builds,revision=craft.view.tileRevision;
            TileEntity original=craft.view.getTileEntity(BlockPos.ORIGIN);TileEntityAnimatedScreenSelector changed=new TileEntityAnimatedScreenSelector();changed.readFromNBT(original.writeToNBT(new NBTTagCompound()));changed.setHousingTexture(15);
            VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.TILE);packet.origin=BlockPos.ORIGIN;packet.sequence=net.minecraft.block.Block.getStateId(craft.view.getBlockState(BlockPos.ORIGIN));java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.writeCompressed(changed.writeToNBT(new NBTTagCompound()),encoded);packet.data=encoded.toByteArray();VehicleSignals.apply(craft,packet);
            if(((TileEntityAnimatedScreenSelector)original).getHousingTexture()!=15)throw new IllegalStateException("wall fixture TILE packet did not apply artwork");
            if(craft.view.getTileEntity(BlockPos.ORIGIN)!=original||craft.view.tileRevision!=revision)throw new IllegalStateException("wall invalidation fixture must update an existing carried tile");
            renderer.doRender(craft,0,0,0,0,0);
            if(batch.ready(cache)&&batch.builds==builds)throw new IllegalStateException("Carried wall VBO retained old artwork after native TILE update");
            batch.draw(cache,craft.structure,0);builds=batch.builds;
            List<VehicleStructure.Cell> attached=new ArrayList<>(craft.structure.cells);BlockPos neighbor=new BlockPos(8,0,0);attached.add(new VehicleStructure.Cell(neighbor,net.minecraft.init.Blocks.STONE.getDefaultState(),null));
            craft.updateSettings(new VehicleStructure(attached,structure.seat,structure.facing,structure.gearCount));renderer.doRender(craft,0,0,0,0,0);
            if(batch.ready(cache)&&batch.builds==builds)throw new IllegalStateException("Carried wall VBO retained old contact geometry after adjacent stone placement");
            batch.draw(cache,craft.structure,0);builds=batch.builds;attached.removeIf(cell->cell.pos.equals(neighbor));craft.updateSettings(new VehicleStructure(attached,structure.seat,structure.facing,structure.gearCount));renderer.doRender(craft,0,0,0,0,0);
            if(batch.ready(cache)&&batch.builds==builds)throw new IllegalStateException("Carried wall VBO retained old contact geometry after adjacent stone removal");
            batch.draw(cache,craft.structure,0);
            System.out.println("[vandorlabs][reprolab] vehicle-wall-invalidation PASS native-TILE artwork update adjacent-section stone placement/removal retained tile identity");
        }catch(ReflectiveOperationException|java.io.IOException failure){throw new IllegalStateException("Vehicle wall cache regression fixture",failure);}
        finally{GlStateManager.popMatrix();RenderGroundVehicle.reload();}
    }

}
