package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.canopy.*;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.persistence.CanopySettingsData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated live Canopy editor: Source pages, drag debounce and rejected-edit reconciliation. */
public final class NativeCanopyScreen extends AbstractContainerScreen<NativeCanopyMenu> {
    private static final int SHADE_BASE=-1000;
    private final Consumer<CanopySettingsPayload> send;
    private final NativeMaterialPicker picker;
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    private NativeCanopySettings draft;
    private CompoundTag sent;
    private ProgrammableDialogLayout panel;
    private EditBox channels;
    private int page,request,sliderDelay;
    private boolean ready,sliderDirty,dragging;
    private CanopySettingsPayload.Result pending;
    private String error="";
    public NativeCanopyScreen(NativeCanopyMenu menu,Inventory inventory,Component title){this(menu,inventory,title,PacketDistributor::sendToServer);}
    public NativeCanopyScreen(NativeCanopyMenu menu,Inventory inventory,Component title,Consumer<CanopySettingsPayload> send){
        super(menu,inventory,title);this.send=Objects.requireNonNull(send);draft=menu.tile.settings();sent=tag(draft);
        picker=new NativeMaterialPicker(selected(),id->{var e=ModernFinishCatalog.option(id);return e.general()||e.displaySelectable();},this::choose).presentation(NativeDoorAssets::preview);
        for(int i=0;i<GlassTextures.IDS.length;i++)picker.specialMaterial(SHADE_BASE-i,GlassTextures.LABELS[i],"Glass","vandorlabs:glass-palette/"+GlassTextures.IDS[i]);
    }
    public static void register(RegisterMenuScreensEvent event,MenuType<NativeCanopyMenu> type){event.<NativeCanopyMenu,NativeCanopyScreen>register(type,(menu,inventory,title)->new NativeCanopyScreen(menu,inventory,title));CanopySettingsPayload.bindResults(result->{if(Minecraft.getInstance().screen instanceof NativeCanopyScreen screen)screen.acknowledge(result);});}
    public NativeCanopySettings editingSettings(){return draft;}public ProgrammableDialogLayout panel(){return panel;}public NativeMaterialPicker activePicker(){return page==0?picker:null;}public EditBox channelField(){return channels;}public Button button(int id){return buttons.get(id);}public int page(){return page;}public String error(){return error;}
    private static CompoundTag tag(NativeCanopySettings settings){var tag=new CompoundTag();settings.write(tag);return tag;}
    private int selected(){return draft.data().material<0?SHADE_BASE-draft.data().shade:draft.data().material;}
    private boolean resizable(){return CanopySettingsData.supports(menu.tile.kind());}
    private boolean opening(){return menu.tile.kind().equals("canopy")||menu.tile.kind().equals("long_canopy");}
    @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;leftPos=panel.left;topPos=panel.top;rebuild(true);}
    private void option(int id,int x,int y,int w,String label,boolean enabled){var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(x,y,w,20).build();button.active=enabled;buttons.put(id,button);addRenderableWidget(button);}
    private void control(int id,int y,String label){option(id,panel.controlsX,panel.top+y,154,label,true);}
    private void rebuild(boolean center){
        String text=channels==null?draft.data().channels.toString():channels.getValue();ready=false;clearWidgets();buttons.clear();
        boolean redstone=resizable()||opening();int tabs=redstone?2:1;
        for(int i=0;i<tabs;i++)option(90+i,panel.left+12+i*(panel.width-24)/tabs,panel.top+28,(panel.width-24)/tabs-2,i==0?"Canopy":"Redstone",page!=i);
        var d=draft.data();int y=54;
        if(page==0){
            if(resizable()){
                addRenderableWidget(new NativeIntegerSlider(panel.controlsX,panel.top+y,154,20,0,80,d.depth,n->Component.literal("Depth: "+n+" px"),n->slider("CanopyDepth",n)));y+=22;
                addRenderableWidget(new NativeIntegerSlider(panel.controlsX,panel.top+y,154,20,10,100,d.scale,n->Component.literal("Inner size: "+n+"%"),n->slider("CanopyScale",n)));y+=22;
            }
            control(2,y,"Tint: "+GlassTextures.tintLabel(d.tint));y+=22;
            control(12,y,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(d.textureLayout()));y+=22;
            if(opening()){
                control(3,y,"Opening: "+(d.motion==0?"Sliding":"Rear hinge"));y+=22;
                if(d.motion==0){control(11,y,"Slide: "+(d.slideForward?"Forward":"Backward"));y+=22;}
                control(4,y,"Front: "+new String[]{"Flat","Angled","Long Angled","Extra Long Angled"}[d.angle]);
            }else if(resizable()){
                control(5,y,"Openable: "+(d.openable?"On":"Off"));y+=22;
                control(6,y,"Hinge: "+new String[]{"Left","Right","Top","Bottom"}[d.hinge]);
            }
            picker.select(selected(),false);picker.layout(panel.listX,panel.top+54,panel.listWidth,Math.max(2,(panel.height-88)/22),center);addRenderableWidget(picker.searchField());channels=null;
        }else{
            channels=new EditBox(font,panel.left+12,panel.top+68,panel.width-24,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(text);channels.setResponder(ignored->{channels.setTextColor(ChannelList.parse(channels.getValue())==null?0xFF8888:0xFFFFFF);apply();});addRenderableWidget(channels);
            option(7,panel.left+12,panel.top+96,panel.width-24,"Activation: "+(!d.redstone?"Manual":d.activateOnPower?"Powered":"Unpowered"),true);
        }
        option(8,panel.left+panel.width-166,panel.doneY,154,Component.translatable("gui.done").getString(),true);ready=true;
    }
    private void slider(String key,int value){var next=tag(draft);if(next.getInt(key)==value)return;next.putInt(key,value);draft=NativeCanopySettings.read(next);sliderDirty=true;}
    public boolean apply(){
        if(!ready)return false;
        if(channels!=null){var parsed=ChannelList.parse(channels.getValue());if(parsed==null)return false;var next=tag(draft);next.putIntArray("CanopyChannels",parsed.toArray());draft=NativeCanopySettings.read(next);}
        sliderDirty=false;var next=tag(draft);if(next.equals(sent))return true;sent=next;
        send.accept(new CanopySettingsPayload(new NativeCanopyEdit(menu.tile.getBlockPos(),menu.containerId,++request,draft)));return true;
    }
    public void choose(int choice){var next=tag(draft);if(choice<=SHADE_BASE&&choice>SHADE_BASE-GlassTextures.IDS.length){next.putInt("CanopyShade",SHADE_BASE-choice);next.putInt("CanopyMaterial",-1);}else{if(!ModernFinishCatalog.validChoice(choice))throw new IllegalArgumentException("Invalid Canopy material");next.putInt("CanopyMaterial",choice);}draft=NativeCanopySettings.read(next);picker.select(choice,false);apply();}
    private static int cycle(int value,int count,int direction){return Math.floorMod(value+(direction<0?-1:1),count);}
    public void action(int id,int direction){
        if(id==8){onClose();return;}if(id==90||id==91){apply();page=id-90;picker.closeInventory();rebuild(true);return;}
        var d=draft.data();var next=tag(draft);
        switch(id){
            case 2->next.putInt("CanopyTint",cycle(d.tint,4,direction));
            case 3->next.putInt("CanopyMotion",cycle(d.motion,2,direction));
            case 4->next.putInt("CanopyAngle",cycle(d.angle,menu.tile.kind().equals("long_canopy")?4:3,direction));
            case 5->next.putBoolean("CanopyOpenable",!d.openable);
            case 6->next.putInt("CanopyHinge",cycle(d.hinge,4,direction));
            case 7->{int mode=cycle(!d.redstone?0:d.activateOnPower?1:2,3,direction);next.putBoolean("CanopyRedstone",mode!=0);next.putBoolean("CanopyActivateOnPower",mode!=2);}
            case 11->next.putBoolean("CanopySlideForward",!d.slideForward);
            case 12->{int layout=cycle(d.textureLayout(),3,direction);next.putInt("CanopyTextureLayout",layout);next.putBoolean("CanopyTileMaterial",layout==1);}
            default->{return;}
        }
        draft=NativeCanopySettings.read(next);apply();rebuild(false);
    }
    public void acknowledge(CanopySettingsPayload.Result result){var ack=result.authoritative();if(ack.window()!=menu.containerId||!ack.pos().equals(menu.tile.getBlockPos())||ack.request()!=request)return;pending=result;reconcile();}
    private void reconcile(){if(pending==null||dragging||minecraft.mouseHandler.isLeftPressed())return;var result=pending;pending=null;if(result.authoritative().request()!=request)return;if(result.applied()){error="";return;}draft=result.authoritative().settings();sent=tag(draft);error="Blocked: close first or clear the new surface.";rebuild(false);}
    public void advanceEditor(){if(sliderDirty&&--sliderDelay<=0){apply();sliderDelay=2;}reconcile();}
    @Override protected void containerTick(){super.containerTick();advanceEditor();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var entry:buttons.entrySet()){var b=entry.getValue();int id=entry.getKey();if(b.active&&b.isMouseOver(x,y)&&(button==0||id==2||id==3||id==4||id==5||id==6||id==7||id==11||id==12)){action(id,button==1?-1:1);return true;}}if(page==0&&picker.click(x,y,button))return true;boolean handled=super.mouseClicked(x,y,button);if(button==0&&getFocused() instanceof NativeIntegerSlider)dragging=handled;return handled;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||page==0&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);picker.release();if(button==0){dragging=false;if(sliderDirty)apply();reconcile();}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return page==0&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return page==0&&picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){apply();super.onClose();}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){if(panel==null)return;graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);graphics.drawString(font,title,panel.left+8,panel.top+8,0xFFFFFF,false);if(page==0)picker.render(graphics,x,y);else graphics.drawString(font,"Receive channels",panel.left+12,panel.top+54,0xFFFFFF,false);if(!error.isEmpty()){if(panel.height<300)graphics.drawString(font,font.plainSubstrByWidth(error,panel.width-24),panel.left+12,panel.top+18,0xFFFFAA88,false);else graphics.drawWordWrap(font,Component.literal(error),panel.controlsX,panel.doneY-34,154,0xFFFFAA88);}}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
