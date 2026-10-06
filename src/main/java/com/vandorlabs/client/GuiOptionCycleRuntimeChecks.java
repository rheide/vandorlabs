package com.vandorlabs.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Exercise option buttons through the actual mouse handler, including reverse wraparound. */
final class GuiOptionCycleRuntimeChecks {
    static void check(Minecraft mc) {
        GuiScreen gui=mc.currentScreen;
        int[] ids;
        if(gui instanceof GuiProgrammableLight)ids=new int[]{101,102,103,104};
        else if(gui instanceof GuiSpaceDoor)ids=new int[]{10,11,12,13,15,16,17,18};
        else if(gui instanceof GuiProgrammableTrapdoor)ids=new int[]{1,3,6,9};
        else if(gui instanceof GuiProgrammableWall)ids=new int[]{101,103,104};
        else if(gui instanceof GuiProgrammableTrigger)ids=new int[]{101,102};
        else if(gui instanceof GuiRedstoneChannel)ids=new int[]{2,3,4,5,6,7,8};
        else if(gui instanceof GuiRedstoneScreen)ids=new int[]{7,8,9};
        else if(gui instanceof GuiAnimatedScreenSelector || gui instanceof GuiProgrammableInput || gui instanceof GuiProgrammableHalfConsole)ids=new int[]{0,4,32};
        else if(gui instanceof GuiConnectedSeat)ids=new int[]{1};
        else if(gui instanceof GuiLandingGear)ids=new int[]{0,4};
        else if(gui instanceof GuiProgrammableChair)ids=new int[]{11};
        else if(gui instanceof GuiProgrammableGlass)ids=new int[]{0,1};
        else if(gui instanceof GuiRampController)ids=new int[]{4,19};
        else if(gui instanceof GuiDuplifier)ids=java.util.stream.IntStream.concat(java.util.stream.IntStream.of(98),java.util.stream.IntStream.range(100,100+com.vandorlabs.items.DuplifierApplyOptions.OPTIONS.length)).toArray();
        else return;
        try {
            Method mouse;
            try{mouse=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}
            catch(NoSuchMethodException e){mouse=GuiScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}
            mouse.setAccessible(true);
            // Keep assembly membership intact: geometry/join changes can detach fixture cells.
            for(int id:ids) {
                GuiButton button=find(gui,id);
                if(button==null || !button.visible || !button.enabled)continue;
                String initial=button.displayString;
                // Both orders must undo one another. Multi-valued cycles expose forward-only right clicks.
                click(mouse,gui,button,1);click(mouse,gui,find(gui,id),0);
                require(initial.equals(find(gui,id).displayString),"reverse then forward failed for "+id);
                click(mouse,gui,find(gui,id),0);click(mouse,gui,find(gui,id),1);
                require(initial.equals(find(gui,id).displayString),"forward then reverse failed for "+id);
                require(mc.currentScreen==gui,"option click closed the dialog");
            }
            for(GuiButton button:new ArrayList<>(buttons(gui)))if(button.visible && button.enabled && "Done".equals(button.displayString)) {
                click(mouse,gui,button,1);require(mc.currentScreen==gui,"right-click activated Done");
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException("option mouse-path check failed",e);}
        System.out.println("[vandorlabs][reprolab] option-reverse-cycle PASS "+gui.getClass().getSimpleName());
    }
    private static List<GuiButton> buttons(GuiScreen gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static GuiButton find(GuiScreen gui,int id){for(GuiButton b:buttons(gui))if(b.id==id)return b;return null;}
    private static void click(Method mouse,GuiScreen gui,GuiButton b,int direction)throws ReflectiveOperationException {
        require(b!=null && b.enabled && b.visible,"cycle button disappeared");
        mouse.invoke(gui,b.x+b.width/2,b.y+b.height/2,direction);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Option cycle: "+message);}
}
