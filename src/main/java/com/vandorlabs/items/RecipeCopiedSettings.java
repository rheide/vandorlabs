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
import net.minecraftforge.event.world.BlockEvent;

/** Standard one-item crafting consumption allows shift-clicking an entire stack. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class RecipeCopiedSettings extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    public RecipeCopiedSettings() { setRegistryName(VandorLabs.MODID,"copied_settings"); }
    public boolean matches(InventoryCrafting inventory,World world) { return !getCraftingResult(inventory).isEmpty(); }
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        ItemStack tool=ItemStack.EMPTY, input=ItemStack.EMPTY;
        for (int i=0;i<inventory.getSizeInventory();i++) {
            ItemStack stack=inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem()==ModItems.DUPLIFIER && tool.isEmpty()) tool=stack;
            else if (input.isEmpty() && stack.getItem() instanceof net.minecraft.item.ItemBlock) input=stack;
            else return ItemStack.EMPTY;
        }
        if (tool.isEmpty() || input.isEmpty() || !ItemDuplifier.hasCopy(tool)) return ItemStack.EMPTY;
        return ProgrammableSettings.applyToItem(input, DuplifierApplyOptions.selected(
                tool.getSubCompound(ItemDuplifier.SETTINGS_TAG),DuplifierApplyOptions.mask(tool)));
    }
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inventory) {
        NonNullList<ItemStack> left=NonNullList.withSize(inventory.getSizeInventory(),ItemStack.EMPTY);
        for (int i=0;i<left.size();i++) if (inventory.getStackInSlot(i).getItem()==ModItems.DUPLIFIER)
            left.set(i,inventory.getStackInSlot(i).copy());
        return left;
    }
    public ItemStack getRecipeOutput() { return ItemStack.EMPTY; }
    public boolean canFit(int width,int height) { return width*height>=2; }
    public boolean isDynamic() { return true; }
    @SubscribeEvent public static void register(RegistryEvent.Register<IRecipe> event) { event.getRegistry().register(new RecipeCopiedSettings()); }
    @SubscribeEvent public static void placed(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote || event.isCanceled()) return;
        net.minecraft.nbt.NBTTagCompound settings=event.getItemInHand().getSubCompound("CopiedRampSettings");
        if (settings!=null) ProgrammableSettings.apply(event.getWorld(),event.getPos(),settings,event.getPlayer());
    }
}
