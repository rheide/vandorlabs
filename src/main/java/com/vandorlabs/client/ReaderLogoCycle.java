package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import java.util.WeakHashMap;

/** Cosmetic proximity-gated animation; no block ticks, state packets or chunk loading. */
final class ReaderLogoCycle {
    private static final WeakHashMap<TileEntityTokenSlot,ReaderLogoCycle> STATES=new WeakHashMap<>();
    private int design,base=-1,mask=-1;
    private long previous=Long.MIN_VALUE,elapsed;
    private boolean active;
    static int design(TileEntityTokenSlot tile){
        Minecraft mc=Minecraft.getMinecraft();Vec3d at=new Vec3d(tile.getPos()).addVector(.5,.5,.5);
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.EntityGroundVehicle craft=((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).craft;if(craft!=null)at=craft.toWorld(at);}
        boolean nearby=mc.player!=null&&mc.player.getDistanceSq(at.x,at.y,at.z)<=64;
        return STATES.computeIfAbsent(tile,key->new ReaderLogoCycle()).advance(tile.logoDesign(),tile.effectiveAllowed(),tile.getWorld().getTotalWorldTime(),nearby);
    }
    int advance(int start,int allowed,long now,boolean nearby){
        if((allowed&1)!=0){base=start;mask=allowed;design=0;elapsed=0;previous=now;active=false;return 0;}
        if(base!=start||mask!=allowed){base=start;mask=allowed;design=start;elapsed=0;previous=now;}
        long delta=previous==Long.MIN_VALUE?0:now-previous;previous=now;
        if(!nearby||delta<0||delta>40){active=nearby;elapsed=0;return design;}
        if(!active){active=true;elapsed=0;return design;}
        elapsed+=delta;
        while(elapsed>=60){elapsed-=60;for(int step=1;step<=7;step++){int next=(design+step)%7;if((allowed&(1<<next))!=0){design=next;break;}}}
        return design;
    }
}
