package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Captured covers use explicit vehicle state updates rather than writing to the read-only view. */
public final class VehicleGearCovers {
    private VehicleGearCovers(){}
    private static final Map<EntityGroundVehicle,VehicleStructure> LAST=new WeakHashMap<>();
    public static void tick(EntityGroundVehicle craft){
        if(craft.world.isRemote||craft.view==null||craft.transferInProgress||craft.ownershipBlocked||craft.structure.gearCovers.isEmpty())return;
        craft.signals.persist();if(LAST.get(craft)==craft.structure)return;LAST.put(craft,craft.structure);
        Map<BlockPos,Boolean> desired=new LinkedHashMap<>();
        for(VehicleStructure.Cell cell:craft.structure.gearCovers){
            if(!craft.structure.isActive(cell.pos))continue;
            TileEntity raw=craft.view.getTileEntity(cell.pos);if(!(raw instanceof TileEntityProgrammableTrapdoor))continue;
            TileEntityProgrammableTrapdoor cover=(TileEntityProgrammableTrapdoor)raw;if(!cover.isCover())continue;
            boolean open=craft.view.getBlockState(cover.getPos()).getValue(BlockProgrammableTrapdoor.OPEN),next=open;
            if(LandingGearCovers.needsOpen(cover))next=true;
            else if(cover.getTrigger()!=0){
                boolean powered=cover.hasLocalRedstoneSignal();ChannelList channels=cover.getRedstoneChannels();
                for(int i=0;i<channels.size();i++)powered|=RedstoneChannels.level(craft.view,cover.getPos(),channels.get(i))>0;
                next=cover.getTrigger()==1?powered:!powered;
            }
            if(next!=open)desired.put(cover.getPos(),next);
        }
        Map<BlockPos,Boolean> batch=new LinkedHashMap<>();
        for(Map.Entry<BlockPos,Boolean> entry:desired.entrySet()){batch.put(entry.getKey(),entry.getValue());if(batch.size()==64){VehicleDoors.set(craft,batch);batch.clear();}}
        if(!batch.isEmpty())VehicleDoors.set(craft,batch);
    }
}
