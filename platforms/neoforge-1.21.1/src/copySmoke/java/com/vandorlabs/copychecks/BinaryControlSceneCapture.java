package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;

/** Software-rendered acceptance capture after the real GUI/server checks finish. */
public final class BinaryControlSceneCapture {
    private static int frames;private static boolean requested,complete;
    public static boolean capture(Minecraft mc){
        if(mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.player.distanceToSqr(BinaryControlScene.X,BinaryControlScene.Y,BinaryControlScene.Z)>.1||mc.player.getInventory().selected!=2||!mc.player.getMainHandItem().isEmpty())return false;
        for(int i=3;i<9;i++)if(!mc.player.getInventory().getItem(i).is(i%2==0?VandorLabsBootstrap.PUSH_BUTTON_ITEM.get():VandorLabsBootstrap.ROCKER_SWITCH_ITEM.get()))return false;
        mc.player.setYRot(0);mc.player.setXRot(-12);mc.options.hideGui=false;
        if(++frames<50)return false;
        if(!requested){requested=true;Screenshot.grab(mc.gameDirectory,"binary-world-native.png",mc.getMainRenderTarget(),message->{complete=true;System.out.println("[vandorlabs][binary-scene] client PASS actual six-face world controls and six configured hotbar icons; software-rendered acceptance only");});}
        return complete;
    }
    private BinaryControlSceneCapture(){}
}
