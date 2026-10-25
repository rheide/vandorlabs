package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Collections;

/** Registered native factories must survive their first save/read/save before moving-block capture. */
public final class MovingSupportRuntimeChecks {
    public static void run(World parent){
        int checked=0,unsupported=0;
        for(Block block:ModBlocks.BLOCKS){
            IBlockState state=block.getDefaultState();if(!block.hasTileEntity(state))continue;
            VehicleWorld sourceView=new VehicleWorld(parent,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,null))));
            TileEntity fresh=block.createTileEntity(sourceView,state);if(fresh==null)throw new IllegalStateException("Missing native tile factory: "+block.getRegistryName());
            fresh.setPos(BlockPos.ORIGIN);fresh.setWorld(sourceView);sourceView.tiles.put(BlockPos.ORIGIN,fresh);
            NBTTagCompound before=fresh.writeToNBT(new NBTTagCompound());VehicleStructure.Cell cell=new VehicleStructure.Cell(BlockPos.ORIGIN,state,before);
            VehicleWorld cloned=new VehicleWorld(parent,VehicleStructure.display(Collections.singletonList(cell)));TileEntity tile=cloned.getTileEntity(BlockPos.ORIGIN);
            if(!MovingBlockSupport.supported(cell,tile)){unsupported++;continue;}
            try{MovingBlockSupport.requireExactTileData(cell,tile);}catch(RuntimeException failure){throw new IllegalStateException("Fresh registered moving-block tile changes NBT after cloning: "+block.getRegistryName()+" "+fresh.getClass().getSimpleName(),failure);}checked++;
        }
        if(checked<100)throw new IllegalStateException("Incomplete registered tile roundtrip matrix: "+checked);
        System.out.println("[vandorlabs][reprolab] moving-support-nbt PASS fresh-registered-tiles="+checked+" explicitly-unsupported="+unsupported+" first-save read/write exact data and default normalizations");
    }
    private MovingSupportRuntimeChecks(){}
}
