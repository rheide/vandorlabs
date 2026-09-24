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
                        new ResourceLocation("vandorlabs", "programmable_door")))
                        && door.getCount() == 1,
                "six ingots craft one Programmable Door");
        grid.setInventorySlotContents(0, ItemStack.EMPTY);
        require(!doorRecipe.matches(grid, player.world), "five ingots crafted a door");
        grid.setInventorySlotContents(0, new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        grid.setInventorySlotContents(2, new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        require(!doorRecipe.matches(grid, player.world), "extra ingredient crafted a door");
        IRecipe rampRecipe=CraftingManager.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_ramp"));
        require(rampRecipe!=null,"Programmable Ramp recipe loaded");
        for (int i=0;i<9;i++) grid.setInventorySlotContents(i,
                new ItemStack(i==4?Item.getItemFromBlock(net.minecraft.init.Blocks.PISTON)
                        :ModItems.PROGRAMMABLE_MATTER_INGOT));
        require(rampRecipe.matches(grid,player.world),"piston surrounded by eight ingots matches");
        ItemStack ramp=CraftingManager.findMatchingResult(grid,player.world);
        require(ramp.getItem()==Item.getItemFromBlock(Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_ramp"))) && ramp.getCount()==1,
                "recipe crafts one Programmable Ramp");
        grid.setInventorySlotContents(4,ItemStack.EMPTY);
        require(!rampRecipe.matches(grid,player.world),"missing piston does not craft ramp");
        grid.setInventorySlotContents(4,new ItemStack(Item.getItemFromBlock(net.minecraft.init.Blocks.PISTON)));
        grid.setInventorySlotContents(0,ItemStack.EMPTY);
        require(!rampRecipe.matches(grid,player.world),"missing ingot does not craft ramp");
        IRecipe glassRecipe=CraftingManager.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_glass"));
        require(glassRecipe!=null,"Programmable Glass recipe loaded");
        for (int i=0;i<9;i++) grid.setInventorySlotContents(i,
                new ItemStack(i==4?ModItems.PROGRAMMABLE_MATTER_INGOT
                        :Item.getItemFromBlock(net.minecraft.init.Blocks.GLASS)));
        require(glassRecipe.matches(grid,player.world),"ingot surrounded by eight glass blocks matches");
        ItemStack programmableGlass=CraftingManager.findMatchingResult(grid,player.world);
        require(programmableGlass.getItem()==Item.getItemFromBlock(Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_glass")))
                && programmableGlass.getCount()==1,"recipe crafts one Programmable Glass");
        grid.setInventorySlotContents(4,ItemStack.EMPTY);
        require(!glassRecipe.matches(grid,player.world),"missing ingot does not craft glass");
        grid.setInventorySlotContents(4,new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        grid.setInventorySlotContents(0,ItemStack.EMPTY);
        require(!glassRecipe.matches(grid,player.world),"missing glass does not craft glass");
        String[][] finishes = {{"industrial_block", "Industrial Block"},
                {"industrial_trim", "Industrial Trim"},
                {"industrial_grate", "Industrial Grate"},
                {"light_industrial_panel", "Light Industrial Panel"},
                {"dark_industrial_panel", "Dark Industrial Panel"}};
        for (String[] finish : finishes) {
            Block finishBlock = Block.REGISTRY.getObject(
                    new ResourceLocation("vandorlabs", finish[0]));
            require(finishBlock != null && finishBlock.getRegistryName() != null,
                    "missing industrial finish " + finish[0]);
            ItemStack finishItem = new ItemStack(finishBlock);
            require(!finishItem.isEmpty() && finishItem.getDisplayName().equals(finish[1]),
                    "industrial finish item name " + finish[0]);
            require(mc.getRenderItem().getItemModelMesher().getItemModel(finishItem)
                    != mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),
                    "industrial finish item model " + finish[0]);
        }
        for (String removed : new String[]{"plasma_vent_side", "plasma_vent_top",
                "plasma_vent_rear", "plasma_vent_trim", "plasma_vent_dark_trim",
                "plasma_vent_cavity", "rubber_studs", "framed_observation_glass",
                "space_glass_small", "space_glass_medium", "space_glass_large",
                "wall_regular", "wall_porthole", "wall_bottom_diagonal", "wall_top_diagonal"})
            require(!Block.REGISTRY.containsKey(new ResourceLocation("vandorlabs", removed)),
                    "retired block remains registered: " + removed);
        System.out.println("[vandorlabs][reprolab] item-runtime PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("item check: " + message);
    }
}
