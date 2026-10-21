package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelList;

import com.vandorlabs.container.ContainerSpaceDoor;
import com.vandorlabs.network.MessageSpaceDoor;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.render.SpaceDoorMotion;
import com.vandorlabs.persistence.SpaceDoorData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** List selector with server-authoritative live updates, like programmable screens. */
public class GuiSpaceDoor extends GuiMaterialPicker {
    private static final int SECURITY_CARDS_Y=196;
    private int textureLayout;
    private final TileEntitySpaceDoor tile;
    private HousingTextureList textureList;
    private ProgrammableDialogLayout layout;
    private int faceTexture;
    private int design,detail,depth,lastValidChannel;
    private int trigger,panelMode,securityMode,securityAllowed;
    private SpaceDoorMotion motion;
    private boolean framed,hinges,panel,tileTexture;
    private GuiTextField channelField;
    private GuiButton done,motionButton,hingeButton;

    public GuiSpaceDoor(TileEntitySpaceDoor tile) {
        super(new ContainerSpaceDoor(tile));
        this.tile=tile;
        faceTexture=tile.getFaceTexture();tileTexture=tile.isTileTexture();textureLayout=tile.getTextureLayout();
        design=tile.getDesign(); detail=0; framed=tile.isFramed();
        faceTexture=HousingTextureList.doorSizeChoice(faceTexture,0);
        motion=SpaceDoorMotion.fromSettings(tile.isSliding(),tile.getSlideDirection());
        depth=tile.getPlacementDepth();
        hinges=tile.hasHinges();
        panel=tile.hasPanel();panelMode=tile.panelMode();securityMode=tile.securityMode();securityAllowed=tile.keyReader().effectiveAllowed();
        trigger=tile.getTrigger();
        lastValidChannels=tile.getRedstoneChannels();lastValidChannel=lastValidChannels.first();
        xSize=420; ySize=240;
    }
    @Override protected HousingTextureList materialPicker(){return textureList;}
    @Override public void initGui() {
        String channelText=channelField==null?lastValidChannels.toString():channelField.getText();
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int listLeft=layout.listX,listTop=guiTop+38;
        textureList=HousingTextureList.forDoors(detail,listLeft,listTop,layout.listWidth,faceTexture<0?ScreenHousingTextures.doorIndex(design,detail):faceTexture,tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor)
                .visibleRows(Math.max(2,(ySize-46)/HousingTextureList.ROW_HEIGHT)).custom(value->{faceTexture=value;tile.setFaceTexture(value);sendUpdate();});
        motionButton=layout.control(10,30,motion.label);buttonList.add(motionButton);
        buttonList.add(layout.choice(12,0,2,52,framed?"Frame: Framed":"Frame: Bare"));
        buttonList.add(layout.control(16,72,triggerLabel()));buttonList.add(layout.choice(13,0,2,92,depthLabel()));
        hingeButton=layout.choice(15,1,2,52,"");buttonList.add(hingeButton);updateHingeButton();
        buttonList.add(layout.choice(17,1,2,92,panelLabel()));
        buttonList.add(layout.control(18,112,layoutLabel()));
        channelField=new GuiTextField(0,fontRenderer,layout.controlsX,guiTop+145,154,18);
        ChannelFields.configure(channelField);channelField.setText(channelText);
        buttonList.add(layout.control(19,168,securityLabel()));
        done=layout.done(1);if(ySize<246){done.y=guiTop+ySize-18;done.height=16;}buttonList.add(done);updateChannelValidity();
    }
    private String panelLabel(){return "Panel: "+new String[]{"Off","On","Both"}[panelMode];}
    private String securityLabel(){return (securityMode==0?"Security: ":"")+TileEntitySpaceDoor.securityLabel(securityMode);}
    private int channel() {return ChannelFields.first(channelField);}
    private void updateHingeButton() {
        hingeButton.enabled=!motion.sliding;
        hingeButton.displayString=!motion.sliding && hinges?"Hinges: On":"Hinges: Off";
    }
    private String triggerLabel() {
        return trigger==SpaceDoorData.TRIGGER_REDSTONE_ON?"Trigger: Redstone ON"
                :trigger==SpaceDoorData.TRIGGER_REDSTONE_OFF?"Trigger: Redstone OFF"
                :"Trigger: Disabled";
    }
    private String layoutLabel(){return "Face texture: "+com.vandorlabs.render.TextureLayoutMode.label(textureLayout);}
    private String depthLabel() {
        return "Pos: " + new String[]{"Middle", "Near", "Far"}[depth];
    }
    private void updateChannelValidity() {
        done.enabled=channel()>=0;
        channelField.setTextColor(done.enabled?0xE0E0E0:0xFF7777);
    }
    private ChannelList lastValidChannels;
    private void sendUpdate() {
        if (channel()>=0) {lastValidChannels=ChannelFields.parse(channelField);lastValidChannel=lastValidChannels.first();}
        // An incomplete channel edit must not prevent previewing appearance.
        PacketHandler.INSTANCE.sendToServer(new MessageSpaceDoor(tile.getPos(),design,detail,framed,
                lastValidChannel,motion.direction,depth,motion.sliding,hinges,trigger,panel,faceTexture,tileTexture).withChannels(lastValidChannels).withTextureLayout(textureLayout).withSecurity(panelMode,securityMode,securityAllowed));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id==1) { if (channel()>=0) { sendUpdate(); mc.player.closeScreen(); } return; }
        if (button.id==10) {
            motion=SpaceDoorMotion.values()[GuiOptionCycle.next(motion.ordinal(),SpaceDoorMotion.values().length)];
            if(motion==SpaceDoorMotion.X_SPLIT && !(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor))motion=SpaceDoorMotion.values()[GuiOptionCycle.next(motion.ordinal(),SpaceDoorMotion.values().length)];
            motionButton.displayString=motion.label;
            updateHingeButton();
        }
        else if (button.id==12) { framed=!framed; button.displayString=framed?"Frame: Framed":"Frame: Bare"; }
        else if (button.id==13) { depth=GuiOptionCycle.next(depth,3);if(depth!=0&&panelMode==2){panelMode=1;for(GuiButton option:buttonList)if(option.id==17)option.displayString=panelLabel();} button.displayString=depthLabel(); }
        else if (button.id==15 && !motion.sliding) { hinges=!hinges; updateHingeButton(); }
        else if (button.id==16) { trigger=GuiOptionCycle.next(trigger,3); button.displayString=triggerLabel(); }
        else if(button.id==18){textureLayout=GuiOptionCycle.next(textureLayout,3);tileTexture=textureLayout==1;tile.setTextureLayout(textureLayout);button.displayString=layoutLabel();}
        else if (button.id==17) { panelMode=GuiOptionCycle.next(panelMode,depth==0?3:2);panel=panelMode>0;button.displayString=panelLabel(); }
        else if(button.id==19){int[] modes={0,2,3,4,1};int index=0;while(modes[index]!=securityMode)index++;securityMode=modes[GuiOptionCycle.next(index,5)];button.displayString=securityLabel();}
        else return;
        sendUpdate();
    }
    private void select(int index) {
        if (index>=0 && index<TileEntitySpaceDoor.DESIGNS.length && tile.acceptsDesign(index) && index!=design) { design=index;faceTexture=-1;textureList.setSelected(com.vandorlabs.tiles.ScreenHousingTextures.doorIndex(design,detail));sendUpdate(); }
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,10,11,12,13,15,16,17,18,19))return;
        if((button==0||button==1)&&y>=guiTop+SECURITY_CARDS_Y&&y<guiTop+SECURITY_CARDS_Y+18&&x>=layout.controlsX&&x<layout.controlsX+154){int design=(x-layout.controlsX)/22;securityAllowed^=1<<design;sendUpdate();return;}
        channelField.mouseClicked(x,y,button);
        int before=textureList.selected();
        if(textureList.click(x,y,button)) {
            if(before!=textureList.selected()) {
                int selected=textureList.selected();com.google.gson.JsonObject entry=com.vandorlabs.tiles.ScreenHousingTextures.entry(selected);
                if(entry!=null && entry.has("design")){design=entry.get("design").getAsInt();detail=entry.get("detail").getAsInt();faceTexture=-1;}
                else faceTexture=selected;
                sendUpdate();
            }return;
        }
        super.mouseClicked(x,y,button);
    }

    @Override protected void keyTyped(char c,int key) throws IOException {
        if (key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER) {
            if (channel()>=0) { sendUpdate(); mc.player.closeScreen(); } return;
        }
        String oldText=channelField.getText();
        if (channelField.textboxKeyTyped(c,key)) {
            updateChannelValidity();
            if (!oldText.equals(channelField.getText()) && channel()>=0) sendUpdate();
            return;
        }
        if (!channelField.isFocused() && (key==Keyboard.KEY_UP || key==Keyboard.KEY_DOWN)) {
            select(design+(key==Keyboard.KEY_UP?-1:1));
            return;
        }
        super.keyTyped(c,key);
    }
    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);
        textureList.draw(fontRenderer,mouseX,mouseY);
        for(int i=0;i<7;i++){int x=layout.controlsX+i*22,y=guiTop+SECURITY_CARDS_Y;drawRect(x-1,y-1,x+17,y+17,(securityAllowed&(1<<i))!=0?0xff45765a:0xff28343e);net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.DATA_CARD,1,i),x,y);net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();}

    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor?"Large Programmable Door":"Programmable Door",12,8,0xFFFFFF);
        fontRenderer.drawString("Door type",12,28,0xDAE8F0);

        fontRenderer.drawString("Channels (0 = none)",layout.controlsX-guiLeft,135,0xDAE8F0);
        if (channel()<0) fontRenderer.drawString("Invalid channel",12,ySize-18,0xFF7777);

    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); super.drawScreen(x,y,partial); channelField.drawTextBox();
        if(y>=guiTop+SECURITY_CARDS_Y-1 && y<guiTop+SECURITY_CARDS_Y+17 && x>=layout.controlsX && x<layout.controlsX+154){int card=(x-layout.controlsX)/22;if((x-layout.controlsX)%22<18)drawHoveringText(java.util.Arrays.asList(new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.DATA_CARD,1,card).getDisplayName(),(securityAllowed&(1<<card))!=0?"Allowed":"Blocked","Click to toggle"),x,y);}
    }
}
