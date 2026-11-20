package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** Ordinary player packet and shared dialog controls on the powered Arm fixture. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ObserverDialogLiveChecks {
    private static int stage,ticks,frames;
    private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Observer dialog stage="+stage+" tick="+ticks+": "+why);}
    private static void click(GuiNavigationTable gui,int id,int mouse)throws Exception{GuiButton button=gui.control(id);need(button!=null&&button.visible&&button.enabled,"available control "+id);gui.mouseClicked(button.x+button.width/2,button.y+button.height/2,mouse);frames=0;}
    public static boolean tick(Minecraft mc,BlockPos target)throws Exception{
        need(++ticks<300,"bounded ordinary interaction");
        net.minecraft.tileentity.TileEntity member=mc.world.getTileEntity(target);
        TileEntityConstructor observer=member instanceof TileEntityConstructor?(TileEntityConstructor)member:member instanceof TileEntityConstructorPart?(TileEntityConstructor)mc.world.getTileEntity(((TileEntityConstructorPart)member).owner()):null;
        if(observer==null)return false;
        if(stage==0){
            if(mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(target.getX()+.5,target.getY()-1,target.getZ()-3.5)>.02||!observer.observerPowered())return false;
            need(mc.playerController.processRightClickBlock(mc.player,mc.world,target,EnumFacing.NORTH,new Vec3d(target.getX()+.5,target.getY()+.5,target.getZ()),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"ordinary right-click packet");stage=1;return false;
        }
        GuiNavigationTable gui=mc.currentScreen instanceof GuiNavigationTable?(GuiNavigationTable)mc.currentScreen:null;
        if(gui==null)return false;
        if(stage==1){need(observer.observerPowered(),"RF connection status available");click(gui,92,0);stage=2;return false;}
        if(stage==2){if(observer.observerScanEnabled()||++frames<6)return false;need(observer.observerPowered()&&observer.activity()==1,"connected Idle retains power status");click(gui,92,1);stage=3;return false;}
        if(stage==3){if(!observer.observerScanEnabled()||++frames<6)return false;click(gui,21,0);click(gui,1,0);stage=4;return false;}
        if(stage==4){if(!observer.screenSettings().redstone||observer.screenOn()||++frames<6)return false;need(observer.observerPowered()&&observer.activity()==1,"Redstone without signal selects Idle");click(gui,1,1);stage=5;return false;}
        if(stage==5){if(observer.screenSettings().redstone||!observer.screenOn()||++frames<6)return false;click(gui,20,0);stage=6;return false;}
        if(++frames<5||!observer.observerScanning())return false;
        File out=new File(System.getenv("VANDOR_LABS_REPRO_OUT"));out.mkdirs();ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(out,"observer-powered-dialog.png"));
        mc.player.closeScreen();System.out.println("[vandorlabs] observer-dialog PASS ordinaryPacket/manualIdle/RedstoneIdle/previous/C2S/connectedArm");return true;
    }
    private ObserverDialogLiveChecks(){}
}
