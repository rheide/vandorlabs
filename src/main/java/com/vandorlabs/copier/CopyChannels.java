package com.vandorlabs.copier;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.nbt.*;
import java.util.*;

/** Copy only documented channel fields; inventories, artwork, levels and foreign NBT stay exact. */
public final class CopyChannels {
    private static final String[] SCALARS={"RedstoneChannel","SpaceDoorChannel","Channel"};
    private static final String[] ARRAYS={"RedstoneChannels","CanopyChannels","LatchedChannels"};
    private static final String[] STRINGS={"GearChannels","ScanChannels","RotationChannels","DirectionChannels","RestoreChannels","CopyChannels"};
    private CopyChannels(){}
    public static VehicleStructure increment(VehicleStructure source,int delta){
        if(delta < -65534 || delta > 65534)throw new IllegalArgumentException("Channel increment must be between -65534 and 65534");
        if(delta==0)return source;
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:source.cells){
            NBTTagCompound tag=cell.tileData();
            if(tag!=null&&cell.state.getBlock().getRegistryName().getResourceDomain().equals("vandorlabs"))tile(tag,delta,0);
            cells.add(new VehicleStructure.Cell(cell.pos,cell.state,tag));
        }
        return source.displayOnly?VehicleStructure.display(cells):new VehicleStructure(cells,source.seat,source.facing,source.gearCount);
    }
    private static int channel(int value,int delta){
        if(value==0)return 0;
        long next=(long)value+delta;
        if(value<1||value>65535||next<1||next>65535)throw new IllegalArgumentException("Channel "+value+" cannot be shifted by "+delta+"; enabled channels must remain within 1..65535");
        return (int)next;
    }
    private static void fields(NBTTagCompound tag,int delta){
        for(String key:SCALARS)if(tag.hasKey(key,3))tag.setInteger(key,channel(tag.getInteger(key),delta));
        for(String key:ARRAYS)if(tag.hasKey(key,11)){
            int[] values=tag.getIntArray(key);for(int i=0;i<values.length;i++)values[i]=channel(values[i],delta);tag.setIntArray(key,values);
        }
        for(String key:STRINGS)if(tag.hasKey(key,8)){
            ChannelList values=ChannelList.parse(tag.getString(key));if(values==null)throw new IllegalArgumentException("Cannot shift malformed "+key);
            int[] shifted=values.toArray();for(int i=0;i<shifted.length;i++)shifted[i]=channel(shifted[i],delta);tag.setString(key,ChannelList.of(shifted).toString());
        }
    }
    private static void tile(NBTTagCompound tag,int delta,int depth){
        if(depth>64)throw new IllegalArgumentException("Nested block channel data is too deep");
        fields(tag,delta);
        for(String key:new String[]{"VehicleModeChannels0","VehicleModeChannels1","VehicleModeChannels2","DoorKeyReader","DoorRearKeyReader"})
            if(tag.hasKey(key,10))tile(tag.getCompoundTag(key),delta,depth+1);
        for(String key:new String[]{"RedstonePrimary","RedstoneSecondary"})if(tag.hasKey(key,10)){
            NBTTagList rows=tag.getCompoundTag(key).getTagList("Rows",10);
            for(int i=0;i<rows.tagCount();i++)fields(rows.getCompoundTagAt(i),delta);
        }
        NBTTagList controls=tag.getTagList("CockpitControls",10);
        for(int i=0;i<controls.tagCount();i++)fields(controls.getCompoundTagAt(i),delta);
        NBTTagList displays=tag.getTagList("CockpitDisplays",10);
        for(int i=0;i<displays.tagCount();i++){
            NBTTagCompound display=displays.getCompoundTagAt(i);if(display.hasKey("Gate",3))display.setInteger("Gate",channel(display.getInteger("Gate"),delta));
        }
        for(String key:new String[]{"Sources","Cells","Origins","RampSourceTiles"}){
            NBTTagList rows=tag.getTagList(key,10);
            for(int i=0;i<rows.tagCount();i++){
                NBTTagCompound row=rows.getCompoundTagAt(i);
                if(row.hasKey("Tile",10)&&row.getCompoundTag("Tile").getString("id").startsWith("vandorlabs:"))tile(row.getCompoundTag("Tile"),delta,depth+1);
            }
        }
        if(tag.hasKey("ArmPayload",10)&&tag.getCompoundTag("ArmPayload").hasKey("Blocks",10))structure(tag.getCompoundTag("ArmPayload").getCompoundTag("Blocks"),delta,depth+1);
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
