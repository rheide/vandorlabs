package com.vandorlabs.ramp;

import com.vandorlabs.tiles.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.lang.ref.WeakReference;
import java.util.*;

/** Loaded, pending commands only; mechanism changes wake their direct waiters. */
public final class RampCanopyCoordination {
    private static final Map<World,Map<BlockPos,List<WeakReference<TileEntity>>>> WAITERS=new WeakHashMap<>();
    private RampCanopyCoordination(){}
    public static void waitingCanopy(TileEntityCanopy owner,Set<BlockPos> ramps){clearCanopyWait(owner);observe(owner,ramps);}
    public static void waitingRamp(TileEntityRampController ramp,Set<BlockPos> canopies){clearRampWait(ramp);observe(ramp,canopies);}
    private static void observe(TileEntity tile,Set<BlockPos> owners){if(tile.getWorld()==null||tile.getWorld().isRemote)return;Map<BlockPos,List<WeakReference<TileEntity>>> world=WAITERS.computeIfAbsent(tile.getWorld(),unused->new HashMap<>());for(BlockPos pos:owners)world.computeIfAbsent(pos.toImmutable(),unused->new ArrayList<>()).add(new WeakReference<>(tile));}
    public static void clearCanopyWait(TileEntityCanopy owner){clear(owner);}
    public static void clearRampWait(TileEntityRampController ramp){clear(ramp);}
    private static void clear(TileEntity tile){Map<BlockPos,List<WeakReference<TileEntity>>> world=WAITERS.get(tile.getWorld());if(world==null)return;world.values().removeIf(list->{list.removeIf(reference->reference.get()==null||reference.get()==tile);return list.isEmpty();});if(world.isEmpty())WAITERS.remove(tile.getWorld());}
    public static void changed(World world,BlockPos owner){
        if(world==null||world.isRemote)return;Map<BlockPos,List<WeakReference<TileEntity>>> watched=WAITERS.get(world);if(watched==null)return;List<WeakReference<TileEntity>> waiters=watched.remove(owner);if(watched.isEmpty())WAITERS.remove(world);if(waiters==null)return;
        for(WeakReference<TileEntity> reference:waiters){TileEntity tile=reference.get();if(tile==null||tile.isInvalid()||tile.getWorld()!=world||!world.isBlockLoaded(tile.getPos())||world.getTileEntity(tile.getPos())!=tile)continue;if(tile instanceof TileEntityCanopy)((TileEntityCanopy)tile).retryReservationClose();else if(tile instanceof TileEntityRampController)((TileEntityRampController)tile).retryCanopyCommand();}
    }
}
