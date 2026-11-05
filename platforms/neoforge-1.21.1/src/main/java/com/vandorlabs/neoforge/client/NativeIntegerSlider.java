package com.vandorlabs.neoforge.client;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Integer slider shared by range and level editors, with mouse-only input and caller-owned publication. */
public final class NativeIntegerSlider extends AbstractSliderButton {
    private final int minimum,maximum;
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
    @Override public boolean keyPressed(int key,int scan,int modifiers){return false;}
}
