package com.vandorlabs.neoforge.copier;

import com.vandorlabs.copier.core.BlueprintCompression;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.io.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

/** Construction input only. Vehicle metadata never grants driving, spawning or operating authority. */
public final class NativeBlueprintDesign {
    public static final int MAX_CELLS=32768,MAX_AXIS=64;
    public static final class Cell {
        public final BlockPos offset;public final BlockState state;private final CompoundTag tile;
        private Cell(BlockPos offset,BlockState state,CompoundTag tile){this.offset=offset.immutable();this.state=state;this.tile=tile==null?null:tile.copy();}
        public CompoundTag tile(){return tile==null?null:tile.copy();}
    }
    public final List<Cell> cells;
    private NativeBlueprintDesign(List<Cell> cells){this.cells=List.copyOf(cells);}
    public static NativeBlueprintDesign read(ItemStack stack,HolderLookup.Provider registries){
        try {
        if(!NativeChipJournal.vehicle(stack))throw new IllegalArgumentException("Insert a written Vehicle Chip");
        var current=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();var data=current.contains("ChipType")?current:current.getCompound(NativeCredentialItems.LEGACY_KEY).getCompound("tag");
        if(!data.contains("VehicleDesign",Tag.TAG_BYTE_ARRAY)||data.contains("VehicleDesignCodec")&&!data.contains("VehicleDesignCodec",Tag.TAG_INT))throw new IllegalArgumentException("Vehicle Chip construction data");
        byte[] decoded=BlueprintCompression.decode(data.getInt("VehicleDesignCodec"),data.getByteArray("VehicleDesign"));var bytes=new ByteArrayInputStream(decoded);var input=new DataInputStream(bytes);var design=NbtIo.read(input,NbtAccounter.create(BlueprintCompression.MAX_DECODED_BYTES*4L));if(bytes.available()!=0)throw new IllegalArgumentException("Trailing Vehicle Chip construction data");
        return read(design,registries);
        }catch(IOException failed){throw new IllegalArgumentException("Malformed Vehicle Chip construction data",failed);}
    }
    public static NativeBlueprintDesign read(CompoundTag design,HolderLookup.Provider registries){
        if(!design.contains("Schema",Tag.TAG_INT)||design.getInt("Schema")!=1||design.getBoolean("DisplayOnly"))throw new IllegalArgumentException("Unsupported construction design schema");
        if(!(design.get("Palette") instanceof ListTag palette)||palette.isEmpty()||palette.getElementType()!=Tag.TAG_COMPOUND||palette.size()>MAX_CELLS||!(design.get("Blocks") instanceof ListTag blocks)||blocks.isEmpty()||blocks.getElementType()!=Tag.TAG_COMPOUND||blocks.size()>MAX_CELLS)throw new IllegalArgumentException("Construction palette/cell bounds");
        var states=new ArrayList<BlockState>();for(Tag value:palette){var tag=(CompoundTag)value;var missing=tag.getString("Name").equals("vandorlabs:turntable_surface")?Map.of("formed","false"):Map.<String,String>of();states.add(NativeRawCell.decodePalette(registries,tag,missing));}
        var cells=new ArrayList<Cell>();var seen=new HashSet<BlockPos>();for(Tag value:blocks){var tag=(CompoundTag)value;if(!tag.getAllKeys().stream().allMatch(Set.of("Pos","State","Tile")::contains)||!tag.contains("Pos",Tag.TAG_LONG)||!tag.contains("State",Tag.TAG_INT)||tag.contains("Tile")&&!tag.contains("Tile",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Construction cell fields");long packed=tag.getLong("Pos");var pos=new BlockPos(com.vandorlabs.persistence.LegacyBlockPosition.x(packed),com.vandorlabs.persistence.LegacyBlockPosition.y(packed),com.vandorlabs.persistence.LegacyBlockPosition.z(packed));int index=tag.getInt("State");if(index<0||index>=states.size()||pos.getX()<0||pos.getY()<0||pos.getZ()<0||pos.getX()>=MAX_AXIS||pos.getY()>=MAX_AXIS||pos.getZ()>=MAX_AXIS||!seen.add(pos))throw new IllegalArgumentException("Construction cell footprint");var state=states.get(index);var tile=com.vandorlabs.neoforge.transfer.NativeMatterMigration.tile(palette.getCompound(index),tag.contains("Tile")?tag.getCompound("Tile"):null,pos,registries);if(state.isAir()||state.hasBlockEntity()!=(tile!=null))throw new IllegalArgumentException("Construction tile/state mismatch");cells.add(new Cell(pos,state,tile));}
        return new NativeBlueprintDesign(cells);
    }
}
