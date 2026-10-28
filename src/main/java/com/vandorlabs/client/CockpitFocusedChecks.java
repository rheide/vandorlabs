package com.vandorlabs.client;
import net.minecraft.client.Minecraft;
final class CockpitFocusedChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=3;}
        if(ticks!=80)return false;
        mc.getIntegratedServer().addScheduledTask(()->{
            try {CockpitRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));}
            catch(Exception failure){throw new IllegalStateException(failure);}
        }).get();
        visualClock(mc);
        CockpitRedstoneRenderChecks.run(mc,output);
        CockpitControlDialogChecks.run(mc,output);
        CockpitDialogRuntimeChecks.runStandalone(mc,output);
        VehicleStatusRenderChecks.run(mc,output);
        System.out.println("[vandorlabs][reprolab] cockpit-focused PASS");return true;
    }
    private static void visualClock(Minecraft mc){
        com.vandorlabs.tiles.TileEntityCockpit tile=new com.vandorlabs.tiles.TileEntityCockpit();
        tile.setWorld(mc.world);tile.setPos(new net.minecraft.util.math.BlockPos(2048,128,2048));
        if(tile.screens(0)!=0)throw new IllegalStateException("Cold cockpit visual pose");
        tile.screenLift.toggle(1000);double begin=tile.screens(0);long saved=mc.world.getTotalWorldTime(),visualTick=CockpitVisualMotion.animationTick(tile);
        try{
            mc.world.setTotalWorldTime(saved+1000000);double ahead=tile.screens(.5F);
            mc.world.setTotalWorldTime(saved-1000000);double behind=tile.screens(.5F);
            if(begin!=0||ahead<begin||behind<ahead||behind>.05)throw new IllegalStateException("Server time correction changed cockpit visual motion");
            long frameTick=CockpitVisualMotion.animationTick(tile);
            if(frameTick<visualTick||frameTick-visualTick>1)throw new IllegalStateException("Cockpit artwork followed server clock correction");
            mc.world.setTotalWorldTime(1015);
            CockpitVisualMotion.authoredFrame(()->{if(Math.abs(tile.screens(0)-.5)>1e-8)throw new IllegalStateException("Authored documentation timeline changed");});
            if(tile.screens(0)>.05)throw new IllegalStateException("Authored timeline leaked into gameplay");
        }finally{mc.world.setTotalWorldTime(saved);}
        System.out.println("[vandorlabs][reprolab] cockpit-network-clock-live PASS real-client-proxy mechanisms-and-artwork forward-backward-time-corrections isolated-authored-frames");
    }
}
