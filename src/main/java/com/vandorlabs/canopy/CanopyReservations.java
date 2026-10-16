package com.vandorlabs.canopy;

import com.vandorlabs.tiles.*;
import com.vandorlabs.ramp.RampCanopyCoordination;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/** Physical helpers may yield to other mechanisms while the authoritative shell stays open. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class CanopyReservations {
    private static final Map<World,Map<BlockPos,List<WeakReference<TileEntityCanopy>>>> WAITS=new WeakHashMap<>();
    private static final Map<TileEntityCanopy,SweepCache> SWEEPS=new LinkedHashMap<TileEntityCanopy,SweepCache>(8,.75F,true);
    private static final Map<TileEntityCanopy,PendingSweep> PENDING=new WeakHashMap<>();
    private static final java.util.concurrent.ThreadPoolExecutor WORKER=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(4),task->{Thread thread=new Thread(task,"Vandor canopy closure");thread.setDaemon(true);return thread;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private static final class SweepCache{final String key;final List<AxisAlignedBB> boxes;final CanopyCollisionIndex index;SweepCache(String key,List<AxisAlignedBB> boxes){this.key=key;this.boxes=Collections.unmodifiableList(boxes);index=new CanopyCollisionIndex(this.boxes);}}
    private static final class PendingSweep{final String key;java.util.concurrent.Future<SweepCache> future;boolean failed;PendingSweep(String key){this.key=key;}}
    /** A worker receives only copied mesh coordinates and settings, never a world or tile. */
    private static final class SweepSource{
        final String key,kind;final int width;final net.minecraft.util.EnumFacing facing;final BlockPos pos;final com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();final List<CanopyMesh.Face> faces=new ArrayList<>();final double progress;
        SweepSource(TileEntityCanopy owner,String key,double progress){
            this.key=key;this.progress=progress;kind=owner.block().kind;width=owner.width();facing=owner.facing();pos=owner.getPos().toImmutable();net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();owner.large.write(tag);settings.read(tag);
            for(CanopyMesh.Face face:owner.baseFaces()){CanopyMesh.Face copy=new CanopyMesh.Face();copy.vertices=face.vertices.clone();copy.group=face.group;faces.add(copy);}
        }
        SweepCache build(){
            List<AxisAlignedBB> result=new ArrayList<>(faces.size()*64);
            for(int sample=0;sample<64;sample++)for(CanopyMesh.Face face:faces){
                AxisAlignedBB local=CanopyPose.sweptBounds(face,kind,width,settings,progress*sample/64,progress*(sample+1D)/64);
                Vec3d a=CanopyMesh.rotate(new Vec3d(local.minX,local.minY,local.minZ),facing),b=CanopyMesh.rotate(new Vec3d(local.maxX,local.maxY,local.maxZ),facing);result.add(new AxisAlignedBB(a,b).offset(pos));
            }
            return new SweepCache(key,result);
        }
    }
    private CanopyReservations(){}
    /** Logical seams survive an intentionally absent physical helper. */
    public static TileEntityCanopy logicalAt(net.minecraft.world.IBlockAccess access,BlockPos pos){
        TileEntityCanopy direct=TileEntityCanopy.at(access,pos);if(direct!=null)return direct;
        // Only Canopy/Long Canopy have a fixed-glass seam through a released
        // rear proxy. Their preserved constituent anchor is at most two cells
        // away; never scan chunk tile maps from a rendering connection query.
        for(net.minecraft.util.EnumFacing direction:net.minecraft.util.EnumFacing.HORIZONTALS)for(int distance=1;distance<=2;distance++){
            TileEntityCanopy candidate=TileEntityCanopy.at(access,pos.offset(direction,distance));
            if(candidate!=null&&!candidate.removing&&candidate.block()!=null&&candidate.block().opening()&&candidate.constituent()==candidate&&candidate.isReleasedCell(pos))return candidate;
        }
        return null;
    }

    private static boolean real(TileEntityCanopy owner){return owner.getWorld()!=null&&!owner.getWorld().isRemote&&!(owner.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld);}
    public static void clearWait(TileEntityCanopy owner){
        World world=owner.getWorld();Map<BlockPos,List<WeakReference<TileEntityCanopy>>> cells=WAITS.get(world);
        if(cells!=null){for(List<WeakReference<TileEntityCanopy>> refs:cells.values())refs.removeIf(ref->ref.get()==null||ref.get()==owner);cells.values().removeIf(List::isEmpty);}
        RampCanopyCoordination.clearCanopyWait(owner);
    }
    private static void watchSharedBackrest(TileEntityCanopy owner,BlockPos cell){
        List<WeakReference<TileEntityCanopy>> watchers=WAITS.computeIfAbsent(owner.getWorld(),k->new HashMap<>()).computeIfAbsent(cell.toImmutable(),k->new ArrayList<>());
        watchers.removeIf(ref->ref.get()==null||ref.get()==owner);watchers.add(new WeakReference<>(owner));
    }
    public static void targetChanged(TileEntityCanopy owner){if(real(owner))RampCanopyCoordination.changed(owner.getWorld(),owner.getPos());}
    public static void loaded(TileEntityCanopy owner){if(real(owner)){TileEntityCanopy root=owner.owner();root.reservationGeometryChanged();targetChanged(root);}}
    public static void removed(TileEntityCanopy owner){cancel(owner);SWEEPS.remove(owner);if(real(owner)){clearWait(owner);targetChanged(owner);}}
    private static TileEntityCanopy pendingAware(World world,BlockPos cell){TileEntity tile=world.getTileEntity(cell);return tile instanceof TileEntityCanopy&&!tile.isInvalid()?(TileEntityCanopy)tile:null;}
    // Mutation paths must see World.addedTileEntityList while tile ticks are in
    // progress. The general at() accessor intentionally reads only chunk maps.
    private static boolean own(TileEntityCanopy member,BlockPos cell){TileEntityCanopy tile=pendingAware(member.getWorld(),cell);return tile!=null&&tile.block()==member.block()&&member.getPos().equals(tile.anchor);}
    /** Pilot seats recreate their invisible upper reservation when an open shell yields it. */
    private static boolean sharedBackrest(TileEntityCanopy member,BlockPos cell){
        World world=member.getWorld();if(member.block()==null||!member.block().sharesSeatBackrest(world.getBlockState(cell)))return false;
        IBlockState lower=world.getBlockState(cell.down());
        return lower.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat&&!lower.getValue(com.vandorlabs.blocks.BlockPilotSeat.UPPER)
                &&world.getTileEntity(cell.down()) instanceof TileEntityPilotSeat;
    }
    private static boolean available(TileEntityCanopy member,BlockPos cell){World world=member.getWorld();return world.isBlockLoaded(cell)&&(world.isAirBlock(cell)||own(member,cell)||sharedBackrest(member,cell));}
    public static void releaseOpen(TileEntityCanopy owner){
        if(!real(owner)||!owner.draws()||owner.progress!=1||!owner.targetOpen&&!owner.waitingForReservations)return;
        World world=owner.getWorld();List<AxisAlignedBB> shell=owner.boxes();boolean changed=false;
        for(TileEntityCanopy member:owner.reservationMembers()){
            for(BlockPos cell:member.cells()){
                if(cell.equals(member.getPos())||member.isReleasedCell(cell)||!world.isBlockLoaded(cell)||!own(member,cell))continue;
                AxisAlignedBB volume=new AxisAlignedBB(cell).shrink(.00001);boolean intersects=false;for(AxisAlignedBB box:shell)if(box.intersects(volume)){intersects=true;break;}
                if(intersects)continue;
                TileEntityCanopy helper=pendingAware(world,cell);member.releasedCell(cell,true);helper.removing=true;
                if(!world.setBlockToAir(cell)){helper.removing=false;member.releasedCell(cell,false);}else changed=true;
            }
            member.sync();
        }
        if(changed)targetChanged(owner);
    }
    /** Reacquire air/exact helpers while preserving legitimate shared seat backrests. */
    public static boolean restoreFootprint(TileEntityCanopy owner){
        if(!real(owner))return true;World world=owner.getWorld();List<TileEntityCanopy> members=owner.reservationMembers();
        for(TileEntityCanopy member:members)for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)&&!available(member,cell))return false;
        if(members.stream().noneMatch(TileEntityCanopy::hasReleasedCells))return true;
        List<BlockPos> added=new ArrayList<>();List<TileEntityCanopy> parents=new ArrayList<>();
        try{
            for(TileEntityCanopy member:members)for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)&&!own(member,cell)&&!sharedBackrest(member,cell)){
                if(!world.setBlockState(cell,world.getBlockState(member.getPos()),2))throw new IllegalStateException("Canopy helper placement refused");
                added.add(cell);parents.add(member);TileEntityCanopy helper=pendingAware(world,cell);if(helper==null)throw new IllegalStateException("Canopy helper tile missing");
                helper.anchor=member.getPos();helper.centeredPlacement=member.centeredPlacement;helper.hollowPlacement=member.hollowPlacement;net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();member.large.write(settings);helper.large.read(settings);helper.sync();
            }
            for(TileEntityCanopy member:members){
                for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)){
                    // Keep the physical backrest and wake this owner if its seat
                    // is later removed, including distant large-shell cells.
                    if(sharedBackrest(member,cell))watchSharedBackrest(owner,cell);else member.releasedCell(cell,false);
                }
                member.sync();
            }
            return true;
        }catch(RuntimeException failed){
            for(int i=0;i<added.size();i++){
                BlockPos cell=added.get(i);TileEntityCanopy parent=parents.get(i),helper=pendingAware(world,cell);
                // Only cells created by this transaction may be rolled back.
                // Also clean an uninitialized/missing tile if creation failed
                // before assigning its anchor, never an unrelated placed root.
                if(world.getBlockState(cell)==world.getBlockState(parent.getPos())&&(helper==null||parent.getPos().equals(helper.anchor)||helper.anchor==null&&!helper.hasSavedFootprint())){
                    if(helper!=null)helper.removing=true;world.setBlockToAir(cell);
                }
            }
            return false;
        }
    }
    private static String sweepKey(TileEntityCanopy owner){return owner.getPos()+":"+owner.facing()+":"+owner.width()+":"+owner.centeredPlacement+":"+owner.hollowPlacement+":"+owner.large.key()+":"+owner.model()+":"+owner.mask();}
    private static void cache(TileEntityCanopy owner,SweepCache sweep){SWEEPS.put(owner,sweep);while(SWEEPS.size()>8)SWEEPS.remove(SWEEPS.keySet().iterator().next());}
    private static void cancel(TileEntityCanopy owner){PendingSweep pending=PENDING.remove(owner);if(pending!=null&&pending.future!=null){pending.future.cancel(false);if(pending.future instanceof Runnable)WORKER.remove((Runnable)pending.future);}}
    private static SweepCache readySweep(TileEntityCanopy owner){
        String key=sweepKey(owner);SweepCache cached=SWEEPS.get(owner);if(cached!=null&&cached.key.equals(key))return cached;
        PendingSweep pending=PENDING.get(owner);if(pending!=null&&!pending.key.equals(key)){cancel(owner);pending=null;}
        if(pending==null){pending=new PendingSweep(key);PENDING.put(owner,pending);}
        if(pending.failed)return null;
        if(pending.future==null&&WORKER.getQueue().remainingCapacity()>0){
            SweepSource source=new SweepSource(owner,key,1);try{pending.future=WORKER.submit(source::build);}catch(java.util.concurrent.RejectedExecutionException busy){return null;}
        }
        if(pending.future==null||!pending.future.isDone())return null;
        try{SweepCache result=pending.future.get();PENDING.remove(owner);cache(owner,result);owner.retryReservationClose();return result;}
        catch(Exception failure){pending.failed=true;pending.future=null;com.vandorlabs.VandorLabs.logger.error("Canopy closure preparation failed; holding safe pose at "+owner.getPos(),failure);return null;}
    }
    /** Opt-in geometry reference; live closure always uses readySweep's nonblocking worker. */
    public static List<AxisAlignedBB> closingSweep(TileEntityCanopy owner){
        String key=sweepKey(owner);SweepCache cached=SWEEPS.get(owner);if(owner.progress==1&&cached!=null&&cached.key.equals(key))return cached.boxes;
        SweepCache built=new SweepSource(owner,key,owner.progress).build();if(owner.progress==1)cache(owner,built);return built.boxes;
    }
    public static boolean preparingClose(TileEntityCanopy owner){if(!PENDING.containsKey(owner))return false;readySweep(owner);return PENDING.containsKey(owner);}
    @SubscribeEvent public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent event){
        if(event.world.isRemote||event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||PENDING.isEmpty())return;int queued=0;
        for(TileEntityCanopy owner:new ArrayList<>(PENDING.keySet())){
            if(owner.getWorld()!=event.world)continue;
            if(owner.isInvalid()||!event.world.isBlockLoaded(owner.getPos())||event.world.getTileEntity(owner.getPos())!=owner){cancel(owner);continue;}
            PendingSweep pending=PENDING.get(owner);if(pending==null||pending.failed)continue;
            if(pending.future!=null&&pending.future.isDone()){if(readySweep(owner)!=null)owner.retryReservationClose();}
            else if(pending.future==null&&queued<4&&WORKER.getQueue().remainingCapacity()>0){queued++;owner.retryReservationClose();}
        }
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){
        for(TileEntityCanopy owner:new ArrayList<>(PENDING.keySet()))if(owner.getWorld()==event.getWorld())cancel(owner);
        SWEEPS.keySet().removeIf(owner->owner.getWorld()==event.getWorld());WAITS.remove(event.getWorld());
    }
    private static void considerPartner(TileEntityCanopy owner,TileEntityRampController ramp,Map<BlockPos,TileEntityRampController> partners){
        if(ramp.moat&&ramp.attached()&&(ramp.isMoving()||ramp.isOpen())&&!ramp.getRedstoneChannels().intersect(owner.getRedstoneChannels()).isEmpty())partners.put(ramp.getPos(),ramp);
    }
    public static boolean prepareClose(TileEntityCanopy owner){
        if(!real(owner))return true;clearWait(owner);World world=owner.getWorld();Set<BlockPos> blocked=new HashSet<>(),ramps=new HashSet<>();
        for(TileEntityCanopy member:owner.reservationMembers())for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)&&!available(member,cell))blocked.add(cell);
        // Ordinary closes do not tessellate a sweep. First inspect only loaded
        // ramp helper tiles in a conservative radius; ordinary bodywork retains
        // the established canopy policy.
        double radius=owner.width()+(owner.resizable()?owner.large.depth/16D:owner.block().length)+2;
        AxisAlignedBB area=new AxisAlignedBB(owner.getPos()).grow(radius);Map<BlockPos,List<AxisAlignedBB>> candidates=new HashMap<>();Map<BlockPos,TileEntityRampController> partners=new HashMap<>();
        for(int x=MathHelper.floor(area.minX)>>4;x<=MathHelper.floor(area.maxX)>>4;x++)for(int z=MathHelper.floor(area.minZ)>>4;z<=MathHelper.floor(area.maxZ)>>4;z++){
            net.minecraft.world.chunk.Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
            for(TileEntity tile:chunk.getTileEntityMap().values()){
                if(tile instanceof TileEntityRampController){TileEntityRampController ramp=(TileEntityRampController)tile;
                    considerPartner(owner,ramp,partners);
                }
                if(tile instanceof TileEntityControlledRamp&&area.intersects(new AxisAlignedBB(tile.getPos()))){
                // A long ramp's controller may be outside this neighborhood;
                // follow the loaded local part's journal owner without loading it.
                BlockPos controller=((TileEntityControlledRamp)tile).controller;
                net.minecraft.world.chunk.Chunk controllerChunk=world.getChunkProvider().getLoadedChunk(controller.getX()>>4,controller.getZ()>>4);
                TileEntity controllerTile=controllerChunk==null?null:controllerChunk.getTileEntityMap().get(controller);
                if(controllerTile instanceof TileEntityRampController)considerPartner(owner,(TileEntityRampController)controllerTile,partners);
                List<AxisAlignedBB> physical=new ArrayList<>();
                for(AxisAlignedBB box:((TileEntityControlledRamp)tile).boxes(0))physical.add(box.offset(tile.getPos()));
                // Reservations span whole blocks, but slabs and empty arc cells
                // must not obstruct a shell that clears their actual geometry.
                if(!physical.isEmpty())candidates.put(tile.getPos(),physical);
                }
            }
        }
        CanopyCollisionIndex sweep=null;
        if(!candidates.isEmpty()||!partners.isEmpty()){SweepCache cached=readySweep(owner);if(cached==null){RampCanopyCoordination.waitingCanopy(owner,partners.keySet());return false;}sweep=cached.index;}
        // A shared Moat's currently empty arc is not permission to close midway.
        // Its persistent source journal proves the complete path until retraction
        // finishes, including after either mechanism is reloaded.
        for(TileEntityRampController ramp:partners.values()){boolean crosses=false;
            for(AxisAlignedBB path:ramp.vehicleMoatSweep())if(sweep.intersects(path.shrink(1e-7))){crosses=true;break;}
            if(crosses){blocked.add(ramp.getPos());ramps.add(ramp.getPos());}
        }
        if(!candidates.isEmpty()){
            for(Map.Entry<BlockPos,List<AxisAlignedBB>> candidate:candidates.entrySet())
                for(AxisAlignedBB physical:candidate.getValue())if(sweep.intersects(physical.shrink(1e-7))){blocked.add(candidate.getKey());break;}
        }
        if(blocked.isEmpty()){if(restoreFootprint(owner))return true;for(TileEntityCanopy member:owner.reservationMembers())for(BlockPos cell:member.cells())if(member.isReleasedCell(cell))blocked.add(cell);}
        Map<BlockPos,List<WeakReference<TileEntityCanopy>>> waits=WAITS.computeIfAbsent(world,k->new HashMap<>());
        for(BlockPos cell:blocked){waits.computeIfAbsent(cell,k->new ArrayList<>()).add(new WeakReference<>(owner));TileEntity tile=world.isBlockLoaded(cell)?world.getTileEntity(cell):null;if(tile instanceof TileEntityControlledRamp)ramps.add(((TileEntityControlledRamp)tile).controller);}
        RampCanopyCoordination.waitingCanopy(owner,ramps);return false;
    }
    @SubscribeEvent public static void chunkLoaded(net.minecraftforge.event.world.ChunkEvent.Load event){
        if(event.getWorld().isRemote)return;Map<BlockPos,List<WeakReference<TileEntityCanopy>>> waits=WAITS.get(event.getWorld());if(waits==null)return;
        for(Map.Entry<BlockPos,List<WeakReference<TileEntityCanopy>>> entry:new ArrayList<>(waits.entrySet()))if((entry.getKey().getX()>>4)==event.getChunk().x&&(entry.getKey().getZ()>>4)==event.getChunk().z){
            waits.remove(entry.getKey());for(WeakReference<TileEntityCanopy> ref:entry.getValue()){TileEntityCanopy owner=ref.get();if(owner!=null&&!owner.isInvalid())owner.retryReservationClose();}
        }
    }
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event){
        if(event.getWorld().isRemote)return;Map<BlockPos,List<WeakReference<TileEntityCanopy>>> waits=WAITS.get(event.getWorld());if(waits==null)return;
        Set<BlockPos> changed=new HashSet<>();changed.add(event.getPos());for(net.minecraft.util.EnumFacing side:event.getNotifiedSides())changed.add(event.getPos().offset(side));
        for(BlockPos pos:changed){
            List<WeakReference<TileEntityCanopy>> refs=waits.remove(pos);if(refs!=null)for(WeakReference<TileEntityCanopy> ref:refs){TileEntityCanopy owner=ref.get();if(owner!=null&&!owner.isInvalid())owner.retryReservationClose();}}
    }
}
