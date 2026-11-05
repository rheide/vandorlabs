package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.ui.TriggerDialog;
import com.vandorlabs.network.MessageProgrammableTrigger;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** Independent artwork and playback for Off/On or each analog signal band. */
public final class GuiProgrammableTrigger extends GuiContainer {
    private final TileEntityProgrammableTrigger tile;
    private final HousingTextureList[] lists=new HousingTextureList[4];
    private final int[] textures=new int[4],modes=new int[4],speeds=new int[4];
    private GuiTextField channelField;
    private ProgrammableDialogLayout layout;
    private int textureTab,page,exact;
    private boolean states,frontOnly;
    private int sidesTexture;
    private HousingTextureList sidesList;
    private HousingTextureList activeList(){return page==2?sidesList:lists[textureTab];}
    public GuiProgrammableTrigger(InventoryPlayer inventory,TileEntityProgrammableTrigger tile){
        super(new ContainerAnimatedScreenSelector(inventory,tile));this.tile=tile;
        for(int i=0;i<4;i++){textures[i]=tile.getTexture(i);modes[i]=tile.getArtworkMode(i);speeds[i]=tile.getArtworkSpeed(i);}
        exact=tile.getExactLevel();states=tile.isLevelStates();frontOnly=tile.isFrontOnly();sidesTexture=tile.getSidesTexture();xSize=420;ySize=196;
    }
    @Override public void initGui(){
        String draft=channelField==null?ChannelFields.text(tile.getRedstoneChannels()):channelField.getText();
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        com.vandorlabs.ui.ProgrammableDialogLayout panel=new com.vandorlabs.ui.ProgrammableDialogLayout(width,height);
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        if(page==0){
            final int slot=textureTab;
            lists[slot]=new HousingTextureList(layout.listX,TriggerDialog.listY(panel),layout.listWidth,textures[slot],8,null,
                choice->HousingTextureList.generalTexture(choice)||HousingTextureList.displayTexture(choice))
                .visibleRows(TriggerDialog.listRows(panel)).custom(value->{applyEditor(editorState().withArtwork(slot,value));send();}).keepView(lists[slot]);
        }
        if(page==2)sidesList=new HousingTextureList(layout.listX,TriggerDialog.listY(panel),layout.listWidth,sidesTexture,8,null,HousingTextureList::generalTexture)
            .visibleRows(TriggerDialog.listRows(panel)).custom(value->{applyEditor(editorState().withSides(value));send();}).keepView(sidesList);
        for(TriggerDialog.Control control:TriggerDialog.controls(panel,editorState(),"Done")){
            GuiButton button=control.tab?new DialogTabButton(control.id,control.x,control.y,control.width,control.height,control.label)
                :control.id==TriggerDialog.DONE?new GuiButton(control.id,control.x,control.y,control.width,control.height,control.label)
                :new net.minecraftforge.fml.client.config.GuiButtonExt(control.id,control.x,control.y,control.width,control.height,control.label);
            button.enabled=control.enabled;buttonList.add(button);
        }
        TriggerDialog.ChannelBox channel=TriggerDialog.channelBox(panel);
        channelField=new GuiTextField(0,fontRenderer,channel.x,channel.y,channel.width,channel.height);ChannelFields.configure(channelField);
        channelField.setText(draft);channelField.setVisible(page==1);channelField.setFocused(false);refreshButtons();
    }
    private TriggerDialog.State editorState(){return new TriggerDialog.State(textures,modes,speeds,textureTab,page,exact,states,frontOnly,sidesTexture);}
    private void applyEditor(TriggerDialog.State state){
        System.arraycopy(state.textures(),0,textures,0,4);System.arraycopy(state.modes(),0,modes,0,4);System.arraycopy(state.speeds(),0,speeds,0,4);
        textureTab=state.textureTab;page=state.page;exact=state.exact;states=state.states;frontOnly=state.frontOnly;sidesTexture=state.sidesTexture;
    }
    private void refreshButtons(){
        TriggerDialog.State state=editorState();for(GuiButton button:buttonList)button.enabled=TriggerDialog.enabled(button.id,state);
    }
    private int channel(){return ChannelFields.first(channelField);}
    private void send(){
        int channel=channel();if(channel<0)return;
        com.vandorlabs.redstone.ChannelList channels=ChannelFields.parse(channelField);
        tile.configureArtwork(textures[0],textures[3],states,exact,textures[1],textures[2],channels,modes,speeds,frontOnly,sidesTexture);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrigger(tile.getPos(),textures[0],textures[3],channel)
                .withChannels(channels).withLevels(states,exact,textures[1],textures[2]).withArtwork(modes,speeds).withFaces(frontOnly,sidesTexture));
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,TriggerDialog.rightClickIds()))return;
        // GuiScreen iterates the live button list; growing it here can redispatch this toggle.
        if(button==0&&page==1)for(GuiButton option:new java.util.ArrayList<>(buttonList))if(TriggerDialog.safeLeftDispatch(option.id)&&option.mousePressed(mc,x,y)){
            option.playPressSound(mc.getSoundHandler());actionPerformed(option);return;
        }
        if(page!=1&&activeList().click(x,y,button)){applyEditor(editorState().choose(activeList().selected()));send();return;}
        super.mouseClicked(x,y,button);if(page==1)channelField.mouseClicked(x,y,button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed){if(page!=1&&activeList().drag(y))return;super.mouseClickMove(x,y,button,elapsed);}
    @Override protected void mouseReleased(int x,int y,int button){if(sidesList!=null)sidesList.release();for(HousingTextureList list:lists)if(list!=null)list.release();super.mouseReleased(x,y,button);}
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int x=Mouse.getEventX()*width/mc.displayWidth,y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(page!=1)activeList().wheel(x,y,Mouse.getEventDWheel());
    }
    @Override protected void actionPerformed(GuiButton button){
        if(TriggerDialog.handles(button.id)){
            TriggerDialog.State next=editorState().action(button.id,GuiOptionCycle.direction());applyEditor(next);
            if(button.id>=1&&button.id<=3||button.id>=10&&button.id<=12)refreshButtons();
            if(button.id==TriggerDialog.EXACT)button.displayString=TriggerDialog.label(button.id,next);
            if(TriggerDialog.sendsImmediately(button.id))send();
            if(TriggerDialog.rebuilds(button.id))initGui();
            return;
        }
        if(button.id==100&&channel()>=0){send();mc.player.closeScreen();}
    }
    @Override protected void keyTyped(char typed,int key)throws IOException{
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){if(channel()>=0){send();mc.player.closeScreen();}return;}
        if(page==1&&channelField.textboxKeyTyped(typed,key))send();else super.keyTyped(typed,key);
    }
    @Override public void updateScreen(){super.updateScreen();channelField.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF101012);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF202028);DialogHeader.logo(guiLeft,guiTop,xSize);
        fontRenderer.drawString(TriggerDialog.TITLE,guiLeft+12,guiTop+8,0xFFFFFFFF);
        if(page==0)activeList().draw(fontRenderer,mouseX,mouseY);
        else if(page==2)activeList().draw(fontRenderer,mouseX,mouseY);
        else fontRenderer.drawString(TriggerDialog.CHANNEL_LABEL,guiLeft+12,guiTop+54,0xDAE8F0);
        super.drawScreen(mouseX,mouseY,partial);DialogOverlay.prepare();channelField.drawTextBox();
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
