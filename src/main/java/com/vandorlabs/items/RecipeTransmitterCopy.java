package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.RegistryEvent;

/** Center is the source, its horizontal neighbor is the target. Only channel configuration copies. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class RecipeTransmitterCopy extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    public RecipeTransmitterCopy(){setRegistryName(VandorLabs.MODID,"redstone_transmitter_copy");}
    private static int target(InventoryCrafting inventory){if(inventory.getWidth()!=3||inventory.getHeight()!=3||!ItemRedstoneTransmitter.valid(inventory.getStackInSlot(4)))return -1;int target=-1;for(int i=0;i<9;i++){if(i==4||inventory.getStackInSlot(i).isEmpty())continue;if((i!=3&&i!=5)||target>=0||!ItemRedstoneTransmitter.valid(inventory.getStackInSlot(i)))return -1;target=i;}return target;}
    public boolean matches(InventoryCrafting inventory,World world){return target(inventory)>=0;}
    public ItemStack getCraftingResult(InventoryCrafting inventory){int target=target(inventory);if(target<0)return ItemStack.EMPTY;ItemStack output=inventory.getStackInSlot(target).copy();output.setCount(1);ItemRedstoneTransmitter.configure(output,ItemRedstoneTransmitter.channels(inventory.getStackInSlot(4)));return output;}
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inventory){NonNullList<ItemStack> result=NonNullList.withSize(inventory.getSizeInventory(),ItemStack.EMPTY);if(target(inventory)>=0){ItemStack source=inventory.getStackInSlot(4).copy();source.setCount(1);result.set(4,source);}return result;}
    public ItemStack getRecipeOutput(){return ItemStack.EMPTY;}
    public boolean canFit(int width,int height){return width==3&&height==3;}
    public boolean isDynamic(){return true;}
    @SubscribeEvent public static void register(RegistryEvent.Register<IRecipe> event){event.getRegistry().register(new RecipeTransmitterCopy());}
}
