package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.GuiTextField;

/** Shared editing rules for channel lists in programmable dialogs. */
final class ChannelFields {
    static void configure(GuiTextField field) {
        field.setMaxStringLength(ChannelList.MAX_TEXT_LENGTH);
        field.setValidator(text->text.matches("[0-9, ]*"));
    }
    static ChannelList parse(GuiTextField field){
        ChannelList list=ChannelList.parse(field.getText());
        field.setTextColor(list==null?0xFF7777:0xE0E0E0);return list;
    }
    static int first(GuiTextField field){ChannelList list=parse(field);return list==null?-1:list.first();}
    private ChannelFields(){}
}
