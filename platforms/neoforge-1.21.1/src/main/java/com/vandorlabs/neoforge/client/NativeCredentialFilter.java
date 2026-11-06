package com.vandorlabs.neoforge.client;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Inventory-backed credential selector shared by door and token-slot dialogs. */
public interface NativeCredentialFilter {
    boolean click(double x,double y,int button,int left,int top,int mask,IntConsumer selected);
    void render(GuiGraphics graphics,Font font,int left,int top,int mask);
}
