package com.vandorlabs.client;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Verify removed machinery is absent from a real client/server registry. */
public final class MachineryRuntimeChecks {
    public static void run(World world,BlockPos origin) {
        for(Block block:Block.REGISTRY)check(block.getRegistryName());
        for(Item item:Item.REGISTRY)check(item.getRegistryName());
        if(!com.vandorlabs.shipsystems.ShipSystemMesh.MODELS.isEmpty())
            throw new IllegalStateException("Retired machinery models remain loaded");
        System.out.println("[vandorlabs][reprolab] machinery-live PASS all four families absent");
    }
    private static void check(ResourceLocation id) {
        if(id!=null && id.getResourceDomain().equals("vandorlabs")
                && com.vandorlabs.blocks.ModBlocks.isRemovedMachineryId(id.getResourcePath()))
            throw new IllegalStateException("Retired machinery remains registered: "+id);
    }
    private MachineryRuntimeChecks() { }
}
