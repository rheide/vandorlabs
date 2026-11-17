package com.vandorlabs.vehicle;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.nbt.NBTTagCompound;
/** Saved only by the opt-in disposable navigation fixture. */
public final class NavigationVehicleTestData extends WorldSavedData {
    public static final String NAME="vandorlabs_navigation_vehicle_fixture";
    public NBTTagCompound data=new NBTTagCompound();
    public NavigationVehicleTestData(){super(NAME);}public NavigationVehicleTestData(String name){super(name);}
    public void readFromNBT(NBTTagCompound tag){data=tag.copy();}public NBTTagCompound writeToNBT(NBTTagCompound tag){tag.merge(data.copy());return tag;}
}
