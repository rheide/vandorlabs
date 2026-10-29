package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Shared receiving-channel and activation controls for native and cockpit displays. */
public final class ReceiverPane {
    public static final int HEIGHT=62;
    private final FontRenderer font;
    private final GuiTextField field;
    private final RedstoneActivationButton button;
    private final int x,y;
    public ReceiverPane(int fieldId,int buttonId,FontRenderer font,int x,int y,int width,ChannelList channels,int mode){
        this.font=font;this.x=x;this.y=y;
        field=new GuiTextField(fieldId,font,x,y+12,width,18);ChannelFields.configure(field);field.setText(ChannelFields.text(channels));
        button=new RedstoneActivationButton(buttonId,x,y+42,width,mode);
    }
    public GuiTextField field(){return field;}
    public RedstoneActivationButton button(){return button;}
    public ChannelList channels(){return ChannelFields.parse(field);}
    public void setMode(int mode){button.setMode(mode);}
    public void setVisible(boolean visible){field.setVisible(visible);button.visible=visible;if(!visible)field.setFocused(false);}
    public void drawLabel(){if(field.getVisible())font.drawString("Receive channels (blank = local)",x,y,0xdae8f0);}
    public void draw(){drawLabel();field.drawTextBox();}
    public boolean keyTyped(char character,int key){return field.getVisible()&&field.textboxKeyTyped(character,key);}
    public void mouseClicked(int mouseX,int mouseY,int mouseButton){if(field.getVisible())field.mouseClicked(mouseX,mouseY,mouseButton);}
}
