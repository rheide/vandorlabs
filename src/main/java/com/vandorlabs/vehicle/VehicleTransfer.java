package com.vandorlabs.vehicle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Write-ahead rollback record; chunk and entity writes are flushed before retiring it.
 * Transfers bypass break callbacks, which otherwise drop inventories and dismantle owned cells. */
public final class VehicleTransfer {
    private static final ThreadLocal<WorldServer> MUTATING=new ThreadLocal<>();
    /** Prevent ramp neighbour callbacks from deploying outside an atomic transfer footprint. */
    public static boolean isMutating(net.minecraft.world.World world){return MUTATING.get()==world;}
    private static Path journal(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("data").resolve("vandorlabs-vehicle-"+world.provider.getDimension()+".nbt");
    }
    private static void write(WorldServer world,NBTTagCompound tag)throws Exception {
        long began=System.nanoTime();
        if(world.disableLevelSaving)throw new IOException("World saving is disabled.");
        Path path=journal(world),temp=path.resolveSibling(path.getFileName()+".tmp");Files.createDirectories(path.getParent());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(tag,bytes);
        try(FileOutputStream stream=new FileOutputStream(temp.toFile())) {stream.write(bytes.toByteArray());stream.getFD().sync();}
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        timing("journal",began);
    }
    private static void flush(WorldServer world)throws Exception {
        long began=System.nanoTime();
        world.saveAllChunks(true,null);world.getChunkProvider().flushToDisk();
        timing("save-flush",began);
    }
    private static void timing(String phase,long began){if(VehicleDiagnostics.ENABLED)System.out.println("[vandorlabs] vehicle-transfer phase="+phase+" elapsed-us="+(System.nanoTime()-began)/1000);}
    public static boolean pending(WorldServer world){return Files.exists(journal(world));}
    private static NBTTagCompound record(VehicleStructure structure,BlockPos origin,EntityGroundVehicle entity,boolean park) {
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);tag.setTag("Craft",structure.write());
        tag.setLong("Origin",origin.toLong());tag.setUniqueId("Entity",entity.getUniqueID());tag.setBoolean("Parking",park);
        tag.setBoolean("TaxiReturnPending",entity.taxiReturnPending);if(entity.taxiReturnActor!=null)tag.setUniqueId("TaxiReturnActor",entity.taxiReturnActor);
        tag.setTag("AutopilotNavigation",entity.autopilot.write());
        tag.setFloat("Yaw",entity.rotationYaw);tag.setDouble("X",entity.posX);tag.setDouble("Y",entity.posY);tag.setDouble("Z",entity.posZ);return tag;
    }
    public static EntityGroundVehicle assemble(WorldServer world,VehicleStructure structure,BlockPos origin)throws Exception {return assemble(world,structure,origin,null);}
    public static EntityGroundVehicle assemble(WorldServer world,VehicleStructure structure,BlockPos origin,net.minecraft.entity.player.EntityPlayer actor)throws Exception {
        if(structure.cells.size()>com.vandorlabs.config.BlockAvailability.vehicleMaxBlocks())throw new IllegalArgumentException("Craft exceeds the configured vehicle block limit.");
        VehicleFlight.requireThruster(structure);
        VehicleRotation.validate(structure,new VehicleWorld(world,structure));
        if(structure.gearCount<3)throw new IllegalArgumentException("A craft needs at least three landing gear or wheel supports.");
        if(pending(world))throw new IOException("A previous vehicle transfer needs recovery.");
        EntityGroundVehicle entity=new EntityGroundVehicle(world);entity.install(structure);entity.setPosition(origin.getX(),origin.getY(),origin.getZ());
        Map<net.minecraft.entity.Entity,BlockPos> seated=new LinkedHashMap<>();
        List<com.vandorlabs.entity.EntityChairSeat> oldSeats=new ArrayList<>();
        for(com.vandorlabs.entity.EntityChairSeat chair:world.getEntitiesWithinAABB(com.vandorlabs.entity.EntityChairSeat.class,structure.bounds.offset(origin).grow(1))){
            BlockPos local=chair.getChairPos().subtract(origin);
            if(structure.pilotSeats().contains(local) && !chair.getPassengers().isEmpty())oldSeats.add(chair);
        }
        oldSeats.sort(java.util.Comparator.comparingInt(net.minecraft.entity.Entity::getEntityId));
        for(com.vandorlabs.entity.EntityChairSeat chair:oldSeats)seated.put(chair.getPassengers().get(0),chair.getChairPos().subtract(origin));
        boolean expandedEscalator=VehicleEscalators.needsExpandedSnapshot(world,structure,origin);
        VehicleEscalators.WorldSnapshot expanded=expandedEscalator?VehicleEscalators.expandedWorldSnapshot(world,structure,origin):new VehicleEscalators.WorldSnapshot(structure,origin);
        if(actor!=null)for(VehicleStructure.Cell cell:expanded.structure.cells){BlockPos p=expanded.origin.add(cell.pos);VehicleService.permission((net.minecraft.entity.player.EntityPlayerMP)actor,p);}
        NBTTagCompound undo=record(structure,origin,entity,false);if(expandedEscalator){undo.setTag("AssemblyOriginal",expanded.structure.write());undo.setLong("AssemblyOriginalOrigin",expanded.origin.toLong());}write(world,undo);
        try {
            for(net.minecraft.entity.Entity rider:seated.keySet())rider.dismountRidingEntity();
            blocks(world,expanded.structure,expanded.origin,false);
            entity.transferEpoch=VehicleOwnership.update(world,entity.getUniqueID(),1,true);
            if(!world.spawnEntity(entity))throw new IOException("Vehicle spawn was cancelled.");
            for(Map.Entry<net.minecraft.entity.Entity,BlockPos> rider:seated.entrySet()){
                entity.assignSeat(rider.getKey(),rider.getValue());
                if(!rider.getKey().startRiding(entity,true))throw new IOException("Could not transfer a seated pilot to the vehicle.");
                entity.updatePassenger(rider.getKey());
            }
            for(com.vandorlabs.entity.EntityChairSeat chair:oldSeats)chair.setDead();
            flush(world);Files.delete(journal(world));return entity;
        }catch(Exception failure){entity.setDead();rollback(world,undo);throw failure;}
    }
    public static void park(WorldServer world,EntityGroundVehicle entity,BlockPos origin,net.minecraft.entity.player.EntityPlayer player)throws Exception {
        park(world,entity,origin,player,true);
    }
    public static void park(WorldServer world,EntityGroundVehicle entity,BlockPos origin,net.minecraft.entity.player.EntityPlayer player,boolean keepSeated)throws Exception {
        if(entity.hover)throw new IOException("Select Taxi mode before parking as blocks.");
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
        NBTTagCompound undo=record(entity.structure,origin,entity,true);undo.setTag("ParkedCraft",placed.write());write(world,undo);
        entity.transferInProgress=true;WorldServer previousParkingMutation=MUTATING.get();MUTATING.set(world);
        try {
            java.util.List<net.minecraftforge.common.util.BlockSnapshot> replaced=new ArrayList<>();
            for(VehicleStructure.Cell cell:placed.cells)replaced.add(net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,origin.add(cell.pos)));
            blocks(world,placed,origin,true);
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
                com.vandorlabs.entity.EntityChairSeat seat=new com.vandorlabs.entity.EntityChairSeat(world,at,com.vandorlabs.blocks.BlockPilotSeat.seatHeight(world,at));
                seat.rotationYaw=world.getBlockState(at).getValue(com.vandorlabs.blocks.BlockPilotSeat.FACING).getHorizontalAngle();
                if(!world.spawnEntity(seat) || !rider.getKey().startRiding(seat,true))throw new IOException("Could not transfer a pilot to the parked seat.");
                seat.updatePassenger(rider.getKey());
            }
            flush(world);Files.delete(journal(world));
        }catch(Exception failure){rollback(world,undo);
            net.minecraft.entity.Entity restored=world.getEntityFromUuid(entity.getUniqueID());
            if(restored instanceof EntityGroundVehicle)for(Map.Entry<net.minecraft.entity.Entity,BlockPos> rider:passengers.entrySet()){
                ((EntityGroundVehicle)restored).assignSeat(rider.getKey(),rider.getValue());rider.getKey().startRiding(restored,true);
            }
            throw failure;}finally{entity.transferInProgress=false;if(previousParkingMutation==null)MUTATING.remove();else MUTATING.set(previousParkingMutation);}
    }
    public static void recover(WorldServer world)throws Exception {
        if(!pending(world))return;
        NBTTagCompound tag;
        try(InputStream in=Files.newInputStream(journal(world))){tag=CompressedStreamTools.readCompressed(in);}
        if(tag.getInteger("Schema")!=1)throw new IOException("Unknown vehicle recovery schema");
        rollback(world,tag);
    }
    private static void rollback(WorldServer world,NBTTagCompound undo)throws Exception {
        VehicleStructure structure=VehicleStructure.read(undo.getCompoundTag("Craft"));BlockPos origin=BlockPos.fromLong(undo.getLong("Origin"));
        // Recovery alone may load the affected chunks: no transfer can have moved before its journal was retired.
        Set<Long> chunks=new HashSet<>();
        VehicleStructure affected=undo.hasKey("ParkedCraft",10)?VehicleStructure.read(undo.getCompoundTag("ParkedCraft")):structure;
        for(VehicleStructure.Cell cell:affected.cells){BlockPos p=origin.add(cell.pos);long key=((long)(p.getX()>>4)<<32)^((p.getZ()>>4)&0xffffffffL);if(chunks.add(key))world.getChunkFromBlockCoords(p);}
        world.getChunkFromBlockCoords(new BlockPos(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z")));
        UUID id=undo.getUniqueId("Entity");
        for(net.minecraft.entity.Entity entity:new ArrayList<>(world.loadedEntityList))
            if(entity.getUniqueID().equals(id)){entity.removePassengers();world.removeEntityDangerously(entity);}
        if(undo.getBoolean("Parking")) {
            blocks(world,undo.hasKey("ParkedCraft",10)?VehicleStructure.read(undo.getCompoundTag("ParkedCraft")):structure,origin,false);
            EntityGroundVehicle restored=new EntityGroundVehicle(world);restored.setUniqueId(id);restored.install(structure);
            restored.taxiReturnPending=undo.getBoolean("TaxiReturnPending");restored.taxiReturnActor=undo.hasUniqueId("TaxiReturnActor")?undo.getUniqueId("TaxiReturnActor"):null;
            restored.autopilot.read(undo.getCompoundTag("AutopilotNavigation"));
            restored.transferEpoch=VehicleOwnership.update(world,id,1,true);
            restored.rotationYaw=undo.getFloat("Yaw");
            restored.setPosition(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z"));
            if(!world.spawnEntity(restored))throw new IOException("Recovery vehicle spawn cancelled; journal retained.");
        }else {VehicleOwnership.update(world,id,1,false);blocks(world,undo.hasKey("AssemblyOriginal",10)?VehicleStructure.read(undo.getCompoundTag("AssemblyOriginal")):structure,undo.hasKey("AssemblyOriginalOrigin",4)?BlockPos.fromLong(undo.getLong("AssemblyOriginalOrigin")):origin,true);}
        flush(world);Files.deleteIfExists(journal(world));
    }
    public static void blocks(WorldServer world,VehicleStructure structure,BlockPos origin,boolean place) {
        long began=System.nanoTime();WorldServer previousMutation=MUTATING.get();MUTATING.set(world);
        try{
        Set<Chunk> chunks=new HashSet<>();Map<BlockPos,IBlockState> previous=new LinkedHashMap<>();
        for(VehicleStructure.Cell cell:structure.cells) {
            BlockPos p=origin.add(cell.pos);previous.put(p,world.getBlockState(p));world.removeTileEntity(p);
        }
        for(VehicleStructure.Cell cell:structure.cells) {
            BlockPos p=origin.add(cell.pos);Chunk chunk=world.getChunkFromBlockCoords(p);chunks.add(chunk);
            ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();int section=p.getY()>>4;
            if(sections[section]==Chunk.NULL_BLOCK_STORAGE)sections[section]=new ExtendedBlockStorage(section<<4,world.provider.hasSkyLight());
            sections[section].set(p.getX()&15,p.getY()&15,p.getZ()&15,place?cell.state:Blocks.AIR.getDefaultState());
        }
        if(place)for(VehicleStructure.Cell cell:structure.cells) if(cell.tileData()!=null) {
            BlockPos p=origin.add(cell.pos);NBTTagCompound restoredTag=VehicleEscalators.parked(VehicleTileData.translated(cell.tileData(),origin),world.getTotalWorldTime());
            TileEntity tile=TileEntity.create(world,restoredTag);
            if(tile==null)throw new IllegalStateException("Cannot restore component at "+p);
            // TileEntity.create invokes readFromNBT before assigning its World.
            // Complete the ramp's live-clock restoration in this atomic context.
            if(tile instanceof com.vandorlabs.tiles.TileEntityRampController) {
                tile.setWorld(world);tile.readFromNBT(restoredTag);
            }
            world.setTileEntity(p,tile);tile.markDirty();
        }
        for(Chunk chunk:chunks){chunk.generateSkylightMap();chunk.markDirty();}
        for(Map.Entry<BlockPos,IBlockState> entry:previous.entrySet()) {
            BlockPos p=entry.getKey();world.checkLight(p);world.notifyBlockUpdate(p,entry.getValue(),world.getBlockState(p),3);
        }
        for(BlockPos p:previous.keySet())world.notifyNeighborsOfStateChange(p,world.getBlockState(p).getBlock(),false);
        timing(place?"restore-blocks-light-neighbors":"remove-blocks-light-neighbors",began);
        }finally{if(previousMutation==null)MUTATING.remove();else MUTATING.set(previousMutation);}
    }
}
