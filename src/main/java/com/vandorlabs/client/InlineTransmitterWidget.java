package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.items.ItemRedstoneTransmitter;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.redstone.*;
import com.vandorlabs.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.*;

/** Real module/hotbar slots and channel editing stay in the authenticated block window. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID,value=Side.CLIENT)
public final class InlineTransmitterWidget {
    public static final int HEIGHT=44;
    private static final String CAPTION="Transmit channels (comma-separated)";
    private static final java.lang.reflect.Field LEFT=ReflectionHelper.findField(GuiContainer.class,"guiLeft","field_147003_i"),TOP=ReflectionHelper.findField(GuiContainer.class,"guiTop","field_147009_r"),GUI_HEIGHT=ReflectionHelper.findField(GuiContainer.class,"ySize","field_147000_g"),BUTTONS=ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
    private static final Map<GuiContainer,State> STATES=new WeakHashMap<>();
    private static final class Placement {final InlineTransmitters.ModuleSlot slot;final GuiButton anchor;final GuiTextField field;ItemStack before=ItemStack.EMPTY;boolean dirty,shown,pending;int x,y,width;
        Placement(GuiButton anchor,InlineTransmitters.ModuleSlot slot){this.anchor=anchor;this.slot=slot;field=new GuiTextField(1980,Minecraft.getMinecraft().fontRenderer,0,0,100,18);ChannelFields.configure(field);}}
    private static final class State {final Map<GuiButton,Placement> buttons=new WeakHashMap<>();}
    private InlineTransmitterWidget(){}
    private static int value(java.lang.reflect.Field field,Object instance){try{return field.getInt(instance);}catch(IllegalAccessException badForge){throw new IllegalStateException(badForge);}}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui){try{return (List<GuiButton>)BUTTONS.get(gui);}catch(IllegalAccessException badForge){throw new IllegalStateException(badForge);}}
    private static void save(GuiContainer gui,Placement p){if(!p.dirty||p.pending||p.before.isEmpty())return;ChannelList next=ChannelFields.parse(p.field);if(next==null)return;if(next.equals(ItemRedstoneTransmitter.channels(p.before))){p.dirty=false;return;}PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.hosted(gui.inventorySlots.windowId,p.slot.selector,p.before,next));p.dirty=false;p.pending=true;}
    private static State layout(GuiContainer gui){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null)return null;
        InlineTransmitters.attach(gui.inventorySlots,mc.player);InlineTransmitters.Binding binding=InlineTransmitters.binding(gui.inventorySlots);if(binding==null)return null;
        binding.hide();State state=STATES.computeIfAbsent(gui,g->new State());for(Placement p:state.buttons.values())p.shown=false;
        int selector=gui instanceof GuiRedstoneScreen?((GuiRedstoneScreen)gui).inlineTransmitterSelector():gui instanceof GuiCockpit?((GuiCockpit)gui).inlineTransmitterSelector():gui instanceof GuiAnimatedScreenSelector?((GuiAnimatedScreenSelector)gui).inlineTransmitterSelector():gui instanceof GuiProgrammableInput?((GuiProgrammableInput)gui).inlineTransmitterSelector():gui instanceof GuiProgrammableHalfConsole?((GuiProgrammableHalfConsole)gui).inlineTransmitterSelector():0;
        int left=value(LEFT,gui),top=value(TOP,gui),height=value(GUI_HEIGHT,gui);boolean shown=false;
        for(GuiButton button:buttons(gui)){
            if(!button.visible||!button.displayString.contains("Transmitter"))continue;InlineTransmitters.ModuleSlot slot=binding.modules.get(selector);if(slot==null)continue;
            Placement p=state.buttons.get(button);if(p==null||p.slot!=slot){if(p!=null)save(gui,p);p=new Placement(button,slot);state.buttons.put(button,p);}
            p.x=button.x;p.y=button.y+(button.height-16)/2;p.width=button.width;p.shown=true;
            slot.xPos=p.x-left;slot.yPos=p.y-top;slot.visible=true;
            p.field.x=p.x;p.field.y=p.y+Math.max(24,mc.fontRenderer.listFormattedStringToWidth(CAPTION,p.width-24).size()*mc.fontRenderer.FONT_HEIGHT+4);p.field.width=p.width;p.field.setVisible(true);p.field.setEnabled(slot.getHasStack());
            ItemStack stack=slot.getStack();if(!ItemStack.areItemStacksEqual(p.before,stack)){boolean sameModule=!p.before.isEmpty()&&!stack.isEmpty()&&p.before.getItem()==stack.getItem()&&p.before.getMetadata()==stack.getMetadata();p.before=stack.copy();p.pending=false;if(!sameModule||!p.dirty){p.field.setText(stack.isEmpty()?"":ItemRedstoneTransmitter.channels(stack).toString());p.dirty=false;}else if(!p.field.isFocused())save(gui,p);}
            button.visible=false;shown=true;
        }
        for(Placement p:state.buttons.values())if(!p.shown){save(gui,p);p.field.setFocused(false);p.field.setVisible(false);}
        if(shown)for(int i=0;i<binding.hotbar.size();i++){InlineTransmitters.VisibleSlot slot=binding.hotbar.get(i);slot.xPos=12+i*18;slot.yPos=height-50;slot.visible=true;}
        return state;
    }
    /** Anchors are restored after rendering so each dialog still owns tab visibility and rebuilds. */
    private static void restore(State state){if(state!=null)for(Placement p:state.buttons.values())if(p.shown)p.anchor.visible=true;}
    @SubscribeEvent public static void beforeDraw(GuiScreenEvent.DrawScreenEvent.Pre event){if(event.getGui() instanceof GuiContainer)layout((GuiContainer)event.getGui());}
    @SubscribeEvent public static void action(GuiScreenEvent.ActionPerformedEvent.Pre event){if(!(event.getGui() instanceof GuiContainer))return;GuiContainer gui=(GuiContainer)event.getGui();State state=STATES.get(gui);if(state==null)return;for(Placement p:state.buttons.values())save(gui,p);if(state.buttons.containsKey(event.getButton()))event.setCanceled(true);}
    static GuiTextField channelsField(GuiContainer gui){State state=STATES.get(gui);if(state!=null)for(Placement p:state.buttons.values())if(p.shown)return p.field;return null;}
    static boolean mouseInput(GuiContainer gui,int x,int y,int button){State state=STATES.get(gui);if(state==null)return false;boolean consumed=false;
        for(Placement p:state.buttons.values())if(p.shown){boolean hit=p.slot.getHasStack()&&x>=p.field.x&&x<p.field.x+p.field.width&&y>=p.field.y&&y<p.field.y+p.field.height;if(!hit)save(gui,p);p.field.mouseClicked(x,y,button);if(hit)consumed=true;}return consumed;
    }
    static boolean keyInput(GuiContainer gui,char character,int key){State state=STATES.get(gui);if(state==null)return false;for(Placement p:state.buttons.values())if(p.shown&&p.field.isFocused()){
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){save(gui,p);p.field.setFocused(false);}else if(key==Keyboard.KEY_ESCAPE){save(gui,p);p.field.setFocused(false);return false;}else if(p.field.textboxKeyTyped(character,key))p.dirty=true;return true;}return false;
    }
    @SubscribeEvent public static void mouse(GuiScreenEvent.MouseInputEvent.Pre event){if(!(event.getGui() instanceof GuiContainer)||!Mouse.getEventButtonState()||Mouse.getEventButton()<0)return;GuiContainer gui=(GuiContainer)event.getGui();Minecraft mc=Minecraft.getMinecraft();int x=Mouse.getEventX()*gui.width/mc.displayWidth,y=gui.height-Mouse.getEventY()*gui.height/mc.displayHeight-1;if(mouseInput(gui,x,y,Mouse.getEventButton()))event.setCanceled(true);}
    @SubscribeEvent public static void key(GuiScreenEvent.KeyboardInputEvent.Pre event){if(event.getGui() instanceof GuiContainer&&Keyboard.getEventKeyState()&&keyInput((GuiContainer)event.getGui(),Keyboard.getEventCharacter(),Keyboard.getEventKey()))event.setCanceled(true);}
    @SubscribeEvent public static void afterDraw(GuiScreenEvent.DrawScreenEvent.Post event){
        if(!(event.getGui() instanceof GuiContainer))return;GuiContainer gui=(GuiContainer)event.getGui();InlineTransmitters.Binding binding=InlineTransmitters.binding(gui.inventorySlots);if(binding==null)return;
        int left=value(LEFT,gui),top=value(TOP,gui);List<InlineTransmitters.VisibleSlot> slots=new ArrayList<>(binding.modules.values());slots.addAll(binding.hotbar);Minecraft mc=Minecraft.getMinecraft();State state=STATES.get(gui);List<String> tooltip=Collections.emptyList();
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.depthMask(false);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
            if(state!=null)for(Placement p:state.buttons.values())if(p.shown){
                mc.fontRenderer.drawSplitString(CAPTION,p.x+24,p.y+2,p.width-24,0xc9d9e2);p.field.drawTextBox();
                if(!p.slot.getHasStack()&&mc.player.inventory.getItemStack().isEmpty()){
                    GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation("vandorlabs","textures/items/redstone_transmitter_short.png"));GlStateManager.color(.55F,.55F,.55F,.65F);Gui.drawModalRectWithCustomSizedTexture(p.x,p.y,0,0,16,16,16,16);GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();
                }
                if(event.getMouseX()>=p.field.x&&event.getMouseX()<p.field.x+p.field.width&&event.getMouseY()>=p.field.y&&event.getMouseY()<p.field.y+p.field.height)tooltip=Arrays.asList(CAPTION,"Enter or click outside to apply.");
            }
            // Finish every slot overlay before drawing any tooltip. A later border must never cross its glyphs.
            for(InlineTransmitters.VisibleSlot slot:slots)if(slot.visible){int x=left+slot.xPos,y=top+slot.yPos;
                Gui.drawRect(x-1,y-1,x+17,y,0xff8296a2);Gui.drawRect(x-1,y+16,x+17,y+17,0xff8296a2);Gui.drawRect(x-1,y,x,y+16,0xff8296a2);Gui.drawRect(x+16,y,x+17,y+16,0xff8296a2);
                if(event.getMouseX()>=x&&event.getMouseX()<x+16&&event.getMouseY()>=y&&event.getMouseY()<y+16&&mc.player.inventory.getItemStack().isEmpty())tooltip=slot.getHasStack()?slot.getStack().getTooltip(mc.player,mc.gameSettings.advancedItemTooltips?net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED:net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL):slot instanceof InlineTransmitters.ModuleSlot?Arrays.asList("Insert a Redstone Transmitter","A module enables transmission.","Receive channels work without a module."):Collections.emptyList();
            }
            if(!tooltip.isEmpty()&&mc.player.inventory.getItemStack().isEmpty()){
                GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.depthMask(false);
                net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(tooltip,event.getMouseX(),event.getMouseY(),gui.width,gui.height,-1,mc.fontRenderer);
            }
        }finally{restore(state);}
    }
}
