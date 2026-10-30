package com.vandorlabs.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;

/** Restore the 2D GUI state after GuiContainer's item pass, before text and fields. */
final class DialogOverlay {
    private DialogOverlay(){}
    static void prepare(){
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1,1,1,1);
    }
}
