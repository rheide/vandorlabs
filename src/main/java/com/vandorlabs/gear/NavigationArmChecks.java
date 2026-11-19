package com.vandorlabs.gear;

import com.vandorlabs.navigation.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.redstone.*;
import net.minecraft.util.math.*;

/** Runs inside the actual asynchronous extension and swing-arm live fixtures. */
public final class NavigationArmChecks {
    static void verify(GearTileEntity arm){
        if(arm.payload()==null||arm.payload().materialized())return;
        for(TileEntityConstructor tile:arm.payload().navigationObservers(arm)){
            BlockPos local=tile.getPos();Vec3d expected=arm.payload().transform(arm,arm.progress(),local.getX()+.5,local.getY(),local.getZ()+.5).add(new Vec3d(arm.getPos()));
            if(NavigationSources.position(tile).squareDistanceTo(expected)>1e-12)throw new IllegalStateException("Observer lost moving physical position");
            int radius=NavigationRange.radius(tile.core().design.equals("standalone_dish")?NavigationRange.SURVEY_BASE:NavigationRange.SENTINEL_BASE,expected.y,tile.observerScale());
            if(NavigationSources.radius(tile)!=radius)throw new IllegalStateException("Observer retained construction-height radius");
            java.util.List<NavigationSources.Area> areas=NavigationSources.matching(arm.getWorld(),new BlockPos(expected),ChannelList.of(47));
            if(!areas.isEmpty()||!NavigationSources.noPower(arm.getWorld(),new BlockPos(expected),ChannelList.of(47)))
                throw new IllegalStateException("Captured observer lost its unpowered link phase="+arm.progress()+" height="+expected.y);
        }
    }
    public static void snapshot(TileEntityConstructor tile){
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        com.vandorlabs.machines.CoreLayout layout=tile.layout();
        for(int x=0;x<layout.width;x++)for(int y=0;y<layout.height;y++)for(int z=0;z<layout.depth;z++){
            BlockPos at=tile.cell(x,y,z);net.minecraft.tileentity.TileEntity member=tile.getWorld().getTileEntity(at);
            cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(at,tile.getWorld().getBlockState(at),member.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        }
        com.vandorlabs.vehicle.VehicleStructure saved=normalized(cells);ArmCorePayload.validate(saved);
        for(int turn=0;turn<4;turn++){
            double angle=turn*Math.PI/2;int c=(int)Math.round(Math.cos(angle)),s=(int)Math.round(Math.sin(angle));int[] basis={c,0,-s,0,1,0,s,0,c};java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> rotated=new java.util.ArrayList<>();
            for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:saved.cells){BlockPos p=cell.pos;BlockPos q=new BlockPos(c*p.getX()-s*p.getZ(),p.getY(),s*p.getX()+c*p.getZ());net.minecraft.nbt.NBTTagCompound data=ArmCorePayload.data(cell,basis);data.setInteger("x",q.getX());data.setInteger("y",q.getY());data.setInteger("z",q.getZ());rotated.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(q,ArmCorePayload.state(cell,cell.state,basis),data));}
            ArmCorePayload.validate(normalized(rotated));
        }
    }
    private static com.vandorlabs.vehicle.VehicleStructure normalized(java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells){
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:cells){minX=Math.min(minX,cell.pos.getX());minY=Math.min(minY,cell.pos.getY());minZ=Math.min(minZ,cell.pos.getZ());}BlockPos shift=new BlockPos(-minX,-minY,-minZ);java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> result=new java.util.ArrayList<>();
        for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:cells)result.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(cell.pos.add(shift),cell.state,com.vandorlabs.vehicle.VehicleTileData.translated(cell.tileData(),shift)));return com.vandorlabs.vehicle.VehicleStructure.display(result);
    }
    private NavigationArmChecks(){}
}
