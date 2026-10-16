package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyReservations;
import com.vandorlabs.tiles.TileEntityCanopy;

/** Synchronous fixtures allow worker completion without changing live-world tick behavior. */
final class CanopyPreparationRuntimeChecks {
    static void await(TileEntityCanopy owner){
        long deadline=System.nanoTime()+15_000_000_000L;
        while(CanopyReservations.preparingClose(owner)){
            if(System.nanoTime()>deadline)throw new IllegalStateException("Canopy closure worker did not finish");
            try{Thread.sleep(2);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
        }
    }
    private CanopyPreparationRuntimeChecks(){}
}
