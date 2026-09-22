package com.vandorlabs.client;

import com.vandorlabs.container.ContainerSpaceDoor;
import com.vandorlabs.network.MessageSpaceDoor;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.render.SpaceDoorMotion;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** List selector with server-authoritative live updates, like programmable screens. */
public class GuiSpaceDoor extends GuiContainer {
    private static final int ROW_H=14, LIST_ROWS=10, LIST_H=ROW_H*LIST_ROWS;
    private final TileEntitySpaceDoor tile;
    private int design,detail,scrollIndex,lastValidChannel;
    private SpaceDoorMotion motion;
    private boolean framed,middle,draggingScrollbar;
    private int listLeft,listTop,listRight,listBottom;
    private GuiTextField channelField;
    private GuiButton done,motionButton;
    private int previewX,previewY;
    private static final String[] LABELS={"Observation","Airlock","Standard","Security","Reactor Service",
            "Viewport","Laboratory","Cargo","Ventilation","Cargo Lift","Blast Shield","Glazed Hangar",
            "Quarantine Seal","Reactor Barrier","Modular Shutter"};
    private static final String[] SIZES={"Small (128 x 256)","Medium (256 x 512)","Large (512 x 1024)"};
    private static final String[] TEXTURES={"observation","airlock","standard","security","reactor",
            "door_viewport","door_laboratory","door_cargo","door_ventilation","lift_cargo_lift",
            "lift_blast_shield","lift_glazed_hangar","lift_quarantine_seal","lift_reactor_barrier","lift_modular_shutter"};

