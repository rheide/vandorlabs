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
    private HousingTextureList textureList;
    private ProgrammableDialogLayout layout;
    private int faceTexture;
    private int design,detail,depth,scrollIndex,lastValidChannel;
    private int trigger;
    private SpaceDoorMotion motion;
    private boolean framed,hinges,panel,draggingScrollbar,tileTexture;
    private int listLeft,listTop,listRight,listBottom;
    private GuiTextField channelField;
    private GuiButton done,motionButton,hingeButton;
    private int previewX,previewY;
    private static final String[] LABELS={"Observation","Airlock","Standard","Security","Reactor Service",
            "Viewport","Laboratory","Cargo","Ventilation","Cargo Lift","Blast Shield","Glazed Hangar",
            "Quarantine Seal","Reactor Barrier","Modular Shutter","White Glass","Dark Glass","Plain Cargo","Stepped Freight","Observation Leaf","Reinforced Leaf","Warehouse Shutter","Slotted Bay","Cross Braced Bay","Split View Bay","Offset Cargo","Twin Observation","Armored Biparting","Service Freight"};
    private static final String[] SIZES={"Size: Small","Size: Large"};
    private static final String[] TEXTURES={"observation","airlock","standard","security","reactor",
            "door_viewport","door_laboratory","door_cargo","door_ventilation","lift_cargo_lift",
            "lift_blast_shield","lift_glazed_hangar","lift_quarantine_seal","lift_reactor_barrier","lift_modular_shutter","white_glass","dark_glass","plain_cargo","stepped_freight","observation_leaf","reinforced_leaf","warehouse_shutter","slotted_bay","cross_braced_bay","split_view_bay","offset_cargo","twin_observation","armored_biparting","service_freight"};

    public GuiSpaceDoor(TileEntitySpaceDoor tile) {
        super(new ContainerSpaceDoor(tile));
        this.tile=tile;
        faceTexture=tile.getFaceTexture();tileTexture=tile.isTileTexture();
        design=tile.getDesign(); detail=tile.getDetail(); framed=tile.isFramed();
        motion=SpaceDoorMotion.fromSettings(tile.isSliding(),tile.getSlideDirection());
        depth=tile.getPlacementDepth();
        hinges=tile.hasHinges();
        panel=tile.hasPanel();
        trigger=tile.getTrigger();
        lastValidChannels=tile.getRedstoneChannels();lastValidChannel=lastValidChannels.first();
        scrollIndex=Math.max(0,Math.min(maxScroll(),design-LIST_ROWS/2));
        xSize=420; ySize=240;
    }
    @Override public void initGui() {
        String channelText=channelField==null?lastValidChannels.toString():channelField.getText();
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        listLeft=layout.listX;listRight=listLeft+layout.listWidth;listTop=guiTop+38;
        textureList=HousingTextureList.forDoors(detail,listLeft,listTop,layout.listWidth,faceTexture<0?ScreenHousingTextures.doorIndex(design,detail):faceTexture,tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor)
                .visibleRows(Math.max(2,(ySize-46)/HousingTextureList.ROW_HEIGHT)).custom(value->{faceTexture=value;tile.setFaceTexture(value);sendUpdate();});
        motionButton=layout.control(10,30,(motion==SpaceDoorMotion.SIDEWAYS?tile.motionLabel():motion.label));buttonList.add(motionButton);
        buttonList.add(layout.control(11,50,SIZES[detail]));
        buttonList.add(layout.control(12,70,framed?"Frame: Framed":"Frame: Bare"));
        buttonList.add(layout.control(16,90,triggerLabel()));buttonList.add(layout.control(13,110,depthLabel()));
        hingeButton=layout.choice(15,0,2,130,"");buttonList.add(hingeButton);updateHingeButton();
        buttonList.add(layout.choice(17,1,2,130,panel?"Panel: On":"Panel: Off"));
        buttonList.add(layout.control(18,150,layoutLabel()));
        channelField=new GuiTextField(0,fontRenderer,layout.controlsX,guiTop+185,154,18);
        ChannelFields.configure(channelField);channelField.setText(channelText);
        done=layout.done(1);buttonList.add(done);updateChannelValidity();
    }
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
    private String layoutLabel(){return "Face texture: "+(tileTexture?"Tile":"Fit");}
    private String depthLabel() {
        return "Position: " + new String[]{"Middle", "Near", "Far"}[depth];
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
                lastValidChannel,motion.direction,depth,motion.sliding,hinges,trigger,panel,faceTexture,tileTexture).withChannels(lastValidChannels));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id==1) { if (channel()>=0) { sendUpdate(); mc.player.closeScreen(); } return; }
        if (button.id==10) {
            if(motion==SpaceDoorMotion.SIDEWAYS) {
                String label=tile.motionLabel();
                motion=label.equals("Split Horizontal")?SpaceDoorMotion.HORIZONTAL_SPLIT:
                        label.equals("Slide Right")?SpaceDoorMotion.RIGHT:SpaceDoorMotion.LEFT;
            }
            motion=motion.next();
            motionButton.displayString=motion.label;
            updateHingeButton();
        }
        else if (button.id==11) { detail=(detail+1)%SIZES.length; faceTexture=HousingTextureList.doorSizeChoice(faceTexture,detail);initGui(); }
        else if (button.id==12) { framed=!framed; button.displayString=framed?"Frame: Framed":"Frame: Bare"; }
        else if (button.id==13) { depth=(depth+1)%3; button.displayString=depthLabel(); }
        else if (button.id==15 && !motion.sliding) { hinges=!hinges; updateHingeButton(); }
        else if (button.id==16) { trigger=(trigger+1)%3; button.displayString=triggerLabel(); }
        else if(button.id==18){tileTexture=!tileTexture;tile.setTileTexture(tileTexture);button.displayString=layoutLabel();}
        else if (button.id==17) { panel=!panel; button.displayString=panel?"Panel: On":"Panel: Off"; }
        else return;
        sendUpdate();
    }
    private int maxScroll() { return Math.max(0,LABELS.length-LIST_ROWS); }
    private void clampScroll() { scrollIndex=Math.max(0,Math.min(maxScroll(),scrollIndex)); }
    private void select(int index) {
        if (index>=0 && index<LABELS.length && tile.acceptsDesign(index) && index!=design) { design=index;faceTexture=-1;textureList.setSelected(com.vandorlabs.tiles.ScreenHousingTextures.doorIndex(design,detail));sendUpdate(); }
    }
    private void dragScrollbarTo(int y) {
        scrollIndex=Math.round((float)(y-listTop)/LIST_H*maxScroll()); clampScroll();
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
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
    @Override protected void mouseReleased(int x,int y,int state) {
        textureList.release();draggingScrollbar=false; super.mouseReleased(x,y,state);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed) {
        if (textureList.drag(y)) return; else super.mouseClickMove(x,y,button,elapsed);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();
        int x=Mouse.getEventX()*width/mc.displayWidth;
        int y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        textureList.wheel(x,y,wheel);
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
        textureList.draw(fontRenderer,mouseX,mouseY);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor?"Large Programmable Door":"Programmable Door",12,8,0xFFFFFF);
        fontRenderer.drawString("Door type",12,28,0xDAE8F0);

        fontRenderer.drawString("Channels (0 = none)",layout.controlsX-guiLeft,174,0xDAE8F0);
        if (channel()<0) fontRenderer.drawString("Invalid channel",12,ySize-18,0xFF7777);

    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); super.drawScreen(x,y,partial); channelField.drawTextBox();
    }
}
