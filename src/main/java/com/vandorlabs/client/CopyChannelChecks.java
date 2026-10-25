package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.copier.CopyChannels;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Persisted scalar/list/row/nested channel schemas, without mutating inventory or foreign data. */
final class CopyChannelChecks {
    private static int assertions;
    private static void require(boolean value,String message){assertions++;if(!value)throw new IllegalStateException("Copy channels: "+message);}
    private static VehicleStructure snapshot(NBTTagCompound data){return VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),data)));}
    private static NBTTagCompound channels(int value){NBTTagCompound tag=new NBTTagCompound();tag.setString("id","vandorlabs:animated_screen_selector");tag.setInteger("RedstoneChannel",value);tag.setIntArray("RedstoneChannels",new int[]{value,value+10});return tag;}
    static void run(){
        assertions=0;NBTTagCompound source=channels(20);
        source.setInteger("SpaceDoorChannel",20);source.setInteger("Channel",20);source.setIntArray("CanopyChannels",new int[]{20,0,30});source.setIntArray("LatchedChannels",new int[]{20});source.setIntArray("LatchedLevels",new int[]{5,15});source.setInteger("ChannelLevel",12);
        for(String key:new String[]{"GearChannels","ScanChannels","RotationChannels","DirectionChannels","RestoreChannels","CopyChannels"})source.setString(key,"20, 30");
        for(int i=0;i<3;i++)source.setTag("VehicleModeChannels"+i,channels(40+i));
        NBTTagCompound primary=new NBTTagCompound();NBTTagList rows=new NBTTagList();NBTTagCompound row=channels(50);row.setIntArray("LatchedChannels",new int[]{50});row.setIntArray("Levels",new int[]{6,14});row.setLong("PulseUntil",12345);rows.appendTag(row);primary.setTag("Rows",rows);source.setTag("RedstonePrimary",primary.copy());source.setTag("RedstoneSecondary",primary.copy());
        NBTTagList controls=new NBTTagList();controls.appendTag(channels(60));source.setTag("CockpitControls",controls);
        NBTTagList displays=new NBTTagList();NBTTagCompound display=new NBTTagCompound();display.setInteger("Gate",70);display.setInteger("Texture",20);displays.appendTag(display);source.setTag("CockpitDisplays",displays);
        source.setTag("DoorKeyReader",channels(80));source.setTag("DoorRearKeyReader",channels(90));
        NBTTagList saved=new NBTTagList();NBTTagCompound entry=new NBTTagCompound();entry.setTag("Tile",channels(100));saved.appendTag(entry);source.setTag("RampSourceTiles",saved);
        NBTTagCompound foreign=new NBTTagCompound();foreign.setString("id","minecraft:furnace");foreign.setInteger("Channel",200);
        VehicleStructure nested=VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),channels(110)),new VehicleStructure.Cell(new BlockPos(1,0,0),Blocks.FURNACE.getDefaultState(),foreign)));
        NBTTagCompound payload=new NBTTagCompound();payload.setTag("Blocks",nested.write());payload.setLong("Offset",123);source.setTag("ArmPayload",payload);
        NBTTagList inventory=new NBTTagList();NBTTagCompound item=new NBTTagCompound();item.setString("id","vandorlabs:programmable_block");item.setByte("Count",(byte)1);NBTTagCompound itemTag=new NBTTagCompound();itemTag.setTag("BlockEntityTag",channels(500));item.setTag("tag",itemTag);inventory.appendTag(item);source.setTag("Items",inventory);source.setTag("ForgeCaps",channels(600));
        NBTTagCompound original=source.copy();VehicleStructure before=snapshot(source);NBTTagCompound result=CopyChannels.increment(before,5).at(BlockPos.ORIGIN).tileData();
        require(source.equals(original)&&before.at(BlockPos.ORIGIN).tileData().equals(original),"source remains immutable");
        for(String key:new String[]{"RedstoneChannel","SpaceDoorChannel","Channel"})require(result.getInteger(key)==25,"scalar "+key);
        require(Arrays.equals(result.getIntArray("CanopyChannels"),new int[]{25,0,35}),"canopy channels plus disabled zero");require(Arrays.equals(result.getIntArray("LatchedChannels"),new int[]{25}),"saved latch IDs shifted");require(Arrays.equals(result.getIntArray("LatchedLevels"),new int[]{5,15})&&result.getInteger("ChannelLevel")==12,"levels unchanged");
        for(String key:new String[]{"GearChannels","ScanChannels","RotationChannels","DirectionChannels","RestoreChannels","CopyChannels"})require(result.getString(key).equals("25, 35"),"string list "+key);
        for(int i=0;i<3;i++)require(result.getCompoundTag("VehicleModeChannels"+i).getInteger("RedstoneChannel")==45+i,"vehicle mode bank "+i);
        for(String key:new String[]{"RedstonePrimary","RedstoneSecondary"}){NBTTagCompound r=result.getCompoundTag(key).getTagList("Rows",10).getCompoundTagAt(0);require(Arrays.equals(r.getIntArray("RedstoneChannels"),new int[]{55,65}),"screen row IDs "+key);require(Arrays.equals(r.getIntArray("Levels"),new int[]{6,14})&&r.getLong("PulseUntil")==12345,"screen levels/timeline unchanged");}
        require(result.getTagList("CockpitControls",10).getCompoundTagAt(0).getInteger("RedstoneChannel")==65,"cockpit output");require(result.getTagList("CockpitDisplays",10).getCompoundTagAt(0).getInteger("Gate")==75&&result.getTagList("CockpitDisplays",10).getCompoundTagAt(0).getInteger("Texture")==20,"cockpit gate only");
        require(result.getCompoundTag("DoorKeyReader").getInteger("RedstoneChannel")==85&&result.getCompoundTag("DoorRearKeyReader").getInteger("RedstoneChannel")==95,"door readers");require(result.getTagList("RampSourceTiles",10).getCompoundTagAt(0).getCompoundTag("Tile").getInteger("RedstoneChannel")==105,"retained ramp material");
        VehicleStructure shiftedNested=VehicleStructure.readDisplay(result.getCompoundTag("ArmPayload").getCompoundTag("Blocks"));require(shiftedNested.at(BlockPos.ORIGIN).tileData().getInteger("RedstoneChannel")==115,"nested actual Vandor block");require(shiftedNested.at(new BlockPos(1,0,0)).tileData().equals(foreign),"nested foreign block unchanged");
        require(result.getTag("Items").equals(original.getTag("Items"))&&result.getTag("ForgeCaps").equals(original.getTag("ForgeCaps")),"stored items and opaque capabilities untouched");
        require(CopyChannels.increment(before,0).write().equals(before.write()),"disabled increment exact");require(CopyChannels.increment(before,-5).at(BlockPos.ORIGIN).tileData().getInteger("RedstoneChannel")==15,"negative increment");
        for(int[] pair:new int[][]{{65535,1},{1,-1},{1,Integer.MAX_VALUE}}){NBTTagCompound tag=channels(pair[0]);tag.setIntArray("RedstoneChannels",new int[]{pair[0]});VehicleStructure model=snapshot(tag);boolean denied=false;try{CopyChannels.increment(model,pair[1]);}catch(IllegalArgumentException expected){denied=true;}require(denied&&model.at(BlockPos.ORIGIN).tileData().equals(tag),"invalid increment rejects atomically");}
        NBTTagCompound zero=new NBTTagCompound();zero.setInteger("RedstoneChannel",0);require(CopyChannels.increment(snapshot(zero),-65534).at(BlockPos.ORIGIN).tileData().getInteger("RedstoneChannel")==0,"zero stays unlinked");
        VehicleStructure outerForeign=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.FURNACE.getDefaultState(),original)));require(CopyChannels.increment(outerForeign,5).at(BlockPos.ORIGIN).tileData().equals(original),"foreign outer block untouched even with mod-like NBT");
        System.out.println("[vandorlabs][reprolab] copy-channel-increment PASS assertions="+assertions+" signed scalars banks rows latches cockpit gates nested payloads source immutability foreign/inventory preservation bounded IDs");
    }
}
