package com.vandorlabs.vehicle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Write-ahead rollback record; chunk and entity writes are flushed before retiring it.
 * Transfers bypass break callbacks, which otherwise drop inventories and dismantle owned cells. */
public final class VehicleTransfer {
    @FunctionalInterface interface Action {void run()throws Exception;}
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"Vandor transfer persistence");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final Map<WorldServer,ArrayDeque<Work>> WORK=new WeakHashMap<>();
    private static final Map<TileEntity,Integer> PAUSED_TILES=new IdentityHashMap<>();
    private static final ThreadLocal<Work> ACTIVE=new ThreadLocal<>();
    private static final ThreadLocal<UpdateBatch> EXTERNAL_UPDATES=new ThreadLocal<>();
    /** Other durable transfers share the same bounded lighting and notification implementation. */
    public static final class UpdateBatch {
        private final ArrayDeque<BlockUpdates> updates=new ArrayDeque<>();
        public AutoCloseable collect(){UpdateBatch previous=EXTERNAL_UPDATES.get();EXTERNAL_UPDATES.set(this);return ()->{if(previous==null)EXTERNAL_UPDATES.remove();else EXTERNAL_UPDATES.set(previous);};}
        public void clear(){updates.clear();}
        public boolean step(){long deadline=System.nanoTime()+2_000_000L;while(!updates.isEmpty()){if(updates.peek().step(deadline))updates.remove();if(System.nanoTime()>=deadline)break;}return updates.isEmpty();}
    }
    /** A preflight pass yields before any mutation. The commit pass revalidates live state. */
    private static final class Deferred extends RuntimeException {private Deferred(){super(null,null,false,false);}}
    private static final Deferred DEFERRED=new Deferred();
    private static final class Work {
        final WorldServer world;final Action action;final java.util.function.Consumer<Exception> failed;
        VehicleStructure geometrySource;VehiclePreparation preparation;VehicleCollision geometry;
        EntityGroundVehicle assembled,held;
        VehicleStructure beforeStructure;VehicleCollision beforeCollision;
        NBTTagCompound undo;VehicleOwnership.Update ownership;Path journal;
        Future<?> io;boolean journalReady,committed,retiring,aborting,rolledBack,rollbackNotice,mutated;
        TransferCheckpoint checkpoint;boolean checkpointReady;
        final ArrayDeque<BlockUpdates> updates=new ArrayDeque<>();
        final Set<TileEntity> pausedTiles=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());
        Work(WorldServer world,Action action,java.util.function.Consumer<Exception> failed){this.world=world;this.action=action;this.failed=failed;}
    }
    static void submit(WorldServer world,Action action,java.util.function.Consumer<Exception> failed){
        submit(world,null,null,action,failed);
    }
    /** Capture-time ticking is suspended only for this assembly's source tiles. */
    static void submit(WorldServer world,VehicleStructure source,BlockPos origin,Action action,java.util.function.Consumer<Exception> failed){
        ArrayDeque<Work> queue=WORK.computeIfAbsent(world,k->new ArrayDeque<>());
        if(queue.size()>=16){failed.accept(new IOException("Vehicle transfer queue is full; try again."));return;}
        Work work=new Work(world,action,failed);
        if(source!=null)for(VehicleStructure.Cell cell:source.cells){BlockPos pos=origin.add(cell.pos);
            if(!world.isBlockLoaded(pos))continue;TileEntity tile=world.getTileEntity(pos);
            if(tile instanceof net.minecraft.util.ITickable&&work.pausedTiles.add(tile))PAUSED_TILES.put(tile,PAUSED_TILES.getOrDefault(tile,0)+1);
        }
        if(!work.pausedTiles.isEmpty())world.tickableTileEntities.removeIf(work.pausedTiles::contains);
        queue.add(work);
    }
    static void unload(WorldServer world){
        ArrayDeque<Work> queue=WORK.remove(world);if(queue==null)return;
        // Do not wait for disk on unload. An unfinished durable record is recovered
        // on the next load, including a write already queued on the I/O worker.
        for(Work work:queue)release(work);
    }
    static boolean busy(WorldServer world){ArrayDeque<Work> queue=WORK.get(world);return queue!=null&&!queue.isEmpty();}
    static boolean waitingForPersistence(WorldServer world){ArrayDeque<Work> queue=WORK.get(world);Work work=queue==null?null:queue.peek();return work!=null&&work.undo!=null&&work.io!=null&&!work.io.isDone();}
    static VehicleCollision geometry(World world,VehicleStructure structure){
        Work work=ACTIVE.get();if(work==null)return new VehicleCollision(new VehicleWorld(world,structure));
        if(work.geometrySource==null){work.geometrySource=structure;work.preparation=new VehiclePreparation(new VehicleWorld(world,structure));}
        else if(work.geometrySource!=structure&&!work.geometrySource.write().equals(structure.write()))throw new IllegalArgumentException("Craft changed while preparing geometry; inspect it again.");
        if(work.geometry==null)try{work.geometry=work.preparation.poll();}catch(Exception failure){throw new IllegalStateException("Vehicle geometry preparation failed.",failure);}
        if(work.geometry==null)throw DEFERRED;
        return work.geometry;
    }
    static Long preparedOwnership(WorldServer world,UUID id,boolean active)throws IOException{
        Work work=ACTIVE.get();if(work==null)return null;
        if(work.ownership==null||!work.journalReady||work.world!=world||!work.ownership.id.equals(id)||work.ownership.active!=active)throw new IOException("Transfer ownership does not match its durable preparation.");
        work.ownership.publish(world);return work.ownership.epoch;
    }
    private static void retire(WorldServer world)throws IOException{Work work=ACTIVE.get();if(work==null)Files.deleteIfExists(journal(world));else work.committed=true;}
    private static void hold(Work work,EntityGroundVehicle entity){if(work!=null){if(work.held==null){work.beforeStructure=entity.structure;work.beforeCollision=entity.collision;}work.held=entity;entity.setTransferInProgress(true);}}
    private static void release(Work work){
        TransferCoordinator.release(work.world,work);
        if(work.checkpoint!=null)work.checkpoint.close();if(work.held!=null)work.held.setTransferInProgress(false);if(work.preparation!=null)work.preparation.cancel();
        for(TileEntity tile:work.pausedTiles){int remaining=PAUSED_TILES.getOrDefault(tile,1)-1;
            if(remaining>0){PAUSED_TILES.put(tile,remaining);continue;}PAUSED_TILES.remove(tile);
            if(!tile.isInvalid()&&work.world.isBlockLoaded(tile.getPos())&&work.world.getTileEntity(tile.getPos())==tile&&!work.world.tickableTileEntities.contains(tile))work.world.tickableTileEntities.add(tile);
        }
        work.pausedTiles.clear();
    }
    /** At most one affected chunk is serialized per tick; disk waits never run on this thread. */
    static void tick(WorldServer world){
        ArrayDeque<Work> queue=WORK.get(world);if(queue==null||queue.isEmpty())return;Work work=queue.peek();
        if(!TransferCoordinator.acquire(world,work))return;
        try{
            if(work.preparation!=null&&work.geometry==null){work.geometry=work.preparation.poll();if(work.geometry==null)return;}
            if(work.io!=null){if(!work.io.isDone())return;work.io.get();work.io=null;if(work.retiring||work.aborting){release(work);queue.remove();return;}work.journalReady=true;}
            if(work.aborting){try{work.io=IO.submit(()->{work.ownership.restore();Files.deleteIfExists(work.journal);return null;});}catch(RejectedExecutionException busy){}return;}
            if(work.committed){
                if(!work.updates.isEmpty()){
                    long deadline=System.nanoTime()+2_000_000L;
                    do{if(work.updates.peek().step(deadline))work.updates.remove();}
                    while(!work.updates.isEmpty()&&System.nanoTime()<deadline);
                    return;
                }
                if(work.checkpoint==null){
                    Set<Chunk> chunks=new LinkedHashSet<>();VehicleStructure affected=VehicleStructure.read(work.undo.getCompoundTag(work.undo.hasKey("ParkedCraft",10)?"ParkedCraft":work.undo.hasKey("AssemblyOriginal",10)?"AssemblyOriginal":"Craft"));
                    BlockPos origin=BlockPos.fromLong(work.undo.getLong(work.undo.hasKey("AssemblyOriginalOrigin",4)?"AssemblyOriginalOrigin":"Origin"));
                    for(VehicleStructure.Cell cell:affected.cells)chunks.add(world.getChunkFromBlockCoords(origin.add(cell.pos)));
                    if(work.held!=null)chunks.add(world.getChunkFromBlockCoords(work.held.getPosition()));
                    work.checkpoint=new TransferCheckpoint(world,chunks);
                }
                if(!work.checkpointReady){if(!work.checkpoint.step())return;work.checkpointReady=true;}
                try{work.io=IO.submit(()->{if(work.rolledBack)work.ownership.restore();Files.deleteIfExists(work.journal);return null;});work.retiring=true;}catch(RejectedExecutionException busy){}
                return;
            }
            ACTIVE.set(work);try{work.action.run();if(work.undo==null){release(work);queue.remove();}else work.committed=true;}finally{ACTIVE.remove();}
        }catch(Deferred waiting){
            if(work.undo!=null&&work.io==null&&!work.journalReady){
                try{work.io=IO.submit(()->{writeFile(work.journal,work.undo);work.ownership.write();return null;});}catch(RejectedExecutionException busy){}
            }
        }catch(Exception failure){
            ACTIVE.remove();
            if(work.rollbackNotice){work.rollbackNotice=false;work.failed.accept(failure);return;}
            if(work.aborting){com.vandorlabs.VandorLabs.logger.error("Vehicle transfer cleanup failed; recovery journal retained",failure);release(work);queue.remove();return;}
            if(work.committed||work.mutated){com.vandorlabs.VandorLabs.logger.error("Vehicle checkpoint failed; craft held and recovery journal retained",failure);work.failed.accept(new IOException("Vehicle checkpoint failed; craft held for recovery.",failure));release(work);if(work.held!=null)work.held.ownershipBlocked=true;queue.remove();return;}
            work.failed.accept(failure);
            if(work.undo!=null&&work.ownership!=null){
                try{work.io=IO.submit(()->{work.ownership.restore();Files.deleteIfExists(work.journal);return null;});work.aborting=true;}catch(RejectedExecutionException busy){/* Retry cleanup without mutating the world. */work.aborting=true;}
            }else{release(work);queue.remove();}
        }
    }
    private static final ThreadLocal<WorldServer> MUTATING=new ThreadLocal<>();
    private static final class BlockUpdates {
        final WorldServer world;final ArrayDeque<Chunk> chunks;final List<Map.Entry<BlockPos,IBlockState>> cells;
        int index;boolean neighbors;
        BlockUpdates(WorldServer world,Set<Chunk> chunks,Map<BlockPos,IBlockState> cells){this.world=world;this.chunks=new ArrayDeque<>(chunks);this.cells=new ArrayList<>(cells.entrySet());}
        boolean step(){return step(System.nanoTime()+2_000_000L);}
        boolean step(long deadline){
            int remaining=64;
            try(Transaction transaction=new Transaction(world)){
                if(!chunks.isEmpty()){Chunk chunk=chunks.remove();chunk.generateSkylightMap();chunk.markDirty();return false;}
                while(index<cells.size()&&remaining-->0){Map.Entry<BlockPos,IBlockState> cell=cells.get(index++);BlockPos pos=cell.getKey();
                    if(neighbors)world.notifyNeighborsOfStateChange(pos,world.getBlockState(pos).getBlock(),false);
                    else{world.checkLight(pos);world.notifyBlockUpdate(pos,cell.getValue(),world.getBlockState(pos),3);}
                    if(System.nanoTime()>=deadline)return false;
                }
                if(index==cells.size()){if(neighbors)return true;neighbors=true;index=0;}
                return false;
            }
        }
    }
    /** Prevent ramp neighbour callbacks from deploying outside an atomic transfer footprint. */
    public static boolean isMutating(net.minecraft.world.World world){return MUTATING.get()==world;}
    /** Share the existing callback and neighbour barrier with another durable ownership transfer. */
    public static AutoCloseable atomic(WorldServer world){return new Transaction(world);}
    /** Callback delivery resumes only after the complete nested conversion or rollback is installed. */
    private static final class Transaction implements AutoCloseable {
        private final WorldServer world,previous;
        Transaction(WorldServer world){this.world=world;previous=MUTATING.get();com.vandorlabs.redstone.RedstoneChannels.beginTransfer(world);MUTATING.set(world);}
        public void close(){if(previous==null)MUTATING.remove();else MUTATING.set(previous);com.vandorlabs.redstone.RedstoneChannels.endTransfer(world);}
    }
    private static Path journal(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("data").resolve("vandorlabs-vehicle-"+world.provider.getDimension()+".nbt");
    }
    private static void write(WorldServer world,NBTTagCompound tag)throws Exception {
        long began=System.nanoTime();
        if(world.disableLevelSaving)throw new IOException("World saving is disabled.");
        Work work=ACTIVE.get();
        if(work!=null){
            if(!(world.getChunkProvider().chunkLoader instanceof net.minecraft.world.chunk.storage.AnvilChunkLoader))throw new IOException("This chunk loader does not support deferred vehicle checkpoints.");
            if(work.undo==null){work.undo=tag.copy();work.journal=journal(world);work.ownership=VehicleOwnership.prepare(world,tag.getUniqueId("Entity"),1,!tag.getBoolean("Parking"));}
            else if(!work.undo.equals(tag))throw new IOException("Craft changed before the durable transfer could commit.");
            if(!work.journalReady)throw DEFERRED;return;
        }
        writeFile(journal(world),tag);
        timing("journal",began);
    }
    private static void writeFile(Path path,NBTTagCompound tag)throws IOException{
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.createDirectories(path.getParent());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(tag,bytes);
        try(FileOutputStream stream=new FileOutputStream(temp.toFile())) {stream.write(bytes.toByteArray());stream.getFD().sync();}
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private static void flush(WorldServer world)throws Exception {
        if(ACTIVE.get()!=null)return;
        long began=System.nanoTime();
        world.saveAllChunks(true,null);world.getChunkProvider().flushToDisk();
        timing("save-flush",began);
    }
    private static void timing(String phase,long began){if(VehicleDiagnostics.ENABLED)System.out.println("[vandorlabs] vehicle-transfer phase="+phase+" elapsed-us="+(System.nanoTime()-began)/1000);}
    public static boolean pending(WorldServer world){Work work=ACTIVE.get();return (work==null||work.world!=world||work.undo==null)&&Files.exists(journal(world));}
    private static NBTTagCompound record(VehicleStructure structure,BlockPos origin,EntityGroundVehicle entity,boolean park) {
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);tag.setTag("Craft",structure.write());
        tag.setLong("Origin",origin.toLong());tag.setUniqueId("Entity",entity.getUniqueID());tag.setBoolean("Parking",park);
        tag.setTag("OccupiedPilotSeats",entity.occupiedSeatAssignments());if(entity.taxiReturnSeat!=null)tag.setLong("TaxiReturnSeat",entity.taxiReturnSeat.toLong());
        tag.setBoolean("TaxiReturnPending",entity.taxiReturnPending);if(entity.taxiReturnActor!=null)tag.setUniqueId("TaxiReturnActor",entity.taxiReturnActor);
        if(entity.couplingActor()!=null)tag.setUniqueId("CouplingActor",entity.couplingActor());tag.setTag("AutopilotNavigation",entity.autopilot.write());
        tag.setFloat("Yaw",entity.rotationYaw);tag.setDouble("X",entity.posX);tag.setDouble("Y",entity.posY);tag.setDouble("Z",entity.posZ);return tag;
    }
    public static EntityGroundVehicle assemble(WorldServer world,VehicleStructure structure,BlockPos origin)throws Exception {return assemble(world,structure,origin,null);}
    public static EntityGroundVehicle assemble(WorldServer world,VehicleStructure structure,BlockPos origin,net.minecraft.entity.player.EntityPlayer actor)throws Exception {
        if(structure.cells.size()>com.vandorlabs.config.BlockAvailability.vehicleMaxBlocks())throw new IllegalArgumentException("Craft exceeds the configured vehicle block limit.");
        if(!structure.hasDedicatedController())throw new IllegalArgumentException("Vehicle Controller missing");
        VehicleWorld validationView=new VehicleWorld(world,structure);
        VehicleFlight.requireThruster(structure);
        boolean forceHover=structure.activeNonWheeledGear()&&!VehicleSkiTraction.canStart(world,structure,origin);
        if(forceHover && !VehicleFlight.canHover(structure))throw new IllegalArgumentException("Taxi requires wheels or skis over ice; other landing gear needs three lift blocks for Hover.");
        VehicleRotation.validate(structure,validationView);
        if(structure.gearCount<3){
            VehicleScan supports=VehicleScan.attachment(new VehicleWorld(world,structure),structure.seat,Collections.emptySet());
            while(!supports.step(256)){}
            if(supports.error!=null||supports.trackSupportCount()<2)throw new IllegalArgumentException("A craft needs at least three landing gear or wheel supports, or two track blocks.");
        }
        if(pending(world))throw new IOException("A previous vehicle transfer needs recovery.");
        Work work=ACTIVE.get();EntityGroundVehicle entity;
        if(work!=null){if(work.assembled==null){VehicleCollision prepared=geometry(world,structure);work.assembled=new EntityGroundVehicle(world);work.assembled.install(structure,prepared);}entity=work.assembled;}
        else{entity=new EntityGroundVehicle(world);entity.install(structure);}
        entity.recordCouplingActor(actor);entity.setPosition(origin.getX(),origin.getY(),origin.getZ());
        if(forceHover && !entity.setHover(true))throw new IllegalArgumentException("Unable to enter Hover for non-wheeled landing gear.");
        Map<net.minecraft.entity.Entity,BlockPos> seated=new LinkedHashMap<>();
        List<com.vandorlabs.entity.EntityChairSeat> oldSeats=new ArrayList<>();
        for(com.vandorlabs.entity.EntityChairSeat chair:world.getEntitiesWithinAABB(com.vandorlabs.entity.EntityChairSeat.class,structure.bounds.offset(origin).grow(1))){
            BlockPos local=chair.getChairPos().subtract(origin);
            if(structure.passengerSeats().contains(local) && !chair.getPassengers().isEmpty())oldSeats.add(chair);
        }
        oldSeats.sort(java.util.Comparator.comparingInt(net.minecraft.entity.Entity::getEntityId));
        for(com.vandorlabs.entity.EntityChairSeat chair:oldSeats)seated.put(chair.getPassengers().get(0),chair.getChairPos().subtract(origin));
        boolean expandedEscalator=VehicleEscalators.needsExpandedSnapshot(world,structure,origin);
        VehicleEscalators.WorldSnapshot expanded=expandedEscalator?VehicleEscalators.expandedWorldSnapshot(world,structure,origin):new VehicleEscalators.WorldSnapshot(structure,origin);
        if(actor!=null)for(VehicleStructure.Cell cell:expanded.structure.cells){BlockPos p=expanded.origin.add(cell.pos);VehicleService.permission((net.minecraft.entity.player.EntityPlayerMP)actor,p);}
        NBTTagCompound undo=record(structure,origin,entity,false);if(expandedEscalator){undo.setTag("AssemblyOriginal",expanded.structure.write());undo.setLong("AssemblyOriginalOrigin",expanded.origin.toLong());}write(world,undo);
        Transaction transaction=new Transaction(world);
        try {
            if(work!=null)work.mutated=true;
            for(net.minecraft.entity.Entity rider:seated.keySet())rider.dismountRidingEntity();
            blocks(world,expanded.structure,expanded.origin,false);
            entity.transferEpoch=VehicleOwnership.update(world,entity.getUniqueID(),1,true);
            hold(work,entity); // Include the hold in the initial spawn, before clients can predict.
            if(!world.spawnEntity(entity))throw new IOException("Vehicle spawn was cancelled.");
            entity.signals.tick();
            for(Map.Entry<net.minecraft.entity.Entity,BlockPos> rider:seated.entrySet()){
                entity.assignSeat(rider.getKey(),rider.getValue());
                if(!rider.getKey().startRiding(entity,true))throw new IOException("Could not transfer a seated pilot to the vehicle.");
                entity.updatePassenger(rider.getKey());
            }
            for(com.vandorlabs.entity.EntityChairSeat chair:oldSeats)chair.setDead();
            hold(work,entity);flush(world);retire(world);return entity;
        }catch(Exception failure){
            entity.setDead();if(work==null)rollback(world,undo);else rollbackDeferred(work,undo);
            try{restoreAssemblyPassengers(world,origin,seated,oldSeats);}catch(Exception seatingFailure){failure.addSuppressed(seatingFailure);}
            throw failure;
        }finally{transaction.close();}
    }
    /** Failed assembly returns riders in their original seat order, including late failures after chair retirement. */
    private static void restoreAssemblyPassengers(WorldServer world,BlockPos origin,Map<net.minecraft.entity.Entity,BlockPos> seated,List<com.vandorlabs.entity.EntityChairSeat> originals)throws IOException{
        for(Map.Entry<net.minecraft.entity.Entity,BlockPos> entry:seated.entrySet()){
            net.minecraft.entity.Entity rider=entry.getKey();if(rider.isDead)continue;
            BlockPos at=origin.add(entry.getValue());com.vandorlabs.entity.EntityChairSeat chair=null;
            for(com.vandorlabs.entity.EntityChairSeat original:originals)if(!original.isDead&&original.getChairPos().equals(at)){chair=original;break;}
            if(chair==null){
                chair=new com.vandorlabs.entity.EntityChairSeat(world,at,VehicleSeats.height(world,at));
                chair.rotationYaw=world.getBlockState(at).getValue(com.vandorlabs.blocks.BlockPilotSeat.FACING).getHorizontalAngle();
                if(!world.spawnEntity(chair))throw new IOException("Could not restore the original pilot chair.");
            }
            rider.dismountRidingEntity();if(!rider.startRiding(chair,true))throw new IOException("Could not restore a seated pilot after failed assembly.");
            chair.updatePassenger(rider);
        }
    }
    public static void park(WorldServer world,EntityGroundVehicle entity,BlockPos origin,net.minecraft.entity.player.EntityPlayer player)throws Exception {
        park(world,entity,origin,player,true);
    }
    public static void park(WorldServer world,EntityGroundVehicle entity,BlockPos origin,net.minecraft.entity.player.EntityPlayer player,boolean keepSeated)throws Exception {
        if(entity.hover && !((entity.structure.activeNonWheeledGear()||!entity.structure.hasDedicatedController()) && VehicleLandingGear.extended(entity) && VehicleFlight.clearance(entity)<=3))throw new IOException(entity.structure.activeNonWheeledGear()?"Extend the landing gear and descend near the ground before parking.":"Select Taxi mode before parking as blocks.");
        if(pending(world))throw new IOException("A previous vehicle transfer needs recovery.");
        VehicleStructure placed=VehicleRotation.plan(entity).structure;
        // Include newly extended gear cells in the final check before journaling or writing.
        for(VehicleStructure.Cell cell:placed.cells){BlockPos target=origin.add(cell.pos);
            if(target.getY()<0 || target.getY()>=world.getHeight() || !world.getWorldBorder().contains(target)
                    || !world.isBlockLoaded(target) || !world.isAirBlock(target))throw new IOException("Parking is blocked at "+target);
        }
        Map<net.minecraft.entity.Entity,BlockPos> passengers=new LinkedHashMap<>();
        for(net.minecraft.entity.Entity rider:entity.getPassengers())passengers.put(rider,entity.seatForPassenger(rider));
        int parkingTurns=VehicleRotation.turns(entity.rotationYaw);
        Work work=ACTIVE.get();hold(work,entity);
        NBTTagCompound undo=record(entity.structure,origin,entity,true);undo.setTag("ParkedCraft",placed.write());write(world,undo);
        entity.setTransferInProgress(true);Transaction transaction=new Transaction(world);
        try {
            if(work!=null)work.mutated=true;
            java.util.List<net.minecraftforge.common.util.BlockSnapshot> replaced=new ArrayList<>();
            for(VehicleStructure.Cell cell:placed.cells)replaced.add(net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,origin.add(cell.pos)));
            blocks(world,placed,origin,true);com.vandorlabs.tiles.WorldActionAuthorization.installed(world,placed,origin,player!=null?player.getUniqueID():entity.couplingActor());
            if(player!=null && net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent(replaced,
                    world.getBlockState(origin.down()),player,net.minecraft.util.EnumHand.MAIN_HAND)))throw new IOException("A protection rule prevented parking.");
            VehicleOwnership.update(world,entity.getUniqueID(),entity.transferEpoch+1,false);
            for(net.minecraft.entity.Entity rider:passengers.keySet())rider.dismountRidingEntity();
            entity.setDead();
            if(keepSeated)for(Map.Entry<net.minecraft.entity.Entity,BlockPos> rider:passengers.entrySet()){
                BlockPos local=VehicleRotation.cell(rider.getValue(),(int)entity.structure.bounds.maxX,(int)entity.structure.bounds.maxZ,parkingTurns);
                // Parking can rebase the hull when extending landing gear.
                local=local.add(placed.seat.subtract(VehicleRotation.cell(entity.structure.seat,(int)entity.structure.bounds.maxX,(int)entity.structure.bounds.maxZ,parkingTurns)));
                BlockPos at=origin.add(local);
                com.vandorlabs.entity.EntityChairSeat seat=new com.vandorlabs.entity.EntityChairSeat(world,at,VehicleSeats.height(world,at));
                seat.rotationYaw=world.getBlockState(at).getValue(com.vandorlabs.blocks.BlockPilotSeat.FACING).getHorizontalAngle();
                if(!world.spawnEntity(seat) || !rider.getKey().startRiding(seat,true))throw new IOException("Could not transfer a pilot to the parked seat.");
                seat.updatePassenger(rider.getKey());
            }
            hold(work,entity);flush(world);retire(world);
        }catch(Exception failure){if(work==null)rollback(world,undo);else rollbackDeferred(work,undo);
            net.minecraft.entity.Entity restored=world.getEntityFromUuid(entity.getUniqueID());
            if(restored instanceof EntityGroundVehicle)for(Map.Entry<net.minecraft.entity.Entity,BlockPos> rider:passengers.entrySet()){
                ((EntityGroundVehicle)restored).assignSeat(rider.getKey(),rider.getValue());rider.getKey().startRiding(restored,true);
            }
            throw failure;}finally{entity.setTransferInProgress(work!=null&&!entity.isDead);transaction.close();}
    }
    public static void recover(WorldServer world)throws Exception {
        if(!pending(world))return;
        NBTTagCompound tag;
        try(InputStream in=Files.newInputStream(journal(world))){tag=CompressedStreamTools.readCompressed(in);}
        if(tag.getInteger("Schema")!=1)throw new IOException("Unknown vehicle recovery schema");
        rollback(world,tag);
    }
    /** Restore live state immediately; checkpoint and ownership persistence remain deferred. */
    private static void rollbackDeferred(Work work,NBTTagCompound undo)throws Exception{
        WorldServer world=work.world;work.updates.clear();work.ownership.restoreMemory(world);
        BlockPos origin=BlockPos.fromLong(undo.getLong("Origin"));
        if(undo.getBoolean("Parking")){
            EntityGroundVehicle entity=work.held;world.removeEntityDangerously(entity);
            blocks(world,VehicleStructure.read(undo.getCompoundTag("ParkedCraft")),origin,false);
            entity.isDead=false;entity.install(work.beforeStructure,work.beforeCollision);
            entity.restoreSeatAssignments(undo.getCompoundTag("OccupiedPilotSeats"));entity.taxiReturnPending=undo.getBoolean("TaxiReturnPending");entity.taxiReturnSeat=undo.hasKey("TaxiReturnSeat",4)?BlockPos.fromLong(undo.getLong("TaxiReturnSeat")):null;entity.taxiReturnActor=undo.hasUniqueId("TaxiReturnActor")?undo.getUniqueId("TaxiReturnActor"):null;
            entity.couplingActor=undo.hasUniqueId("CouplingActor")?undo.getUniqueId("CouplingActor"):null;entity.autopilot.read(undo.getCompoundTag("AutopilotNavigation"));entity.rotationYaw=undo.getFloat("Yaw");entity.setPosition(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z"));
            if(!world.spawnEntity(entity))throw new IOException("Recovery vehicle spawn canceled; journal retained.");
        }else{
            if(work.assembled!=null)world.removeEntityDangerously(work.assembled);
            blocks(world,VehicleStructure.read(undo.getCompoundTag(undo.hasKey("AssemblyOriginal",10)?"AssemblyOriginal":"Craft")),undo.hasKey("AssemblyOriginalOrigin",4)?BlockPos.fromLong(undo.getLong("AssemblyOriginalOrigin")):origin,true);com.vandorlabs.tiles.WorldActionAuthorization.installed(world,VehicleStructure.read(undo.getCompoundTag(undo.hasKey("AssemblyOriginal",10)?"AssemblyOriginal":"Craft")),undo.hasKey("AssemblyOriginalOrigin",4)?BlockPos.fromLong(undo.getLong("AssemblyOriginalOrigin")):origin,undo.hasUniqueId("CouplingActor")?undo.getUniqueId("CouplingActor"):null);
        }
        work.rolledBack=true;work.rollbackNotice=true;work.committed=true;
    }
    private static void rollback(WorldServer world,NBTTagCompound undo)throws Exception {
        try(Transaction transaction=new Transaction(world)){
        VehicleStructure structure=VehicleStructure.read(undo.getCompoundTag("Craft"));BlockPos origin=BlockPos.fromLong(undo.getLong("Origin"));
        // Recovery alone may load the affected chunks: no transfer can have moved before its journal was retired.
        Set<Long> chunks=new HashSet<>();
        VehicleStructure affected=undo.hasKey("ParkedCraft",10)?VehicleStructure.read(undo.getCompoundTag("ParkedCraft")):structure;
        for(VehicleStructure.Cell cell:affected.cells){BlockPos p=origin.add(cell.pos);long key=((long)(p.getX()>>4)<<32)^((p.getZ()>>4)&0xffffffffL);if(chunks.add(key))world.getChunkFromBlockCoords(p);}
        world.getChunkFromBlockCoords(new BlockPos(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z")));
        VehicleStructure restoreSource=undo.hasKey("AssemblyOriginal",10)?VehicleStructure.read(undo.getCompoundTag("AssemblyOriginal")):structure;
        BlockPos restoreOrigin=undo.hasKey("AssemblyOriginalOrigin",4)?BlockPos.fromLong(undo.getLong("AssemblyOriginalOrigin")):origin;
        if(!undo.getBoolean("Parking"))for(VehicleStructure.Cell cell:restoreSource.cells)world.getChunkFromBlockCoords(restoreOrigin.add(cell.pos));
        requireUnchangedRecovery(world,undo.getBoolean("Parking")?affected:restoreSource,undo.getBoolean("Parking")?origin:restoreOrigin);
        UUID id=undo.getUniqueId("Entity");
        for(net.minecraft.entity.Entity entity:world.loadedEntityList)
            if(entity.getUniqueID().equals(id)&&!(entity instanceof EntityGroundVehicle))throw new IOException("Recovery entity UUID conflict; journal retained.");
        for(net.minecraft.entity.Entity entity:new ArrayList<>(world.loadedEntityList))
            if(entity.getUniqueID().equals(id)){entity.removePassengers();world.removeEntityDangerously(entity);}
        if(undo.getBoolean("Parking")) {
            blocks(world,undo.hasKey("ParkedCraft",10)?VehicleStructure.read(undo.getCompoundTag("ParkedCraft")):structure,origin,false);
            EntityGroundVehicle restored=new EntityGroundVehicle(world);restored.setUniqueId(id);restored.install(structure);
            restored.restoreSeatAssignments(undo.getCompoundTag("OccupiedPilotSeats"));restored.taxiReturnSeat=undo.hasKey("TaxiReturnSeat",4)?BlockPos.fromLong(undo.getLong("TaxiReturnSeat")):null;
            restored.taxiReturnPending=undo.getBoolean("TaxiReturnPending");restored.taxiReturnActor=undo.hasUniqueId("TaxiReturnActor")?undo.getUniqueId("TaxiReturnActor"):null;
            restored.couplingActor=undo.hasUniqueId("CouplingActor")?undo.getUniqueId("CouplingActor"):null;restored.autopilot.read(undo.getCompoundTag("AutopilotNavigation"));
            restored.transferEpoch=VehicleOwnership.update(world,id,1,true);
            restored.rotationYaw=undo.getFloat("Yaw");
            restored.setPosition(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z"));
            if(!world.spawnEntity(restored))throw new IOException("Recovery vehicle spawn cancelled; journal retained.");
            restored.signals.tick();
        }else {VehicleOwnership.update(world,id,1,false);blocks(world,undo.hasKey("AssemblyOriginal",10)?VehicleStructure.read(undo.getCompoundTag("AssemblyOriginal")):structure,undo.hasKey("AssemblyOriginalOrigin",4)?BlockPos.fromLong(undo.getLong("AssemblyOriginalOrigin")):origin,true);com.vandorlabs.tiles.WorldActionAuthorization.installed(world,restoreSource,restoreOrigin,undo.hasUniqueId("CouplingActor")?undo.getUniqueId("CouplingActor"):null);}
        flush(world);Files.deleteIfExists(journal(world));
        }
    }
    /** A retained journal must not overwrite edits made after the original interrupted transfer. */
    static void requireUnchangedRecovery(net.minecraft.world.World world,VehicleStructure snapshot,BlockPos origin)throws IOException {
        List<VehicleStructure.Cell> present=new ArrayList<>();
        for(VehicleStructure.Cell cell:snapshot.cells){
            BlockPos target=origin.add(cell.pos);
            if(!world.isBlockLoaded(target))throw new IOException("Recovery chunk unavailable; journal retained.");
            if(world.isAirBlock(target))continue;
            if(world.getBlockState(target)!=cell.state)throw new IOException("Recovery blocked by changed blocks at "+target+"; journal retained.");
            present.add(cell);
        }
        if(!VehicleStructure.display(present).matches(world,origin))throw new IOException("Recovery blocked by changed block contents; journal retained.");
    }
    public static void blocks(WorldServer world,VehicleStructure structure,BlockPos origin,boolean place) {
        long began=System.nanoTime();
        try(Transaction transaction=new Transaction(world)){
        Set<Chunk> chunks=new HashSet<>();Map<BlockPos,IBlockState> previous=new LinkedHashMap<>();
        for(VehicleStructure.Cell cell:structure.cells) {
            BlockPos p=origin.add(cell.pos);previous.put(p,world.getBlockState(p));
        }
        for(VehicleStructure.Cell cell:structure.cells) {
            BlockPos p=origin.add(cell.pos);Chunk chunk=world.getChunkFromBlockCoords(p);chunks.add(chunk);
            ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();int section=p.getY()>>4;
            if(sections[section]==Chunk.NULL_BLOCK_STORAGE)sections[section]=new ExtendedBlockStorage(section<<4,world.provider.hasSkyLight());
            sections[section].set(p.getX()&15,p.getY()&15,p.getZ()&15,place?cell.state:Blocks.AIR.getDefaultState());
        }
        // Invalidation may inspect neighboring tile entities. Publish all final states
        // first, so an already removed tile cannot be recreated from its old block.
        // World.removeTileEntity also removes pending entries created during a tile tick.
        for(BlockPos p:previous.keySet()){TileEntity detached=world.getTileEntity(p);world.removeTileEntity(p);VehicleForeignCompat.clearDetachedInventory(detached);}
        if(place)for(VehicleStructure.Cell cell:structure.cells) if(cell.tileData()!=null) {
            BlockPos p=origin.add(cell.pos);NBTTagCompound restoredTag=VehicleEscalators.parked(VehicleTileData.translated(cell.tileData(),origin),world.getTotalWorldTime());
            TileEntity tile=BuildingBlockCompat.createTile(cell,world,restoredTag);
            if(tile==null)throw new IllegalStateException("Cannot restore component at "+p);
            // TileEntity.create invokes readFromNBT before assigning its World.
            // Complete the ramp's live-clock restoration in this atomic context.
            if(tile instanceof com.vandorlabs.tiles.TileEntityRampController) {
                tile.setWorld(world);tile.readFromNBT(restoredTag);
            }
            world.setTileEntity(p,tile);tile.markDirty();
        }
        if(place)com.vandorlabs.tiles.DeferredTileLoad.flushInstalled(world,previous.keySet());
        Work work=ACTIVE.get();BlockUpdates updates=new BlockUpdates(world,chunks,previous);
        if(work!=null){for(Chunk chunk:chunks)chunk.markDirty();work.updates.add(updates);}
        else if(EXTERNAL_UPDATES.get()!=null){for(Chunk chunk:chunks)chunk.markDirty();EXTERNAL_UPDATES.get().updates.add(updates);}
        else while(!updates.step()){}
        timing(place?"restore-blocks-light-neighbors":"remove-blocks-light-neighbors",began);
        }
    }
}
