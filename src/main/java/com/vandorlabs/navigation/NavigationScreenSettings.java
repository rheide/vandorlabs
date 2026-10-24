package com.vandorlabs.navigation;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.nbt.NBTTagCompound;

/** Shared, immutable display configuration; reader inventory remains on its one owner. */
public final class NavigationScreenSettings {
    public static final NavigationScreenSettings DEFAULT=new NavigationScreenSettings(-1,true,1,false,false);
    public final int texture,speed;
    public final boolean animated,redstone,security;
    public NavigationScreenSettings(int texture,boolean animated,int speed,boolean redstone,boolean security){
        if(!validTexture(texture)||speed<0||speed>2)throw new IllegalArgumentException("Invalid navigation display settings");
        this.texture=texture;this.animated=animated;this.speed=speed;this.redstone=redstone;this.security=security;
    }
    public static boolean validTexture(int choice){
        if(choice==-1)return true;
        if(!ScreenHousingTextures.validChoice(choice))return false;
        if(ScreenHousingTextures.lightOnly(choice))return false;
        com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
        if(entry!=null&&entry.has("detail")&&entry.get("detail").getAsInt()>0)return false;
        String category=ScreenHousingTextures.category(choice);
        if(category.equals("Doors")||category.equals("Double Doors")||category.equals("Lights"))return false;
        return !category.equals("Screens")||entry!=null&&!entry.get("source").getAsString().startsWith("console_inputs/");
    }
    public void write(NBTTagCompound tag){
        tag.setInteger("NavigationTexture",texture);tag.setBoolean("NavigationAnimated",animated);tag.setInteger("NavigationSpeed",speed);
        tag.setBoolean("NavigationRedstone",redstone);tag.setBoolean("NavigationSecurity",security);
    }
    public static NavigationScreenSettings read(NBTTagCompound tag){
        int texture=tag.hasKey("NavigationTexture",3)?tag.getInteger("NavigationTexture"):-1;
        return new NavigationScreenSettings(validTexture(texture)?texture:-1,!tag.hasKey("NavigationAnimated")||tag.getBoolean("NavigationAnimated"),
                Math.max(0,Math.min(2,tag.hasKey("NavigationSpeed",3)?tag.getInteger("NavigationSpeed"):1)),tag.getBoolean("NavigationRedstone"),tag.getBoolean("NavigationSecurity"));
    }
}
