package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.redstone.*;
import com.vandorlabs.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;
import java.util.*;

/** Shared inline module slot, original-window editor and native hotbar interaction. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID,value=Side.CLIENT)
public final class InlineTransmitterWidget {
    private static final java.lang.reflect.Field LEFT=ReflectionHelper.findField(GuiContainer.class,"guiLeft","field_147003_i"),TOP=ReflectionHelper.findField(GuiContainer.class,"guiTop","field_147009_r"),HEIGHT=ReflectionHelper.findField(GuiContainer.class,"ySize","field_147000_g"),BUTTONS=ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
    private static final Map<GuiContainer,State> STATES=new WeakHashMap<>();
    private static final class Placement {final int x,width;final InlineTransmitters.ModuleSlot slot;Placement(GuiButton button,InlineTransmitters.ModuleSlot slot){this(button.x,button.width,slot);}Placement(int x,int width,InlineTransmitters.ModuleSlot slot){this.x=x;this.width=width;this.slot=slot;}}
    private static final class State {final Map<GuiButton,Placement> buttons=new WeakHashMap<>();}
    private InlineTransmitterWidget(){}
    private static int value(java.lang.reflect.Field field,Object instance){try{return field.getInt(instance);}catch(IllegalAccessException badForge){throw new IllegalStateException(badForge);}}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui){try{return (List<GuiButton>)BUTTONS.get(gui);}catch(IllegalAccessException badForge){throw new IllegalStateException(badForge);}}
    private static State layout(GuiContainer gui){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null)return null;
        InlineTransmitters.attach(gui.inventorySlots,mc.player);InlineTransmitters.Binding binding=InlineTransmitters.binding(gui.inventorySlots);if(binding==null)return null;
        binding.hide();State state=STATES.computeIfAbsent(gui,g->new State());int selector=gui instanceof GuiRedstoneScreen?((GuiRedstoneScreen)gui).inlineTransmitterSelector():gui instanceof GuiCockpit?((GuiCockpit)gui).inlineTransmitterSelector():0;
        int left=value(LEFT,gui),top=value(TOP,gui),height=value(HEIGHT,gui);boolean shown=false;
        for(GuiButton button:buttons(gui)){
            Placement placement=state.buttons.get(button);boolean trigger=placement!=null||button.displayString.contains("Transmitter");
            if(!button.visible||!trigger)continue;InlineTransmitters.ModuleSlot slot=binding.modules.get(selector);if(slot==null)continue;
            if(placement==null||placement.slot!=slot){placement=placement==null?new Placement(button,slot):new Placement(placement.x,placement.width,slot);state.buttons.put(button,placement);}
            slot.xPos=placement.x-left;slot.yPos=button.y-top+(button.height-16)/2;slot.visible=true;
            button.x=placement.x+24;button.width=Math.max(36,placement.width-24);button.displayString="Channels...";button.enabled=slot.getHasStack()&&mc.player.inventory.getItemStack().isEmpty();shown=true;
        }
        if(shown)for(int i=0;i<binding.hotbar.size();i++){InlineTransmitters.VisibleSlot slot=binding.hotbar.get(i);slot.xPos=12+i*18;slot.yPos=height-50;slot.visible=true;}
        return state;
    }
    @SubscribeEvent public static void beforeDraw(GuiScreenEvent.DrawScreenEvent.Pre event){if(event.getGui() instanceof GuiContainer)layout((GuiContainer)event.getGui());}
    @SubscribeEvent public static void action(GuiScreenEvent.ActionPerformedEvent.Pre event){if(!(event.getGui() instanceof GuiContainer))return;GuiContainer gui=(GuiContainer)event.getGui();State state=layout(gui);if(state==null)return;Placement placement=state.buttons.get(event.getButton());if(placement==null||!placement.slot.visible)return;event.setCanceled(true);Minecraft mc=Minecraft.getMinecraft();ItemStack stack=placement.slot.getStack();if(!stack.isEmpty()&&mc.player.inventory.getItemStack().isEmpty()){mc.currentScreen=null;mc.displayGuiScreen(new GuiHostedTransmitterEditor(gui,placement.slot.selector,stack));}}
    @SubscribeEvent public static void afterDraw(GuiScreenEvent.DrawScreenEvent.Post event){if(!(event.getGui() instanceof GuiContainer))return;GuiContainer gui=(GuiContainer)event.getGui();InlineTransmitters.Binding binding=InlineTransmitters.binding(gui.inventorySlots);if(binding==null)return;int left=value(LEFT,gui),top=value(TOP,gui);List<InlineTransmitters.VisibleSlot> slots=new ArrayList<>(binding.modules.values());slots.addAll(binding.hotbar);Minecraft mc=Minecraft.getMinecraft();
        for(InlineTransmitters.VisibleSlot slot:slots)if(slot.visible){int x=left+slot.xPos,y=top+slot.yPos;Gui.drawRect(x-1,y-1,x+17,y,0xff8296a2);Gui.drawRect(x-1,y+16,x+17,y+17,0xff8296a2);Gui.drawRect(x-1,y,x,y+16,0xff8296a2);Gui.drawRect(x+16,y,x+17,y+16,0xff8296a2);if(event.getMouseX()>=x&&event.getMouseX()<x+16&&event.getMouseY()>=y&&event.getMouseY()<y+16&&slot.getHasStack()&&mc.player.inventory.getItemStack().isEmpty())net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(slot.getStack().getTooltip(mc.player,mc.gameSettings.advancedItemTooltips?net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED:net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL),event.getMouseX(),event.getMouseY(),gui.width,gui.height,-1,mc.fontRenderer);}
    }
}
