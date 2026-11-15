package com.vandorlabs.neoforge.rack;

import com.vandorlabs.rack.*;
import com.vandorlabs.redstone.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Engine tag adapter only; the bounded slot/layout/configuration policy is portable. */
public final class NativeRackData {
    public static final String LEGACY_ITEM="RackLegacyItem";
    public record Transmission(ChannelList channels,int range,boolean local,boolean present) {
        public Transmission {if(channels==null||!TransmissionSettings.validRange(range)||!present&&(!channels.isEmpty()||range!=16||!local))throw new IllegalArgumentException("Rack transmission");}
        public static Transmission fresh(){return new Transmission(ChannelList.EMPTY,16,true,false);}
    }
    private NativeRackData() { }
    public static int kind(ItemStack stack){return stack.isEmpty()?0:ModuleRackPolicy.kind(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}
    public static CompoundTag data(ItemStack module){return module.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void data(ItemStack module,CompoundTag tag){module.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public static CompoundTag state(ItemStack module){return data(module).getCompound(AutopilotModuleSettings.KEY).copy();}
    public static void state(ItemStack module,CompoundTag state){var tag=data(module);tag.put(AutopilotModuleSettings.KEY,state.copy());data(module,tag);}
    public static AutopilotModuleSettings settings(ItemStack module){return settings(state(module));}
    public static AutopilotModuleSettings settings(CompoundTag tag){
        for(String key:List.of("AutopilotTrigger","RouteMarkerMeta"))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Module setting type");
        if(tag.contains("RedstoneChannels")&&!tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)||tag.contains("RouteMarker")&&!tag.contains("RouteMarker",Tag.TAG_STRING))throw new IllegalArgumentException("Module setting type");
        return new AutopilotModuleSettings(tag.contains("RedstoneChannels")?ChannelList.of(tag.getIntArray("RedstoneChannels")):ChannelList.EMPTY,
                tag.contains("AutopilotTrigger")?tag.getInt("AutopilotTrigger"):1,
                tag.contains("RouteMarker")?tag.getString("RouteMarker"):"minecraft:concrete",tag.getInt("RouteMarkerMeta"));
    }
    /** Public carried appearance only; original items and arbitrary saved fields never enter the arm view. */
    public static CompoundTag appearance(CompoundTag raw){
        if(raw.contains("ModuleMask")&&!raw.contains("ModuleMask",Tag.TAG_INT)||raw.contains("ModuleKinds")&&!raw.contains("ModuleKinds",Tag.TAG_INT)||raw.contains("RackHousing")&&!raw.contains("RackHousing",Tag.TAG_INT))throw new IllegalArgumentException("Rack appearance field type");
        int mask=raw.getInt("ModuleMask"),kinds=raw.contains("ModuleKinds")?raw.getInt("ModuleKinds"):ModuleRackPolicy.blankKinds(mask),housing=raw.contains("RackHousing")?raw.getInt("RackHousing"):-1;
        if((mask&~15)!=0||!ModuleRackPolicy.validKinds(kinds)||ModuleRackPolicy.occupied(kinds)!=mask||housing< -1||housing>=0&&!com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(housing))throw new IllegalArgumentException("Rack appearance bounds");
        var result=new CompoundTag();result.putBoolean("RackPublic",true);result.putInt("ModuleMask",mask);if(ModuleRackPolicy.autopilots(kinds)>0)result.putInt("ModuleKinds",kinds);result.putInt("RackHousing",housing);return result;
    }
    public static ItemStack marker(AutopilotModuleSettings settings,HolderLookup.Provider registries){
        var id=ResourceLocation.tryParse(settings.marker);if(id==null)return ItemStack.EMPTY;
        if(settings.markerMetadata==0&&BuiltInRegistries.BLOCK.containsKey(id))return new ItemStack(BuiltInRegistries.BLOCK.get(id));
        if(!com.vandorlabs.neoforge.inventory.NativeInventoryItems.sourceVanillaItems().contains(settings.marker))return ItemStack.EMPTY;
        var legacy=new CompoundTag();legacy.putString("id",settings.marker);legacy.putByte("Count",(byte)1);legacy.putShort("Damage",(short)settings.markerMetadata);
        try{var result=com.vandorlabs.neoforge.inventory.NativeInventoryItems.legacy(legacy,registries);return result.getItem() instanceof net.minecraft.world.item.BlockItem?result:ItemStack.EMPTY;}catch(RuntimeException unsupported){return ItemStack.EMPTY;}
    }
    public static CompoundTag publicSettings(AutopilotModuleSettings settings){var tag=new CompoundTag();tag.putIntArray("RedstoneChannels",settings.channels.toArray());tag.putInt("AutopilotTrigger",settings.activation);tag.putString("RouteMarker",settings.marker);tag.putInt("RouteMarkerMeta",settings.markerMetadata);return tag;}
    public static void configure(ItemStack module,AutopilotModuleSettings settings){var state=state(module);state.merge(publicSettings(settings));state.putBoolean("AutopilotEnabled",false);state.putString("AutopilotStatus","Off");state(module,state);}
    public static void placed(ItemStack module){
        var state=state(module);state.putBoolean("AutopilotEnabled",false);state.putBoolean("AutopilotHigh",false);state.putString("AutopilotStatus","Off");state.putLong("AutopilotDepartureAt",0);
        for(String key:List.of("AutopilotDepartureSeat","AutopilotDepartureOwnerMost","AutopilotDepartureOwnerLeast","AutopilotDepartureWaypoint"))state.remove(key);state(module,state);
    }
    public static boolean signal(ItemStack module,boolean high){
        var tag=state(module);var settings=settings(tag);boolean previous=tag.getBoolean("AutopilotHigh");if(previous==high)return false;
        boolean before=AutopilotModuleSettings.powered(settings.activation,previous),after=AutopilotModuleSettings.powered(settings.activation,high);
        tag.putBoolean("AutopilotHigh",high);
        if(!before&&after){tag.putBoolean("AutopilotEnabled",true);tag.putString("AutopilotStatus","Vehicle navigation is not available on this version");}
        state(module,tag);return true;
    }
    public static Transmission transmission(CompoundTag root){
        if(root.contains("ForgeData")&&!root.contains("ForgeData",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Rack metadata");
        var forge=root.getCompound("ForgeData");if(forge.contains(TransmissionSettings.SETTINGS)&&!forge.contains(TransmissionSettings.SETTINGS,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Rack transmission type");var settings=forge.getCompound(TransmissionSettings.SETTINGS);
        if(!settings.contains("main"))return Transmission.fresh();
        if(!settings.contains("main",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Rack transmission");
        var main=settings.getCompound("main");
        if(!main.contains("Channels",Tag.TAG_INT_ARRAY)||!main.contains("Range",Tag.TAG_INT)||!main.contains("Local",Tag.TAG_BYTE)||main.getByte("Local")<0||main.getByte("Local")>1)throw new IllegalArgumentException("Rack transmission types");
        return new Transmission(ChannelList.of(main.getIntArray("Channels")),main.getInt("Range"),main.getBoolean("Local"),true);
    }
    public static void transmission(CompoundTag root,Transmission value){
        CompoundTag forge=root.getCompound("ForgeData"),all=forge.getCompound(TransmissionSettings.SETTINGS),main=new CompoundTag();
        if(!value.present){all.remove("main");if(all.isEmpty())forge.remove(TransmissionSettings.SETTINGS);else forge.put(TransmissionSettings.SETTINGS,all);if(forge.isEmpty())root.remove("ForgeData");else root.put("ForgeData",forge);return;}
        main.putIntArray("Channels",value.channels.toArray());main.putInt("Range",value.range);main.putBoolean("Local",value.local);all.put("main",main);forge.put(TransmissionSettings.SETTINGS,all);root.put("ForgeData",forge);
    }
    public static ItemStack readItem(CompoundTag original,HolderLookup.Provider registries){
        var raw=original.copy();raw.remove("Slot");ItemStack result;
        if(raw.contains("Count")){
            if(raw.contains("ForgeCaps")&&(!raw.contains("ForgeCaps",Tag.TAG_COMPOUND)||!raw.getCompound("ForgeCaps").isEmpty()))throw new IllegalArgumentException("Unsupported Source module capabilities");
            // Like the credential adapter, retain the complete old stack in the one physical item.
            String id=raw.getString("id");if(ModuleRackPolicy.kind(id)==0||raw.getByte("Count")!=1||raw.getShort("Damage")!=0)throw new IllegalArgumentException("Unsupported Source Rack item");
            var key=ResourceLocation.tryParse(id);if(key==null||!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Missing Rack module");
            result=new ItemStack(BuiltInRegistries.ITEM.get(key));var custom=raw.getCompound("tag").copy();custom.put(LEGACY_ITEM,raw.copy());result.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
            var display=raw.getCompound("tag").getCompound("display");if(display.contains("Name",Tag.TAG_STRING))result.set(DataComponents.CUSTOM_NAME,Component.literal(display.getString("Name")));
        }else result=com.vandorlabs.neoforge.inventory.NativeInventoryItems.modern(raw,registries);
        if(kind(result)==0||result.getCount()!=1||com.vandorlabs.neoforge.inventory.NativeInventoryItems.containedOwnership(result))throw new IllegalArgumentException("Unsupported Rack module ownership");
        if(kind(result)==2)settings(result);return result;
    }
    public static CompoundTag writeItem(ItemStack stack,int slot,HolderLookup.Provider registries){var result=com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(stack,registries);result.putByte("Slot",(byte)slot);return result;}
}