    public GuiSpaceDoor(TileEntitySpaceDoor tile) {
        super(new ContainerSpaceDoor(tile));
        this.tile=tile;
        design=tile.getDesign(); detail=tile.getDetail(); framed=tile.isFramed();
        motion=SpaceDoorMotion.fromSettings(tile.isSliding(),tile.getSlideDirection());
        middle=tile.isMiddle();
        lastValidChannel=tile.getRedstoneChannel();
        scrollIndex=Math.max(0,Math.min(maxScroll(),design-LIST_ROWS/2));
        xSize=430; ySize=270;
    }
    @Override public void initGui() {
        String channelText=channelField==null?Integer.toString(lastValidChannel):channelField.getText();
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        listLeft=guiLeft+12; listRight=listLeft+140; listTop=guiTop+40; listBottom=listTop+LIST_H;
        previewX=guiLeft+338; previewY=guiTop+42;
        motionButton=new GuiButton(10,guiLeft+168,guiTop+40,154,20,motion.label);
        buttonList.add(motionButton);
        buttonList.add(new GuiButton(11,guiLeft+168,guiTop+66,154,20,SIZES[detail]));
        buttonList.add(new GuiButton(12,guiLeft+168,guiTop+92,154,20,framed?"Frame: Framed":"Frame: Bare"));
        buttonList.add(new GuiButton(14,guiLeft+168,guiTop+118,154,20,middle?"Position: Middle":"Position: Edge"));
        channelField=new GuiTextField(0,fontRenderer,guiLeft+168,guiTop+190,154,18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text->text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(channelText);
        done=new GuiButton(1,guiLeft+109,guiTop+242,212,20,"Done");
        buttonList.add(done);
        updateChannelValidity();
    }
    private int channel() {
        try {
            long value=Long.parseLong(channelField.getText());
            return value>Integer.MAX_VALUE?-1:(int)value;
        } catch (NumberFormatException e) { return -1; }
    }
    private void updateChannelValidity() {
        done.enabled=channel()>=0;
        channelField.setTextColor(done.enabled?0xE0E0E0:0xFF7777);
    }
    private void sendUpdate() {
        if (channel()>=0) lastValidChannel=channel();
        // An incomplete channel edit must not prevent previewing appearance.
        PacketHandler.INSTANCE.sendToServer(new MessageSpaceDoor(tile.getPos(),design,detail,framed,
                lastValidChannel,motion.direction,middle,motion.sliding));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id==1) { if (channel()>=0) { sendUpdate(); mc.player.closeScreen(); } return; }
        if (button.id==10) {
            motion=motion.next();
            motionButton.displayString=motion.label;
        }
        else if (button.id==11) { detail=(detail+1)%SIZES.length; button.displayString=SIZES[detail]; }
        else if (button.id==12) { framed=!framed; button.displayString=framed?"Frame: Framed":"Frame: Bare"; }
        else if (button.id==14) { middle=!middle; button.displayString=middle?"Position: Middle":"Position: Edge"; }
        else return;
        sendUpdate();
    }
    private int maxScroll() { return Math.max(0,LABELS.length-LIST_ROWS); }
    private void clampScroll() { scrollIndex=Math.max(0,Math.min(maxScroll(),scrollIndex)); }
    private void select(int index) {
        if (index>=0 && index<LABELS.length && index!=design) { design=index; sendUpdate(); }
    }
    private void dragScrollbarTo(int y) {
        scrollIndex=Math.round((float)(y-listTop)/LIST_H*maxScroll()); clampScroll();
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        channelField.mouseClicked(x,y,button);
        if (button==0 && y>=listTop && y<listBottom) {
            if (x>=listRight && x<listRight+8) { draggingScrollbar=true; dragScrollbarTo(y); return; }
            if (x>=listLeft && x<listRight) { select(scrollIndex+(y-listTop)/ROW_H); return; }
        }
        super.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int state) {
        draggingScrollbar=false; super.mouseReleased(x,y,state);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed) {
        if (draggingScrollbar) dragScrollbarTo(y); else super.mouseClickMove(x,y,button,elapsed);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();
        int x=Mouse.getEventX()*width/mc.displayWidth;
        int y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if (wheel!=0 && x>=listLeft && x<listRight+8 && y>=listTop && y<listBottom) {
            scrollIndex+=wheel>0?-1:1; clampScroll();
        }
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
            if (design<scrollIndex) scrollIndex=design;
            if (design>=scrollIndex+LIST_ROWS) scrollIndex=design-LIST_ROWS+1;
            clampScroll(); return;
        }
        super.keyTyped(c,key);
    }
    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);
        drawRect(listLeft-1,listTop-1,listRight+9,listBottom+1,0xFF070E14);
        for (int row=0;row<LIST_ROWS;row++) {
            int index=scrollIndex+row;
            if (index>=LABELS.length) break;
            int y=listTop+row*ROW_H;
            boolean hover=mouseX>=listLeft && mouseX<listRight && mouseY>=y && mouseY<y+ROW_H;
            drawRect(listLeft,y,listRight,y+ROW_H,index==design?0xFF385F78:hover?0xFF293F4F:0xFF14212B);
            fontRenderer.drawString(LABELS[index],listLeft+4,y+3,index==design?0xFFFFFF:0xDAE8F0);
        }
        int thumb=Math.max(12,LIST_H*LIST_ROWS/LABELS.length);
        int top=listTop+(maxScroll()==0?0:(LIST_H-thumb)*scrollIndex/maxScroll());
        drawRect(listRight+1,top,listRight+7,top+thumb,0xFF7895A8);
        drawRect(previewX-2,previewY-2,previewX+82,previewY+162,0xFF070E14);
        drawRect(previewX,previewY,previewX+80,previewY+160,0xFF26343D);
        int nativeWidth=128<<detail;
        ResourceLocation preview=new ResourceLocation("vandorlabs","textures/blocks/space_doors/"
                +TileEntitySpaceDoor.DETAILS[detail]+"/"+TEXTURES[design]+".png");
        mc.getTextureManager().bindTexture(preview);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        drawScaledCustomSizeModalRect(previewX,previewY,0,0,nativeWidth,nativeWidth*2,
                80,160,nativeWidth,nativeWidth*2);
        GlStateManager.disableBlend();
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString("Space Door",12,8,0xFFFFFF);
        fontRenderer.drawString("Door type",12,28,0xDAE8F0);
        fontRenderer.drawString("Options",168,28,0xDAE8F0);
        fontRenderer.drawString("Preview",338,28,0xDAE8F0);
        fontRenderer.drawString(motion.sliding?"Hinges: Off":"Hinges: On",338,208,0xDAE8F0);
        fontRenderer.drawString("Channel (0 = none)",168,176,0xDAE8F0);
        if (channel()<0) fontRenderer.drawString("Invalid channel",168,213,0xFF7777);
        fontRenderer.drawString("Changes apply live to both paired leaves",12,224,0xDAE8F0);
    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); super.drawScreen(x,y,partial); channelField.drawTextBox();
    }
}
