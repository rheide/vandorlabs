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
    private static final int SECURITY_CARDS_Y=136;
    private int textureLayout;
    private int settingsPage;
    private final TileEntitySpaceDoor tile;
    private HousingTextureList textureList,glassTextures;private FrameAppearancePane framePane;
    private com.vandorlabs.tiles.DoorGlassAppearance glassAppearance;
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
        this.tile=tile;glassAppearance=tile.glassAppearance();
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
    @Override protected HousingTextureList materialPicker(){return settingsPage==4?glassTextures:settingsPage==1&&framePane!=null?framePane.picker():settingsPage==0?textureList:null;}
    @Override public void initGui() {
        String channelText=channelField==null?lastValidChannels.toString():channelField.getText();
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int listLeft=layout.listX,listTop=guiTop+54;
        if(settingsPage==4&&!glassDesign())settingsPage=0;
        int[] pages=glassDesign()?new int[]{0,1,4,3,2}:new int[]{0,1,3,2};String[] labels={"Door","Frame","Redstone","Security","Glass"};
        for(int at=0;at<pages.length;at++)buttonList.add(layout.fullTab(190+pages[at],at,pages.length,labels[pages[at]]));
        textureList=HousingTextureList.forDoors(detail,listLeft,listTop,layout.listWidth,faceTexture<0?ScreenHousingTextures.doorIndex(design,detail):faceTexture,tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor)
                .visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT)).custom(value->{faceTexture=value;tile.setFaceTexture(value);sendUpdate();});
        motionButton=layout.control(10,54,motion.label);buttonList.add(motionButton);
        buttonList.add(layout.choice(12,0,2,52,framed?"Frame: Framed":"Frame: Bare"));
        buttonList.add(new RedstoneActivationButton(16,guiLeft+12,guiTop+96,xSize-24,trigger));buttonList.add(layout.control(13,102,depthLabel()));
        hingeButton=layout.control(15,78,"");buttonList.add(hingeButton);updateHingeButton();
        buttonList.add(layout.control(17,64,panelLabel()));
        buttonList.add(layout.control(18,126,layoutLabel()));
        channelField=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+68,xSize-24,18);
        ChannelFields.configure(channelField);channelField.setText(channelText);
        buttonList.add(layout.control(19,90,securityLabel()));
        if(settingsPage==4){
            glassTextures=HousingTextureList.forGlassMaterials(0,listLeft,listTop,layout.listWidth,glassAppearance.shade,glassAppearance.material).visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT)).keepView(glassTextures);
            if(glassAppearance.material==com.vandorlabs.tiles.DoorGlassAppearance.ORIGINAL)glassTextures.setSelected(-1);
            GuiButton tint=layout.control(20,54,"Tint: "+com.vandorlabs.tiles.GlassTextures.tintLabel(glassAppearance.tint));tint.enabled=glassAppearance.material!=com.vandorlabs.tiles.DoorGlassAppearance.ORIGINAL;buttonList.add(tint);
            GuiButton repeat=layout.control(21,78,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(glassAppearance.layout));repeat.visible=glassAppearance.material>=0;buttonList.add(repeat);
            GuiButton original=layout.control(22,102,"Original glass");original.enabled=glassAppearance.material!=com.vandorlabs.tiles.DoorGlassAppearance.ORIGINAL;buttonList.add(original);
        }
        done=layout.done(1);if(ySize<246){done.y=guiTop+ySize-18;done.height=16;}buttonList.add(done);updateChannelValidity();refreshPages();if(settingsPage==1){if(framePane==null)framePane=new FrameAppearancePane(this,tile,()->{framed=!framed;sendUpdate();},()->framed);framePane.init(54,buttonList);}
    }
    private void refreshPages(){for(GuiButton b:buttonList){if(b.id>=190&&b.id<=194){b.enabled=b.id-190!=settingsPage;continue;}if(b.id==1)continue;if(b.id>=20&&b.id<=22)continue;b.visible=settingsPage==0&&(b.id==10||b.id==13||b.id==15||b.id==18)||settingsPage==2&&(b.id==16||b.id==198)||settingsPage==3&&(b.id==17||b.id==19);}channelField.setVisible(settingsPage==2);if(!channelField.getVisible())channelField.setFocused(false);}
    private net.minecraft.item.ItemStack companyCard(){return CredentialFilterPicker.sample(false,securityAllowed);}
    String companyLabel(){return CredentialFilterPicker.label(false,securityAllowed);}
    private String panelLabel(){return "Panel: "+new String[]{"Off","On","Both"}[panelMode];}
    private String securityLabel(){return (securityMode==0?"Security: ":"")+TileEntitySpaceDoor.securityLabel(securityMode);}
    private int channel() {return ChannelFields.first(channelField);}
    private void updateHingeButton() {
        hingeButton.enabled=!motion.sliding;
        hingeButton.displayString=!motion.sliding && hinges?"Hinges: On":"Hinges: Off";
    }

    private boolean glassDesign(){return design==15||design==16||design==29;}
    void chooseGlass(int choice){int shade=glassAppearance.shade,material=choice;if(choice>=HousingTextureList.CANOPY_GLASS_BASE&&choice<HousingTextureList.CANOPY_GLASS_BASE+com.vandorlabs.tiles.GlassTextures.IDS.length){shade=choice-HousingTextureList.CANOPY_GLASS_BASE;material=-1;}glassAppearance=new com.vandorlabs.tiles.DoorGlassAppearance(material,shade,glassAppearance.tint,glassAppearance.layout);faceTexture=-1;sendUpdate();sendGlass();initGui();}
    private void sendGlass(){tile.setGlassAppearance(glassAppearance);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageDoorGlass(tile.getPos(),glassAppearance));}
    private String layoutLabel(){return "Texture: "+com.vandorlabs.render.TextureLayoutMode.label(textureLayout);}
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
        if(button.id>=20&&button.id<=22){
            if(button.id==22)glassAppearance=com.vandorlabs.tiles.DoorGlassAppearance.DEFAULT;
            else glassAppearance=new com.vandorlabs.tiles.DoorGlassAppearance(glassAppearance.material,glassAppearance.shade,button.id==20?GuiOptionCycle.next(glassAppearance.tint,com.vandorlabs.tiles.GlassTextures.TINT_COUNT):glassAppearance.tint,button.id==21?GuiOptionCycle.next(glassAppearance.layout,3):glassAppearance.layout);
            sendGlass();initGui();return;
        }
        if(settingsPage==1&&framePane!=null&&framePane.action(button))return;if(button.id>=190&&button.id<=194){settingsPage=button.id-190;initGui();return;}if(button.id==198){PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(tile.getPos(),0));return;}

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
        else if (button.id==16) { trigger=GuiOptionCycle.next(trigger,3); ((RedstoneActivationButton)button).setMode(trigger); }
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
        if(settingsPage==4&&glassTextures!=null&&glassTextures.click(x,y,button)){if(glassTextures.picked())chooseGlass(glassTextures.selected());return;}
        if(settingsPage==1&&framePane!=null&&framePane.click(x,y,button))return;
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,10,11,12,13,15,16,17,18,19,20,21))return;
        if(settingsPage==3&&CredentialFilterPicker.click(this,x,y,button,layout.controlsX,guiTop+SECURITY_CARDS_Y,false,securityAllowed,mask->{securityAllowed=mask;sendUpdate();}))return;
        if(channelField.getVisible())channelField.mouseClicked(x,y,button);
        int before=textureList.selected();
        if(settingsPage==0&&textureList.click(x,y,button)) {
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
        if (channelField.getVisible() && channelField.textboxKeyTyped(c,key)) {
            updateChannelValidity();
            if (!oldText.equals(channelField.getText()) && channel()>=0) sendUpdate();
            return;
        }
        if (settingsPage==0 && !channelField.isFocused() && (key==Keyboard.KEY_UP || key==Keyboard.KEY_DOWN)) {
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
        if(settingsPage==0)textureList.draw(fontRenderer,mouseX,mouseY);if(settingsPage==4&&glassTextures!=null)glassTextures.draw(fontRenderer,mouseX,mouseY);if(settingsPage==1&&framePane!=null)framePane.draw(mouseX,mouseY);
        if(settingsPage==3)CredentialFilterPicker.draw(mc,fontRenderer,layout.controlsX,guiTop+SECURITY_CARDS_Y,false,securityAllowed);

    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor?"Large Programmable Door":"Programmable Door",12,8,0xFFFFFF);


        if(settingsPage==2)fontRenderer.drawString("Receive channels",12,54,0xDAE8F0);
        if (channel()<0) fontRenderer.drawString("Invalid channel",12,ySize-18,0xFF7777);

    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground();super.drawScreen(x,y,partial);DialogOverlay.prepare();DialogHeader.logo(guiLeft,guiTop,xSize); channelField.drawTextBox();
        if(settingsPage==3&&y>=guiTop+SECURITY_CARDS_Y-1&&y<guiTop+SECURITY_CARDS_Y+17&&x>=layout.controlsX&&x<layout.controlsX+154)drawHoveringText(java.util.Arrays.asList("Empty filter: any card","Left/right-click the slot to cycle company","Click the company name to clear","The sample card is not consumed"),x,y);
    }
}
