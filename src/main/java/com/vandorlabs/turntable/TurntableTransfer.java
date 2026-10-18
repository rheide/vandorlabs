package com.vandorlabs.turntable;

import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.world.BlockEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Durable rollback snapshots surround raw world transfers. A failed checkpoint retains its journal. */
public final class TurntableTransfer {
    private TurntableTransfer(){}
    private static Path directory(WorldServer world){return world.getSaveHandler().getWorldDirectory().toPath().resolve("data").resolve("vandorlabs-turntables-"+world.provider.getDimension());}
    private static Path file(WorldServer world,UUID id){return directory(world).resolve(id+".nbt");}
    public static boolean pending(EntityTurntable entity){return !entity.world.isRemote&&Files.exists(file((WorldServer)entity.world,entity.getUniqueID()));}
    private static void save(Path path,NBTTagCompound tag)throws IOException{
        Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(tag,bytes);
        try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(bytes.toByteArray());out.getFD().sync();}
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static void flush(WorldServer world)throws Exception{world.saveAllChunks(true,null);net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();}
    private static NBTTagCompound read(Path path)throws IOException{try(InputStream stream=Files.newInputStream(path)){return CompressedStreamTools.readCompressed(stream);}}
    private static void journal(WorldServer world,EntityTurntable entity,boolean captured)throws Exception{
        if(world.disableLevelSaving)throw new IOException("World saving is disabled");
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);tag.setBoolean("Captured",captured);tag.setTag("Display",entity.writeToNBT(new NBTTagCompound()));save(file(world,entity.getUniqueID()),tag);
    }
    private static EntityPlayerMP actor(WorldServer world,EntityTurntable entity,EntityPlayerMP player){if(player!=null)return player;if(entity.actor==null)throw new IllegalArgumentException("Missing turntable owner");return FakePlayerFactory.get(world,new com.mojang.authlib.GameProfile(entity.actor,"Turntable"));}
    private static boolean loaded(WorldServer world,TurntableSnapshot snapshot){
        BlockPos a=snapshot.layout.cell(0,0,1),b=snapshot.layout.cell(snapshot.layout.size-1,snapshot.layout.size-1,snapshot.height);
        BlockPos min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
        BlockPos max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        return snapshot.layout.loaded(world)&&world.isAreaLoaded(min,max,false);
    }
    private static Map<UUID,Entity> entities(WorldServer world){Map<UUID,Entity> result=new HashMap<>();for(Entity e:world.loadedEntityList)if(!e.isDead)result.put(e.getUniqueID(),e);return result;}
    private static void removeEntities(WorldServer world,TurntableSnapshot snapshot){Map<UUID,Entity> live=entities(world);for(TurntableSnapshot.FrozenEntity frozen:snapshot.entities){Entity e=live.get(frozen.id);if(e!=null){e.dismountRidingEntity();e.removePassengers();world.removeEntityDangerously(e);}}}
    private static List<Entity> prepare(WorldServer world,TurntableSnapshot snapshot){List<Entity> result=new ArrayList<>();for(TurntableSnapshot.FrozenEntity frozen:snapshot.entities){Entity e=EntityList.createEntityFromNBT(frozen.data(),world);if(e==null)throw new IllegalArgumentException("A captured entity type is unavailable; contents retained");if(!e.getUniqueID().equals(frozen.id))throw new IllegalArgumentException("Captured entity identity mismatch");result.add(e);}return result;}
    private static void spawn(WorldServer world,TurntableSnapshot snapshot,List<Entity> prepared)throws IOException{
        Map<UUID,Entity> live=entities(world);for(Entity entity:prepared)if(!live.containsKey(entity.getUniqueID())){if(!world.spawnEntity(entity))throw new IOException("An entity spawn was prevented; contents retained");live.put(entity.getUniqueID(),entity);}
        for(TurntableSnapshot.FrozenEntity frozen:snapshot.entities)if(frozen.parent!=null&&!live.get(frozen.id).startRiding(live.get(frozen.parent),true))throw new IOException("Could not restore captured mount");
    }
    public static void capture(WorldServer world,TileEntityTurntableController owner,TurntableSnapshot snapshot,EntityPlayerMP player)throws Exception{
        EntityTurntable entity=new EntityTurntable(world);entity.controller=owner.getPos();entity.controllerKey=owner.key;entity.actor=player.getUniqueID();entity.install(snapshot);
        if(!loaded(world,snapshot))throw new IOException("Load the complete capture volume first");
        journal(world,entity,false);
        try{
            VehicleTransfer.blocks(world,snapshot.blocks,snapshot.origin,false);
            if(!world.spawnEntity(entity))throw new IOException("A protection rule prevented the turntable entity");
            removeEntities(world,snapshot);owner.captureId=entity.getUniqueID();owner.changed();flush(world);Files.delete(file(world,entity.getUniqueID()));
        }catch(Exception failure){world.removeEntityDangerously(entity);try{rollback(world,read(file(world,entity.getUniqueID())));flush(world);Files.delete(file(world,entity.getUniqueID()));}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
    }
    public static void restore(WorldServer world,EntityTurntable entity,EntityPlayerMP requested)throws Exception{
        if(entity.snapshot==null)throw new IOException("Captured components are unavailable; contents retained");
        if(pending(entity))throw new IOException("A previous transfer requires recovery");
        TurntableSnapshot snapshot=entity.snapshot;EntityPlayerMP player=actor(world,entity,requested);
        if(!loaded(world,snapshot))throw new IOException("Load the complete return volume first");
        List<Entity> prepared=prepare(world,snapshot);List<BlockSnapshot> replaced=new ArrayList<>();
        for(VehicleStructure.Cell cell:snapshot.blocks.cells){BlockPos pos=snapshot.origin.add(cell.pos);VehicleService.permission(player,pos);if(!world.isAirBlock(pos))throw new IOException("Return space is blocked at "+pos);replaced.add(BlockSnapshot.getBlockSnapshot(world,pos));}
        Map<UUID,Entity> live=entities(world);for(TurntableSnapshot.FrozenEntity frozen:snapshot.entities)if(live.containsKey(frozen.id))throw new IOException("A captured entity UUID is already present; contents retained");
        journal(world,entity,true);
        try{
            VehicleTransfer.blocks(world,snapshot.blocks,snapshot.origin,true);
            if(!replaced.isEmpty()&&MinecraftForge.EVENT_BUS.post(new BlockEvent.MultiPlaceEvent(replaced,world.getBlockState(snapshot.origin.down()),player,net.minecraft.util.EnumHand.MAIN_HAND)))throw new IOException("A protection rule prevented restoring the contents");
            spawn(world,snapshot,prepared);world.removeEntityDangerously(entity);TileEntityTurntableController owner=entity.owner();if(owner!=null){owner.captureId=null;owner.reverse=false;owner.release();owner.status("Restored to the original scan positions");owner.changed();}
            flush(world);Files.delete(file(world,entity.getUniqueID()));
        }catch(Exception failure){try{rollback(world,read(file(world,entity.getUniqueID())));flush(world);Files.delete(file(world,entity.getUniqueID()));}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
    }
    private static void rollback(WorldServer world,NBTTagCompound tag)throws Exception{
        if(tag.getInteger("Schema")!=1)throw new IOException("Unknown turntable recovery schema");
        EntityTurntable saved=new EntityTurntable(world);saved.readFromNBT(tag.getCompoundTag("Display"));TurntableSnapshot snapshot=saved.snapshot;
        if(snapshot==null||!loaded(world,snapshot))throw new IOException("Recovery volume unavailable");
        boolean captured=tag.getBoolean("Captured");
        for(VehicleStructure.Cell cell:snapshot.blocks.cells){BlockPos pos=snapshot.origin.add(cell.pos);if(!world.isAirBlock(pos)&&world.getBlockState(pos)!=cell.state)throw new IOException("Recovery blocked by changed blocks at "+pos);}
        Map<UUID,Entity> live=entities(world);Entity current=live.get(saved.getUniqueID());
        if(current!=null&&!(current instanceof EntityTurntable))throw new IOException("Recovery entity UUID conflict");
        if(captured){VehicleTransfer.blocks(world,snapshot.blocks,snapshot.origin,false);removeEntities(world,snapshot);if(current==null&&!world.spawnEntity(saved))throw new IOException("Recovery spawn denied");}
        else{List<Entity> prepared=prepare(world,snapshot);VehicleTransfer.blocks(world,snapshot.blocks,snapshot.origin,true);if(current!=null)world.removeEntityDangerously(current);spawn(world,snapshot,prepared);}
        TileEntityTurntableController owner=saved.owner();if(owner!=null){owner.captureId=captured?saved.getUniqueID():null;if(captured){owner.layout=snapshot.layout;for(BlockPos cell:snapshot.layout.cells){net.minecraft.tileentity.TileEntity part=world.getTileEntity(cell);if(part instanceof com.vandorlabs.tiles.TileEntityTurntableSurface)((com.vandorlabs.tiles.TileEntityTurntableSurface)part).bind(owner);}}else owner.release();owner.changed();}
    }
    public static void recover(WorldServer world){
        Path folder=directory(world);if(!Files.isDirectory(folder))return;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(folder,"*.nbt")){for(Path path:files)try(InputStream stream=Files.newInputStream(path)){rollback(world,CompressedStreamTools.readCompressed(stream));flush(world);Files.delete(path);}catch(Exception deferred){/* Keep the durable original until every affected chunk/component is available. */}}
        catch(IOException failure){com.vandorlabs.VandorLabs.logger.error("Cannot inspect turntable recovery records",failure);}
    }
}
