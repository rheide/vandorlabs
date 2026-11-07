package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Operation-local logical controls, owned by the existing articulation lifecycle. */
final class VehicleArmCouplerControls {
    private final EntityGroundVehicle craft;
    private final Map<VehicleArmCouplers.Address,VehicleArmCouplerControl> heads=new LinkedHashMap<>();
    private int revision=Integer.MIN_VALUE;
    private VehicleArmCouplerControl[] active=new VehicleArmCouplerControl[0];
    VehicleArmCouplerControls(EntityGroundVehicle craft){this.craft=craft;}
    VehicleArmCouplerControl resolve(BlockPos root,BlockPos cell){
        VehicleArmCouplers.Address address=VehicleArmCouplers.Address.payloadCell(root,cell);VehicleArmCouplerControl existing=heads.get(address);if(existing!=null&&existing.current())return existing;
        TileEntity raw=craft.view==null?null:craft.view.getTileEntity(root);if(!(raw instanceof GearTileEntity)||!((GearTileEntity)raw).isOperational()||!((GearTileEntity)raw).isController()||!craft.structure.isActive(root))return null;ArmPayload payload=((GearTileEntity)raw).payload();if(payload==null||payload.materialized()||payload.hasOwnershipJournal())return null;VehicleStructure.Cell selected=payload.blocks.at(cell);if(selected==null||!(selected.state.getBlock() instanceof com.vandorlabs.coupler.BlockCoupler))return null;
        try{VehicleArmCouplerControl next=new VehicleArmCouplerControl(craft,address);heads.put(address,next);active=heads.values().toArray(new VehicleArmCouplerControl[heads.size()]);return next;}catch(IllegalArgumentException unavailable){return null;}
    }
    private void discover(){
        if(revision==craft.view.tileRevision)return;revision=craft.view.tileRevision;Set<VehicleArmCouplers.Address> wanted=new HashSet<>();
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity){GearTileEntity arm=(GearTileEntity)raw;ArmPayload payload=arm.payload();if(payload==null||payload.materialized()||payload.hasOwnershipJournal())continue;for(VehicleStructure.Cell head:payload.blocks.couplers){VehicleArmCouplers.Address address=VehicleArmCouplers.Address.payloadCell(arm.getPos(),head.pos);wanted.add(address);resolve(address.arm,address.cell);}}
        if(heads.keySet().retainAll(wanted))active=heads.values().toArray(new VehicleArmCouplerControl[heads.size()]);
    }
    void tick(){if(craft.view==null)return;discover();for(VehicleArmCouplerControl control:active)if(control.current())control.sample();}
}
