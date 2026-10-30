package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Route text before container hotkeys; only pickers drawn on the current page participate. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class TextureSearchInput {
    private static GuiScreen screen;
    private static Boolean repeatBefore;
    private static final List<HousingTextureList> visible=new ArrayList<>();
    static void shown(HousingTextureList picker){if(repeatBefore==null)repeatBefore=Keyboard.areRepeatEventsEnabled();Keyboard.enableRepeatEvents(true);if(!visible.contains(picker))visible.add(picker);}
    @SubscribeEvent public static void draw(GuiScreenEvent.DrawScreenEvent.Pre event){screen=event.getGui();visible.clear();}
    @SubscribeEvent public static void open(net.minecraftforge.client.event.GuiOpenEvent event){screen=null;visible.clear();if(repeatBefore!=null){Keyboard.enableRepeatEvents(repeatBefore);repeatBefore=null;}}
    @SubscribeEvent public static void init(GuiScreenEvent.InitGuiEvent.Pre event){screen=null;visible.clear();}
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST) public static void key(GuiScreenEvent.KeyboardInputEvent.Pre event){
        if(screen!=event.getGui() || !Keyboard.getEventKeyState())return;
        for(HousingTextureList picker:visible)if(picker.searchKey(Keyboard.getEventCharacter(),Keyboard.getEventKey())){event.setCanceled(true);return;}
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST) public static void mouse(GuiScreenEvent.MouseInputEvent.Pre event){
        if(screen!=event.getGui() || Mouse.getEventButton()<0 || !Mouse.getEventButtonState())return;
        Minecraft mc=Minecraft.getMinecraft();GuiScreen gui=event.getGui();
        int x=Mouse.getEventX()*gui.width/mc.displayWidth,y=gui.height-Mouse.getEventY()*gui.height/mc.displayHeight-1;
        boolean consumed=false;for(HousingTextureList picker:visible)consumed|=picker.searchClick(x,y,Mouse.getEventButton());
        if(consumed)event.setCanceled(true);
    }
}
