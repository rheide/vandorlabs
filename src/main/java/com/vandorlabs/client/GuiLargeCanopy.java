package com.vandorlabs.client;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.container.ContainerLargeCanopy;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Mouse;
import java.io.IOException;
/** One live settings view with the shared full-height material picker. */
public final class GuiLargeCanopy extends GuiMaterialPicker {
    private final TileEntityCanopy tile;
    private int depth,scale,shade,tint;
    private final LargeCanopySettings draft=new LargeCanopySettings();
    private HousingTextureList textures;private GuiTextField channels;private int channelLabelY,channelLabelX;
    private int page;
    private String sent,error="";private boolean ready,sliderDirty,sliderDragging;private int sliderDelay;private MessageCanopySettingsResult pending;
    public GuiLargeCanopy(TileEntityCanopy tile){super(new ContainerLargeCanopy(tile));this.tile=tile.owner();net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();this.tile.large.write(tag);draft.read(tag);depth=draft.depth;scale=draft.scale;shade=draft.shade;tint=draft.tint;sent=key();}
    private String key(){draft.depth=depth;draft.scale=scale;draft.shade=shade;draft.tint=tint;return draft.key();}
    void apply(){if(!ready)return;if(channels!=null){com.vandorlabs.redstone.ChannelList parsed=ChannelFields.parse(channels);if(parsed==null)return;draft.channels=parsed;}sliderDirty=false;if(!key().equals(sent)){sent=key();PacketHandler.INSTANCE.sendToServer(new MessageLargeCanopy(tile.getPos(),draft));}}
    public void acknowledge(MessageCanopySettingsResult result){if(tile.getPos().equals(result.pos) && result.requested.equals(sent)){pending=result;reconcile();}}
    private void reconcile(){if(pending==null || sliderDragging || Mouse.isButtonDown(0))return;MessageCanopySettingsResult result=pending;pending=null;if(!result.requested.equals(sent))return;if(result.applied){error="";return;}net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();result.settings.write(tag);draft.read(tag);depth=draft.depth;scale=draft.scale;shade=draft.shade;tint=draft.tint;sent=key();error="Blocked: close first or clear the new surface.";initGui();}
    @Override public void updateScreen(){super.updateScreen();if(sliderDirty && --sliderDelay<=0){apply();sliderDelay=2;}reconcile();if(channels!=null)channels.updateCursorCounter();}
    @Override protected HousingTextureList materialPicker(){return page==0?textures:null;}
    @Override public void initGui(){
        String text=channels==null?draft.channels.toString():channels.getText();
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();ready=false;
        int leftWidth=layout.controlsX-guiLeft-31;
        HousingTextureList previous=textures;
        textures=HousingTextureList.forCanopy(guiLeft+12,guiTop+54,leftWidth,shade,draft.material)
                .visibleRows(Math.max(2,(ySize-88)/HousingTextureList.ROW_HEIGHT)).custom(this::choose).keepView(previous);
        boolean redstoneControls=tile.resizable()||tile.block().opening();
        buttonList.add(layout.tab(90,0,redstoneControls?2:1,"Canopy"));if(redstoneControls)buttonList.add(layout.tab(91,1,2,"Redstone"));
        for(GuiButton button:buttonList)if(button.id==90+page)button.enabled=false;
        int y=54;
        if(page==0){
        if(tile.resizable()){
            buttonList.add(new GuiSlider(0,layout.controlsX,guiTop+y,154,20,"Depth: "," px",0,80,depth,false,true,slider->sliderChanged(0,slider)));y+=22;
            buttonList.add(new GuiSlider(1,layout.controlsX,guiTop+y,154,20,"Inner size: ","%",10,100,scale,false,true,slider->sliderChanged(1,slider)));y+=22;
        }
        buttonList.add(layout.control(2,y,"Tint: "+GlassTextures.tintLabel(tint)));y+=22;
        buttonList.add(layout.control(12,y,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(draft.textureLayout())));y+=22;
        if(tile.block().opening()){
            buttonList.add(layout.control(3,y,"Opening: "+(draft.motion==0?"Sliding":"Rear hinge")));y+=22;
            if(draft.motion==0){buttonList.add(layout.control(11,y,"Slide: "+(draft.slideForward?"Forward":"Backward")));y+=22;}
            buttonList.add(layout.control(4,y,"Front: "+new String[]{"Flat","Angled","Long Angled","Extra Long Angled"}[draft.angle]));y+=22;
        }else if(tile.resizable()){
            buttonList.add(layout.control(5,y,"Openable: "+(draft.openable?"On":"Off")));y+=22;
            buttonList.add(layout.control(6,y,"Hinge: "+new String[]{"Left","Right","Top","Bottom"}[draft.hinge]));y+=22;
        }
        }
        if(page==1 && (tile.resizable() || tile.block().opening())){
            buttonList.add(RedstoneActivationButton.from(layout.control(7,y,""),!draft.redstone?0:draft.activateOnPower?1:2));y+=22;
            channelLabelX=layout.controlsX;channelLabelY=guiTop+82;channels=new GuiTextField(10,fontRenderer,layout.controlsX,guiTop+94,154,18);ChannelFields.configure(channels);channels.setText(text);

        }else channels=null;
        buttonList.add(layout.done(8));ready=true;
    }
    void choose(int choice){
        if(choice>=HousingTextureList.CANOPY_GLASS_BASE && choice<HousingTextureList.CANOPY_GLASS_BASE+GlassTextures.IDS.length){shade=choice-HousingTextureList.CANOPY_GLASS_BASE;draft.material=-1;}
        else draft.material=choice;
        textures.setSelected(choice);apply();
    }
    private void sliderChanged(int id,GuiSlider slider){
        int value=slider.getValueInt();
        if(id==0){if(value==depth)return;depth=value;}
        else {if(value==scale)return;scale=value;}
        sliderDirty=true;
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==90||button.id==91){apply();page=button.id-90;initGui();return;}
        if(button.id==92){apply();PacketHandler.INSTANCE.sendToServer(MessageTransmitterSlot.open(tile.getPos(),0));return;}
        // GuiSlider owns its value callback and drag state. Rebuilding here cancels the drag.
        if(button instanceof GuiSlider)return;
        switch(button.id){
            case 2:tint=GuiOptionCycle.next(tint,3);break;
            case 3:draft.motion=GuiOptionCycle.next(draft.motion,2);break;
            case 12:draft.textureLayout(GuiOptionCycle.next(draft.textureLayout(),3));break;
            case 11:draft.slideForward=!draft.slideForward;break;
            case 4:draft.angle=GuiOptionCycle.next(draft.angle,tile.block().length==3?4:3);break;
            case 5:draft.openable=!draft.openable;break;
            case 6:draft.hinge=GuiOptionCycle.next(draft.hinge,4);break;
            case 7:int trigger=GuiOptionCycle.next(!draft.redstone?0:draft.activateOnPower?1:2,3);draft.redstone=trigger!=0;draft.activateOnPower=trigger!=2;break;
            case 8:apply();mc.player.closeScreen();return;
        }
        apply();initGui();
    }
    @Override protected void keyTyped(char typed,int key)throws IOException{if(channels!=null && channels.textboxKeyTyped(typed,key)){apply();return;}if(key==1 || key==mc.gameSettings.keyBindInventory.getKeyCode())apply();super.keyTyped(typed,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(channels!=null)channels.mouseClicked(x,y,button);
        if(button==0)for(GuiButton control:buttonList)if(control instanceof GuiSlider && control.enabled && control.visible && x>=control.x && x<control.x+control.width && y>=control.y && y<control.y+control.height)sliderDragging=true;
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2,3,4,5,6,7,11,12))return;
        if(page==0 && textures.click(x,y,button)){if(textures.picked())choose(textures.selected());return;}super.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int button){if(button==0){for(GuiButton control:buttonList)if(control instanceof GuiSlider)control.mouseReleased(x,y);sliderDragging=false;if(sliderDirty)apply();reconcile();}super.mouseReleased(x,y,button);}
    @Override public void onGuiClosed(){if(sliderDirty)apply();super.onGuiClosed();}

    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee101012);fontRenderer.drawString(net.minecraft.client.resources.I18n.format(tile.block().getUnlocalizedName()+".name"),guiLeft+8,guiTop+8,0xffffff);DialogHeader.logo(guiLeft,guiTop,xSize);if(page==0)textures.draw(fontRenderer,x,y);super.drawScreen(x,y,partial);if(channels!=null){fontRenderer.drawString("Receive channels",channelLabelX,channelLabelY,0xffffff);channels.drawTextBox();}if(!error.isEmpty())fontRenderer.drawSplitString(error,guiLeft+xSize-166,guiTop+ySize+3,154,0xffffaa88);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
