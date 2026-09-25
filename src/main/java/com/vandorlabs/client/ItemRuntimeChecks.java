package com.vandorlabs.client;

import com.vandorlabs.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
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
        checkIndustrialAlloyIngot(grid, player, mc);
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
        checkProgrammableRecipes(grid, player);
        checkWallRecipes(grid, player);
        for (String finish : com.vandorlabs.tiles.ScreenHousingTextures.IDS)
            require(!Block.REGISTRY.containsKey(new ResourceLocation("vandorlabs", finish)),
                    "retired finish remains a separate block: " + finish);
        ItemStack programmableBlock = new ItemStack(
                com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK);
        require("Programmable Block".equals(programmableBlock.getDisplayName())
                        && mc.getRenderItem().getItemModelMesher().getItemModel(programmableBlock)
                        != mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),
                "programmable block item/name/model");
        checkConfiguredItemModels(mc);
        for (String removed : new String[]{"plasma_vent_side", "plasma_vent_top",
                "plasma_vent_rear", "plasma_vent_trim", "plasma_vent_dark_trim",
                "plasma_vent_cavity", "rubber_studs", "framed_observation_glass",
                "space_glass_small", "space_glass_medium", "space_glass_large",
                "wall_regular", "wall_porthole", "wall_bottom_diagonal", "wall_top_diagonal",
                "non_slip_metal_floor", "tritanium_hull", "wall_vent",
                "bolted_wall_plate", "porthole", "porthole_unlit",
                "light_column_wall", "wall_light_columns_unlit",
                "slatted_lamp", "slatted_lamp_unlit",
                "window_lamp", "window_lamp_unlit",
                "lightbar_wall", "wall_lightbar_unlit", "control_buttons",
                "vent_grille", "burgundy_carpet", "bluegray_carpet",
                "bridge_chair_command", "bridge_chair_companion",
                "bridge_chair_operator", "bridge_chair_conference",
                "bridge_chair_mess_hall"})
            require(!Block.REGISTRY.containsKey(new ResourceLocation("vandorlabs", removed)),
                    "retired block remains registered: " + removed);
        System.out.println("[vandorlabs][reprolab] item-runtime PASS");
    }

    private static void checkConfiguredItemModels(Minecraft mc) {
        Block[] housingBlocks = {
                com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK,
                com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_SLAB,
                com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_WALL,
                com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,
                com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_PORTHOLE_WALL};
        for (Block block : housingBlocks) {
            ItemStack first = new ItemStack(block);
            ItemStack last = new ItemStack(block);
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setInteger(com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE,
                    com.vandorlabs.tiles.ScreenHousingTextures.IDS.length - 1);
            last.setTagInfo("BlockEntityTag", tag);
            net.minecraft.client.renderer.block.model.IBakedModel a =
                    mc.getRenderItem().getItemModelMesher().getItemModel(first);
            net.minecraft.client.renderer.block.model.IBakedModel b =
                    mc.getRenderItem().getItemModelMesher().getItemModel(last);
            require(a != b && a != mc.getRenderItem().getItemModelMesher()
                            .getModelManager().getMissingModel()
                            && b != mc.getRenderItem().getItemModelMesher()
                            .getModelManager().getMissingModel(),
                    block.getRegistryName() + " hotbar texture does not follow copied housing");
        }
        ItemStack chairA = new ItemStack(com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_CHAIR);
        ItemStack chairB = chairA.copy();
        net.minecraft.nbt.NBTTagCompound chairTag = new net.minecraft.nbt.NBTTagCompound();
        chairTag.setInteger("ChairStyle", 4);
        chairB.setTagInfo("BlockEntityTag", chairTag);
        require(mc.getRenderItem().getItemModelMesher().getItemModel(chairA)
                        != mc.getRenderItem().getItemModelMesher().getItemModel(chairB),
                "programmable chair hotbar model does not follow selected style");
        ItemStack doorA = new ItemStack(Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_door")));
        ItemStack doorB = doorA.copy();
        net.minecraft.nbt.NBTTagCompound doorTag = new net.minecraft.nbt.NBTTagCompound();
        new com.vandorlabs.persistence.SpaceDoorData(7, 2, false, 0)
                .write(new com.vandorlabs.persistence.NbtPrimitiveData(doorTag));
        doorB.setTagInfo("SpaceDoorSettings", doorTag);
        require(mc.getRenderItem().getItemModelMesher().getItemModel(doorA)
                        != mc.getRenderItem().getItemModelMesher().getItemModel(doorB),
                "programmable door hotbar model does not follow copied design");
    }

    private static void checkIndustrialAlloyIngot(InventoryCrafting grid,
            EntityPlayer player, Minecraft mc) {
        ResourceLocation id = new ResourceLocation("vandorlabs", "industrial_alloy_ingot");
        require(Item.REGISTRY.getObject(id) == ModItems.INDUSTRIAL_ALLOY_INGOT,
                "industrial alloy ingot registration");
        require(!Block.REGISTRY.containsKey(id), "industrial alloy ingot must not be a block");
        IRecipe recipe = CraftingManager.REGISTRY.getObject(id);
        require(recipe != null, "industrial alloy recipe loaded");
        for (int i = 0; i < 9; i++)
            grid.setInventorySlotContents(i, new ItemStack(i == 4
                    ? ModItems.PROGRAMMABLE_MATTER_INGOT : Items.IRON_INGOT));
        require(recipe.matches(grid, player.world),
                "programmable matter surrounded by iron does not match");
        ItemStack result = CraftingManager.findMatchingResult(grid, player.world);
        require(result.getItem() == ModItems.INDUSTRIAL_ALLOY_INGOT
                        && result.getCount() == 9,
                "industrial alloy recipe must yield nine ingots");
        require("Industrial Alloy Ingot".equals(result.getDisplayName()),
                "industrial alloy ingot display name");
        require(mc.getRenderItem().getItemModelMesher().getItemModel(result)
                        != mc.getRenderItem().getItemModelMesher().getModelManager()
                                .getMissingModel(),
                "industrial alloy ingot model missing");
        String sprite = "vandorlabs:items/industrial_alloy_ingot";
        require(sprite.equals(mc.getTextureMapBlocks().getAtlasSprite(sprite).getIconName()),
                "industrial alloy ingot texture missing");
        grid.setInventorySlotContents(4, ItemStack.EMPTY);
        require(!recipe.matches(grid, player.world),
                "industrial alloy recipe accepted a missing programmable ingot");
        grid.setInventorySlotContents(4, new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT));
        grid.setInventorySlotContents(0, ItemStack.EMPTY);
        require(!recipe.matches(grid, player.world),
                "industrial alloy recipe accepted a missing iron ingot");
    }

    private static void checkProgrammableRecipes(InventoryCrafting grid,
            EntityPlayer player) {
        String[] blocks = {"programmable_viewscreen", "programmable_console",
                "programmable_diagonal_screen", "programmable_half_console",
                "programmable_half_input", "programmable_input"};
        Item[] components = {Item.getItemFromBlock(Blocks.GLASS), Items.REDSTONE,
                Item.getItemFromBlock(Blocks.QUARTZ_STAIRS),
                Item.getItemFromBlock(Blocks.PISTON),
                Item.getItemFromBlock(Blocks.STONE_PRESSURE_PLATE),
                Item.getItemFromBlock(Blocks.LEVER)};
        for (int index = 0; index < blocks.length; index++) {
            ResourceLocation id = new ResourceLocation("vandorlabs", blocks[index]);
            IRecipe recipe = CraftingManager.REGISTRY.getObject(id);
            require(recipe != null, "missing programmable recipe: " + id);
            for (int slot = 0; slot < 9; slot++)
                grid.setInventorySlotContents(slot, new ItemStack(slot == 4 ? Items.DIAMOND
                        : slot == 7 ? components[index]
                        : ModItems.PROGRAMMABLE_MATTER_INGOT));
            require(recipe.matches(grid, player.world), "recipe shape does not match: " + id);
            ItemStack crafted = CraftingManager.findMatchingResult(grid, player.world);
            require(crafted.getItem() == Item.getItemFromBlock(Block.REGISTRY.getObject(id))
                            && crafted.getCount() == 1,
                    "recipe crafts wrong block: " + id);
            grid.setInventorySlotContents(4, ItemStack.EMPTY);
            require(!recipe.matches(grid, player.world), "recipe accepts no diamond: " + id);
            grid.setInventorySlotContents(4, new ItemStack(Items.DIAMOND));
            grid.setInventorySlotContents(7, ItemStack.EMPTY);
            require(!recipe.matches(grid, player.world), "recipe accepts no component: " + id);
        }
    }

    private static void checkWallRecipes(InventoryCrafting grid,
            EntityPlayer player) {
        String[] names = {"programmable_block", "programmable_wall", "programmable_porthole_wall",
                "programmable_diagonal_wall"};
        int[] counts = {4, 4, 2, 4, 4};
        for (int variant = 0; variant < names.length; variant++) {
            for (int slot = 0; slot < 9; slot++)
                grid.setInventorySlotContents(slot, ItemStack.EMPTY);
            if (variant == 0) {
                for (int slot : new int[] {1, 3, 5, 7})
                    grid.setInventorySlotContents(slot,
                            new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
                grid.setInventorySlotContents(4, new ItemStack(Items.REDSTONE));
            } else if (variant == 1) {
                for (int slot = 0; slot < 3; slot++)
                    grid.setInventorySlotContents(slot,
                            new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
            } else if (variant == 2) {
                grid.setInventorySlotContents(0,
                        new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
                grid.setInventorySlotContents(1, new ItemStack(Blocks.GLASS_PANE));
                grid.setInventorySlotContents(2,
                        new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
            } else if (variant == 3) {
                for (int slot : new int[] {0, 3, 4})
                    grid.setInventorySlotContents(slot,
                            new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
            } else {
                for (int slot : new int[] {0, 1, 3})
                    grid.setInventorySlotContents(slot,
                            new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
                grid.setInventorySlotContents(4, new ItemStack(Items.REDSTONE));
            }
            ResourceLocation id = new ResourceLocation("vandorlabs", names[variant]);
            IRecipe recipe = CraftingManager.REGISTRY.getObject(id);
            require(recipe != null && recipe.matches(grid, player.world),
                    "wall recipe missing or does not match: " + id);
            ItemStack result = CraftingManager.findMatchingResult(grid, player.world);
            require(result.getItem() == Item.getItemFromBlock(Block.REGISTRY.getObject(id))
                            && result.getCount() == counts[variant],
                    "wall recipe crafts wrong result: " + id);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("item check: " + message);
    }
}
