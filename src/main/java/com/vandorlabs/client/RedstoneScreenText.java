package com.vandorlabs.client;

import net.minecraft.client.gui.FontRenderer;

/** Text budgets leave room for the screen border and the row status indicator. */
final class RedstoneScreenText {
    static final int LABEL_WIDTH=86,TITLE_WIDTH=112;
    static boolean fits(FontRenderer font,String text,int width){return font.getStringWidth(text.trim())<=width;}
    private RedstoneScreenText(){}
}
