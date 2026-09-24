package com.vandorlabs.client;

import com.vandorlabs.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

/** Exercises the loaded recipe and baked model, not a duplicate recipe parser. */
final class ItemRuntimeChecks {
    static void run(EntityPlayer player) {
        ResourceLocation id = new ResourceLocation("vandorlabs", "programmable_matter_ingot");
        require(Item.REGISTRY.getObject(id) == ModItems.PROGRAMMABLE_MATTER_INGOT,
                "ingot registration");
        require(!Block.REGISTRY.containsKey(id), "ingot must not be a block");
        IRecipe recipe = CraftingManager.REGISTRY.getObject(id);
        require(recipe != null, "ingot recipe loaded");
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return true; }
        }, 3, 3);
        Item[] ingredients = {Items.CLAY_BALL, Items.IRON_INGOT, Items.CLAY_BALL,
                Items.GOLD_INGOT, Items.REDSTONE, Items.GOLD_INGOT,
                Items.CLAY_BALL, Items.IRON_INGOT, Items.CLAY_BALL};
        for (int i = 0; i < ingredients.length; i++)
            grid.setInventorySlotContents(i, new ItemStack(ingredients[i]));
        require(recipe.matches(grid, player.world), "specified shaped recipe matches");
        ItemStack result = CraftingManager.findMatchingResult(grid, player.world);
        require(result.getItem() == ModItems.PROGRAMMABLE_MATTER_INGOT && result.getCount() == 1,
                "crafting yields one ingot");
        for (int i = 0; i < ingredients.length; i++) {
            grid.setInventorySlotContents(i, ItemStack.EMPTY);
            require(!recipe.matches(grid, player.world), "missing ingredient accepted at " + i);
            grid.setInventorySlotContents(i, new ItemStack(ingredients[i]));
        }
        grid.setInventorySlotContents(1, new ItemStack(Items.GOLD_INGOT));
        grid.setInventorySlotContents(3, new ItemStack(Items.IRON_INGOT));
        require(!recipe.matches(grid, player.world), "wrong ingredient positions accepted");
        Minecraft mc = Minecraft.getMinecraft();
        require(mc.getRenderItem().getItemModelMesher().getItemModel(result)
                != mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),
                "ingot model missing");
        require("Programmable Matter Ingot".equals(result.getDisplayName()), "ingot display name");
        IRecipe doorRecipe = CraftingManager.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_door"));
        require(doorRecipe != null, "door recipe loaded");
        for (int i = 0; i < grid.getSizeInventory(); i++)
            grid.setInventorySlotContents(i, ItemStack.EMPTY);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 2; col++)
                grid.setInventorySlotContents(row * 3 + col,
                        new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        require(doorRecipe.matches(grid, player.world), "six ingots match door shape");
        ItemStack door = CraftingManager.findMatchingResult(grid, player.world);
        require(door.getItem() == Item.getItemFromBlock(Block.REGISTRY.getObject(
                        new ResourceLocation("vandorlabs", "space_door")))
                        && door.getCount() == 1,
                "six ingots craft one Programmable Door");
        grid.setInventorySlotContents(0, ItemStack.EMPTY);
        require(!doorRecipe.matches(grid, player.world), "five ingots crafted a door");
        grid.setInventorySlotContents(0, new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        grid.setInventorySlotContents(2, new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        require(!doorRecipe.matches(grid, player.world), "extra ingredient crafted a door");
        System.out.println("[vandorlabs][reprolab] item-runtime PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("item check: " + message);
    }
}
