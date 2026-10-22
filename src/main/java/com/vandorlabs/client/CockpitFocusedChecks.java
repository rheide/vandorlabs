package com.vandorlabs.client;
import net.minecraft.client.Minecraft;
final class CockpitFocusedChecks {
    private static int ticks;
    static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=3;}
        if(ticks!=80)return false;
        mc.getIntegratedServer().addScheduledTask(()->{
            try {CockpitRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));}
            catch(Exception failure){throw new IllegalStateException(failure);}
        }).get();
        System.out.println("[vandorlabs][reprolab] cockpit-focused PASS");return true;
    }
}
