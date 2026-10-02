package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableTrapdoor;
import com.vandorlabs.network.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.tiles.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.input.*;
import java.io.IOException;

/** Same housing finish picker as other programmable blocks, with live settings. */
public final class GuiProgrammableTrapdoor extends GuiContainer {
    private final TileEntityProgrammableTrapdoor tile;
    private HousingTextureList textures;
    private int position,trigger,channel;
    private boolean sliding,inverted;
    private boolean diagonal(){return tile instanceof TileEntityProgrammableDiagonalTrapdoor;}
    private GuiTextField channelField;
    private GuiButton done;
    public GuiProgrammableTrapdoor(TileEntityProgrammableTrapdoor tile) {
        super(new ContainerProgrammableTrapdoor(tile));this.tile=tile;
        position=tile.getPosition();sliding=tile.isSliding();trigger=tile.getTrigger();channel=tile.getRedstoneChannel();
        inverted=diagonal() && ((TileEntityProgrammableDiagonalTrapdoor)tile).isInverted();
        xSize=360;ySize=240;
    }
    @Override public void initGui() {
        super.initGui();Keyboard.enableRepeatEvents(true);buttonList.clear();
        textures=new HousingTextureList(guiLeft+12,guiTop+38,185,tile.getHousingTexture());
        buttonList.add(new GuiButton(1,guiLeft+214,guiTop+38,134,20,motionLabel()));
        buttonList.add(new GuiButton(2,guiLeft+214,guiTop+64,134,20,positionLabel()));
        buttonList.add(new GuiButton(3,guiLeft+214,guiTop+90,134,20,triggerLabel()));
        if(diagonal())buttonList.add(new GuiButton(5,guiLeft+214,guiTop+166,134,20,"Reverse slope"));
        channelField=new GuiTextField(0,fontRenderer,guiLeft+214,guiTop+140,134,18);
        channelField.setMaxStringLength(10);channelField.setValidator(s->s.isEmpty() || s.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(channel));
        done=new GuiButton(4,guiLeft+12,guiTop+210,336,20,"Done");buttonList.add(done);
    }
    private String motionLabel(){return "Movement: "+(sliding?"Sliding":"Rotating");}
    private String positionLabel(){return diagonal()?new String[]{"Half width / tall","Full width / tall","Full width / shallow"}[position]:"Position: "+new String[]{"Bottom","Middle","Top"}[position];}
    private String triggerLabel(){return trigger==SpaceDoorData.TRIGGER_REDSTONE_ON?"Redstone: On":trigger==SpaceDoorData.TRIGGER_REDSTONE_OFF?"Redstone: Off":"Redstone: Disabled";}
    private int parsedChannel(){try{return Integer.parseInt(channelField.getText());}catch(NumberFormatException e){return -1;}}
    private void send() {
        if(parsedChannel()>=0)channel=parsedChannel();
        int selected=textures.selected();
        tile.configure(selected,position,sliding,trigger,channel);
        if(diagonal())((TileEntityProgrammableDiagonalTrapdoor)tile).setInverted(inverted);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrapdoor(tile.getPos(),selected,position,sliding,trigger,channel,inverted));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==4){if(parsedChannel()>=0){send();mc.player.closeScreen();}return;}
        if(button.id==1){sliding=!sliding;button.displayString=motionLabel();}
        else if(button.id==2){position=(position+1)%3;button.displayString=positionLabel();}
        else if(button.id==3){trigger=(trigger+1)%3;button.displayString=triggerLabel();}
        else if(button.id==5){inverted=!inverted;}
        send();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        channelField.mouseClicked(x,y,button);int before=textures.selected();
        if(textures.click(x,y,button)){if(before!=textures.selected())send();return;}super.mouseClicked(x,y,button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed){if(!textures.drag(y))super.mouseClickMove(x,y,button,elapsed);}
    @Override protected void mouseReleased(int x,int y,int button){textures.release();super.mouseReleased(x,y,button);}
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();textures.wheel(Mouse.getEventX()*width/mc.displayWidth,
                height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());
    }
    @Override protected void keyTyped(char c,int key)throws IOException {
        if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER){if(parsedChannel()>=0){send();mc.player.closeScreen();}return;}
        if(channelField.textboxKeyTyped(c,key)){done.enabled=parsedChannel()>=0;channelField.setTextColor(done.enabled?0xE0E0E0:0xFF7777);if(done.enabled)send();return;}
        super.keyTyped(c,key);
    }
    @Override public void updateScreen(){super.updateScreen();channelField.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);textures.draw(fontRenderer,x,y);

    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString(diagonal()?"Programmable Diagonal Trapdoor":"Programmable Trapdoor",12,8,0xFFFFFF);
        fontRenderer.drawString("Block texture",12,27,0xDAE8F0);
        fontRenderer.drawString("Channel (0 = none)",214,127,0xDAE8F0);
        fontRenderer.drawString("Changes apply to the group",12,145,0xDAE8F0);
        fontRenderer.drawString("Facing sets opening direction",12,196,0xDAE8F0);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);channelField.drawTextBox();}
}
