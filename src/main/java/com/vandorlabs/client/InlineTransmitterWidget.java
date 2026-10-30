package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.redstone.*;
import com.vandorlabs.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.*;

/** Shared range, local-output and channel controls inside each authenticated block dialog. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID,value=Side.CLIENT)
public final class InlineTransmitterWidget {
    public static final int HEIGHT=64;
    private static final java.lang.reflect.Field BUTTONS=ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
    private static final Map<GuiContainer,State> STATES=new WeakHashMap<>();
    private static boolean sharedScreen(GuiContainer gui){return gui instanceof GuiAnimatedScreenSelector||gui instanceof GuiProgrammableInput||gui instanceof GuiProgrammableHalfConsole||gui instanceof GuiRedstoneScreen;}
    private static final class State {final Map<GuiButton,Placement> buttons=new IdentityHashMap<>();Placement draft;}
    private static final class Placement {
        final GuiButton anchor,local;final int selector;final java.lang.ref.WeakReference<GuiContainer> owner;final GuiTextField field;final GuiSlider range;
        ChannelList observed;int observedRange;boolean observedLocal,dirty,shown;int savedAt=-1000;
        Placement(GuiContainer gui,GuiButton anchor,int selector,RedstoneChannelMember member,Placement draft){
            this.anchor=anchor;this.selector=selector;this.owner=new java.lang.ref.WeakReference<>(gui);
            field=new GuiTextField(1980,Minecraft.getMinecraft().fontRenderer,anchor.x+100,anchor.y+44,anchor.width-100,18);ChannelFields.configure(field);
            boolean pending=draft!=null&&(draft.dirty||Minecraft.getMinecraft().player.ticksExisted-draft.savedAt<=40);
            observed=pending?draft.observed:TransmitterSlots.channels(member);observedRange=pending?draft.observedRange:member.transmissionRange();observedLocal=pending?draft.observedLocal:TransmitterSlots.localRedstone(member.channelTile());field.setText(pending?draft.field.getText():ChannelFields.text(observed));dirty=pending&&draft.dirty;if(pending)savedAt=draft.savedAt;
            local=new net.minecraftforge.fml.client.config.GuiButtonExt(1982,anchor.x,anchor.y+22,anchor.width,20,"");
            range=new GuiSlider(1981,anchor.x,anchor.y,anchor.width,20,"","",0,6,index(observedRange),false,true,s->{int index=s.getValueInt();s.setValue(index);s.displayString="Range: "+TransmitterSlots.RANGES[index]+" blocks";GuiContainer current=owner.get();if(current!=null&&rangeReady())save(current,this);}){
                @Override public int getHoverState(boolean hovered){return enabled?(hovered?2:1):0;}
            };
            range.displayString="Range: "+observedRange+" blocks";label();
        }
        boolean rangeReady(){return range!=null;}
        void label(){local.displayString="Local Redstone: "+(observedLocal?"On":"Off");}
    }
    private InlineTransmitterWidget(){}
    private static int index(int value){for(int i=0;i<TransmitterSlots.RANGES.length;i++)if(TransmitterSlots.RANGES[i]==value)return i;return 2;}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui){try{return (List<GuiButton>)BUTTONS.get(gui);}catch(IllegalAccessException badForge){throw new IllegalStateException(badForge);}}
    private static void save(GuiContainer gui,Placement p){
        ChannelList next=ChannelFields.parse(p.field);if(next==null)return;int range=TransmitterSlots.RANGES[p.range.getValueInt()];
        if(!p.dirty&&next.equals(p.observed)&&range==p.observedRange)return;
        PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.settings(gui.inventorySlots.windowId,p.selector,next,range,p.observedLocal));
        p.observed=next;p.observedRange=range;p.dirty=false;p.savedAt=Minecraft.getMinecraft().player.ticksExisted;
    }
    private static State layout(GuiContainer gui){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null)return null;InlineTransmitters.attach(gui.inventorySlots,mc.player);
        InlineTransmitters.Binding binding=InlineTransmitters.binding(gui.inventorySlots);if(binding==null)return null;binding.hide();
        State state=STATES.computeIfAbsent(gui,g->new State());List<GuiButton> current=buttons(gui);
        Iterator<Map.Entry<GuiButton,Placement>> iterator=state.buttons.entrySet().iterator();while(iterator.hasNext()){Map.Entry<GuiButton,Placement> e=iterator.next();if(!current.contains(e.getKey())){save(gui,e.getValue());state.draft=e.getValue();iterator.remove();}}
        for(Placement p:state.buttons.values())p.shown=false;
        for(GuiButton button:current){
            if(!button.visible||!button.displayString.contains("Transmitter"))continue;
            RedstoneChannelMember member=binding.source(0);if(member==null||!TransmitterSlots.supported(member))continue;
            Placement p=state.buttons.get(button);if(p==null){p=new Placement(gui,button,0,member,state.draft);state.buttons.put(button,p);}
            p.shown=true;p.range.x=button.x;p.range.y=button.y;p.local.x=button.x;p.local.y=button.y+22;
            p.field.x=button.x+100;p.field.y=button.y+44;p.field.setVisible(!sharedScreen(gui));button.visible=false;
        }
        for(Placement p:state.buttons.values())if(!p.shown){save(gui,p);p.field.setFocused(false);p.field.setVisible(false);}
        return state;
    }
    private static void restore(State state){if(state!=null)for(Placement p:state.buttons.values())if(p.shown)p.anchor.visible=true;}
    static int retainedAnchors(GuiContainer gui){State s=STATES.get(gui);return s==null?0:s.buttons.size();}
    static GuiTextField channelsField(GuiContainer gui){State s=STATES.get(gui);if(s!=null)for(Placement p:s.buttons.values())if(p.shown)return p.field;return null;}
    static List<GuiButton> controls(GuiContainer gui){State s=STATES.get(gui);List<GuiButton> result=new ArrayList<>();if(s!=null)for(Placement p:s.buttons.values())if(p.shown){result.add(p.range);result.add(p.local);}return result;}
    static boolean mouseInput(GuiContainer gui,int x,int y,int button){
        State s=STATES.get(gui);if(s==null)return false;
        for(Placement p:s.buttons.values())if(p.shown){
            if(button==0&&p.range.mousePressed(Minecraft.getMinecraft(),x,y))return true;
            if((button==0||button==1)&&p.local.mousePressed(Minecraft.getMinecraft(),x,y)){p.observedLocal=!p.observedLocal;p.label();p.dirty=true;save(gui,p);return true;}
            boolean hit=p.field.getVisible()&&x>=p.field.x&&x<p.field.x+p.field.width&&y>=p.field.y&&y<p.field.y+p.field.height;
            if(!hit)save(gui,p);if(p.field.getVisible())p.field.mouseClicked(x,y,button);if(hit)return true;
        }return false;
    }
    static boolean keyInput(GuiContainer gui,char character,int key){State s=STATES.get(gui);if(s==null)return false;for(Placement p:s.buttons.values())if(p.shown&&p.field.getVisible()&&p.field.isFocused()){
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_ESCAPE){save(gui,p);p.field.setFocused(false);return key!=Keyboard.KEY_ESCAPE;}
        if(p.field.textboxKeyTyped(character,key))p.dirty=true;return true;
    }return false;}
    @SubscribeEvent public static void beforeDraw(GuiScreenEvent.DrawScreenEvent.Pre event){if(event.getGui() instanceof GuiContainer)layout((GuiContainer)event.getGui());}
    @SubscribeEvent public static void action(GuiScreenEvent.ActionPerformedEvent.Pre event){if(!(event.getGui() instanceof GuiContainer))return;GuiContainer gui=(GuiContainer)event.getGui();State s=STATES.get(gui);if(s==null)return;for(Placement p:s.buttons.values())save(gui,p);if(s.buttons.containsKey(event.getButton()))event.setCanceled(true);}
    @SubscribeEvent public static void mouse(GuiScreenEvent.MouseInputEvent.Pre event){
        if(!(event.getGui() instanceof GuiContainer)||Mouse.getEventButton()<0)return;GuiContainer gui=(GuiContainer)event.getGui();
        if(!Mouse.getEventButtonState()){State s=STATES.get(gui);if(s!=null)for(Placement p:s.buttons.values())p.range.mouseReleased(0,0);return;}
        Minecraft mc=Minecraft.getMinecraft();int x=Mouse.getEventX()*gui.width/mc.displayWidth,y=gui.height-Mouse.getEventY()*gui.height/mc.displayHeight-1;
        if(mouseInput(gui,x,y,Mouse.getEventButton()))event.setCanceled(true);
    }
    @SubscribeEvent public static void key(GuiScreenEvent.KeyboardInputEvent.Pre event){if(event.getGui() instanceof GuiContainer&&Keyboard.getEventKeyState()&&keyInput((GuiContainer)event.getGui(),Keyboard.getEventCharacter(),Keyboard.getEventKey()))event.setCanceled(true);}
    @SubscribeEvent public static void closed(GuiScreenEvent.InitGuiEvent.Pre event){if(Minecraft.getMinecraft().currentScreen instanceof GuiContainer){GuiContainer gui=(GuiContainer)Minecraft.getMinecraft().currentScreen;State s=STATES.get(gui);if(s!=null)for(Placement p:s.buttons.values())save(gui,p);}}
    @SubscribeEvent public static void afterDraw(GuiScreenEvent.DrawScreenEvent.Post event){
        if(!(event.getGui() instanceof GuiContainer))return;GuiContainer gui=(GuiContainer)event.getGui();State s=STATES.get(gui);Minecraft mc=Minecraft.getMinecraft();
        if(s==null)return;
        // GuiContainer leaves item lighting and depth enabled; these controls are a 2D overlay.
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
        try{if(s!=null)for(Placement p:s.buttons.values())if(p.shown){
            p.range.drawButton(mc,event.getMouseX(),event.getMouseY(),event.getRenderPartialTicks());p.local.drawButton(mc,event.getMouseX(),event.getMouseY(),event.getRenderPartialTicks());p.field.drawTextBox();
            if(p.field.getVisible())mc.fontRenderer.drawString(gui instanceof GuiCockpit?"Default channels":"Transmit channels",p.anchor.x,p.field.y+5,0xdae8f0);
        }}finally{restore(s);}
    }
}
