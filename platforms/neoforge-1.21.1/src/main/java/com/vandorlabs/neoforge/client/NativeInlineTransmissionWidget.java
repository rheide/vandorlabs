package com.vandorlabs.neoforge.client;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

/** Inline transmission drafts with host-owned authenticated publication; reusable by any native menu. */
public final class NativeInlineTransmissionWidget {
    @FunctionalInterface public interface Sender {void send(ChannelList channels,int range,boolean local);}
    private final Sender sender;
    private final EditBox channels;
    private final NativeIntegerSlider range;
    private final Button localButton;
    private final List<AbstractWidget> controls;
    private ChannelList observed;
    private int observedRange,index,x,y;
    private boolean local,dirty;
    public NativeInlineTransmissionWidget(Font font,int x,int y,int width,ChannelList channels,int range,boolean local,Sender sender){
        this.sender=sender;this.observed=channels;this.observedRange=range;this.local=local;this.index=index(range);
        this.channels=new EditBox(font,x+100,y+44,width-100,18,Component.literal("Transmit channels")){
            @Override public void setFocused(boolean focused){boolean lost=isFocused()&&!focused;super.setFocused(focused);if(lost)flush();}
        };
        this.channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);this.channels.setFilter(value->value.matches("[0-9, ]*"));this.channels.setValue(channels.isEmpty()?"":channels.toString());this.channels.setResponder(value->{dirty=true;validate();});
        this.range=new NativeIntegerSlider(x,y,width,20,0,6,index,selected->Component.literal("Range: "+TransmissionSettings.RANGES[selected]+" blocks"),selected->{index=selected;flush();});this.localButton=Button.builder(Component.empty(),ignored->{this.local=!this.local;dirty=true;labelLocal();flush();}).bounds(x,y+22,width,20).build();labelLocal();
        controls=List.of(this.range,this.localButton,this.channels);layout(x,y,width);
    }
    private static int index(int range){for(int i=0;i<TransmissionSettings.RANGES.length;i++)if(TransmissionSettings.RANGES[i]==range)return i;return 2;}
    public void layout(int x,int y,int width){this.x=x;this.y=y;range.setPosition(x,y);range.setWidth(width);localButton.setPosition(x,y+22);localButton.setWidth(width);channels.setPosition(x+100,y+44);channels.setWidth(width-100);}
    public List<AbstractWidget> controls(){return controls;}
    public EditBox channelField(){return channels;}
    public AbstractSliderButton rangeControl(){return range;}
    public Button localControl(){return localButton;}
    public int rangeIndex(){return index;}
    public boolean localRedstone(){return local;}
    private ChannelList validate(){ChannelList parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xFF7777:0xE0E0E0);return parsed;}
    public boolean flush(){
        ChannelList parsed=validate();if(parsed==null)return false;int selected=TransmissionSettings.RANGES[index];
        if(!dirty&&parsed.equals(observed)&&selected==observedRange)return true;
        sender.send(parsed,selected,local);observed=parsed;observedRange=selected;dirty=false;return true;
    }
    private void labelLocal(){localButton.setMessage(Component.literal("Local Redstone: "+(local?"On":"Off")));}
    public void hide(){flush();channels.setFocused(false);}
    /** Local left/right clicks save once with the current channel draft; range handles only left drags. */
    public boolean mouseClicked(double mouseX,double mouseY,int button){
        if((button==0||button==1)&&localButton.isMouseOver(mouseX,mouseY)){localButton.playDownSound(Minecraft.getInstance().getSoundManager());localButton.onPress();channels.setFocused(false);return true;}
        if(button==0&&range.isMouseOver(mouseX,mouseY))return false;
        if(!channels.isMouseOver(mouseX,mouseY)){flush();channels.setFocused(false);}return false;
    }
    /** Enter flushes and blurs without closing; Escape flushes before the host's ordinary close. */
    public boolean specialKey(int key){
        if(!channels.isFocused())return false;
        if(key==257||key==335||key==256){flush();channels.setFocused(false);return key!=256;}return false;
    }
    public void renderLabels(GuiGraphics graphics,Font font){graphics.drawString(font,"Transmit channels",x,y+49,0xDAE8F0,false);}
}
