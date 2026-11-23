package com.vandorlabs.copier;

import com.vandorlabs.copier.core.CopyChannelFields;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.rack.AutopilotModuleSettings;
import com.vandorlabs.rack.ModuleRackPolicy;
import com.vandorlabs.redstone.TransmissionSettings;
import com.vandorlabs.redstone.ChannelRange;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.nbt.*;
import java.util.*;

/** Copy only documented channel fields; inventories, artwork, levels and foreign NBT stay exact. */
public final class CopyChannels {
    private CopyChannels(){}
    public static VehicleStructure increment(VehicleStructure source,int delta){
        ChannelRange.requireIncrement(delta);
        if(delta==0)return source;
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:source.cells){
            NBTTagCompound tag=cell.tileData();
            if(tag!=null&&cell.state.getBlock().getRegistryName().getResourceDomain().equals("vandorlabs"))tile(tag,delta,0);
            cells.add(new VehicleStructure.Cell(cell.pos,cell.state,tag));
        }
        return source.displayOnly?VehicleStructure.display(cells):new VehicleStructure(cells,source.seat,source.facing,source.gearCount);
    }
    private static void fields(NBTTagCompound tag,int delta){
        CopyChannelFields.shift(new NbtPrimitiveData(tag),delta);
    }
    private static void bank(NBTTagCompound tag,String key,int delta){
        CopyChannelFields.shiftBank(new NbtPrimitiveData(tag),key,delta);
    }
    private static void transmitters(NBTTagCompound tag,int delta){
        NBTTagCompound slots=tag.getCompoundTag("ForgeData").getCompoundTag(TransmissionSettings.SETTINGS);
        for(String slot:slots.getKeySet())bank(slots.getCompoundTag(slot),TransmissionSettings.CHANNELS,delta);
    }
    private static void installedModules(NBTTagCompound rack,int delta){
        NBTTagList items=rack.getTagList("Items",10);
        for(int i=0;i<items.tagCount();i++){
            NBTTagCompound item=items.getCompoundTagAt(i);
            if(ModuleRackPolicy.AUTOPILOT_ID.equals(item.getString("id"))){
                NBTTagCompound active=item.getCompoundTag("tag").getCompoundTag(AutopilotModuleSettings.KEY);
                bank(active,com.vandorlabs.redstone.ChannelData.CHANNELS,delta);
            }
        }
    }
    private static void tile(NBTTagCompound tag,int delta,int depth){
        if(depth>64)throw new IllegalArgumentException("Nested block channel data is too deep");
        fields(tag,delta);
        transmitters(tag,delta);
        if("vandorlabs:module_rack".equals(tag.getString("id")))installedModules(tag,delta);
        for(String key:new String[]{"VehicleModeChannels0","VehicleModeChannels1","VehicleModeChannels2","DoorKeyReader","DoorRearKeyReader"})
            if(tag.hasKey(key,10))tile(tag.getCompoundTag(key),delta,depth+1);
        for(int slot=0;slot<com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SLOTS;slot++){
            String key=com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.redstoneTag(slot);
            if(!tag.hasKey(key,10))continue;
            NBTTagList rows=tag.getCompoundTag(key).getTagList("Rows",10);
            for(int i=0;i<rows.tagCount();i++)fields(rows.getCompoundTagAt(i),delta);
        }
        NBTTagList controls=tag.getTagList("CockpitControls",10);
        for(int i=0;i<controls.tagCount();i++){
            NBTTagCompound control=controls.getCompoundTagAt(i);
            fields(control,delta);
            bank(control,"LevelChannels",delta);
        }
        NBTTagList displays=tag.getTagList("CockpitDisplays",10);
        for(int i=0;i<displays.tagCount();i++){
            NBTTagCompound display=displays.getCompoundTagAt(i);
            if(display.hasKey("Gate",3))display.setInteger("Gate",ChannelRange.shift(display.getInteger("Gate"),delta));
            bank(display,"ReceiveChannels",delta);
        }
        for(String key:new String[]{"Sources","Cells","Origins","RampSourceTiles"}){
            NBTTagList rows=tag.getTagList(key,10);
            for(int i=0;i<rows.tagCount();i++){
                NBTTagCompound row=rows.getCompoundTagAt(i);
                if(row.hasKey("Tile",10)&&row.getCompoundTag("Tile").getString("id").startsWith("vandorlabs:"))tile(row.getCompoundTag("Tile"),delta,depth+1);
            }
        }
        NBTTagCompound payload=tag.getCompoundTag("ArmPayload"),stored=tag.getCompoundTag("ArmStoredSections");
        if(payload.hasKey("Blocks",10))structure(payload.getCompoundTag("Blocks"),delta,depth+1);
        if(payload.hasKey("PlacedBlocks",10))structure(payload.getCompoundTag("PlacedBlocks"),delta,depth+1);
        if(stored.hasKey("Blocks",10))structure(stored.getCompoundTag("Blocks"),delta,depth+1);
    }
    private static void structure(NBTTagCompound tag,int delta,int depth){
        if(depth>64)throw new IllegalArgumentException("Nested block channel data is too deep");
        NBTTagList palette=tag.getTagList("Palette",10),blocks=tag.getTagList("Blocks",10);
        for(int i=0;i<blocks.tagCount();i++){
            NBTTagCompound block=blocks.getCompoundTagAt(i);int state=block.getInteger("State");
            if(state<0||state>=palette.tagCount())throw new IllegalArgumentException("Invalid nested block palette");
            if(palette.getCompoundTagAt(state).getString("Name").startsWith("vandorlabs:")&&block.hasKey("Tile",10))tile(block.getCompoundTag("Tile"),delta,depth+1);
        }
    }
}
