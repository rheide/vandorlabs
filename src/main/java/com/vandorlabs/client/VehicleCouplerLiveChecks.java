package com.vandorlabs.client;
import net.minecraft.client.Minecraft;
import java.io.File;
final class VehicleCouplerLiveChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if("1".equals(System.getenv("VANDOR_LABS_COUPLER_PEERS_ONLY")))return VehicleArmCouplerPeersLiveChecks.tick(mc,output);
        if("1".equals(System.getenv("VANDOR_LABS_COUPLER_ARM_RELEASE_ONLY")))return VehicleArmCouplerReleaseLiveChecks.tick(mc,output);
        if("1".equals(System.getenv("VANDOR_LABS_COUPLER_ARM_MULTI_ONLY")))return VehicleArmCouplerOwnershipConsumerChecks.tick(mc,output);
        if("1".equals(System.getenv("VANDOR_LABS_COUPLER_ARM_ONLY")))return VehicleArmCouplerLiveChecks.tick(mc,output);
        if(ticks<0){if(!VehicleTerrainAccessChecks.tick(mc))return false;System.out.println("[vandorlabs][reprolab] coupler-articulation-live PASS "+("1".equals(System.getenv("VANDOR_LABS_COUPLER_VERTICAL_ONLY"))?"vertical topology saved-poses server-driver terrain-access":"physics rendering terrain-access"));return true;}
        mc.gameSettings.pauseOnLostFocus=false;mc.displayGuiScreen(null);if(++ticks<40)return false;
        mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
            if("1".equals(System.getenv("VANDOR_LABS_COUPLER_VERTICAL_ONLY"))){try{com.vandorlabs.vehicle.VehicleVerticalCouplerChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));}catch(Exception failure){throw new IllegalStateException(failure);}return;}
            try{com.vandorlabs.vehicle.VehicleArticulationLimitChecks.run(world);com.vandorlabs.vehicle.VehicleTurningCostChecks.run(world);com.vandorlabs.vehicle.VehicleTrainDriveChecks.run(world);}catch(Exception failure){throw new IllegalStateException(failure);}
            com.vandorlabs.vehicle.VehicleSegmentControllerChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));
            com.vandorlabs.vehicle.VehicleCouplerSectionChecks.run(world);
            com.vandorlabs.vehicle.VehicleCouplerPhysicsChecks.run(world);
            com.vandorlabs.coupler.CouplerStaticChecks.run(world,new net.minecraft.util.math.BlockPos(900,100,900));
        }).get();if(!"1".equals(System.getenv("VANDOR_LABS_COUPLER_VERTICAL_ONLY"))){CouplerClientChecks.run(mc,output);VehicleCouplerRenderChecks.run(mc,output);}
        ticks=-1;return false;
    }
    private VehicleCouplerLiveChecks(){}
}
