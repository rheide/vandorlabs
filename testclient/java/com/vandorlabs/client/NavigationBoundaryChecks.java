package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Incomplete loaded discovery cannot certify a new chip owner. */
public final class NavigationBoundaryChecks {
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();BlockPos start=new BlockPos(15,80,8);
        LoadedPlaneConnections.Scan partial=LoadedPlaneConnections.scan(start,PanelPlane.of(EnumFacing.UP),p->p.getX()<16,p->{
            if(p.getX()>=16)throw new AssertionError("Queried unloaded candidate");return p.equals(start);
        },257);
        if(partial.complete||partial.members.size()!=1)throw new AssertionError("Unloaded boundary certified a complete assembly");
        LoadedPlaneConnections.Scan complete=LoadedPlaneConnections.scan(start,PanelPlane.of(EnumFacing.UP),p->true,p->p.equals(start),257);
        if(!complete.complete||complete.members.size()!=1)throw new AssertionError("Complete singleton rejected");
        LoadedPlaneConnections.Scan capped=LoadedPlaneConnections.scan(start,PanelPlane.of(EnumFacing.UP),p->true,p->true,257);
        if(capped.complete||capped.members.size()!=257)throw new AssertionError("Unbounded traversal accepted");
        System.out.println("Navigation boundaries PASS loaded-only candidates complete singleton bounded oversized component");
    }
}
