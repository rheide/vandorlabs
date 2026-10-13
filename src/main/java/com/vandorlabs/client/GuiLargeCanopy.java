package com.vandorlabs.client;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.container.ContainerLargeCanopy;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Mouse;
import java.io.IOException;
/** Compact live controls leave the world visible around the dialog. */
public final class GuiLargeCanopy extends GuiContainer {
    private final TileEntityCanopy tile;
    private int depth,scale,shade,tint;
    private final LargeCanopySettings draft=new LargeCanopySettings();
    private HousingTextureList textures;private GuiTextField channels;private int channelLabelY,channelLabelX;
    private String sent,error="";private boolean ready,materials;private int page;private MessageCanopySettingsResult pending;
    public GuiLargeCanopy(TileEntityCanopy tile){super(new ContainerLargeCanopy(tile));this.tile=tile.owner();net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();this.tile.large.write(tag);draft.read(tag);depth=draft.depth;scale=draft.scale;shade=draft.shade;tint=draft.tint;materials=draft.material>=0;sent=key();}
    private String key(){draft.depth=depth;draft.scale=scale;draft.shade=shade;draft.tint=tint;return draft.key();}
    void apply(){if(!ready)return;if(channels!=null){com.vandorlabs.redstone.ChannelList parsed=ChannelFields.parse(channels);if(parsed==null)return;draft.channels=parsed;}if(!key().equals(sent)){sent=key();PacketHandler.INSTANCE.sendToServer(new MessageLargeCanopy(tile.getPos(),draft));}}
    public void acknowledge(MessageCanopySettingsResult result){if(tile.getPos().equals(result.pos) && result.requested.equals(sent)){pending=result;reconcile();}}
    private void reconcile(){if(pending==null || Mouse.isButtonDown(0))return;MessageCanopySettingsResult result=pending;pending=null;if(!result.requested.equals(sent))return;if(result.applied){error="";return;}net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();result.settings.write(tag);draft.read(tag);depth=draft.depth;scale=draft.scale;shade=draft.shade;tint=draft.tint;sent=key();error="Blocked: close first or clear the new surface.";initGui();}
    @Override public void updateScreen(){super.updateScreen();reconcile();if(channels!=null)channels.updateCursorCounter();}
    @Override public void initGui(){
        String text=channels==null?draft.channels.toString():channels.getText();
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height,Math.min(330,width*3/4),Math.min(164,height*3/4));xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();ready=false;
        int leftWidth=layout.controlsX-guiLeft-31;
        buttonList.add(new GuiButton(9,guiLeft+12,guiTop+26,leftWidth,20,materials?"Textures: Materials":"Textures: Glass"));
        textures=(materials?new HousingTextureList(guiLeft+12,guiTop+50,leftWidth,Math.max(0,draft.material)):HousingTextureList.forGlass(1,guiLeft+12,guiTop+50,leftWidth,shade)).visibleRows(Math.max(2,(ySize-90)/HousingTextureList.ROW_HEIGHT));
        boolean mechanism=tile.resizable() || tile.block().opening();
        if(mechanism)buttonList.add(layout.control(10,26,page==0?"Settings: Shape":"Settings: Opening"));
        if(materials)textures.custom(value->{draft.material=value;apply();});
        int y=mechanism?50:26;
        if(page==0 && tile.resizable()){
            buttonList.add(new GuiSlider(0,layout.controlsX,guiTop+y,154,20,"Depth: "," px",0,80,depth,false,true,slider->{depth=slider.getValueInt();apply();}));y+=22;
            buttonList.add(new GuiSlider(1,layout.controlsX,guiTop+y,154,20,"Inner size: ","%",10,100,scale,false,true,slider->{scale=slider.getValueInt();apply();}));y+=22;
        }
        if(page==0){buttonList.add(layout.control(2,y,"Tint: "+GlassTextures.tintLabel(tint)));y+=22;}
        if(tile.block().opening()){
            if(page==1){buttonList.add(layout.control(3,y,"Opening: "+(draft.motion==0?"Sliding":"Rear hinge")));y+=22;}
            else {buttonList.add(layout.control(4,y,"Front: "+new String[]{"Flat","Angled","Long Angled"}[draft.angle]));y+=22;}
        }else if(page==1 && tile.resizable()){
            buttonList.add(layout.control(5,y,"Openable: "+(draft.openable?"On":"Off")));y+=22;
            buttonList.add(layout.control(6,y,"Hinge: "+new String[]{"Left","Right","Top","Bottom"}[draft.hinge]));y+=22;
        }
        if(page==1 && mechanism){
            buttonList.add(layout.control(7,y,"Redstone: "+(draft.redstone?"On":"Off")));y+=22;
            channelLabelX=layout.controlsX;channelLabelY=guiTop+y+5;channels=new GuiTextField(10,fontRenderer,layout.controlsX+48,guiTop+y,106,18);ChannelFields.configure(channels);channels.setText(text);
        }else channels=null;
        buttonList.add(new GuiButton(8,guiLeft+12,guiTop+ySize-28,leftWidth,20,"Done"));ready=true;
    }
    @Override protected void actionPerformed(GuiButton button){
        switch(button.id){
            case 2:tint=GuiOptionCycle.next(tint,3);break;
            case 3:draft.motion=GuiOptionCycle.next(draft.motion,2);break;
            case 4:draft.angle=GuiOptionCycle.next(draft.angle,3);break;
            case 5:draft.openable=!draft.openable;break;
            case 6:draft.hinge=GuiOptionCycle.next(draft.hinge,4);break;
            case 7:draft.redstone=!draft.redstone;break;
            case 8:apply();mc.player.closeScreen();return;
            case 9:materials=!materials;initGui();return;
            case 10:apply();page=1-page;initGui();return;
        }
        apply();initGui();
    }
    @Override protected void keyTyped(char typed,int key)throws IOException{if(channels!=null && channels.textboxKeyTyped(typed,key)){apply();return;}super.keyTyped(typed,key);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(channels!=null)channels.mouseClicked(x,y,button);
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2,3,4,6))return;
        if(textures.click(x,y,button)){if(textures.picked()){if(materials)draft.material=textures.selected();else{shade=textures.selected();draft.material=-1;}apply();}return;}super.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int button){textures.release();super.mouseReleased(x,y,button);}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(!textures.drag(y))super.mouseClickMove(x,y,button,time);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();textures.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee101012);fontRenderer.drawString(net.minecraft.client.resources.I18n.format(tile.block().getUnlocalizedName()+".name"),guiLeft+8,guiTop+8,0xffffff);textures.draw(fontRenderer,x,y);super.drawScreen(x,y,partial);if(channels!=null){fontRenderer.drawString("Channels",channelLabelX,channelLabelY,0xffffff);channels.drawTextBox();}if(!error.isEmpty())fontRenderer.drawSplitString(error,guiLeft+xSize-166,guiTop+ySize+3,154,0xffffaa88);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
