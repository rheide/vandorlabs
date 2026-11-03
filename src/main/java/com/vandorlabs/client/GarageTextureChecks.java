package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import java.lang.reflect.Field;
import java.util.*;

final class GarageTextureChecks {
    static int selected;
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void choose(Minecraft mc,GuiGarageDoor gui)throws Exception{
        gui.actionPerformed(new GuiButton(92,0,0,"Textures"));HousingTextureList list=(HousingTextureList)field(gui,"textures");
        @SuppressWarnings("unchecked") Map<String,?> groups=(Map<String,?>)field(list,"groups");for(String category:new String[]{"Industrial","Trapdoors","Doors","Double Doors"})if(!groups.containsKey(category))throw new IllegalStateException("Sectional Door picker missing "+category);
        selected=-1;for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("sectional_door_panel_light".equals(ScreenHousingTextures.IDS[i]))selected=i;if(selected<0)throw new IllegalStateException("light panel missing");
        list.setSelected(selected);@SuppressWarnings("unchecked") List<Integer> rows=(List<Integer>)field(list,"rows");int row=rows.indexOf(selected)-(Integer)field(list,"scroll");gui.mouseClicked((Integer)field(list,"x")+30,(Integer)field(list,"y")+row*HousingTextureList.ROW_HEIGHT+8,0);
        if(!list.picked()||list.selected()!=selected)throw new IllegalStateException("light panel selection failed");
    }
    private GarageTextureChecks(){}
}
