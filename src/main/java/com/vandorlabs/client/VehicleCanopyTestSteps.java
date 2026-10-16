package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleCanopies;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Manual runtime fixtures count published animation steps, including cold preparation. */
final class VehicleCanopyTestSteps {
    private VehicleCanopyTestSteps(){}
    static void step(EntityGroundVehicle craft){
        Map<TileEntityCanopy,Float> before=new IdentityHashMap<>(),expected=new IdentityHashMap<>();
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy tile=(TileEntityCanopy)raw;if(tile.draws()&&tile.block()!=null&&tile.opening()){before.put(tile,tile.progress);expected.put(tile,Math.max(0,Math.min(1,tile.progress+(tile.targetOpen?1:-1)/16F)));}}
        long deadline=System.nanoTime()+5_000_000_000L;
        do{
            craft.collisionCache.tick(craft.view);VehicleCanopies.tick(craft);boolean complete=true;
            for(Map.Entry<TileEntityCanopy,Float> entry:expected.entrySet()){
                TileEntityCanopy tile=entry.getKey();float actual=tile.progress;
                if(actual!=entry.getValue()){complete=false;if(actual!=before.get(tile))throw new IllegalStateException("canopy fixture advanced beyond one requested step: "+actual+" expected="+entry.getValue());}
            }
            if(complete)return;
            try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("canopy fixture interrupted while awaiting exact prepared step",interrupted);}
        }while(System.nanoTime()<deadline);
        throw new IllegalStateException("canopy fixture timed out awaiting one exact prepared step: warming="+craft.collisionCache.warming()+" bypass="+craft.collisionCache.bypass());
    }
}

