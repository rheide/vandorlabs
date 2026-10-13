package com.vandorlabs.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/** The machinery focus verifies retirement instead of rendering removed blocks. */
final class MachineryVisualChecks {
    static boolean tick(Minecraft mc,File output) throws Exception {
        mc.getIntegratedServer().addScheduledTask(()->MachineryRuntimeChecks.run(
                mc.getIntegratedServer().getWorld(0),BlockPos.ORIGIN)).get();
        System.out.println("[vandorlabs] machinery-render-live PASS all four families absent");
        return true;
    }
}
