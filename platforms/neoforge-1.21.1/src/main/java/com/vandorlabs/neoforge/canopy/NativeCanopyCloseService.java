package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.ramp.NativeRampReservations;
import com.vandorlabs.persistence.CanopySettingsData;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Real loaded Ramp/journal admission and event-driven reservation retry; never commands Ramp recovery. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCanopyCloseService {
    private record Wait(WeakReference<NativeCanopyEntity> owner,Set<BlockPos> cells){}
    private static final class State {final Map<BlockPos,Wait> waits=new HashMap<>();final Set<BlockPos> retry=new HashSet<>();}
    private static final Map<ServerLevel,State> WORLDS=new WeakHashMap<>();
    public static final NativeCanopyControl.Access WORLD=new NativeCanopyControl.Access(){public NativeCanopyControl.Close prepareClose(NativeCanopyEntity owner){return NativeCanopyCloseService.prepare(owner);}};
    static {var previous=NativeRampReservations.bindChanged((world,root)->changed(world));if(previous!=null)NativeRampReservations.bindChanged((world,root)->{previous.accept(world,root);changed(world);});}
    private NativeCanopyCloseService(){}
    private static boolean installed(NativeCanopyEntity owner){return owner.getLevel() instanceof ServerLevel world&&world.getServer().isSameThread()&&com.vandorlabs.neoforge.fluid.NativeFluidWorld.open(world)&&owner.available()&&NativeCanopyLifecycle.installed(owner);}
    private static void changed(ServerLevel world){var state=WORLDS.get(world);if(state!=null)state.retry.addAll(state.waits.keySet());}
    public static NativeCanopyControl.Close prepare(NativeCanopyEntity owner){
        if(!installed(owner)||!owner.publication().draws())return NativeCanopyControl.Close.REFUSED;var world=(ServerLevel)owner.getLevel();var state=WORLDS.computeIfAbsent(world,unused->new State());state.waits.remove(owner.getBlockPos());var blocked=new HashSet<BlockPos>();
        for(var member:NativeCanopyLifecycle.members(owner)){var cells=NativeCanopyFootprint.cells(member.kind(),member.getBlockPos(),member.facing(),member.settings(),member.links());for(int index:member.links().released())if(index>=0&&index<cells.size())blocked.add(cells.get(index));}
        double radius=owner.width()+(CanopySettingsData.supports(owner.kind())?owner.settings().data().depth/16D:NativeCanopyModels.builtin().dimensions(owner.kind()).length())+2;
        var result=NativeRampReservations.prepareClose(world,owner.getBlockPos(),owner.getRedstoneChannels(),new AABB(owner.getBlockPos()).inflate(radius),NativeCanopySweeps.query(owner));
        if(result.status()==NativeRampReservations.Status.READY&&NativeCanopyReservations.restoreFootprint(owner,WORLD))return NativeCanopyControl.Close.READY;
        blocked.addAll(result.blockedCells());if(state.waits.size()>=1024&&!state.waits.containsKey(owner.getBlockPos()))return NativeCanopyControl.Close.REFUSED;state.waits.put(owner.getBlockPos(),new Wait(new WeakReference<>(owner),Set.copyOf(blocked)));
        return result.status()==NativeRampReservations.Status.REFUSED?NativeCanopyControl.Close.REFUSED:NativeCanopyControl.Close.PENDING;
    }
    /** Normal server tick entry point, deliberately separate from client cosmetic preparation. */
    public static void retire(NativeCanopyEntity host){if(host.getLevel() instanceof ServerLevel world&&world.getServer().isSameThread()){var state=WORLDS.get(world);if(state!=null){state.waits.remove(host.getBlockPos());state.retry.remove(host.getBlockPos());}}}
    public static void tick(NativeCanopyEntity host){if(host.links().open())retire(host);if(host.getLevel() instanceof ServerLevel)NativeCanopyControl.tick(host,WORLD);else NativeCanopyLifecycle.prepare(host);}
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event){if(!(event.getLevel() instanceof ServerLevel world))return;var state=WORLDS.get(world);if(state==null)return;for(var entry:state.waits.entrySet())if(entry.getValue().cells().contains(event.getPos()))state.retry.add(entry.getKey());}
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){if(!(event.getLevel() instanceof ServerLevel world))return;var state=WORLDS.get(world);if(state==null)return;var chunk=event.getChunk().getPos();for(var entry:state.waits.entrySet())for(var pos:entry.getValue().cells())if((pos.getX()>>4)==chunk.x&&(pos.getZ()>>4)==chunk.z){state.retry.add(entry.getKey());break;}}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;var state=WORLDS.get(world);if(state==null)return;for(var pos:state.retry){var wait=state.waits.get(pos);var owner=wait==null?null:wait.owner().get();if(owner!=null&&installed(owner))NativeCanopyControl.retryClose(owner);else state.waits.remove(pos);}state.retry.clear();}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world)WORLDS.remove(world);}
}
