package com.vandorlabs.client;

import com.vandorlabs.ports.*;
import com.vandorlabs.ui.ResourcePortDialog;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Focused real Port/Battery screen rendering, separate from unrelated transfer/migration matrices. */
final class MachineDialogVisualChecks {
    static void run(Minecraft mc,File output)throws Exception{
        net.minecraft.client.gui.GuiScreen old=mc.currentScreen;net.minecraft.inventory.Container previous=mc.player.openContainer;int scale=mc.gameSettings.guiScale;
        try{for(boolean battery:new boolean[]{false,true}){
            ResourcePortTile tile=battery?new com.vandorlabs.tiles.TileEntityBattery():new MatterPortTile();ResourcePortContainer menu=new ResourcePortContainer(tile);menu.windowId=-96;menu.batteryValues[0]=50000;
            GuiResourcePort gui=new GuiResourcePort(menu);mc.player.openContainer=menu;mc.displayGuiScreen(gui);
            try{
                capture(mc,gui,output,battery,"general");int before=((ResourcePortSettings)field(gui,"settings")).face(0).ordinal();press(gui,10,1);need(((ResourcePortSettings)field(gui,"settings")).face(0).ordinal()==Math.floorMod(before-1,3),"previous face option");
                press(gui,102,0);capture(mc,gui,output,battery,"housing");if(battery){need(gui.controls().stream().anyMatch(button->button.id==40),"Source Battery shape option");press(gui,40,1);need(!(Boolean)field(gui,"batteryBlock"),"reverse shape toggles existing Block/Hexagon choice");}
                press(gui,101,0);int mode=(Integer)field(gui,"mode");press(gui,30,1);need((Integer)field(gui,"mode")==Math.floorMod(mode-1,3),"previous activation option");capture(mc,gui,output,battery,"redstone");
            }finally{gui.onGuiClosed();mc.player.openContainer=previous;}
        }}finally{mc.gameSettings.guiScale=scale;mc.player.openContainer=previous;mc.displayGuiScreen(old);org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
        System.out.println("[vandorlabs][reprolab] port-dialog PASS actualMatter/Battery General/Housing/Redstone header/logo/sharedbounds/previousface/activation/shape");
    }
    private static Object field(Object owner,String name)throws Exception{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static void press(GuiResourcePort gui,int id,int mouse)throws Exception{GuiButton button=gui.controls().stream().filter(value->value.id==id).findFirst().orElseThrow(()->new IllegalStateException("Missing Source control "+id));need(button.enabled&&button.visible,"available Source control "+id);gui.mouseClicked(button.x+button.width/2,button.y+button.height/2,mouse);}
    private static void capture(Minecraft mc,GuiResourcePort gui,File output,boolean battery,String page)throws Exception{
        need(gui.controlsFit(),"actual viewport/control fit");int selected=(Integer)field(gui,"page"),mode=(Integer)field(gui,"mode");ResourcePortSettings settings=(ResourcePortSettings)field(gui,"settings");boolean block=(Boolean)field(gui,"batteryBlock");
        for(ResourcePortDialog.Control expected:ResourcePortDialog.controls(gui.left(),gui.top(),selected,settings,mode,battery,block)){GuiButton actual=gui.controls().stream().filter(button->button.id==expected.id).findFirst().orElseThrow(()->new IllegalStateException("Source descriptor control missing"));need(actual.x==expected.x&&actual.y==expected.y&&actual.width==expected.width&&actual.height==expected.height&&actual.displayString.equals(expected.label)&&actual.enabled==expected.enabled,"actual Source descriptor "+expected.id);}
        BufferedImage image=DialogRenderCapture.image(mc,gui,gui.width,gui.height);javax.imageio.ImageIO.write(image,"png",new File(output,"source-"+(battery?"battery":"port")+"-"+page+".png"));need((image.getRGB(gui.left()+2,gui.top()+2)&0xffffff)==0x304858,"actual Source28pxheader");need((image.getRGB(gui.left()+2,gui.top()+ResourcePortDialog.HEIGHT-3)&0xffffff)==0x19232c,"actual Sourcepanelbody");
    }
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Source machine dialog: "+message);}
}
