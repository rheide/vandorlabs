package com.vandorlabs.client;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Actual shared picker textbox input and catalog rows, in an isolated client fixture. */
final class TextureSearchLiveChecks {
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    @SuppressWarnings("unchecked") static void type(GuiProgrammableWall gui)throws Exception{
        HousingTextureList picker=(HousingTextureList)field(gui,"textureList");GuiTextField search=(GuiTextField)field(picker,"search");
        if(search==null)throw new IllegalStateException("Search widget was not rendered");picker.searchClick(search.x+4,search.y+8,0);
        char[] text={'v','a','n','d'};int[] keys={Keyboard.KEY_V,Keyboard.KEY_A,Keyboard.KEY_N,Keyboard.KEY_D};for(int i=0;i<text.length;i++)picker.searchKey(text[i],keys[i]);
        if(!search.getText().equals("vand")||!field(picker,"query").equals("vand"))throw new IllegalStateException("Real picker textbox did not accept vand: "+search.getText());
        Map<Integer,HousingTextureList.Option> options=(Map<Integer,HousingTextureList.Option>)field(picker,"options");List<Integer> expected=new ArrayList<>(),actual=new ArrayList<>();
        for(HousingTextureList.Option option:options.values()){String path=option.texture.substring(option.texture.indexOf(':')+1);if((option.label+" "+option.category+" "+path).toLowerCase(Locale.ROOT).contains("vand"))expected.add(option.choice);}
        for(int id:(List<Integer>)field(picker,"rows"))if(options.containsKey(id))actual.add(id);
        if(expected.isEmpty()||expected.size()>=options.size()||!actual.equals(expected))throw new IllegalStateException("Search namespace leaked into actual catalog: actual="+actual.size()+" expected="+expected.size()+" total="+options.size());
        System.out.println("[vandorlabs][reprolab] texture-search-live PASS typed=vand matches="+actual.size()+" catalog="+options.size()+" real textbox and filtered catalog rows");
    }
    static void clear(GuiProgrammableWall gui)throws Exception{HousingTextureList picker=(HousingTextureList)field(gui,"textureList");for(int i=0;i<4;i++)picker.searchKey('\b',Keyboard.KEY_BACK);}
}
