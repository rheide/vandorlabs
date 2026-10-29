package com.vandorlabs.client;

import net.minecraft.client.Minecraft;

/** Isolated integrated-world Taxi support regression, without unrelated gameplay matrices. */
final class TaxiLandingClientChecks {
    private static int ticks;
    static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ticks!=80)return false;
        mc.getIntegratedServer().callFromMainThread(()->{try{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);VehicleCombinedLightChecks.run(world);com.vandorlabs.vehicle.SavedTaxiFollowupChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));com.vandorlabs.vehicle.TaxiLandingRuntimeChecks.run(world);com.vandorlabs.vehicle.TaxiModularRuntimeChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}catch(Exception failure){throw new RuntimeException(failure);}}).get();
        VehicleCombinedLightChecks.runClient(mc);
        System.out.println("[vandorlabs][reprolab] taxi-landing27 PASS");return true;
    }
}
