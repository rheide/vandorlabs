package com.vandorlabs.client;

/** Retired machinery must never acquire a registered block or item again. */
public final class ConsoleRectangleChecks {
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        for(net.minecraft.block.Block block:com.vandorlabs.blocks.ModBlocks.BLOCKS)
            if(block.getRegistryName().getResourcePath().matches("(?:aster|rivet|vektor|kestrel|hadron)_.*"))
                throw new AssertionError("Retired machinery remains registered: "+block.getRegistryName());
        System.out.println("PASS: all five retired machinery families are absent");
    }
}
