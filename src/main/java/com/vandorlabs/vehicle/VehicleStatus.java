package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.TileEntityAutopilot;
import com.vandorlabs.tiles.TileEntityWaypoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import java.util.*;

/** Bounded display-only discovery; parked craft have no entity-local tile index. */
public final class VehicleStatus {
    private static final Map<World,Map<BlockPos,Entry>> cache=new WeakHashMap<>();
    private static final class Entry {final long at;final BlockPos controller;Entry(long at,BlockPos controller){this.at=at;this.controller=controller;}}
    private VehicleStatus(){}
    public static TileEntityAutopilot find(World world,BlockPos screen){
        if(world instanceof VehicleWorld){EntityGroundVehicle craft=((VehicleWorld)world).craft;return craft==null?null:craft.autopilot.controller();}
        Map<BlockPos,Entry> entries=cache.computeIfAbsent(world,w->new HashMap<>());Entry old=entries.get(screen);
        if(old!=null&&world.getTotalWorldTime()-old.at<100){TileEntity tile=old.controller==null?null:world.getTileEntity(old.controller);return tile instanceof TileEntityAutopilot?(TileEntityAutopilot)tile:null;}
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();queue.add(screen);seen.add(screen);
        TileEntityAutopilot found=null;int budget=4096;
        while(!queue.isEmpty()&&budget-->0){BlockPos p=queue.removeFirst();if(!world.isBlockLoaded(p)||world.isAirBlock(p))continue;TileEntity tile=world.getTileEntity(p);
            if(tile instanceof TileEntityAutopilot&&!(tile instanceof TileEntityWaypoint)){found=(TileEntityAutopilot)tile;break;}
            net.minecraft.block.state.IBlockState state=world.getBlockState(p);if(state.getBlock() instanceof com.vandorlabs.blocks.BlockLandingGear)continue;
            try{for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values())if(VehicleScan.touches(world,p,state,face)){BlockPos next=p.offset(face);if(seen.add(next))queue.add(next);}}catch(RuntimeException|LinkageError unavailable){VehicleCompatibility.warn(state.getBlock(),"status connectivity",unavailable);}
        }
        if(entries.size()>256)entries.clear();entries.put(screen.toImmutable(),new Entry(world.getTotalWorldTime(),found==null?null:found.getPos()));return found;
    }
}
