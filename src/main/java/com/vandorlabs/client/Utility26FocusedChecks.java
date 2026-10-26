package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/** Independent focused fixtures report all failures before ending the disposable client. */
final class Utility26FocusedChecks {
    private static int ticks;
    private interface Check{void run()throws Exception;}
    private static void check(java.util.List<Throwable> failures,String name,Check action){
        try{action.run();}catch(Exception|LinkageError failure){System.err.println("[vandorlabs][reprolab] utility26 fixture FAILED "+name);failure.printStackTrace();failures.add(failure);}
    }
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=3;}
        if(ticks!=80)return false;
        java.util.List<Throwable> failures=mc.getIntegratedServer().callFromMainThread(()->{
            java.util.List<Throwable> problems=new java.util.ArrayList<>();
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
            net.minecraft.entity.player.EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            check(problems,"cockpit copy",()->CockpitCopyRuntimeChecks.run(world,actor));
            check(problems,"boundaries and ramps",()->Utility26RuntimeChecks.run(world,actor));
            check(problems,"optional block compatibility",()->ForeignCompatibility26Checks.run(world,actor));
            check(problems,"arms and tracks",()->com.vandorlabs.gear.Gear26RuntimeChecks.run(world,actor));
            check(problems,"surface appearance",()->com.vandorlabs.surface.SurfaceRuntimeChecks.run(world,actor));
            check(problems,"moving block exact snapshots",()->com.vandorlabs.vehicle.MovingSupportRuntimeChecks.run(world));
            check(problems,"previous-release vehicle snapshots",()->com.vandorlabs.vehicle.VehicleUpgradeChecks.run(world));
            check(problems,"attachment scan",()->com.vandorlabs.vehicle.AttachmentScanRuntimeChecks.run(world));
            check(problems,"Taxi support radius and wheelbase",()->com.vandorlabs.vehicle.VehicleSteeringChecks.run(world));
            check(problems,"stacked complete vehicles",()->com.vandorlabs.vehicle.VehicleStackChecks.run(world,actor));
            check(problems,"decorative drive activation",()->com.vandorlabs.drives.DriveRuntimeChecks.run(world,actor));
            check(problems,"copy player-relative placement",()->com.vandorlabs.copier.CopyPlacementChecks.run(world,actor));
            check(problems,"copy channel schemas",CopyChannelChecks::run);
            check(problems,"copy plan and materials",()->com.vandorlabs.copier.CopyPlanRuntimeChecks.run(world,actor));
            check(problems,"copy transactions and recovery",()->com.vandorlabs.copier.CopyRuntimeChecks.run(world,actor));
            check(problems,"pressure plate",()->com.vandorlabs.plate.PlateRuntimeChecks.run(world,actor,new BlockPos(560,104,170)));
            check(problems,"Track player placement and style",()->com.vandorlabs.gear.track.TrackRuntimeChecks.run(world,actor));
            check(problems,"thruster joined shape",()->com.vandorlabs.blocks.ThrusterJoinChecks.run(world,actor));
            check(problems,"programmer",()->com.vandorlabs.programmer.ProgrammerRuntimeChecks.run(world,new BlockPos(500,104,120)));
            check(problems,"carried chest access",()->com.vandorlabs.vehicle.VehicleChestRuntimeChecks.run(world,actor));
            check(problems,"vehicle coupler static",()->com.vandorlabs.coupler.CouplerStaticChecks.run(world,new BlockPos(1440,110,300)));
            check(problems,"dynamic vehicle coupling",()->com.vandorlabs.vehicle.VehicleCouplingRuntimeChecks.run(world,actor));
            check(problems,"vehicle coupling recovery",()->com.vandorlabs.vehicle.VehicleCouplingTransferChecks.run(world,actor));
            return problems;
        }).get();
        check(failures,"decorative drive rendering and dialog",()->DriveClientChecks.run(mc,output));
        check(failures,"programmer rendering and dialog",()->com.vandorlabs.programmer.client.ProgrammerClientChecks.run(mc,output));
        check(failures,"pressure plate rendering and dialog",()->PressurePlateClientChecks.run(mc,output));
        check(failures,"copy controller rendering and dialog",()->com.vandorlabs.copier.client.CopyClientChecks.run(mc,output));
        check(failures,"surface rendering and dialog",()->SurfaceClientChecks.run(mc,output));
        check(failures,"legacy vanilla Fit appearance",()->JoinedFitAppearanceChecks.run(mc));
        check(failures,"vehicle coupler models and dialog",()->CouplerClientChecks.run(mc,output));
        check(failures,"Track appearance bounds and errors",()->TrackClientChecks.run(mc,output));
        check(failures,"Track tread animation",()->com.vandorlabs.gear.client.TrackTreadChecks.run(mc,output));
        check(failures,"thruster joined hex rendering",()->ThrusterJoinClientChecks.run(mc,output));
        check(failures,"coupler models",()->Utility26RuntimeChecks.models(mc));
        check(failures,"arm and track rendering",()->com.vandorlabs.gear.client.Gear26ClientChecks.run(mc,output));
        if(!failures.isEmpty()){IllegalStateException failure=new IllegalStateException("Utility 2.6 failed fixtures="+failures.size());for(Throwable problem:failures)failure.addSuppressed(problem);throw failure;}
        System.out.println("[vandorlabs][reprolab] utility26-focused PASS");return true;
    }
}
