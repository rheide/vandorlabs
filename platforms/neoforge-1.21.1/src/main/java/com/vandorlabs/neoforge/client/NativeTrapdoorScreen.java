package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.neoforge.trapdoor.*;
import com.vandorlabs.persistence.TrapdoorData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.ui.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated regular/diagonal editor using the shared picker, layout and released option rules. */
public final class NativeTrapdoorScreen extends AbstractContainerScreen<NativeTrapdoorMenu> {
    private final Consumer<NativeTrapdoorEdit> send;
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    private ProgrammableDialogLayout panel;private NativeMaterialPicker picker;private EditBox channels;
    private NativeTrapdoorSettings settings;private boolean inverted;private int tallWidth,page;
    private String draft;private ChannelList lastValidChannels;
    public NativeTrapdoorScreen(NativeTrapdoorMenu menu,Inventory inventory,Component title){this(menu,inventory,title,edit->PacketDistributor.sendToServer(new TrapdoorSettingsPayload(edit)));}
    public NativeTrapdoorScreen(NativeTrapdoorMenu menu,Inventory inventory,Component title,Consumer<NativeTrapdoorEdit> send){
        super(menu,inventory,title);this.send=Objects.requireNonNull(send);var tile=menu.tile;var old=tile.settings();
        settings=new NativeTrapdoorSettings(old.choices(),old.channels(),tile.facing().get2DDataValue(),old.hingeSide(),old.rotationFlipped(),old.diagonal());
        inverted=tile.inverted();tallWidth=old.choices().position==2?0:old.choices().position;
        lastValidChannels=old.channels();draft=lastValidChannels.toString();
    }
    public ProgrammableDialogLayout panel(){return panel;}public NativeMaterialPicker activePicker(){return page==0?picker:null;}
    public EditBox channelField(){return channels;}public Button button(int id){return buttons.get(id);}
    public int page(){return page;}public NativeTrapdoorSettings settings(){return settings;}
    private boolean diagonal(){return settings.diagonal();}
    public static boolean selectable(ModernFinishCatalog.Entry entry){
        var door=entry.door();if(door!=null&&(door.design()==15||door.design()==16))return false;
        return NativeDoorAssets.selectable(entry,false,0)||entry.frame()!=null&&entry.frame().aspect()<=1.01;
    }
    @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;leftPos=panel.left;topPos=panel.top;
        if(picker==null)picker=new NativeMaterialPicker(settings.choices().texture,id->selectable(ModernFinishCatalog.option(id)),this::choose).presentation(NativeDoorAssets::preview);
        rebuild(true);
    }
    private void option(int id,int x,int y,int width,String label,boolean enabled){
        var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(x,y,width,20).build();button.active=enabled;buttons.put(id,button);addRenderableWidget(button);
    }
    private void rebuild(boolean center){
        if(channels!=null)draft=channels.getValue();clearWidgets();buttons.clear();var c=settings.choices();
        option(100,panel.left+12,panel.top+30,110,"General",page!=0);option(101,panel.left+126,panel.top+30,110,"Redstone",page!=1);
        if(page==0){int x=panel.controlsX,y=panel.top+64,w=ProgrammableDialogLayout.CONTROLS_WIDTH,half=(w-2)/2;
            option(1,x,y,w,TrapdoorOptions.motionLabel(diagonal(),c.sliding,c.cover,c.intoWall,c.overSurface),true);y+=22;
            option(2,x,y,diagonal()?half:w,TrapdoorOptions.positionLabel(diagonal(),c.position),!diagonal()||c.position!=2);
            if(diagonal())option(7,x+(w+2)/2,y,half,TrapdoorOptions.heightLabel(c.position),true);y+=22;
            option(6,x,y,w,"Texture: "+TextureLayoutMode.label(c.layout()),true);y+=22;
            option(diagonal()?5:8,x,y,diagonal()?half:w,diagonal()?"Flip slope":"Hinge: "+net.minecraft.core.Direction.from2DDataValue(settings.facing()).getName(),diagonal()||menu.tile.canOffsetClosedLeaf());
            if(diagonal())option(10,x+(w+2)/2,y,half,TrapdoorOptions.hingeLabel(settings.hingeSide()),!c.sliding);y+=22;
            if(diagonal())option(9,x,y,w,TrapdoorOptions.rotationLabel(settings.rotationFlipped()),!c.sliding);
            picker.layout(panel.listX,panel.top+64,panel.listWidth,Math.max(2,(panel.height-92)/ProgrammableDialogLayout.ROW_HEIGHT),center);addRenderableWidget(picker.searchField());
        }
        channels=new EditBox(font,panel.left+12,panel.top+68,panel.width-24,18,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft);channels.visible=channels.active=page==1;channels.setResponder(value->{draft=value;apply();});addRenderableWidget(channels);
        if(page==1)option(3,panel.left+12,panel.top+96,panel.width-24,"Activation: "+com.vandorlabs.ui.RedstoneActivation.label(c.trigger),true);
        option(4,panel.controlsX,panel.top+panel.height-26,154,Component.translatable("gui.done").getString(),true);validate();
    }
    private boolean validate(){boolean valid=ChannelList.parse(channels==null?draft:channels.getValue())!=null;var done=buttons.get(4);if(done!=null)done.active=valid;if(channels!=null)channels.setTextColor(valid?0xE0E0E0:0xFF7777);return valid;}
    public NativeTrapdoorEdit edit(){var c=settings.choices();return new NativeTrapdoorEdit(menu.containerId,new NativeTrapdoorSettings(new TrapdoorData(c.texture,c.position,c.layout(),c.trigger,c.layout()==1,c.cover,c.sliding,c.intoWall,c.overSurface),lastValidChannels,settings.facing(),settings.hingeSide(),settings.rotationFlipped(),diagonal()),inverted);}
    private void apply(){var parsed=ChannelList.parse(channels==null?draft:channels.getValue());if(parsed!=null)lastValidChannels=parsed;send.accept(edit());validate();}
    public void choose(int texture){if(texture<0||!ModernFinishCatalog.validChoice(texture))throw new IllegalArgumentException("Invalid trapdoor artwork");var c=settings.choices();settings=new NativeTrapdoorSettings(new TrapdoorData(texture,c.position,c.rawLayout,c.trigger,c.tileTexture,c.cover,c.sliding,c.intoWall,c.overSurface),lastValidChannels,settings.facing(),settings.hingeSide(),settings.rotationFlipped(),diagonal());apply();rebuild(false);}
    public void action(int id,int direction){
        if(picker!=null)picker.closeInventory();
        if(id==100||id==101){page=id-100;picker.closeInventory();rebuild(true);return;}
        if(id==4){if(validate()){apply();onClose();}return;}
        var c=settings.choices();int position=c.position,layout=c.layout(),trigger=c.trigger,facing=settings.facing(),hinge=settings.hingeSide();boolean sliding=c.sliding,cover=c.cover,into=c.intoWall,over=c.overSurface,rotation=settings.rotationFlipped();
        switch(id){
            case 1->{var next=TrapdoorOptions.cycleMotion(diagonal(),menu.tile.canOffsetClosedLeaf(),sliding,cover,into,over,direction);sliding=next.sliding;cover=next.cover;into=next.intoWall;over=next.overSurface;}
            case 2->{if(diagonal()&&position==2)return;position=diagonal()?(position==0?1:0):DialogInput.cycle(position,3,direction);if(diagonal())tallWidth=position;}
            case 3->trigger=DialogInput.cycle(trigger,3,direction);
            case 5->{if(!diagonal())return;inverted=!inverted;}
            case 6->layout=DialogInput.cycle(layout,3,direction);
            case 7->{if(!diagonal())return;if(position==2)position=tallWidth;else{tallWidth=position;position=2;}}
            case 8->{if(diagonal()||!menu.tile.canOffsetClosedLeaf())return;facing=DialogInput.cycle(facing,4,direction);}
            case 9->{if(!diagonal()||sliding)return;rotation=!rotation;}
            case 10->{if(!diagonal()||sliding)return;hinge=DialogInput.cycle(hinge,3,direction);}
            default->{return;}
        }
        settings=new NativeTrapdoorSettings(new TrapdoorData(c.texture,position,layout,trigger,layout==1,cover,sliding,into,over),lastValidChannels,facing,hinge,rotation,diagonal());apply();rebuild(false);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1)for(var entry:buttons.entrySet())if(entry.getKey()!=4&&entry.getKey()<100&&entry.getValue().active&&entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}
        return page==0&&picker.click(x,y,button)||super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==0&&picker.keyPressed(key,scan,modifiers))return true;if(page==1&&channels.isFocused()&&key!=256&&key!=257&&key!=335){channels.keyPressed(key,scan,modifiers);return true;}if(key==257||key==335){action(4,1);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return page==0&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return page==0&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xEE18212C);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+26,0xFF304858);g.drawString(font,title,panel.left+12,panel.top+10,0xFFFFFFFF,false);if(page==0)picker.render(g,x,y);else g.drawString(font,"Channels",panel.left+12,panel.top+54,0xFFCBD6E2,false);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
