package com.vandorlabs.rack;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityAutopilot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Navigation stays in one real module item; opaque item fields and legacy block data are not configuration. */
public final class ModuleAutopilotData {
    public static final String LEGACY = "LegacyAutopilotData";
    private ModuleAutopilotData() { }
    public static NBTTagCompound state(ItemStack module) {
        if (module.hasTagCompound() && module.getTagCompound().hasKey(AutopilotModuleSettings.KEY,10))
            return module.getTagCompound().getCompoundTag(AutopilotModuleSettings.KEY).copy();
        return new TileEntityAutopilot().writeAutopilotData(new NBTTagCompound());
    }
    public static void state(ItemStack module,NBTTagCompound state) {
        NBTTagCompound data = module.hasTagCompound() ? module.getTagCompound().copy() : new NBTTagCompound();
        data.setTag(AutopilotModuleSettings.KEY,state.copy()); module.setTagCompound(data);
    }
    public static AutopilotModuleSettings settings(ItemStack module) {
        NBTTagCompound data=state(module);
        return new AutopilotModuleSettings(ChannelData.read(data,0),data.hasKey("AutopilotTrigger")?data.getInteger("AutopilotTrigger"):1,
                data.hasKey("RouteMarker")?data.getString("RouteMarker"):"minecraft:concrete",data.getInteger("RouteMarkerMeta"));
    }
    public static void configure(ItemStack module,AutopilotModuleSettings settings) {
        NBTTagCompound data=state(module);ChannelData.write(data,settings.channels);
        data.setInteger("AutopilotTrigger",settings.activation);data.setString("RouteMarker",settings.marker);
        data.setInteger("RouteMarkerMeta",settings.markerMetadata);data.setBoolean("AutopilotEnabled",false);
        data.setString("AutopilotStatus","Off");state(module,data);
    }
    /** Manual relocation keeps choices and opaque item data; old departure coordinates cannot authorize a new location. */
    public static void placed(ItemStack module) {
        NBTTagCompound data=state(module);data.setBoolean("AutopilotEnabled",false);data.setBoolean("AutopilotHigh",false);
        data.setString("AutopilotStatus","Off");data.setLong("AutopilotDepartureAt",0);
        for(String key:new String[]{"AutopilotDepartureSeat","AutopilotDepartureOwnerMost","AutopilotDepartureOwnerLeast","AutopilotDepartureWaypoint"})data.removeTag(key);
        state(module,data);
    }
    /** A migration backup is opaque recovery data, never an installed second capability provider. */
    public static void legacy(ItemStack module,NBTTagCompound original) {
        NBTTagCompound data=module.hasTagCompound()?module.getTagCompound().copy():new NBTTagCompound();
        data.setTag(LEGACY,LegacyAutopilotData.moduleBackup(original));module.setTagCompound(data);
        TileEntityAutopilot parsed=new TileEntityAutopilot();parsed.readAutopilotData(TransmitterSlots.normalize(original));
        state(module,parsed.writeAutopilotData(new NBTTagCompound()));
    }
    public static boolean powered(NBTTagCompound data,boolean high) {
        int trigger=data.hasKey("AutopilotTrigger")?data.getInteger("AutopilotTrigger"):1;
        return AutopilotModuleSettings.powered(trigger,high);
    }
    /** Secondary modules keep their receiving state even while multiple navigation controllers prevent assembly. */
    public static boolean signal(ItemStack module,boolean high) {
        NBTTagCompound data=state(module);boolean before=powered(data,data.getBoolean("AutopilotHigh"));
        if(data.getBoolean("AutopilotHigh")==high)return false;
        data.setBoolean("AutopilotHigh",high);
        if(!before&&powered(data,high)){data.setBoolean("AutopilotEnabled",true);data.setString("AutopilotStatus","Following route");}
        state(module,data);return true;
    }
}
