package com.vandorlabs.copier;

import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.util.*;

/** Complete snapshots install without break callbacks, drops, or lossy parking-time substitutions. */
public final class CopyWorld {
    private CopyWorld(){}
    public static boolean frozen(WorldServer world,BlockPos at){return world.getTileEntity(at) instanceof CopyFrozenTile;}
    public static Map<BlockPos,TileEntity> prepare(WorldServer world,VehicleStructure snapshot,BlockPos origin){Map<BlockPos,TileEntity> result=new LinkedHashMap<>();VehicleWorld view=new VehicleWorld(world,snapshot);view.origin=origin;MovingBlockSupport.validate(snapshot,view);for(VehicleStructure.Cell cell:snapshot.cells)if(cell.hasTileData()){NBTTagCompound data=VehicleTileData.translated(cell.tileData(),origin);TileEntity tile=TileEntity.create(null,data);if(tile==null)throw new IllegalArgumentException("A copied block's data cannot be created");result.put(origin.add(cell.pos),tile);}return result;}
    public static void install(WorldServer world,VehicleStructure snapshot,BlockPos origin,Map<BlockPos,TileEntity> prepared){install(world,snapshot,origin,prepared,false);}
    public static void install(WorldServer world,VehicleStructure snapshot,BlockPos origin,Map<BlockPos,TileEntity> prepared,boolean frozen){
        for(VehicleStructure.Cell cell:snapshot.cells){BlockPos at=origin.add(cell.pos);if(!world.isBlockLoaded(at,false))throw new IllegalArgumentException("A copy target chunk unloaded");Chunk chunk=world.getChunkFromBlockCoords(at);ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();int section=at.getY()>>4;if(sections[section]==Chunk.NULL_BLOCK_STORAGE)sections[section]=new ExtendedBlockStorage(section<<4,world.provider.hasSkyLight());sections[section].set(at.getX()&15,at.getY()&15,at.getZ()&15,cell.state);chunk.markDirty();}
        for(VehicleStructure.Cell cell:snapshot.cells){BlockPos at=origin.add(cell.pos);TileEntity old=world.getTileEntity(at);world.removeTileEntity(at);if(frozen)emptyDetached(old);}
        for(Map.Entry<BlockPos,TileEntity> entry:prepared.entrySet()){TileEntity tile=frozen?new CopyFrozenTile(VehicleTileData.translated(snapshot.at(entry.getKey().subtract(origin)).tileData(),origin)):entry.getValue();tile.setWorld(world);tile.validate();world.setTileEntity(entry.getKey(),tile);tile.markDirty();if(world.getTileEntity(entry.getKey())!=tile)throw new IllegalStateException("A copied tile could not be installed");}
        rebuildColumns(world,snapshot,origin);
    }
    private static void emptyDetached(TileEntity tile){if(tile==null||tile instanceof CopyFrozenTile)return;tile.setWorld(null);if(tile instanceof net.minecraft.inventory.IInventory)((net.minecraft.inventory.IInventory)tile).clear();}
    public static void freeze(WorldServer world,BlockPos at){TileEntity tile=world.getTileEntity(at);if(tile==null||tile instanceof CopyFrozenTile)return;NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());world.removeTileEntity(at);emptyDetached(tile);CopyFrozenTile proxy=new CopyFrozenTile(data);proxy.setWorld(world);world.setTileEntity(at,proxy);}
    public static void publish(WorldServer world,BlockPos at){TileEntity raw=world.getTileEntity(at);if(!(raw instanceof CopyFrozenTile))return;TileEntity tile=TileEntity.create(null,((CopyFrozenTile)raw).saved());if(tile==null)throw new IllegalStateException("Frozen copy data cannot be published");world.removeTileEntity(at);tile.setWorld(world);tile.validate();world.setTileEntity(at,tile);tile.markDirty();}
    public static void clear(WorldServer world,VehicleStructure snapshot,BlockPos origin){for(VehicleStructure.Cell cell:snapshot.cells){BlockPos at=origin.add(cell.pos);Chunk chunk=world.getChunkFromBlockCoords(at);ExtendedBlockStorage section=chunk.getBlockStorageArray()[at.getY()>>4];if(section!=Chunk.NULL_BLOCK_STORAGE)section.set(at.getX()&15,at.getY()&15,at.getZ()&15,Blocks.AIR.getDefaultState());chunk.markDirty();}for(VehicleStructure.Cell cell:snapshot.cells)world.removeTileEntity(origin.add(cell.pos));rebuildColumns(world,snapshot,origin);}
    private static void rebuildColumns(WorldServer world,VehicleStructure snapshot,BlockPos origin){Set<Chunk> chunks=new LinkedHashSet<>();for(VehicleStructure.Cell cell:snapshot.cells)chunks.add(world.getChunkFromBlockCoords(origin.add(cell.pos)));for(Chunk chunk:chunks){chunk.generateSkylightMap();chunk.markDirty();}}
    public static boolean clearOrMatches(WorldServer world,VehicleStructure snapshot,BlockPos origin,boolean internal){for(VehicleStructure.Cell cell:snapshot.cells){BlockPos at=origin.add(cell.pos);if(!world.isBlockLoaded(at,false))return false;IBlockState state=world.getBlockState(at);if(world.isAirBlock(at)){if(world.getTileEntity(at)!=null)return false;continue;}if(state!=cell.state)return false;TileEntity tile=world.getTileEntity(at);if(cell.hasTileData()){if(tile==null&&internal)continue;if(tile==null||!VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin)).equals(cell.tileData()))return false;}else if(tile!=null)return false;}return true;}
    public static boolean empty(WorldServer world,VehicleStructure snapshot,BlockPos origin){for(VehicleStructure.Cell cell:snapshot.cells){BlockPos at=origin.add(cell.pos);if(!world.isBlockLoaded(at,false)||!world.isAirBlock(at)||world.getTileEntity(at)!=null)return false;}return true;}
    public static void notify(WorldServer world,VehicleStructure snapshot,BlockPos origin){for(VehicleStructure.Cell cell:snapshot.cells){BlockPos at=origin.add(cell.pos);world.checkLight(at);IBlockState state=world.getBlockState(at);world.notifyBlockUpdate(at,Blocks.AIR.getDefaultState(),state,3);world.notifyNeighborsOfStateChange(at,state.getBlock(),false);}}
}
