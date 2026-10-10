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
    private static Path journal(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("data").resolve("vandorlabs-vehicle-"+world.provider.getDimension()+".nbt");
    }
    private static void write(WorldServer world,NBTTagCompound tag)throws Exception {
        if(world.disableLevelSaving)throw new IOException("World saving is disabled.");
        Path path=journal(world),temp=path.resolveSibling(path.getFileName()+".tmp");Files.createDirectories(path.getParent());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(tag,bytes);
        try(FileOutputStream stream=new FileOutputStream(temp.toFile())) {stream.write(bytes.toByteArray());stream.getFD().sync();}
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private static void flush(WorldServer world)throws Exception {
        world.saveAllChunks(true,null);world.getChunkProvider().flushToDisk();
    }
    public static boolean pending(WorldServer world){return Files.exists(journal(world));}
    private static NBTTagCompound record(VehicleStructure structure,BlockPos origin,EntityGroundVehicle entity,boolean park) {
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);tag.setTag("Craft",structure.write());
        tag.setLong("Origin",origin.toLong());tag.setUniqueId("Entity",entity.getUniqueID());tag.setBoolean("Parking",park);
        tag.setFloat("Yaw",entity.rotationYaw);tag.setDouble("X",entity.posX);tag.setDouble("Y",entity.posY);tag.setDouble("Z",entity.posZ);return tag;
    }
    public static EntityGroundVehicle assemble(WorldServer world,VehicleStructure structure,BlockPos origin)throws Exception {return assemble(world,structure,origin,null);}
    public static EntityGroundVehicle assemble(WorldServer world,VehicleStructure structure,BlockPos origin,net.minecraft.entity.player.EntityPlayer actor)throws Exception {
        if(pending(world))throw new IOException("A previous vehicle transfer needs recovery.");
        EntityGroundVehicle entity=new EntityGroundVehicle(world);entity.install(structure);entity.setPosition(origin.getX(),origin.getY(),origin.getZ());
        net.minecraft.entity.Entity seated=null;com.vandorlabs.entity.EntityChairSeat oldSeat=null;
        for(com.vandorlabs.entity.EntityChairSeat chair:world.getEntitiesWithinAABB(com.vandorlabs.entity.EntityChairSeat.class,new net.minecraft.util.math.AxisAlignedBB(origin.add(structure.seat)).grow(1)))
            if(chair.getChairPos().equals(origin.add(structure.seat)) && !chair.getPassengers().isEmpty()){seated=chair.getPassengers().get(0);oldSeat=chair;break;}
        NBTTagCompound undo=record(structure,origin,entity,false);write(world,undo);
        try {
            if(seated!=null)seated.dismountRidingEntity();
            blocks(world,structure,origin,false);
            entity.transferEpoch=VehicleOwnership.update(world,entity.getUniqueID(),1,true);
            if(!world.spawnEntity(entity))throw new IOException("Vehicle spawn was cancelled.");
            if(seated!=null){seated.startRiding(entity,true);entity.updatePassenger(seated);if(oldSeat!=null)oldSeat.setDead();}
            flush(world);Files.delete(journal(world));return entity;
        }catch(Exception failure){entity.setDead();rollback(world,undo);throw failure;}
    }
    public static void park(WorldServer world,EntityGroundVehicle entity,BlockPos origin,net.minecraft.entity.player.EntityPlayer player)throws Exception {
        if(pending(world))throw new IOException("A previous vehicle transfer needs recovery.");
        net.minecraft.entity.Entity driver=entity.getControllingPassenger();
        net.minecraft.entity.Entity relocate=driver!=null?driver:player!=null && entity.getEntityBoundingBox().contains(player.getPositionVector())?player:null;
        NBTTagCompound undo=record(entity.structure,origin,entity,true);write(world,undo);
        try {
            java.util.List<net.minecraftforge.common.util.BlockSnapshot> replaced=new ArrayList<>();
            for(VehicleStructure.Cell cell:entity.structure.cells)replaced.add(net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,origin.add(cell.pos)));
            blocks(world,entity.structure,origin,true);
            if(player!=null && net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent(replaced,
                    world.getBlockState(origin.down()),player,net.minecraft.util.EnumHand.MAIN_HAND)))throw new IOException("A protection rule prevented parking.");
            VehicleOwnership.update(world,entity.getUniqueID(),entity.transferEpoch+1,false);
            if(relocate!=null)relocate.dismountRidingEntity();entity.setDead();
            if(relocate!=null) {
                com.vandorlabs.entity.EntityChairSeat seat=new com.vandorlabs.entity.EntityChairSeat(world,origin.add(entity.structure.seat),com.vandorlabs.blocks.BlockPilotSeat.SEAT_HEIGHT);
                seat.rotationYaw=entity.structure.facing.getHorizontalAngle();
                if(!world.spawnEntity(seat) || !relocate.startRiding(seat,true))throw new IOException("Could not transfer the pilot to the parked seat.");
                seat.updatePassenger(relocate);
            }
            flush(world);Files.delete(journal(world));
        }catch(Exception failure){rollback(world,undo);
            net.minecraft.entity.Entity restored=world.getEntityFromUuid(entity.getUniqueID());if(driver!=null && restored instanceof EntityGroundVehicle)driver.startRiding(restored,true);
            throw failure;}
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
        for(VehicleStructure.Cell cell:structure.cells){BlockPos p=origin.add(cell.pos);long key=((long)(p.getX()>>4)<<32)^((p.getZ()>>4)&0xffffffffL);if(chunks.add(key))world.getChunkFromBlockCoords(p);}
        world.getChunkFromBlockCoords(new BlockPos(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z")));
        UUID id=undo.getUniqueId("Entity");
        for(net.minecraft.entity.Entity entity:new ArrayList<>(world.loadedEntityList))
            if(entity.getUniqueID().equals(id)){entity.removePassengers();world.removeEntityDangerously(entity);}
        if(undo.getBoolean("Parking")) {
            blocks(world,structure,origin,false);
            EntityGroundVehicle restored=new EntityGroundVehicle(world);restored.setUniqueId(id);restored.install(structure);
            restored.transferEpoch=VehicleOwnership.update(world,id,1,true);
            restored.rotationYaw=undo.getFloat("Yaw");
            restored.setPosition(undo.getDouble("X"),undo.getDouble("Y"),undo.getDouble("Z"));
            if(!world.spawnEntity(restored))throw new IOException("Recovery vehicle spawn cancelled; journal retained.");
        }else {VehicleOwnership.update(world,id,1,false);blocks(world,structure,origin,true);}
        flush(world);Files.deleteIfExists(journal(world));
    }
    public static void blocks(WorldServer world,VehicleStructure structure,BlockPos origin,boolean place) {
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
            BlockPos p=origin.add(cell.pos);TileEntity tile=TileEntity.create(world,VehicleTileData.translated(cell.tileData(),origin));
            if(tile==null)throw new IllegalStateException("Cannot restore component at "+p);
            world.setTileEntity(p,tile);tile.markDirty();
        }
        for(Chunk chunk:chunks){chunk.generateSkylightMap();chunk.markDirty();}
        for(Map.Entry<BlockPos,IBlockState> entry:previous.entrySet()) {
            BlockPos p=entry.getKey();world.checkLight(p);world.notifyBlockUpdate(p,entry.getValue(),world.getBlockState(p),3);
        }
        for(BlockPos p:previous.keySet())world.notifyNeighborsOfStateChange(p,world.getBlockState(p).getBlock(),false);
    }
}
