package com.vandorlabs.vehicle;

import com.vandorlabs.gear.ArmParkingMaterialization;
import com.vandorlabs.gear.GearTileEntity;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Entity-owned CURRENT layout cache. It cannot keep an unloaded world alive through a static key. */
final class VehicleArmParkingCurrent {
    final VehicleStructure structure;
    final Map<BlockPos,BlockPos> seats;
    private final VehicleArmParkingSearch search;
    private VehicleRotation.Plan cardinal;
    private int turns;
    private long x,y,z;

    private VehicleArmParkingCurrent(EntityGroundVehicle craft){
        search=new VehicleArmParkingSearch(craft,ArmParkingMaterialization::mayDisplace,true,false);
        // Each domain contains precisely the live phase, so there is no combinatorial phase search.
        int slices=0;
        while(!search.step(256,Long.MAX_VALUE))if(++slices>1024)throw new IllegalArgumentException("Current arm parking preparation exceeded its bound");
        VehicleArmParkingComposition composition=new VehicleArmParkingComposition(craft.structure,search.geometry(),search.result());
        while(!composition.step(256,Long.MAX_VALUE))if(++slices>2048)throw new IllegalArgumentException("Current arm parking composition exceeded its bound");
        search.requireStable();structure=composition.result();seats=composition.seatMap();
    }
    VehicleRotation.Plan plan(EntityGroundVehicle craft,int rotation){
        long px=Double.doubleToLongBits(craft.posX),py=Double.doubleToLongBits(craft.posY),pz=Double.doubleToLongBits(craft.posZ);
        if(cardinal==null||turns!=rotation||x!=px||y!=py||z!=pz){
            cardinal=VehicleRotation.safePlan(craft,rotation,structure,seats);
            VehicleRotation.validate(cardinal.structure,new VehicleWorld(craft.world,cardinal.structure));
            turns=rotation;x=px;y=py;z=pz;
        }
        return cardinal;
    }
    static boolean hasPayload(EntityGroundVehicle craft){
        for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).payload()!=null)return true;
        return false;
    }
    static VehicleArmParkingCurrent get(EntityGroundVehicle craft){
        VehicleArmParkingCurrent cached=craft.armParkingPreview;
        if(cached!=null)try{cached.search.requireStable();return cached;}catch(IllegalArgumentException changed){craft.armParkingPreview=null;}
        VehicleArmParkingCurrent prepared=new VehicleArmParkingCurrent(craft);craft.armParkingPreview=prepared;return prepared;
    }
}
