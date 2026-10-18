package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.*;

/** Bounded immutable local geometry; world access and publication stay on the owning tick thread. */
public final class VehicleCollisionCache {
    public static final int MAX_ENTRIES=20,MAX_BOXES=120000,MAX_RAW_BOXES=200000;
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2),task->{Thread thread=new Thread(task,"Vandor collision preparation");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private static final Set<String> SAFE_OWN=new HashSet<>(Arrays.asList(
            "BlockCanopy","BlockPilotSeat","BlockVehicleController","BlockAutopilot","BlockLandingGear","BlockTelescopicLandingGear","BlockWheel",
            "BlockProgrammableBlock","BlockProgrammableSlab","BlockProgrammableStairs","BlockProgrammableGlass","BlockProgrammableWall","BlockGlassWall","BlockProgrammableDiagonalGlass",
            "BlockAnimatedScreenSelector","BlockProgrammableDiagonalScreen","BlockProgrammableConsole","BlockProgrammableHalfConsole","BlockVandorConsole",
            "BlockPropulsionLight","BlockConnectedPropulsionLight","BlockConnectedHoverPropulsionLight","BlockTrianglePropulsionLight",
            "BlockProgrammableTrapdoor","BlockProgrammableDiagonalTrapdoor","BlockSignalControl","BlockVandorSwitch","BlockTwinPowerLever","BlockIndustrialLever","BlockCompactLever","BlockMountedSwitch","BlockRockerSwitch",
            "BlockRampController","BlockControlledRamp"));
    private final LinkedHashMap<Pose,VehicleCollision.Geometry> entries=new LinkedHashMap<>(20,.75F,true);
    private NBTTagCompound configuration;
    private net.minecraft.world.World parent;
    private VehicleStructure warmSnapshot;
    private BlockPos warmOwner;
    private VehicleWorld warmView;
    private int nextPose,weight;
    private long generation,hits,misses,waits,fallbacks;
    private boolean asyncUnavailable,uncached;
    private long waitingSince;
    private String failure="";
    public long waits(){return waits;}
    public long fallbacks(){return fallbacks;}
    public String failure(){return failure;}
    private String bypass="";
    public String bypass(){return bypass;}
    private Future<VehicleCollision.Geometry> pending;
    private Pose pendingPose;
    private long pendingGeneration;
    private static final class Pose {
        final int[] values;final int hash;
        Pose(List<Integer> values){this.values=new int[values.size()];for(int i=0;i<values.size();i++)this.values[i]=values.get(i);hash=Arrays.hashCode(this.values);}
        Pose(int bits){values=new int[]{bits};hash=Arrays.hashCode(values);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){return value instanceof Pose&&Arrays.equals(values,((Pose)value).values);}
    }
    private static final class Description {
        final NBTTagCompound configuration;final VehicleStructure snapshot;final Pose pose;final BlockPos owner;
        Description(NBTTagCompound configuration,VehicleStructure snapshot,Pose pose,BlockPos owner){this.configuration=configuration;this.snapshot=snapshot;this.pose=pose;this.owner=owner;}
    }
    public int entries(){return entries.size();}
    public int boxes(){return weight;}
    public long hits(){return hits;}
    public long misses(){return misses;}
    public long generation(){return generation;}
    public boolean warming(){return pending!=null||warmSnapshot!=null&&nextPose<=16;}
    public void clear(){generation++;asyncUnavailable=false;waitingSince=0;failure="";entries.clear();weight=0;configuration=null;parent=null;warmSnapshot=null;warmOwner=null;warmView=null;nextPose=17;if(pending!=null)pending.cancel(false);pending=null;pendingPose=null;}
    private static boolean knownSource(Block source){String name=source.getClass().getName();return name.startsWith("net.minecraft.block.")||name.startsWith("com.vandorlabs.blocks.")&&SAFE_OWN.contains(source.getClass().getSimpleName());}
    private static boolean supported(VehicleWorld view,VehicleStructure.Cell cell,TileEntity tile){
        Block block=cell.state.getBlock();if(block.getRegistryName()==null)return false;
        if(block instanceof com.vandorlabs.gear.GearBlock)return tile instanceof com.vandorlabs.gear.GearTileEntity;
        String name=block.getClass().getName();
        if(name.startsWith("net.minecraft.block."))return tile==null&&!block.hasTileEntity(cell.state);
        if(!name.startsWith("com.vandorlabs.blocks.")||!SAFE_OWN.contains(block.getClass().getSimpleName()))return false;
        if(cell.hasTileData()&&tile==null)return false;
        if(tile!=null&&!tile.getClass().getName().startsWith("com.vandorlabs.tiles."))return false;
        if(tile instanceof TileEntityControlledRamp){
            TileEntityControlledRamp ramp=(TileEntityControlledRamp)tile;
            if(ramp.source==null||!knownSource(ramp.source.getBlock()))return false;
            for(BlockPos origin:ramp.origins)if(!knownSource(ramp.sourceState(origin).getBlock()))return false;
            return ramp.vehicleFrozen&&!ramp.isMoving();
        }
        if(tile instanceof TileEntityRampController){TileEntityRampController ramp=(TileEntityRampController)tile;return !ramp.isMoving()&&ramp.writeToNBT(new NBTTagCompound()).getBoolean("VehicleRampFrozen");}
        return true;
    }
    private Description describe(VehicleWorld view){return describe(view,true);}
    private Description describe(VehicleWorld view,boolean requireSupported){
        if(view==null||view.structure==null)return null;
        NBTTagCompound signature=new NBTTagCompound();signature.setBoolean("Hover",view.hover);signature.setLong("Seat",view.structure.seat.toLong());signature.setInteger("Facing",view.structure.facing.getIndex());
        NBTTagList blocks=new NBTTagList();List<VehicleStructure.Cell> cells=new ArrayList<>();List<Integer> poses=new ArrayList<>();BlockPos owner=null;int movingOwners=0;
        for(VehicleStructure.Cell cell:view.structure.cells){
            TileEntity tile=view.getTileEntity(cell.pos);if(requireSupported&&!supported(view,cell,tile)){bypass=String.valueOf(cell.state.getBlock().getRegistryName())+"/"+cell.state.getBlock().getClass().getSimpleName();return null;}
            NBTTagCompound data=tile==null?null:tile.writeToNBT(new NBTTagCompound());
            cells.add(new VehicleStructure.Cell(cell.pos,cell.state,data));
            NBTTagCompound entry=new NBTTagCompound();entry.setLong("Pos",cell.pos.toLong());entry.setTag("State",NBTUtil.writeBlockState(new NBTTagCompound(),cell.state));
            if(data!=null){
                NBTTagCompound normalized=data.copy();
                if(tile instanceof TileEntityCanopy){
                    TileEntityCanopy canopy=(TileEntityCanopy)tile;
                    normalized.removeTag("Open");normalized.removeTag("Progress");
                    normalized.removeTag("CanopyPowerKnown");normalized.removeTag("CanopyLastPower");normalized.removeTag("CanopyLastPolarity");
                    if(canopy.draws()&&canopy.opening()){poses.add(Float.floatToIntBits(canopy.progress));owner=canopy.getPos();movingOwners++;}
                }
                if(tile instanceof TileEntityLandingGear&&((TileEntityLandingGear)tile).isRoot()){normalized.removeTag("Progress");normalized.removeTag("LastSignal");poses.add(Float.floatToIntBits(((TileEntityLandingGear)tile).progress));entry.setTag("State",NBTUtil.writeBlockState(new NBTTagCompound(),cell.state.withProperty(com.vandorlabs.blocks.BlockTelescopicLandingGear.EXTENDED,true)));}
                if(tile instanceof com.vandorlabs.gear.GearTileEntity&&((com.vandorlabs.gear.GearTileEntity)tile).isController()){normalized.removeTag("GearProgress");normalized.removeTag("GearBlocked");normalized.removeTag("GearTargetDeployed");poses.add(Float.floatToIntBits((float)((com.vandorlabs.gear.GearTileEntity)tile).progress()));}
                entry.setTag("Tile",normalized);
            }
            blocks.appendTag(entry);
        }
        bypass="";signature.setTag("Blocks",blocks);
        return new Description(signature,new VehicleStructure(cells,view.structure.seat,view.structure.facing,view.structure.gearCount),new Pose(poses),movingOwners==1?owner:null);
    }
    private void accept(Description description,VehicleWorld view){
        if(parent==view.parent&&configuration!=null&&configuration.equals(description.configuration))return;
        clear();parent=view.parent;configuration=description.configuration;
        if(!uncached&&description.owner!=null){warmSnapshot=description.snapshot;warmOwner=description.owner;nextPose=0;}
    }
    private void put(Pose pose,VehicleCollision.Geometry geometry){
        if(!geometry.cacheable||geometry.boxes.size()>MAX_BOXES)return;
        VehicleCollision.Geometry old=entries.put(pose,geometry);if(old!=null)weight-=old.boxes.size();weight+=geometry.boxes.size();
        while(entries.size()>MAX_ENTRIES||weight>MAX_BOXES){Iterator<VehicleCollision.Geometry> iterator=entries.values().iterator();weight-=iterator.next().boxes.size();iterator.remove();}
    }
    public void reset(VehicleWorld view,VehicleCollision baseline){clear();Description description=describe(view);if(description!=null){accept(description,view);put(description.pose,baseline.geometry());}}
    public void restart(VehicleWorld view){clear();Description description=describe(view);if(description!=null)accept(description,view);}
    public VehicleCollision collision(VehicleWorld view){
        long signatureStart=VehicleAnimationDiagnostics.start(view.craft);Description description=describe(view);if(signatureStart!=0)sample(view,"cache-signature",System.nanoTime()-signatureStart);if(description==null){if(uncached){Description current=describe(view,false);poll();VehicleCollision.Geometry prepared=current==null?null:entries.remove(current.pose);if(prepared!=null&&configuration.equals(current.configuration)){weight-=prepared.boxes.size();hits++;return new VehicleCollision(prepared);}}clear();misses++;return new VehicleCollision(view);}
        accept(description,view);poll();VehicleCollision.Geometry found=entries.get(description.pose);
        if(found!=null){hits++;sample(view,"cache-hit-count",1);return new VehicleCollision(found);}
        misses++;sample(view,"cache-miss-count",1);VehicleCollision result=new VehicleCollision(view);put(description.pose,result.geometry());return result;
    }
    private static void sample(VehicleWorld view,String metric,long value){if(view.craft!=null&&VehicleAnimationDiagnostics.start(view.craft)!=0)VehicleAnimationDiagnostics.value((view.parent.isRemote?"client/":"server/")+metric,value);}
    private void poll(){
        if(pending==null||!pending.isDone())return;
        try{VehicleCollision.Geometry result=pending.get();if(pendingGeneration==generation)put(pendingPose,result);}catch(CancellationException canceled){fail("preparation canceled");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();fail("preparation interrupted");}catch(ExecutionException failed){fail("preparation failed: "+failed.getCause().getClass().getSimpleName());}
        pending=null;pendingPose=null;
    }
    private void fail(String reason){asyncUnavailable=true;failure=reason;warmSnapshot=null;warmView=null;}
    private static List<TileEntityCanopy> openingOwners(VehicleWorld view){List<TileEntityCanopy> result=new ArrayList<>();for(VehicleStructure.Cell cell:view.structure.cells){TileEntity tile=view.getTileEntity(cell.pos);if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=(TileEntityCanopy)tile;if(canopy.draws()&&canopy.opening())result.add(canopy);}}return result;}
    private static List<TileEntity> animatedOwners(VehicleWorld view){
        List<TileEntity> result=new ArrayList<>();for(VehicleStructure.Cell cell:view.structure.cells){TileEntity tile=view.getTileEntity(cell.pos);
            if(tile instanceof TileEntityCanopy&&((TileEntityCanopy)tile).draws()&&((TileEntityCanopy)tile).opening()||tile instanceof TileEntityLandingGear&&((TileEntityLandingGear)tile).isRoot()||tile instanceof com.vandorlabs.gear.GearTileEntity&&((com.vandorlabs.gear.GearTileEntity)tile).isController())result.add(tile);}
        return result;
    }
    private static float progress(TileEntity tile){return tile instanceof TileEntityCanopy?((TileEntityCanopy)tile).progress:tile instanceof com.vandorlabs.gear.GearTileEntity?(float)((com.vandorlabs.gear.GearTileEntity)tile).progress():((TileEntityLandingGear)tile).progress;}
    private static Pose pose(VehicleWorld view,Map<BlockPos,Float> desired){List<Integer> values=new ArrayList<>();for(TileEntity tile:animatedOwners(view))values.add(Float.floatToIntBits(desired.getOrDefault(tile.getPos(),progress(tile))));return new Pose(values);}
    /** Keep the last exact physical pose until the next pose is prepared. */
    public boolean readyForCanopyStep(VehicleWorld view){
        long discovery=VehicleAnimationDiagnostics.start(view.craft);
        Map<BlockPos,Float> desired=new HashMap<>();for(TileEntityCanopy tile:openingOwners(view))desired.put(tile.getPos(),Math.max(0,Math.min(1,tile.progress+(tile.targetOpen?1:-1)/16F)));
        VehicleAnimationDiagnostics.elapsed(view.craft,"cache-readiness-owner-discovery",discovery);
        return readyForCanopyPose(view,desired);
    }
    public boolean readyForCanopyPose(VehicleWorld view,Map<BlockPos,Float> desired){
        long started=VehicleAnimationDiagnostics.start(view.craft);try{return readyPose(view,desired);}finally{VehicleAnimationDiagnostics.elapsed(view.craft,"cache-readiness-total",started);}
    }
    private boolean readyPose(VehicleWorld view,Map<BlockPos,Float> desired){
        long described=VehicleAnimationDiagnostics.start(view.craft);Description description=describe(view);VehicleAnimationDiagnostics.elapsed(view.craft,"cache-readiness-describe",described);if(description==null){description=describe(view,false);uncached=true;if(description==null)return false;}else uncached=false;
        long checked=VehicleAnimationDiagnostics.start(view.craft);accept(description,view);poll();VehicleAnimationDiagnostics.elapsed(view.craft,"cache-readiness-accept-poll",checked);
        checked=VehicleAnimationDiagnostics.start(view.craft);Pose pose=pose(view,desired);VehicleAnimationDiagnostics.elapsed(view.craft,"cache-readiness-pose-vector",checked);
        if(entries.containsKey(pose)){waitingSince=0;return true;}
        if(asyncUnavailable){waits++;return false;}
        long now=System.nanoTime();if(waitingSince==0)waitingSince=now;
        // Queue pressure must never turn a deferred collision build into tick-thread work.
        if(pending!=null&&!pose.equals(pendingPose)){waits++;return false;}
        if(pending==null){long queued=VehicleAnimationDiagnostics.start(view.craft);queue(view,description,pose);VehicleAnimationDiagnostics.elapsed(view.craft,"cache-readiness-queue",queued);}
        if(asyncUnavailable){waits++;return false;}
        waits++;sample(view,"cache-animation-wait-count",1);return false;
    }
    private void queue(VehicleWorld view,Description description,Pose pose){
        if(pending!=null||WORKER.getQueue().remainingCapacity()==0)return;
        if(warmView==null){long cloned=VehicleAnimationDiagnostics.start(view.craft);warmView=new VehicleWorld(view.parent,description.snapshot);warmView.hover=view.hover;VehicleAnimationDiagnostics.elapsed(view.craft,"cache-isolated-view-create",cloned);}
        long configured=VehicleAnimationDiagnostics.start(view.craft);List<TileEntity> owners=animatedOwners(warmView);if(owners.size()!=pose.values.length){fail("canopy owner count changed");return;}
        for(int i=0;i<owners.size();i++){TileEntity tile=owners.get(i);float value=Float.intBitsToFloat(pose.values[i]);if(tile instanceof TileEntityCanopy)((TileEntityCanopy)tile).progress=value;else if(tile instanceof com.vandorlabs.gear.GearTileEntity)((com.vandorlabs.gear.GearTileEntity)tile).captureProgress(value);else ((TileEntityLandingGear)tile).progress=value;}
        VehicleAnimationDiagnostics.elapsed(view.craft,"cache-isolated-owner-configure",configured);
        long started=VehicleAnimationDiagnostics.start(view.craft);
        VehicleCollision.DeferredRaw raw=VehicleCollision.defer(warmView,MAX_RAW_BOXES);sample(view,"cache-mainthread-collect",started==0?0:System.nanoTime()-started);
        if(raw==null){fail("deferred geometry is unsupported or exceeds its budget");return;}
        pendingPose=pose;pendingGeneration=generation;
        try{pending=WORKER.submit(()->VehicleCollision.prepare(raw,MAX_RAW_BOXES));}catch(RejectedExecutionException busy){pendingPose=null;}
    }
    /** One future pose, closest to the real current endpoint/direction, without a Cartesian product. */
    public void tick(VehicleWorld view){
        poll();if(warmSnapshot==null||asyncUnavailable||pending!=null||WORKER.getQueue().remainingCapacity()==0)return;
        Description current=describe(view);if(current==null){return;}accept(current,view);if(warmSnapshot==null)return;
        List<TileEntityCanopy> owners=openingOwners(view);if(owners.size()!=1)return;TileEntityCanopy owner=owners.get(0);
        int at=Math.round(owner.progress*16),direction=owner.progress<=0?1:owner.progress>=1?-1:owner.targetOpen?1:-1;
        for(int offset=1;offset<=17;offset++){Pose pose=pose(view,Collections.singletonMap(owner.getPos(),Math.floorMod(at+direction*offset,17)/16F));if(!entries.containsKey(pose)){queue(view,current,pose);return;}}
        warmView=null;warmSnapshot=null;nextPose=17;
    }
}
