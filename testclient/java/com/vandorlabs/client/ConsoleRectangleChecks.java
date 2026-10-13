package com.vandorlabs.client;

/** Retired machinery must never acquire a registered block or item again. */
public final class ConsoleRectangleChecks {
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        if(!com.vandorlabs.shipsystems.ShipSystemMesh.MODELS.isEmpty()
                || !com.vandorlabs.shipsystems.ShipSystemMesh.BLOCK_MODELS.isEmpty())
            throw new AssertionError("Retired machinery meshes remain loaded");
        for(String prefix:new String[]{"rivet_","vektor_","kestrel_","hadron_"})
            if(!com.vandorlabs.blocks.ModBlocks.isRemovedMachineryId(prefix+"console"))
                throw new AssertionError("Retired registry prefix is not handled: "+prefix);
        System.out.println("PASS: all four retired machinery families are absent");
    }
}
