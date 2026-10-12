package com.vandorlabs.client;

import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;

final class GlassVisualChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,File output) throws Exception {
        ticks++;mc.gameSettings.pauseOnLostFocus=false;
        if(ticks==1){mc.displayGuiScreen(null);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.gameSettings.renderDistanceChunks=4;}
        if(ticks==80) {
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                MaterialRuntimeChecks.run(player);GlassAppearanceRuntimeChecks.run(player);
                GlassAppearanceRuntimeChecks.buildGallery(player.world);
                player.capabilities.isFlying=true;player.connection.setPlayerLocation(45,7,33,0,20);
            }).get();
            mc.player.capabilities.isFlying=true;mc.player.setPositionAndRotation(45,7,33,0,20);
            mc.gameSettings.hideGUI=true;
        }
        if(ticks==140) {
            GlassAppearanceRuntimeChecks.checkRenderMeshes(mc.world);save(mc,output,"glass-strengths-and-frames");
            mc.gameSettings.hideGUI=false;
            openDialog(mc,new BlockPos(42,5,40),com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_GLASS);
        }
        if(ticks==160) {
            checkDialog(mc,false);ProgrammableDialogRuntimeChecks.check(mc);
            ((GuiProgrammableGlass)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(4,0,0,""));
            ((GuiProgrammableGlass)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(5,0,0,""));
        }
        if(ticks==180) {
            checkDialog(mc,false);save(mc,output,"glass-regular-settings");
            openDialog(mc,new BlockPos(42,5,43),com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR);
        }
        if(ticks==200) {
            checkDialog(mc,true);ProgrammableDialogRuntimeChecks.check(mc);
            ((GuiProgrammableWall)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(113,0,0,""));
            ((GuiProgrammableWall)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(114,0,0,""));
        }
        if(ticks==220) {
            checkDialog(mc,true);save(mc,output,"glass-diagonal-settings");mc.player.closeScreen();mc.gameSettings.hideGUI=true;
            mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(45.5,5,47,0,30)).get();
            mc.player.setPositionAndRotation(45.5,5,47,0,30);
        }
        if(ticks==250){
            save(mc,output,"glass-bare-joined-edges");
            mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(41,7,60,-35,25)).get();
            mc.player.setPositionAndRotation(41,7,60,-35,25);
        }
        if(ticks==280){save(mc,output,"glass-w-joined-edges");System.out.println("[vandorlabs] glass-render-live PASS strengths, frames, W joins and dialogs");return true;}
        return false;
    }
    private static void openDialog(Minecraft mc,BlockPos pos,int gui) throws Exception {
        mc.player.closeScreen();
        mc.player.setPositionAndRotation(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.closeScreen();
            player.connection.setPlayerLocation(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);
            player.openGui(com.vandorlabs.VandorLabs.instance,gui,player.world,pos.getX(),pos.getY(),pos.getZ());
        }).get();
    }
    private static void checkDialog(Minecraft mc,boolean diagonal) throws Exception {
        if(diagonal?!(mc.currentScreen instanceof GuiProgrammableWall):!(mc.currentScreen instanceof GuiProgrammableGlass))
            throw new IllegalStateException("Glass dialog did not open through server container");
        BlockPos pos=new BlockPos(42,5,diagonal?43:40);
        net.minecraft.tileentity.TileEntity clientTile=mc.world.getTileEntity(pos);
        final int tint=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)clientTile).getGlassTint()
                :((com.vandorlabs.tiles.TileEntityProgrammableGlass)clientTile).getTint();
        final boolean frame=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)clientTile).isGlassFramed()
                :((com.vandorlabs.tiles.TileEntityProgrammableGlass)clientTile).isFramed();
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            boolean correct=diagonal?player.openContainer instanceof com.vandorlabs.container.ContainerAnimatedScreenSelector
                    :player.openContainer instanceof com.vandorlabs.container.ContainerProgrammableGlass;
            if(!correct || !player.openContainer.canInteractWith(player))
                throw new IllegalStateException("Glass dialog server container is missing or out of reach");
            net.minecraft.tileentity.TileEntity tile=player.world.getTileEntity(pos);
            int actualTint=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).getGlassTint()
                    :((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).getTint();
            boolean actualFrame=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).isGlassFramed()
                    :((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).isFramed();
            if(actualTint!=tint || actualFrame!=frame)
                throw new IllegalStateException("Glass dialog appearance did not synchronize to server");
        }).get();
    }
    private static void save(Minecraft mc,File output,String name) throws Exception {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name+".png"));
    }
}
