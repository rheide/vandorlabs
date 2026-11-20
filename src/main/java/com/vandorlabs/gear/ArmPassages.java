package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Large Door cells that an arm currently reaches into. */
public final class ArmPassages {
    private ArmPassages(){}
    /** True when an assembled arm's current, visible geometry reaches into {@code cell}. */
    public static boolean occupied(World world,BlockPos cell){
        if(world==null||world.isRemote||!GearAssembly.loaded(world,cell))return false;
        for(EnumFacing side:EnumFacing.values()){
            for(int distance=1;distance<=GearTopology.MAX_COLUMN_HEIGHT;distance++){
                BlockPos at=cell.offset(side,distance);
                if(!GearAssembly.loaded(world,at))break;
                TileEntity raw=world.getTileEntity(at);
                if(raw instanceof GearTileEntity){
                    GearTileEntity arm=((GearTileEntity)raw).loadedController();
                    if(arm!=null&&arm.occupiesNow(cell))return true;
                    break;
                }
                if(!GearAssembly.doorCell(world,at))break;
            }
        }
        return false;
    }
}
