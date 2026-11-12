package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.util.*;

/** Same flat state tabs, picker and playback widgets in every programmable display dialog. */
final class DisplayActivationPane {
    private final GuiContainer host;private final TileEntityAnimatedScreenSelector tile;private final boolean input;
    private HousingTextureList picker;private ProgrammableDialogLayout layout;private int selected=-1;private boolean active;private int pickerState=-1,pickerMode=-1;
    DisplayActivationPane(GuiContainer host,TileEntityAnimatedScreenSelector tile,boolean input){this.host=host;this.tile=tile;this.input=input;}
    boolean active(){return active;}
    void init(ProgrammableDialogLayout layout,List<GuiButton> buttons,int page,int redstonePage){
        this.layout=layout;int mode=tile.activationMode();
        active=mode>0&&page==tile.activationSurfaceSlot();
        if(active&&(selected<0||mode==1&&selected!=0&&selected!=3))selected=mode==1?3:0;
        List<GuiButton> tabs=new ArrayList<>();for(GuiButton b:buttons)if(b.id>=90&&b.id<=93||b.id==197)tabs.add(b);
        tabs.sort(Comparator.comparingInt(b->b.x));buttons.removeAll(tabs);
        List<GuiButton> all=new ArrayList<>();
        for(GuiButton tab:tabs)if(tab.id==90+tile.activationSurfaceSlot()&&mode>0){for(int s=0;s<4;s++)if(mode==2||s==0||s==3){GuiButton b=new DialogTabButton(650+s,0,0,0,18,mode==1?(s==0?"Off":"On"):new String[]{"0","1-5","6-10","11-15"}[s]);b.enabled=!active||s!=selected;all.add(b);}}else all.add(tab);
        if(mode==0)buttons.addAll(all);
        else {
            int available=layout.width-24-2*(all.size()-1),minimum=0;int[] widths=new int[all.size()];
            for(int i=0;i<all.size();i++){widths[i]=Math.max(14,Minecraft.getMinecraft().fontRenderer.getStringWidth(all.get(i).displayString)+4);minimum+=widths[i];}
            int extra=Math.max(0,available-minimum),x=layout.left+12;
            for(int i=0;i<all.size();i++){GuiButton b=all.get(i);b.x=x;b.y=layout.top+30;b.width=widths[i]+extra/all.size()+(i<extra%all.size()?1:0);x+=b.width+2;buttons.add(b);}
        }
        if(page==redstonePage){for(GuiButton b:buttons)if(b.id==0){b.visible=false;GuiButton activation=new net.minecraftforge.fml.client.config.GuiButtonExt(640,b.x,b.y,b.width-84,20,"Activation: "+new String[]{"Disabled","Digital","Analog"}[mode]);buttons.add(activation);buttons.add(new GuiButton(641,b.x+b.width-80,b.y,80,20,tile.isRedstoneInverted()?"Signal: Low":"Signal: High"));break;}}
        if(!active)return;
        for(GuiButton b:buttons)if(!(b.id>=90&&b.id<=93||b.id==197||b.id>=650&&b.id<=653||b.id==20||b.id==30||b.id==3004))b.visible=false;
        picker=new HousingTextureList(layout.listX,layout.top+54,layout.listWidth,tile.activationTexture(selected),8,null,
            value->HousingTextureList.generalTexture(value)||(input?ScreenTextureList.artwork(value,true):HousingTextureList.displayTexture(value)))
            .visibleRows(layout.rows(true)).keepView(picker).custom(value->save(value,tile.activationPlayback(selected),tile.activationSpeed(selected)));
        if(pickerState!=selected||pickerMode!=mode)picker.revealSelected();
        pickerState=selected;pickerMode=mode;
        boolean sharedRows=tile.activation.texture(selected)<0&&tile.baseSurfaceTexture(tile.activationSurfaceSlot())==TileEntityAnimatedScreenSelector.REDSTONE_SURFACE;
        for(int i=0;i<3;i++){GuiButton b=layout.choice(660+i,i,3,66,new String[]{"Off","Static","Animated"}[i]);b.enabled=!sharedRows&&tile.activationPlayback(selected)!=i;buttons.add(b);b=layout.choice(663+i,i,3,100,new String[]{"Slow","Normal","Fast"}[i]);b.enabled=!sharedRows&&tile.activationSpeed(selected)!=i;buttons.add(b);}
        int resetY=input?(buttons.stream().anyMatch(b->b.id==30&&b.visible)?152:128):148;GuiButton inherit=layout.control(666,resetY,"Use Shared Artwork");inherit.enabled=tile.activation.texture(selected)>=0;buttons.add(inherit);
    }
    boolean action(GuiButton button){
        if(button.id>=650&&button.id<=653){selected=button.id-650;return true;}
        if(button.id==641){tile.setRedstoneInverted(!tile.isRedstoneInverted());save(tile.activationMode(),0,tile.activation.texture(0),tile.activation.playback(0),tile.activation.speed(0));return true;}
        if(button.id==640){int mode=GuiOptionCycle.next(tile.activationMode(),3);save(mode,0,tile.activation.texture(0),tile.activation.playback(0),tile.activation.speed(0));if(mode==0)selected=-1;return true;}
        if(button.id==666){save(-1,tile.activationPlayback(selected),tile.activationSpeed(selected));return true;}
        if(button.id>=660&&button.id<=662){save(tile.activation.texture(selected),button.id-660,tile.activationSpeed(selected));return true;}
        if(button.id>=663&&button.id<=665){save(tile.activation.texture(selected),tile.activationPlayback(selected),button.id-663);return true;}
        return false;
    }
    private void save(int texture,int playback,int speed){save(tile.activationMode(),selected,texture,playback,speed);}
    private void save(int mode,int state,int texture,int playback,int speed){com.vandorlabs.tiles.DisplayEditGroups.apply(tile,mode,state,texture,playback,speed,tile.isRedstoneInverted(),target->true);PacketHandler.INSTANCE.sendToServer(new MessageDisplayActivation(tile.getPos(),mode,state,texture,playback,speed,tile.isRedstoneInverted()));}
    void click(int x,int y,int button){if(picker.click(x,y,button)&&picker.picked())save(picker.selected(),tile.activationPlayback(selected),tile.activationSpeed(selected));}
    boolean key(char character,int key){return active&&picker.searchKey(character,key);}
    void wheel(int x,int y,int delta){picker.wheel(x,y,delta);}
    void drag(int y){picker.drag(y);}
    void release(){if(picker!=null)picker.release();}
    void draw(int x,int y){if(!active)return;Gui.drawRect(layout.left+8,layout.top+51,layout.left+layout.width-8,layout.top+120,0xff101012);picker.draw(Minecraft.getMinecraft().fontRenderer,x,y);Minecraft.getMinecraft().fontRenderer.drawString("Displays",layout.controlsX,layout.top+54,0xdae8f0);Minecraft.getMinecraft().fontRenderer.drawString("Speed",layout.controlsX,layout.top+88,0xdae8f0);}
}
