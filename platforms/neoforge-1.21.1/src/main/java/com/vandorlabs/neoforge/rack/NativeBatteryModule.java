package com.vandorlabs.neoforge.rack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.List;

/** Charge belongs to the physical module's custom data, never a second capability buffer. */
public final class NativeBatteryModule extends Item {
    public static final int CAPACITY=10000;
    public NativeBatteryModule(){super(new Properties().stacksTo(1));}
    public static boolean valid(ItemStack stack){var data=NativeRackData.data(stack);return !stack.isEmpty()&&stack.getItem() instanceof NativeBatteryModule&&(!data.contains("BatteryEnergy")||data.contains("BatteryEnergy",Tag.TAG_INT)&&data.getInt("BatteryEnergy")>=0&&data.getInt("BatteryEnergy")<=CAPACITY);}
    public static int energy(ItemStack stack){return valid(stack)?NativeRackData.data(stack).getInt("BatteryEnergy"):0;}
    public static int transfer(ItemStack stack,int maximum,boolean simulate,boolean receive){
        if(!valid(stack))return 0;int before=energy(stack),amount=Math.min(Math.max(0,maximum),receive?CAPACITY-before:before);
        if(!simulate&&amount>0){var data=NativeRackData.data(stack);data.putInt("BatteryEnergy",before+(receive?amount:-amount));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));}return amount;
    }
    public static IEnergyStorage capability(ItemStack stack){return new IEnergyStorage(){
        public int receiveEnergy(int amount,boolean simulate){return transfer(stack,amount,simulate,true);}
        public int extractEnergy(int amount,boolean simulate){return transfer(stack,amount,simulate,false);}
        public int getEnergyStored(){return energy(stack);}public int getMaxEnergyStored(){return CAPACITY;}
        public boolean canReceive(){return valid(stack);}public boolean canExtract(){return valid(stack);}
    };}
    @Override public boolean isBarVisible(ItemStack stack){return true;}
    @Override public int getBarWidth(ItemStack stack){return Math.round(13F*energy(stack)/CAPACITY);}
    @Override public int getBarColor(ItemStack stack){return 0xff4033;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lines.add(Component.literal(energy(stack)+" / "+CAPACITY+" RF"));}
}
