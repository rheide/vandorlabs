package com.vandorlabs.client;

import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;

/** Small live renderer/GUI scene; never invokes the full documentation gallery. */
final class MachineryVisualChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,File output) throws Exception {
        ticks++;
        mc.gameSettings.pauseOnLostFocus=false;
        if(ticks==1){mc.displayGuiScreen(null);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.gameSettings.renderDistanceChunks=4;}
        if(ticks==80) {
            mc.getIntegratedServer().addScheduledTask(()->MachineryRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),new BlockPos(0,5,0))).get();
            camera(mc,10,18,-19,0,24);
        }
        if(ticks==140){save(mc,output,"machinery-grid");camera(mc,17,8,-4,25,18);}
        if(ticks==160)save(mc,output,"machinery-animation-a");
        if(ticks==175)save(mc,output,"machinery-animation-b");
        if(ticks==180){save(mc,output,"machinery-crossflow-studs");camera(mc,22,9,-4,0,28);}
        if(ticks==220){save(mc,output,"machinery-vektor-2x3");
            camera(mc,1,7,-2,0,20);
            mc.gameSettings.hideGUI=false;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));
                player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_REDSTONE_CHANNEL,player.world,0,5,0);
            }).get();
        }
        if(ticks==240){
            if(!(mc.currentScreen instanceof GuiRedstoneChannel))throw new IllegalStateException("machinery server-authorized GUI did not open");
            ProgrammableDialogRuntimeChecks.check(mc);
            ((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(17,0,0,"Animation"));
        }
        if(ticks==260)mc.getIntegratedServer().addScheduledTask(()->{
            if(com.vandorlabs.tiles.TileEntityShipSystem.at(mc.getIntegratedServer().getWorld(0),new BlockPos(0,5,0)).isAnimated())throw new IllegalStateException("machinery GUI animation change did not reach server");
        }).get();
        if(ticks==270)((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(17,0,0,"Animation"));
        if(ticks==290){
            mc.getIntegratedServer().addScheduledTask(()->{
                if(!com.vandorlabs.tiles.TileEntityShipSystem.at(mc.getIntegratedServer().getWorld(0),new BlockPos(0,5,0)).isAnimated())throw new IllegalStateException("machinery GUI animation restore did not reach server");
            }).get();
            save(mc,output,"machinery-settings");mc.player.closeScreen();
            System.out.println("[vandorlabs] machinery-render-live PASS grid, authored struts, 2x3 navigation display and settings");return true;
        }
        return false;
    }
    private static void camera(Minecraft mc,double x,double y,double z,float yaw,float pitch) throws Exception {
        mc.gameSettings.hideGUI=true;mc.gameSettings.thirdPersonView=0;
        mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.capabilities.isFlying=true;player.connection.setPlayerLocation(x,y,z,yaw,pitch);}).get();
        mc.player.capabilities.isFlying=true;mc.player.setPositionAndRotation(x,y,z,yaw,pitch);
        mc.player.prevRotationYaw=yaw;mc.player.prevRotationPitch=pitch;
    }
    private static void save(Minecraft mc,File output,String name) throws Exception {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name+".png"));
    }
}
