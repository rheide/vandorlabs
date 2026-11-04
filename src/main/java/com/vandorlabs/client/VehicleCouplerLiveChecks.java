package com.vandorlabs.client;
import net.minecraft.client.Minecraft;
import java.io.File;
final class VehicleCouplerLiveChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;mc.displayGuiScreen(null);if(++ticks<40)return false;
        mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
            com.vandorlabs.vehicle.VehicleCouplerSectionChecks.run(world);
            com.vandorlabs.vehicle.VehicleCouplerPhysicsChecks.run(world);
            com.vandorlabs.coupler.CouplerStaticChecks.run(world,new net.minecraft.util.math.BlockPos(900,100,900));
        }).get();CouplerClientChecks.run(mc,output);VehicleCouplerRenderChecks.run(mc,output);
        System.out.println("[vandorlabs][reprolab] coupler-articulation-live PASS section-Hover gating and full/slab configuration regressions");return true;
    }
    private VehicleCouplerLiveChecks(){}
}
