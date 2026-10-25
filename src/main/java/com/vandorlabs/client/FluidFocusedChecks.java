package com.vandorlabs.client;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
final class FluidFocusedChecks {
    private static int ticks;private static boolean checked;
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=6;}
        if(ticks==80)mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            VanillaTextureCopyChecks.run(world);StairConfigurationChecks.server(world,player);DoorWaterGushChecks.run(world);OpeningFluidRuntimeChecks.run(world);SpongeFluidChecks.run(world);DoorSecurityRuntimeChecks.run(world);
            TokenSlotVisualChecks.build(world);player.connection.setPlayerLocation(326,34,142,0,0);
        }).get();
        if(ticks<200)return false;
        if(Boolean.getBoolean("vandorlabs.shapeModelGoldensOnly")){if(!checked){LegacyShapeModelGoldens.run(mc,output);checked=true;}return true;}
        if(!checked){LegacyShapeModelGoldens.run(mc,output);WaterloggedSurfaceChecks.run(mc,output);checked=true;}
        if(!StairDialogLiveChecks.tick(mc,output))return false;
        if(!DiagonalTrapdoorDirectionLiveChecks.tick(mc,output))return false;
        if(!MountFilterLiveChecks.tick(mc,output))return false;
        System.out.println("[vandorlabs][reprolab] fluid-focused PASS");return true;
    }
}
