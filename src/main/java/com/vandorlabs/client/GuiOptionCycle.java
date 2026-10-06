package com.vandorlabs.client;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.EnumFacing;

/** One mouse direction for bounded option cycles; ordinary action buttons stay left-click only. */
final class GuiOptionCycle {
    private static int step=1;
    private GuiOptionCycle(){}
    interface Action {void run(GuiButton button)throws IOException;}
    static int next(int value,int count){return Math.floorMod(value+step,count);}
    static int next(int value,int low,int high){return low+next(value-low,high-low+1);}
    static EnumFacing next(EnumFacing facing){return step<0?facing.rotateYCCW():facing.rotateY();}
    static boolean rightClick(Minecraft mc,List<GuiButton> buttons,int x,int y,int mouse,Action action,int... ids)throws IOException {
        if(mouse!=1)return false;
        for(GuiButton button:buttons)for(int id:ids)if(button.id==id && button.mousePressed(mc,x,y)) {
            int previous=step;step=-1;
            try{button.playPressSound(mc.getSoundHandler());action.run(button);}finally{step=previous;}
            return true;
        }
        return false;
    }
}
