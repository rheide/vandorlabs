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
        if(cache.size()!=0)throw new IllegalStateException("vehicle wall cache retains GPU resources after deletion");
    }
}
