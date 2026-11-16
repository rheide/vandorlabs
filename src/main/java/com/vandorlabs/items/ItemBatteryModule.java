package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.rack.ModuleBatteryData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.energy.*;

public final class ItemBatteryModule extends Item {
    public ItemBatteryModule(){setRegistryName(VandorLabs.MODID,"battery_module");setUnlocalizedName("vandorlabs.battery_module");setCreativeTab(VandorLabs.VANDOR_LABS_TAB);setMaxStackSize(1);}
    @Override public ICapabilityProvider initCapabilities(final ItemStack stack,NBTTagCompound nbt){
        return new ICapabilityProvider(){
            private final IEnergyStorage energy=new IEnergyStorage(){
                public int receiveEnergy(int amount,boolean simulate){return ModuleBatteryData.receive(stack,amount,simulate);}
                public int extractEnergy(int amount,boolean simulate){return ModuleBatteryData.extract(stack,amount,simulate);}
                public int getEnergyStored(){return ModuleBatteryData.energy(stack);}
                public int getMaxEnergyStored(){return ModuleBatteryData.CAPACITY;}
                public boolean canReceive(){return ModuleBatteryData.valid(stack);}
                public boolean canExtract(){return ModuleBatteryData.valid(stack);}
            };
            public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY;}
            public <T>T getCapability(Capability<T> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(energy):null;}
        };
    }
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override public void addInformation(ItemStack stack,net.minecraft.world.World world,java.util.List<String> lines,net.minecraft.client.util.ITooltipFlag flag){lines.add(ModuleBatteryData.energy(stack)+" / "+ModuleBatteryData.CAPACITY+" RF");}
    @Override public boolean showDurabilityBar(ItemStack stack){return true;}
    @Override public double getDurabilityForDisplay(ItemStack stack){return 1-ModuleBatteryData.energy(stack)/(double)ModuleBatteryData.CAPACITY;}
    @Override public int getRGBDurabilityForDisplay(ItemStack stack){return 0x43cc55;}
}
