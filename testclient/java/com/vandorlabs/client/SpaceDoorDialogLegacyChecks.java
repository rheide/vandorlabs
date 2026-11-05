package com.vandorlabs.client;

import com.vandorlabs.render.SpaceDoorMotion;
import java.lang.reflect.*;
import net.minecraft.client.gui.GuiButton;

/** Actual current dialog label/enabled-state baseline without a Minecraft window. */
public final class SpaceDoorDialogLegacyChecks {
    private static void set(Object screen,String name,Object value)throws Exception{Field field=GuiSpaceDoor.class.getDeclaredField(name);field.setAccessible(true);field.set(screen,value);}
    private static Object call(Object screen,String name)throws Exception{Method method=GuiSpaceDoor.class.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(screen);}
    public static void main(String[] args)throws Exception{
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);GuiSpaceDoor screen=(GuiSpaceDoor)unsafe.allocateInstance(GuiSpaceDoor.class);
        java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
        for(int design=0;design<30;design++)for(int depth=0;depth<3;depth++)for(int panel=0;panel<3;panel++)for(SpaceDoorMotion motion:SpaceDoorMotion.values())for(boolean hinges:new boolean[]{false,true}){
            set(screen,"design",design);set(screen,"depth",depth);set(screen,"panelMode",panel);set(screen,"motion",motion);set(screen,"hinges",hinges);GuiButton button=new GuiButton(15,7,11,"unused");set(screen,"hingeButton",button);call(screen,"updateHingeButton");
            String record=call(screen,"glassDesign")+"/"+call(screen,"panelLabel")+"/"+call(screen,"depthLabel")+"/"+button.enabled+"/"+button.displayString+";";hash.update(record.getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;
        }
        StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format("%02x",b&255));if(!text.toString().equals("d97d5e58bdfaab1f046a64c0653a446005ce5d98fbc7d3872ac248b3b5f3a019"))throw new AssertionError("Actual door dialog changed "+text);System.out.println("Actual SpaceDoor dialog PASS cases="+cases+" golden="+text);
    }
}
