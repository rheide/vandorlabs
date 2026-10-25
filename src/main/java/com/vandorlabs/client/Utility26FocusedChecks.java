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
            check(problems,"programmer",()->com.vandorlabs.programmer.ProgrammerRuntimeChecks.run(world,new BlockPos(500,104,120)));
            return problems;
        }).get();
        check(failures,"programmer rendering and dialog",()->com.vandorlabs.programmer.client.ProgrammerClientChecks.run(mc,output));
        check(failures,"coupler models",()->Utility26RuntimeChecks.models(mc));
        check(failures,"arm and track rendering",()->com.vandorlabs.gear.client.Gear26ClientChecks.run(mc,output));
        if(!failures.isEmpty()){IllegalStateException failure=new IllegalStateException("Utility 2.6 failed fixtures="+failures.size());for(Throwable problem:failures)failure.addSuppressed(problem);throw failure;}
        System.out.println("[vandorlabs][reprolab] utility26-focused PASS");return true;
    }
}
