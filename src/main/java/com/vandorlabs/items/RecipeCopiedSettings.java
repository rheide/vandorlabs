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
        CopiedSettingsRecipeSlots.Selection slots=CopiedSettingsRecipeSlots.find(inventory.getSizeInventory(),i->{
            ItemStack stack=inventory.getStackInSlot(i);
            if(stack.isEmpty())return CopiedSettingsRecipeSlots.EMPTY;
            if(stack.getItem()==ModItems.DUPLIFIER)return CopiedSettingsRecipeSlots.TOOL;
            return stack.getItem() instanceof net.minecraft.item.ItemBlock || stack.getItem() instanceof ItemProgrammableArmor
                    ?CopiedSettingsRecipeSlots.INPUT:CopiedSettingsRecipeSlots.OTHER;
        });
        if(slots==null)return ItemStack.EMPTY;
        ItemStack tool=inventory.getStackInSlot(slots.tool),input=inventory.getStackInSlot(slots.input);
        if (!ItemDuplifier.hasCopy(tool)) return ItemStack.EMPTY;
        if(input.getItem() instanceof net.minecraft.item.ItemBlock
                && !com.vandorlabs.config.BlockAvailability.enabled(((net.minecraft.item.ItemBlock)input.getItem()).getBlock()))return ItemStack.EMPTY;
        net.minecraft.nbt.NBTTagCompound settings=DuplifierApplyOptions.selected(
                tool.getSubCompound(ItemDuplifier.SETTINGS_TAG),DuplifierApplyOptions.maskWords(tool));
        if(input.getItem() instanceof ItemProgrammableArmor) {
            ItemStack output=input.copy();
            return DuplifierArmorTextures.apply(output,settings)?output:ItemStack.EMPTY;
        }
        return ProgrammableSettings.applyToItem(input,settings);
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
