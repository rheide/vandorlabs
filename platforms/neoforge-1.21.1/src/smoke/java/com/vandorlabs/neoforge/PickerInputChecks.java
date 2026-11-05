package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Actual EditBox/container shortcut dispatch without a world or unrelated gameplay fixtures. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class PickerInputChecks {
    private static boolean done;private static int ticks,checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError("Native picker input: "+message);}
    private static final class Menu extends AbstractContainerMenu{Menu(){super(null,0);}@Override public boolean stillValid(Player player){return true;}@Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}}
    private static final class TestScreen extends AbstractContainerScreen<Menu>{
        final boolean guard;NativeMaterialPicker picker;int closed;
        TestScreen(boolean guard){super(new Menu(),new Inventory(null),Component.literal("Focused picker input"));this.guard=guard;}
        @Override protected void init(){super.init();picker=new NativeMaterialPicker(0,id->ModernFinishCatalog.option(id).general(),ignored->{});picker.layout(20,40,220,8,false);addRenderableWidget(picker.searchField());}
        @Override public boolean keyPressed(int key,int scan,int modifiers){return guard&&picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
        @Override public void onClose(){closed++;}
        @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        if(done||!Boolean.getBoolean("vandorlabs.pickerInputChecks"))return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>12000)throw new AssertionError("Picker input startup timeout");if(ticks<80||mc.getOverlay()!=null)return;
        int inventory=mc.options.keyInventory.getKey().getValue();require(inventory==GLFW.GLFW_KEY_E,"Deterministic default inventory binding");var old=new TestScreen(false);mc.setScreen(old);var field=old.picker.searchField();require(old.mouseClicked(field.getX()+4,field.getY()+4,0)&&field.isFocused(),"Real focus click");old.keyPressed(inventory,0,0);require(old.closed==1,"Negative control: unguarded AbstractContainerScreen inventory key closes focused EditBox");
        var screen=new TestScreen(true);mc.setScreen(screen);field=screen.picker.searchField();screen.mouseClicked(field.getX()+4,field.getY()+4,0);for(char ch:"steel".toCharArray()){int key=GLFW.GLFW_KEY_A+Character.toUpperCase(ch)-'A';require(screen.keyPressed(key,0,0),"Focused key consumed before inventory/hotbar handling");require(screen.charTyped(ch,0),"Character typed into actual EditBox");}require(screen.closed==0&&field.getValue().equals("steel"),"Inventory letter preserved in query and screen remains open");require(screen.picker.rows().size()>0,"Real filter rows available");require(screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0)&&screen.closed==0,"Focused Enter does not close");screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0);require(field.getValue().equals("stee"),"Backspace edit once");field.setValue("");screen.charTyped('e',0);require(screen.closed==0&&field.getValue().equals("e"),"Repeated inventory letter");screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);require(screen.closed==1,"Escape retains container close");field.setFocused(false);screen.keyPressed(inventory,0,0);require(screen.closed==2,"Unfocused inventory shortcut unchanged");field.setFocused(true);field.active=false;screen.keyPressed(inventory,0,0);require(screen.closed==3,"Disabled search does not consume shortcut");done=true;System.out.println("[vandorlabs][picker-input] PASS checks="+checks+" actual EditBox/container key/char dispatch; title-screen development fixture only");mc.stop();
    }
}
