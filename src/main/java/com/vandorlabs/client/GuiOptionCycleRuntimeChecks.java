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
        else if(gui instanceof GuiWirelessRedstone)ids=new int[]{1,2};
        else if(gui instanceof GuiTokenSlot)ids=new int[]{1};
        else if(gui instanceof GuiCockpit)ids=new int[]{0,1,10,11,12,30,31,32,40};
        else if(gui instanceof GuiSpaceDoor)ids=new int[]{10,11,12,13,15,16,17,18,19};
        else if(gui instanceof GuiProgrammableTrapdoor)ids=new int[]{1,3,6,9};
        else if(gui instanceof GuiProgrammableWall)ids=new int[]{101,103,104,113,114,115,116};
        else if(gui instanceof GuiProgrammableTrigger)ids=new int[]{101,102};
        else if(gui instanceof GuiRedstoneChannel)ids=new int[]{2,3,4,5,6,7,8,9,10,11,12,13,15,16,18,19};
        else if(gui instanceof GuiLargeCanopy)ids=new int[]{2,3,4,5,6,7,11,12};
        else if(gui instanceof GuiAutopilot)ids=new int[]{0,3,4,5,6};
        else if(gui!=null&&gui.getClass().getName().endsWith("VehicleClient$Preview"))ids=find(gui,5)!=null?new int[]{2,5}:new int[]{4};
        else if(gui instanceof GuiRedstoneScreen)ids=new int[]{7,8,9};
        else if(gui instanceof GuiAnimatedScreenSelector || gui instanceof GuiProgrammableInput || gui instanceof GuiProgrammableHalfConsole)ids=new int[]{0,4,32};
        else if(gui instanceof GuiConnectedSeat)ids=new int[]{1};
        else if(gui instanceof GuiModularGear)ids=new int[]{0,1,2};
        else if(gui instanceof GuiTurntable)ids=new int[]{0,2,3,4};
        else if(gui instanceof GuiStorageStatus)ids=new int[]{1,2};
        else if(gui instanceof GuiLandingGear)ids=new int[]{0,4};
        else if(gui instanceof GuiProgrammableChair)ids=new int[]{11};
        else if(gui instanceof GuiProgrammableGlass)ids=new int[]{0,3,4,5,6};
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
                if(gui instanceof GuiTokenSlot&&id==1){GuiTokenSlot mount=(GuiTokenSlot)gui;int start=mount.behaviorDraft();click(mouse,gui,button,1);require(mount.behaviorDraft()==Math.floorMod(start-1,4),"mount right-click selects previous behavior");mount.updateScreen();require(mount.behaviorDraft()==Math.floorMod(start-1,4),"mount draft survives screen refresh");click(mouse,gui,find(gui,id),0);}
                if(gui instanceof GuiRedstoneChannel && id==19){int start=((GuiRedstoneChannel)gui).pilotHeightDraft();click(mouse,gui,button,1);require(((GuiRedstoneChannel)gui).pilotHeightDraft()==Math.floorMod(start-1,4),"Pilot Seat height right-click selects previous with wraparound");click(mouse,gui,find(gui,id),0);require(((GuiRedstoneChannel)gui).pilotHeightDraft()==start,"Pilot Seat height restores after forward click");}
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
    static void checkNewDialogs(Minecraft mc){
        com.vandorlabs.tiles.TileEntityAutopilot autopilot=new com.vandorlabs.tiles.TileEntityAutopilot();autopilot.setWorld(mc.world);autopilot.setPos(net.minecraft.util.math.BlockPos.ORIGIN);mc.displayGuiScreen(new GuiAutopilot(autopilot));check(mc);
        com.vandorlabs.tiles.TileEntityWaypoint waypoint=new com.vandorlabs.tiles.TileEntityWaypoint();waypoint.setWorld(mc.world);waypoint.setPos(net.minecraft.util.math.BlockPos.ORIGIN);mc.displayGuiScreen(new GuiAutopilot(waypoint));check(mc);mc.displayGuiScreen(null);
        System.out.println("[vandorlabs][reprolab] autopilot-waypoint-reverse-cycle PASS real-mouse trigger stop-mode departure-mode auto-departure direct-signal");
    }
    static void checkMoatMode(Minecraft mc)throws ReflectiveOperationException{
        com.vandorlabs.tiles.TileEntityRampController controller=new com.vandorlabs.tiles.TileEntityRampController();controller.setWorld(mc.world);controller.setPos(net.minecraft.util.math.BlockPos.ORIGIN);
        GuiRampController gui=new GuiRampController(controller);mc.displayGuiScreen(gui);
        Method mouse;try{mouse=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}catch(NoSuchMethodException e){mouse=GuiScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}mouse.setAccessible(true);
        String initial=find(gui,1).displayString;click(mouse,gui,find(gui,1),1);require(find(gui,1).displayString.equals("Mode: Moat"),"normal ramp reverses into Moat");
        require(find(gui,19).enabled,"Moat exposes ordinary Ramp interpolation profiles");
        String interpolation=find(gui,19).displayString;
        for(int step=0;step<3;step++)click(mouse,gui,find(gui,19),0);
        require(interpolation.equals(find(gui,19).displayString),"Moat cycles all three interpolation profiles");
        click(mouse,gui,find(gui,19),1);require(!interpolation.equals(find(gui,19).displayString),"Moat interpolation supports reverse selection");
        click(mouse,gui,find(gui,19),0);require(interpolation.equals(find(gui,19).displayString),"Moat interpolation reverse and forward restore the profile");
        click(mouse,gui,find(gui,1),0);require(initial.equals(find(gui,1).displayString),"Moat advances into normal ramp");
        for(int step=0;step<7;step++)click(mouse,gui,find(gui,1),0);require(initial.equals(find(gui,1).displayString),"seven forward modes wrap");
        for(int step=0;step<7;step++)click(mouse,gui,find(gui,1),1);require(initial.equals(find(gui,1).displayString),"seven reverse modes wrap");
        mc.displayGuiScreen(null);System.out.println("[vandorlabs][reprolab] moat-mode-reverse-cycle PASS actual-mouse seven-mode wrap");
    }
    private static List<GuiButton> buttons(GuiScreen gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static GuiButton find(GuiScreen gui,int id){for(GuiButton b:buttons(gui))if(b.id==id)return b;return null;}
    private static void click(Method mouse,GuiScreen gui,GuiButton b,int direction)throws ReflectiveOperationException {
        require(b!=null && b.enabled && b.visible,"cycle button disappeared");
        mouse.invoke(gui,b.x+b.width/2,b.y+b.height/2,direction);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Option cycle: "+message);}
}
