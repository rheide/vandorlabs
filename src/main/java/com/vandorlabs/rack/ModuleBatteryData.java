package com.vandorlabs.rack;

import com.vandorlabs.items.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The physical item is the sole owner of charge, including while installed. */
public final class ModuleBatteryData {
    public static final int CAPACITY = 10000;
    private static final String KEY = "BatteryEnergy";
    private ModuleBatteryData() { }
    public static boolean isBattery(ItemStack stack) { return !stack.isEmpty() && stack.getItem() == ModItems.BATTERY_MODULE; }
    public static boolean valid(ItemStack stack) {
        return isBattery(stack) && (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(KEY)
                || stack.getTagCompound().hasKey(KEY,3) && stack.getTagCompound().getInteger(KEY)>=0 && stack.getTagCompound().getInteger(KEY)<=CAPACITY);
    }
    public static int energy(ItemStack stack) { return valid(stack) && stack.hasTagCompound() ? stack.getTagCompound().getInteger(KEY) : 0; }
    private static void set(ItemStack stack,int value) {
        NBTTagCompound data=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
        data.setInteger(KEY,value);stack.setTagCompound(data);
    }
    public static int receive(ItemStack stack,int maximum,boolean simulate) {
        if(!valid(stack))return 0;int amount=Math.min(Math.max(0,maximum),CAPACITY-energy(stack));
        if(!simulate&&amount>0)set(stack,energy(stack)+amount);return amount;
    }
    public static int extract(ItemStack stack,int maximum,boolean simulate) {
        if(!valid(stack))return 0;int amount=Math.min(Math.max(0,maximum),energy(stack));
        if(!simulate&&amount>0)set(stack,energy(stack)-amount);return amount;
    }
}
