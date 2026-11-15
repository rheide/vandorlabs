package com.vandorlabs.neoforge.copier;

import com.vandorlabs.copier.core.CopyChannelFields;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.redstone.ChannelRange;
import java.util.List;
import net.minecraft.nbt.*;

/** Typed setting children only. Inventories, private item components and capability envelopes remain exact. */
final class NativeCopyChannels {
    static NativeRawCell increment(NativeRawCell source,int delta){ChannelRange.requireIncrement(delta);if(delta==0||!source.hasTile()||!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(source.state.getBlock()).getNamespace().equals("vandorlabs"))return source;var raw=source.tile();tile(raw,delta,0);return new NativeRawCell(source.pos,source.state,raw,2*1024*1024);}
    private static void fields(CompoundTag raw,int delta){CopyChannelFields.shift(new com.vandorlabs.neoforge.ModernHousingTags(raw),delta);}
    private static void tile(CompoundTag raw,int delta,int depth){
        if(depth>64)throw new IllegalArgumentException("Nested channel data bound");fields(raw,delta);
        for(String key:List.of("VehicleModeChannels0","VehicleModeChannels1","VehicleModeChannels2","DoorKeyReader","DoorRearKeyReader"))if(raw.contains(key,Tag.TAG_COMPOUND))tile(raw.getCompound(key),delta,depth+1);
        for(String key:List.of("RedstonePrimary","RedstoneSecondary"))if(raw.contains(key,Tag.TAG_COMPOUND))for(Tag value:raw.getCompound(key).getList("Rows",Tag.TAG_COMPOUND))fields((CompoundTag)value,delta);
        for(Tag value:raw.getList("CockpitControls",Tag.TAG_COMPOUND))fields((CompoundTag)value,delta);
        for(Tag value:raw.getList("CockpitDisplays",Tag.TAG_COMPOUND)){var display=(CompoundTag)value;if(display.contains("Gate",Tag.TAG_INT))display.putInt("Gate",ChannelRange.shift(display.getInt("Gate"),delta));}
        for(String key:List.of("Sources","Cells","Origins","RampSourceTiles"))for(Tag value:raw.getList(key,Tag.TAG_COMPOUND)){var row=(CompoundTag)value;if(row.contains("Tile",Tag.TAG_COMPOUND)&&row.getCompound("Tile").getString("id").startsWith("vandorlabs:"))tile(row.getCompound("Tile"),delta,depth+1);}
    }
    private NativeCopyChannels(){}
}
