package com.vandorlabs.gear;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.VehicleStructure;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Transient packet correspondence only. Never written into terrain or ownership snapshots. */
public final class ArmCoreRenderTransfer {
    public static final String KEY="CoreRenderTransfer";
    public static final int LIMIT=64;
    public static final class Entry {
        public final int index;public final UUID source;public UUID destination;public BlockPos at;
        Entry(int index,UUID source){this.index=index;this.source=source;}
    }
    public final UUID epoch;public final List<Entry> entries;
    private ArmCoreRenderTransfer(UUID epoch,List<Entry> entries){this.epoch=epoch;this.entries=Collections.unmodifiableList(entries);}
    private static ArmCoreRenderTransfer create(GearTileEntity arm,ArmPayload payload,boolean source){
        List<Entry> entries=new ArrayList<>();for(int i=0;i<payload.blocks.cells.size();i++)if(payload.blocks.cells.get(i).state.getBlock() instanceof com.vandorlabs.blocks.BlockConstructorCore){
            if(entries.size()==LIMIT)return null;TileEntity tile=source?arm.getWorld().getTileEntity(payload.placedCellPosition(arm,i)):null;entries.add(new Entry(i,tile instanceof TileEntityConstructor?((TileEntityConstructor)tile).renderIdentity():null));
        }return entries.isEmpty()?null:new ArmCoreRenderTransfer(UUID.randomUUID(),entries);
    }
    static void capturing(GearTileEntity arm,ArmPayload payload){
        arm.coreRenderTransfer=create(arm,payload,true);if(arm.coreRenderTransfer!=null&&arm.getWorld() instanceof WorldServer){
            // Publish the cosmetic snapshot before any source AIR packet, on the same connection.
            send(arm);
        }
    }
    private static void send(GearTileEntity arm){WorldServer world=(WorldServer)arm.getWorld();for(net.minecraft.entity.player.EntityPlayerMP player:world.getMinecraftServer().getPlayerList().getPlayers())if(player.world==world&&world.getPlayerChunkMap().isPlayerWatchingChunk(player,arm.getPos().getX()>>4,arm.getPos().getZ()>>4))player.connection.sendPacket(arm.getUpdatePacket());}
    static void publishing(GearTileEntity arm,ArmPayload payload,BlockPos origin,VehicleStructure destination,Map<VehicleStructure.Cell,TileEntity> prepared){
        ArmCoreRenderTransfer transfer=arm.coreRenderTransfer;if(transfer==null)transfer=create(arm,payload,false);arm.coreRenderTransfer=transfer;if(transfer==null)return;
        for(Entry entry:transfer.entries){BlockPos at=origin.add(destination.cells.get(entry.index).pos);TileEntity tile=prepared.get(destination.cells.get(entry.index));if(tile instanceof TileEntityConstructor){TileEntityConstructor core=(TileEntityConstructor)tile;entry.at=at;entry.destination=core.renderIdentity();core.renderOwner=arm.getPos().toImmutable();core.renderEpoch=transfer.epoch;core.renderCell=entry.index;}}
    }
    static void published(GearTileEntity arm){if(arm.coreRenderTransfer!=null)for(Entry entry:arm.coreRenderTransfer.entries)if(entry.at!=null){net.minecraft.block.state.IBlockState state=arm.getWorld().getBlockState(entry.at);arm.getWorld().notifyBlockUpdate(entry.at,state,state,2);}}
    static void nativePublished(WorldServer world,BlockPos root,BlockPos at){
        TileEntity owner=world.getTileEntity(root),raw=world.getTileEntity(at);if(!(owner instanceof GearTileEntity)||!(raw instanceof TileEntityConstructor))return;GearTileEntity arm=(GearTileEntity)owner;ArmCoreRenderTransfer transfer=arm.coreRenderTransfer;if(transfer==null||arm.payload()==null||!arm.payload().materialized())return;
        for(Entry entry:transfer.entries)if(at.equals(entry.at)&&entry.destination!=null||entry.destination==null&&at.equals(arm.payload().placedCellPosition(arm,entry.index))){TileEntityConstructor core=(TileEntityConstructor)raw;if(entry.destination==null){entry.destination=core.renderIdentity();entry.at=at;send(arm);}core.renderIdentity(entry.destination);core.renderOwner=root;core.renderEpoch=transfer.epoch;core.renderCell=entry.index;return;}
    }
    public boolean containsIdentity(UUID id){if(id==null)return false;for(Entry entry:entries)if(id.equals(entry.source)||id.equals(entry.destination))return true;return false;}
    public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setUniqueId("Epoch",epoch);NBTTagList list=new NBTTagList();for(Entry entry:entries){NBTTagCompound e=new NBTTagCompound();e.setInteger("Cell",entry.index);if(entry.source!=null)e.setUniqueId("Source",entry.source);if(entry.destination!=null){e.setUniqueId("Destination",entry.destination);e.setLong("At",entry.at.toLong());}list.appendTag(e);}tag.setTag("Entries",list);return tag;}
    public static ArmCoreRenderTransfer read(NBTTagCompound tag,ArmPayload payload){return read(tag,payload,null);}
    public static ArmCoreRenderTransfer read(NBTTagCompound tag,ArmPayload payload,BlockPos root){
        if(!tag.hasUniqueId("Epoch")||!tag.hasKey("Entries",9)||payload==null)return null;NBTTagList list=tag.getTagList("Entries",10);if(list.tagCount()<1||list.tagCount()>LIMIT)return null;List<Entry> result=new ArrayList<>();Set<Integer> used=new HashSet<>();for(int i=0;i<list.tagCount();i++){NBTTagCompound e=list.getCompoundTagAt(i);int cell=e.getInteger("Cell");if(!e.hasKey("Cell",3)||cell<0||cell>=payload.blocks.cells.size()||!used.add(cell)||!(payload.blocks.cells.get(cell).state.getBlock() instanceof com.vandorlabs.blocks.BlockConstructorCore))return null;Entry entry=new Entry(cell,e.hasUniqueId("Source")?e.getUniqueId("Source"):null);if(e.hasUniqueId("Destination")){if(!e.hasKey("At",4))return null;entry.destination=e.getUniqueId("Destination");entry.at=BlockPos.fromLong(e.getLong("At"));if(root!=null&&(Math.abs((long)entry.at.getX()-root.getX())>128||Math.abs((long)entry.at.getY()-root.getY())>128||Math.abs((long)entry.at.getZ()-root.getZ())>128))return null;}result.add(entry);}return new ArmCoreRenderTransfer(tag.getUniqueId("Epoch"),result);
    }
    private ArmCoreRenderTransfer(){throw new AssertionError();}
}
