package com.vandorlabs.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/**
 * Named screenshots from fixed camera poses for focused live checks. Build the scene on the
 * server first, then call {@link #tick} from the fixture's client tick until it returns true.
 * Review the resulting PNGs with {@code tools/focus.sh <focus> --shots}, which tiles them into
 * one contact sheet.
 */
final class LiveGallery {
    private static final int SETTLE_TICKS=30;
    private static final class Shot {
        final String name;final double x,y,z;final float yaw,pitch;
        Shot(String name,double x,double y,double z,float yaw,float pitch){this.name=name;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
    }
    private final String prefix;
    private final BlockPos origin;
    private final List<Shot> shots=new ArrayList<>();
    private int index,ticks,settled;

    /** Shot positions are relative to {@code origin}; files are named {@code prefix-name.png}. */
    LiveGallery(String prefix,BlockPos origin){this.prefix=prefix;this.origin=origin;}
    /** Eye position relative to the origin, with Minecraft yaw and pitch in degrees. */
    LiveGallery shot(String name,double x,double y,double z,float yaw,float pitch){
        shots.add(new Shot(name,origin.getX()+x,origin.getY()+y,origin.getZ()+z,yaw,pitch));return this;
    }
    /** Returns true once every shot has been written to {@code output}. */
    boolean tick(Minecraft mc,File output)throws Exception{
        if(index>=shots.size())return true;
        Shot shot=shots.get(index);
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(ticks==0){
            mc.gameSettings.hideGUI=true;mc.gameSettings.thirdPersonView=0;mc.setRenderViewEntity(mc.player);
            // Never block the client on the server: a paused integrated server runs no tasks.
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
                player.connection.setPlayerLocation(shot.x,shot.y,shot.z,shot.yaw,shot.pitch);
            });
        }
        if(++ticks>SETTLE_TICKS*20)throw new IllegalStateException("Gallery camera never reached shot "+shot.name);
        if(mc.player.getDistanceSq(shot.x,shot.y,shot.z)>.01){settled=0;return false;}
        mc.player.rotationYaw=mc.player.prevRotationYaw=shot.yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=shot.pitch;
        if(++settled<SETTLE_TICKS)return false;
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",
                new File(output,prefix+"-"+shot.name+".png"));
        index++;ticks=0;settled=0;
        return index>=shots.size();
    }
    /** Place through the real ItemBlock path, as a player click on {@code face} of {@code support}. */
    static boolean place(WorldServer world,EntityPlayerMP player,ItemStack stack,BlockPos support,EnumFacing face,float hitX,float hitY,float hitZ){
        player.setHeldItem(EnumHand.MAIN_HAND,stack);
        return stack.getItem().onItemUse(player,world,support,EnumHand.MAIN_HAND,face,hitX,hitY,hitZ)==EnumActionResult.SUCCESS;
    }
}
