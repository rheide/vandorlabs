package com.vandorlabs.neoforge.transfer;

import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Immutable snapshot mechanics shared by Copy and Turntable. Contains no publication or ownership authority. */
public final class NativeTransferSnapshot {
    public record Limits(int cells,int tileBytes,int bytes,int retainedBytes){
        public Limits {if(cells<1||cells>32768||tileBytes<1||tileBytes>8*1024*1024||bytes<1||bytes>16*1024*1024||retainedBytes<1||retainedBytes>64*1024*1024)throw new IllegalArgumentException("Transfer snapshot limits");}
    }
    private final List<NativeRawCell> cells;
    public final int retainedBytes,decodedBytes;
    private final Limits limits;
    public NativeTransferSnapshot(Collection<NativeRawCell> cells,Limits limits){
        this.limits=Objects.requireNonNull(limits);if(cells.isEmpty()||cells.size()>limits.cells)throw new IllegalArgumentException("Transfer snapshot cell bound");
        var seen=new HashSet<BlockPos>();var stable=new ArrayList<NativeRawCell>(cells.size());long weight=256;
        for(var cell:cells){Objects.requireNonNull(cell);if(!seen.add(cell.pos))throw new IllegalArgumentException("Duplicate transfer snapshot cell");var raw=cell.tile();if(raw!=null&&raw.sizeInBytes()>limits.tileBytes)throw new IllegalArgumentException("Transfer tile byte bound");weight=Math.addExact(weight,512L+(raw==null?0:4L*raw.sizeInBytes()));if(weight>limits.retainedBytes)throw new IllegalArgumentException("Transfer retained byte bound");stable.add(cell);}
        this.cells=List.copyOf(stable);retainedBytes=Math.toIntExact(weight);decodedBytes=write().sizeInBytes();
    }
    public List<NativeRawCell> cells(){return cells;}
    public CompoundTag write(){
        var result=new CompoundTag();result.putInt("Schema",1);var list=new ListTag();for(var cell:cells){var entry=new CompoundTag();entry.putLong("Pos",cell.pos.asLong());entry.put("State",NbtUtils.writeBlockState(cell.state));if(cell.hasTile())entry.put("Tile",cell.tile());list.add(entry);}result.put("Cells",list);if(result.sizeInBytes()>limits.bytes)throw new IllegalArgumentException("Transfer snapshot byte bound");return result;
    }
    public static NativeTransferSnapshot read(HolderLookup.Provider registries,CompoundTag input,Limits limits,Function<CompoundTag,Map<String,String>> missingProperties){
        if(input.sizeInBytes()>limits.bytes||!input.getAllKeys().equals(Set.of("Schema","Cells"))||!input.contains("Schema",Tag.TAG_INT)||input.getInt("Schema")!=1||!(input.get("Cells") instanceof ListTag list)||list.isEmpty()||list.size()>limits.cells||list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Transfer snapshot envelope");
        var cells=new ArrayList<NativeRawCell>(list.size());for(Tag value:list){var entry=(CompoundTag)value;boolean tile=entry.contains("Tile");if(!entry.getAllKeys().equals(tile?Set.of("Pos","State","Tile"):Set.of("Pos","State"))||!entry.contains("Pos",Tag.TAG_LONG)||!entry.contains("State",Tag.TAG_COMPOUND)||tile&&!entry.contains("Tile",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Transfer snapshot cell schema");var stateTag=entry.getCompound("State");var state=NativeRawCell.decodePalette(registries,stateTag,missingProperties.apply(stateTag.copy()));var position=BlockPos.of(entry.getLong("Pos"));var raw=NativeMatterMigration.tile(stateTag,tile?entry.getCompound("Tile"):null,position,registries);cells.add(new NativeRawCell(position,state,raw,limits.tileBytes));}return new NativeTransferSnapshot(cells,limits);
    }
    public List<BlockEntity> stage(ServerLevel world,Predicate<BlockEntity> supported,BiPredicate<CompoundTag,CompoundTag> equivalent){
        thread(world);var tiles=new ArrayList<BlockEntity>(cells.size());for(var cell:cells){loaded(world,cell.pos);tiles.add(cell.stage(world,supported,equivalent));}return Collections.unmodifiableList(tiles);
    }
    public boolean matches(ServerLevel world,BiPredicate<CompoundTag,CompoundTag> equivalent){
        thread(world);for(var cell:cells){loaded(world,cell.pos);if(world.getBlockState(cell.pos)!=cell.state)return false;var tile=world.getBlockEntity(cell.pos);if((tile!=null)!=cell.hasTile())return false;if(tile!=null&&!equivalent.test(cell.tile(),tile.saveWithFullMetadata(world.registryAccess())))return false;}return true;
    }
    private static void loaded(ServerLevel world,BlockPos pos){if(world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)throw new IllegalArgumentException("Load the complete transfer volume first");}
    private static void thread(ServerLevel world){if(!world.getServer().isSameThread())throw new IllegalStateException("Transfer snapshot requires its server thread");}
}
