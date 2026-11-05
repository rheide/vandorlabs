package com.vandorlabs.client;

import java.lang.reflect.*;
import java.util.*;

/** Actual legacy picker adapters retain negative artwork rows, host actions and mutable thumbnails. */
public final class MaterialPickerRowsChecks {
    private MaterialPickerRowsChecks() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Field field(String name)throws Exception{Field f=HousingTextureList.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void rebuild(HousingTextureList picker)throws Exception{Method m=HousingTextureList.class.getDeclaredMethod("rebuild");m.setAccessible(true);m.invoke(picker);}
    @SuppressWarnings("unchecked") private static List<Integer> rows(HousingTextureList picker)throws Exception{return (List<Integer>)field("rows").get(picker);}
    public static void main(String[] ignored)throws Exception{
        List<HousingTextureList.Option> entries=Arrays.asList(new HousingTextureList.Option(11,"blue plate","Bridge/Sub","tex/zeta"),new HousingTextureList.Option(-1,"Original artwork","Frame","frame/original"),new HousingTextureList.Option(7,"Alpha","Bridge","tex/red"),new HousingTextureList.Option(2,"alpha","Bridge","tex/blue"),new HousingTextureList.Option(23,"Pilot","Bridge/Sub","tex/green"));
        HousingTextureList picker=new HousingTextureList(0,0,180,-1,8,entries).visibleRows(3);
        require(picker.selected()==-1&&rows(picker).contains(-1),"Original artwork -1 lost as a native material option");
        field("scroll").setInt(picker,rows(picker).indexOf(-1));Method visible=HousingTextureList.class.getDeclaredMethod("visibleChoice",int.class);visible.setAccessible(true);
        require(((Integer)visible.invoke(picker,0))==-200002,"Negative Original artwork must pin its category heading");
        picker.custom(value->{}).redstone(()->{}).vehicle(()->{}).chip(()->{});
        require(rows(picker).subList(rows(picker).size()-4,rows(picker).size()).equals(Arrays.asList(-100000,-100001,-100002,-100003)),"Adapter action rows changed order");
        field("query").set(picker,"bridge");rebuild(picker);
        require(rows(picker).equals(Arrays.asList(11,7,2,23,-100000,-100001,-100002,-100003)),"Search no longer follows original options order with actions last");
        picker.thumbnail(11,"tex/changed");field("query").set(picker,"changed");rebuild(picker);
        require(rows(picker).equals(Arrays.asList(11,-100000,-100001,-100002,-100003)),"Replacement thumbnail search text was cached stale");
        field("query").set(picker,"");picker.setSelected(11);
        @SuppressWarnings("unchecked") Set<String> expanded=(Set<String>)field("expanded").get(picker);expanded.remove("Bridge");rebuild(picker);
        require(!rows(picker).contains(11)&&picker.selected()==11,"Collapsed ancestry changed selection or left child visible");
        HousingTextureList namespaced=new HousingTextureList(0,0,180,1,8,Arrays.asList(
                new HousingTextureList.Option(1,"Blue plate","Materials","vandorlabs:blocks/blue_plate"),
                new HousingTextureList.Option(2,"Vandor panel","Materials","vandorlabs:blocks/panel"),
                new HousingTextureList.Option(3,"Logo","Materials","vandorlabs:blocks/vandor_logo")));
        field("query").set(namespaced,"vand");rebuild(namespaced);
        require(rows(namespaced).equals(Arrays.asList(2,3)),"vand must match branded names/paths, not the common namespace");
        field("query").set(namespaced,"vandorlabs:");rebuild(namespaced);
        require(rows(namespaced).equals(Arrays.asList(1,2,3)),"Explicit namespace filter remains supported");
        System.out.println("Legacy material picker rows PASS Original artwork -1, pinned category, input-order search, mutable thumbnails, host action tails, retained collapsed selection");
    }
}
