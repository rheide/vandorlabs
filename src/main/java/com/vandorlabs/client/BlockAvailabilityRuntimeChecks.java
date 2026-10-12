package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.config.BlockAvailability;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Loaded-config check against real registries, creative lists and server recipes. */
final class BlockAvailabilityRuntimeChecks {
    static void run(World world) {
        NonNullList<ItemStack> menu=NonNullList.create();
        VandorLabs.VANDOR_LABS_TAB.displayAllRelevantItems(menu);
        for(ItemStack stack:menu)if(stack.getItem() instanceof ItemBlock)
            check(BlockAvailability.enabled(((ItemBlock)stack.getItem()).getBlock()),"disabled block remains in creative menu");
        int preserved=0;
        for(Block block:ModBlocks.BLOCKS) {
            check(Block.REGISTRY.containsKey(block.getRegistryName()),"registered identity missing");
            if(!BlockAvailability.enabled(block)) {
                check(block.getCreativeTabToDisplayOn()==null,"disabled block still has creative tab");
                preserved++;
            }
        }
        int recipes=0;
        for(net.minecraft.item.crafting.IRecipe recipe:net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES) {
            ItemStack result=recipe.getRecipeOutput();
            if(!result.isEmpty()&&result.getItem() instanceof ItemBlock) {
                Block block=((ItemBlock)result.getItem()).getBlock();
                check(BlockAvailability.enabled(block),"disabled crafting recipe remains: "+recipe.getRegistryName());
                recipes++;
            }
        }
        // Negative-only checks miss a failed condition factory that removes every recipe.
        for(String id:new String[]{"programmable_block","programmable_ramp","programmable_door"}) {
            net.minecraft.util.ResourceLocation key=new net.minecraft.util.ResourceLocation("vandorlabs",id);
            boolean present=net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(key);
            check(present==BlockAvailability.recipeEnabled(key),
                    "recipe availability disagrees with configuration: "+key+" present="+present);
        }
        BlockPos pos=new BlockPos(0,80,0);
        world.setBlockState(pos,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        check(world.getBlockState(pos).getBlock()==ModBlocks.PROGRAMMABLE_BLOCK,"existing disabled block not preserved");
        world.setBlockToAir(pos);
        System.out.println("[vandorlabs] block-categories-live PASS preserved="+preserved+" blockRecipes="+recipes);
    }
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException("Block categories: "+message);}
}
