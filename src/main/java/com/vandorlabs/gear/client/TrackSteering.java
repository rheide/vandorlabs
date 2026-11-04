package com.vandorlabs.gear.client;

import com.vandorlabs.gear.track.*;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import net.minecraft.util.math.BlockPos;

/** A lone top-mounted Track swivels like a wheel; its support remains fixed. */
public final class TrackSteering {
    public static float angle(EntityGroundVehicle craft,BlockPos pos){
        net.minecraft.tileentity.TileEntity raw=craft.view.getTileEntity(pos);
        if(!(raw instanceof TrackTile))return 0;
        TrackTile track=(TrackTile)raw;TrackAppearance appearance=track.appearance();
        if(track.hasLoadedController()||track.trackMembers().size()>1||appearance.beltMask!=0||appearance.bridgeMask!=0
                ||appearance.mount!=TrackAppearance.Mount.TOP&&appearance.mount!=TrackAppearance.Mount.TOP_HIGH)return 0;
        // Track mesh affine yaw uses the opposite convention to wheel steering.
        return -com.vandorlabs.client.VehicleWheelMotion.steer(craft,pos);
    }
    private TrackSteering(){}
}
