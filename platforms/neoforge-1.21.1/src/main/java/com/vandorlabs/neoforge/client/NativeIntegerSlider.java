package com.vandorlabs.neoforge.client;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Integer slider shared by range and level editors, with mouse-only input and caller-owned publication. */
public final class NativeIntegerSlider extends AbstractSliderButton {
    private final int minimum,maximum;
    private boolean draggingValue;
    private final IntFunction<Component> label;
    private final IntConsumer changed;
    public NativeIntegerSlider(int x,int y,int width,int height,int minimum,int maximum,int initial,IntFunction<Component> label,IntConsumer changed){
        super(x,y,width,height,Component.empty(),normalized(minimum,maximum,initial));
        this.minimum=minimum;this.maximum=maximum;this.label=label;this.changed=changed;updateMessage();
    }
    private static double normalized(int minimum,int maximum,int initial){if(maximum<minimum)throw new IllegalArgumentException("Invalid slider bounds");int selected=Math.max(minimum,Math.min(maximum,initial));return maximum==minimum?0:(selected-minimum)/(double)(maximum-minimum);}
    public int getIntValue(){return Math.max(minimum,Math.min(maximum,minimum+(int)Math.round(value*(maximum-minimum))));}
    @Override protected void updateMessage(){if(label!=null)setMessage(label.apply(getIntValue()));}
    @Override protected void applyValue(){int selected=getIntValue();value=maximum==minimum?0:(selected-minimum)/(double)(maximum-minimum);updateMessage();changed.accept(selected);}
    @Override public boolean mouseClicked(double x,double y,int button){boolean handled=super.mouseClicked(x,y,button);if(button==0)draggingValue=handled;return handled;}
    @Override public boolean mouseReleased(double x,double y,int button){if(!draggingValue)return false;draggingValue=false;return super.mouseReleased(x,y,button);}
    /** Container screens handle inventory dragging themselves, so focused sliders need explicit routing. */
    public static boolean dragFocused(net.minecraft.client.gui.components.events.GuiEventListener focused,double x,double y,int button,double dx,double dy){return focused instanceof NativeIntegerSlider slider&&slider.draggingValue&&slider.active&&slider.visible&&button==0&&slider.mouseDragged(x,y,button,dx,dy);}
    public static void releaseFocused(net.minecraft.client.gui.components.events.GuiEventListener focused,double x,double y,int button){if(focused instanceof NativeIntegerSlider slider)slider.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return false;}
}
