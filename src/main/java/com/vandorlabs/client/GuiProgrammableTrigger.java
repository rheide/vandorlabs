package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
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
    private boolean states;
    private HousingTextureList activeList(){return lists[textureTab];}
    public GuiProgrammableTrigger(InventoryPlayer inventory,TileEntityProgrammableTrigger tile){
        super(new ContainerAnimatedScreenSelector(inventory,tile));this.tile=tile;
        for(int i=0;i<4;i++){textures[i]=tile.getTexture(i);modes[i]=tile.getArtworkMode(i);speeds[i]=tile.getArtworkSpeed(i);}
        exact=tile.getExactLevel();states=tile.isLevelStates();xSize=420;ySize=196;
    }
    @Override public void initGui(){
        String draft=channelField==null?ChannelFields.text(tile.getRedstoneChannels()):channelField.getText();
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        if(page==0){
            final int slot=textureTab;
            lists[slot]=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,textures[slot],8,null,
                choice->HousingTextureList.generalTexture(choice)||HousingTextureList.displayTexture(choice))
                .visibleRows(layout.rows(true)).custom(value->{textures[slot]=value;send();}).keepView(lists[slot]);
        }
        String[] names={"0","1-5","6-10","11-15"};int tabs=states?5:3;
        for(int i=0;i<4;i++)if(states||i==0||i==3)buttonList.add(layout.fullTab(90+i,states?i:i==0?0:1,tabs,states?names[i]:i==0?"Off":"On"));
        buttonList.add(layout.fullTab(111,tabs-1,tabs,"Redstone"));
        if(page==0){
            for(int i=0;i<3;i++){
                buttonList.add(layout.choice(1+i,i,3,66,I18n.format("gui.vandorlabs.selector."+new String[]{"off","static","animated"}[i])));
                buttonList.add(layout.choice(10+i,i,3,100,I18n.format("gui.vandorlabs.selector."+new String[]{"slow","normal","fast"}[i])));
            }
        }else{
            buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(101,guiLeft+12,guiTop+96,xSize-24,20,states?"Artwork: Signal bands":"Artwork: Off / On"));
            buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(102,guiLeft+12,guiTop+120,xSize-24,20,exact<0?"Trigger: Any signal":"Trigger: Exactly "+exact));
        }
        channelField=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+68,xSize-24,18);ChannelFields.configure(channelField);
        channelField.setText(draft);channelField.setVisible(page==1);channelField.setFocused(false);buttonList.add(layout.done(100));refreshButtons();
    }
    private void refreshButtons(){
        for(GuiButton b:buttonList){
            if(b.id>=90&&b.id<=93)b.enabled=page!=0||b.id!=90+textureTab;
            else if(b.id==111)b.enabled=page!=1;
            else if(b.id>=1&&b.id<=3)b.enabled=b.id-1!=modes[textureTab];
            else if(b.id>=10&&b.id<=12)b.enabled=b.id-10!=speeds[textureTab];
        }
    }
    private int channel(){return ChannelFields.first(channelField);}
    private void send(){
        int channel=channel();if(channel<0)return;
        com.vandorlabs.redstone.ChannelList channels=ChannelFields.parse(channelField);
        tile.configureArtwork(textures[0],textures[3],states,exact,textures[1],textures[2],channels,modes,speeds);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrigger(tile.getPos(),textures[0],textures[3],channel)
                .withChannels(channels).withLevels(states,exact,textures[1],textures[2]).withArtwork(modes,speeds));
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,101,102))return;
        // GuiScreen iterates the live button list; growing it here can redispatch this toggle.
        if(button==0&&page==1)for(GuiButton option:new java.util.ArrayList<>(buttonList))if(option.id==101&&option.mousePressed(mc,x,y)){
            option.playPressSound(mc.getSoundHandler());actionPerformed(option);return;
        }
        if(page==0&&activeList().click(x,y,button)){textures[textureTab]=activeList().selected();send();return;}
        super.mouseClicked(x,y,button);if(page==1)channelField.mouseClicked(x,y,button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed){if(page==0&&activeList().drag(y))return;super.mouseClickMove(x,y,button,elapsed);}
    @Override protected void mouseReleased(int x,int y,int button){for(HousingTextureList list:lists)if(list!=null)list.release();super.mouseReleased(x,y,button);}
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int x=Mouse.getEventX()*width/mc.displayWidth,y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(page==0)activeList().wheel(x,y,Mouse.getEventDWheel());
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==111){page=1;initGui();return;}
        if(button.id>=90&&button.id<=93){textureTab=button.id-90;page=0;initGui();return;}
        if(button.id>=1&&button.id<=3){modes[textureTab]=button.id-1;refreshButtons();send();return;}
        if(button.id>=10&&button.id<=12){speeds[textureTab]=button.id-10;refreshButtons();send();return;}
        if(button.id==101){states=!states;if(!states&&(textureTab==1||textureTab==2))textureTab=3;send();initGui();return;}
        if(button.id==102){exact=GuiOptionCycle.next(exact,-1,15);button.displayString=exact<0?"Trigger: Any signal":"Trigger: Exactly "+exact;send();return;}
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
        fontRenderer.drawString("Programmable Trigger Block",guiLeft+12,guiTop+8,0xFFFFFFFF);
        if(page==0){activeList().draw(fontRenderer,mouseX,mouseY);fontRenderer.drawString("Display",layout.controlsX,guiTop+54,0xDAE8F0);fontRenderer.drawString("Speed",layout.controlsX,guiTop+88,0xDAE8F0);}
        else fontRenderer.drawString("Receive channels",guiLeft+12,guiTop+54,0xDAE8F0);
        super.drawScreen(mouseX,mouseY,partial);DialogOverlay.prepare();channelField.drawTextBox();
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
