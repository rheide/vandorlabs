package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.TileEntityTurntableSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.Set;

/** Keep one decal renderer until endpoint block and tile packets are both visible. */
public final class SurfaceArmHandoff {
    private static final class State {
        final ArmPayload payload;final Set<BlockPos> cells;int tick=-1;boolean ready;
        State(GearTileEntity arm,ArmPayload payload){this.payload=payload;cells=payload.surfaceEndpointCells(arm);}
    }
    public static boolean fallback(GearTileEntity arm){
        ArmPayload payload=arm.payload();if(payload==null||!payload.materialized()||arm.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return false;
        State state=arm.surfaceVisualHandoff instanceof State?(State)arm.surfaceVisualHandoff:null;
        if(state==null||state.payload!=payload){state=new State(arm,payload);arm.surfaceVisualHandoff=state;}
        if(state.cells.isEmpty()||state.ready)return false;
        int tick=Minecraft.getMinecraft().ingameGUI.getUpdateCounter();if(state.tick==tick)return true;state.tick=tick;
        for(BlockPos cell:state.cells){
            if(!arm.getWorld().isBlockLoaded(cell,false))return false;
            TileEntity tile=arm.getWorld().getTileEntity(cell);
            if(!(tile instanceof TileEntityTurntableSurface)||!((TileEntityTurntableSurface)tile).appearanceReady())return true;
        }
        state.ready=true;return false;
    }
    public static boolean hideNative(TileEntityTurntableSurface tile){
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return false;
        GearTileEntity arm=ArmSurfaceActivity.owner(tile);if(arm==null||arm.payload()==null)return false;
        return !arm.payload().materialized()&&!arm.payload().hasOwnershipJournal()||fallback(arm);
    }
    public static void changed(TileEntityTurntableSurface tile){
        GearTileEntity arm=ArmSurfaceActivity.owner(tile);if(arm!=null&&arm.surfaceVisualHandoff instanceof State){State state=(State)arm.surfaceVisualHandoff;if(!state.ready)state.tick=-1;}
    }
    private SurfaceArmHandoff(){}
}
