package com.vandorlabs.gear.core;

import java.util.Objects;
import java.util.UUID;

/** Read-only client admission. Packets never establish their own ownership scope. */
public final class GearMotionScope {
    public enum Decision { APPLY, WAIT, DROP }
    public static Decision classify(UUID packet,long packetTick,UUID current,long scopeTick,long sourceTick,boolean ready,boolean pendingSource){
        if(Objects.equals(packet,current)){
            if(!ready||pendingSource)return Decision.WAIT;
            if(current!=null&&packetTick<sourceTick)return Decision.DROP;
            return Decision.APPLY;
        }
        if(packet==null)return Decision.DROP;
        if(pendingSource)return Decision.WAIT;
        if(packetTick<=scopeTick)return Decision.DROP;
        return Decision.WAIT;
    }
    private GearMotionScope(){}
}
