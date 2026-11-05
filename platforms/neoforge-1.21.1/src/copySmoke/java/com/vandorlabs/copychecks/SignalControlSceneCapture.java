package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameType;

/** Actual world and canonical hotbar icons, after authoritative GUI acknowledgements. */
public final class SignalControlSceneCapture {
    private static int frames,previousScale;private static boolean prepared,requested;private static volatile boolean complete;
    public static boolean capture(Minecraft mc){
        if(mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.player.distanceToSqr(SignalControlScene.X,SignalControlScene.Y,SignalControlScene.Z)>.1||mc.player.getInventory().selected!=8||!mc.player.getMainHandItem().isEmpty())return false;
        for(int i=0;i<8;i++){var stack=mc.player.getInventory().getItem(i);if(!stack.is(i%4==3?VandorLabsBootstrap.WALL_SLIDER_ITEM.get():VandorLabsBootstrap.THRUSTER_LEVER_ITEM.get()))return false;var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null||custom.copyTag().getCompound("RedstoneChannelSettings").getInt("ControlType")!=(i%4==3?0:i%4))return false;}
        for(int row=0;row<2;row++)for(int column=0;column<4;column++){var pos=SignalControlScene.position(row,column);if(!(mc.level.getBlockEntity(pos) instanceof NativeSignalControlBlockEntity tile)||tile.getStep()!=SignalControlScene.step(row,column))return false;}
        if(!prepared){prepared=true;previousScale=mc.options.guiScale().get();mc.options.guiScale().set(2);mc.resizeDisplay();}mc.player.setYRot(0);mc.player.setXRot(0);mc.options.hideGui=false;if(++frames<50)return false;
        if(!requested){requested=true;Screenshot.grab(mc.gameDirectory,"signal-world-native.png",mc.getMainRenderTarget(),message->{complete=true;System.out.println("[vandorlabs][signal-scene] client PASS eight actual floor/wall detents and all canonical style/mount hotbar variants; software-rendered acceptance only");});}
        if(complete){mc.options.guiScale().set(previousScale);mc.resizeDisplay();}return complete;
    }
    private SignalControlSceneCapture(){}
}
