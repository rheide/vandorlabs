package com.vandorlabs.client;

import com.vandorlabs.coupler.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.segmented.*;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.*;

/** Actual legacy dialog fields on the built-JAR client, independent of runtime ownership checks. */
@SideOnly(Side.CLIENT)
final class ChannelInputClientChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Channel input client: "+why);}
 private static void bank(Object gui,Class<?> type,String field,String text)throws Exception{Field f=type.getDeclaredField(field);f.setAccessible(true);GuiTextField value=(GuiTextField)f.get(gui);value.setText(text);need(value.getText().equals(text)&&ChannelList.parse(value.getText()).size()==64,"all64maximum-width channel IDs survive "+type.getSimpleName());value.setText("67002");need(ChannelList.parse(value.getText()).first()==67002,"high input survives "+type.getSimpleName());value.setText("2147483647");need(ChannelList.parse(value.getText()).first()==Integer.MAX_VALUE,"maximum input survives "+type.getSimpleName());}
 static void run(Minecraft mc,File output)throws Exception{checks=0;int[] ids=new int[64];for(int i=0;i<64;i++)ids[i]=Integer.MAX_VALUE-63+i;String text=ChannelList.of(ids).toString();net.minecraft.inventory.Container old=mc.player.openContainer;try{
  GuiCoupler coupler=new GuiCoupler(new ContainerCoupler(new TileCoupler()));coupler.inventorySlots.windowId=-92;coupler.setWorldAndResolution(mc,460,340);bank(coupler,GuiCoupler.class,"channels",text);
  GuiSegmentedDoor segmented=new GuiSegmentedDoor(new SegmentedDoorContainer(new SegmentedDoorTile()));segmented.inventorySlots.windowId=-93;segmented.setWorldAndResolution(mc,640,360);bank(segmented,GuiSegmentedDoor.class,"channels",text);
  com.vandorlabs.copier.client.CopyClientChecks.channelInputFocus(mc,output);
 }finally{mc.player.openContainer=old;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}System.out.println("[vandorlabs][reprolab] channel-input-dialogs PASS checks="+checks+" SourceCoupler/Segmented/CopyController/full64bank/maxInt");}
}
