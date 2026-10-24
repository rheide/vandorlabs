package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageSyncScreenSelector;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Compact selector containing the categorized input-surface and material lists. */
public class GuiProgrammableInput extends GuiContainer {

    private static final int ROW_H = 12;
    private static final int ROWS = 8;
    private ProgrammableDialogLayout layout;
    private int textureTab;
    private final TileEntityAnimatedScreenSelector te;
    private String selected;
    private int scroll;
    private int listX;
    private int listY;
    private int displayMode;
    private int speedIndex;
    private boolean redstoneEnabled;
    private boolean redstoneInverted;
    private boolean smallInput;
    private int housingTexture;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private GuiTextField channelField;
    private HousingTextureList housingList;
    private ScreenTextureList screenList;

    public GuiProgrammableInput(InventoryPlayer inventory,
            TileEntityAnimatedScreenSelector te) {
        super(new ContainerAnimatedScreenSelector(inventory, te));
        this.te = te;
        this.selected = te.getInputPanel();
        this.displayMode = te.getDisplayMode();
        this.speedIndex = te.getAnimationSpeedIndex();
        this.redstoneEnabled = te.isRedstoneEnabled();
        redstoneInverted=te.isRedstoneInverted();
        this.smallInput = te.isSmallInput();
        this.housingTexture = te.getHousingTexture();
        this.xSize = 420;
        this.ySize = 240;
    }

    @Override public void initGui() {
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int cx=layout.controlsX, rows=layout.rows(true);
        listX=layout.listX;listY=guiTop+54;
        screenList=new ScreenTextureList(listX,listY,layout.listWidth,8,selected,TileEntityAnimatedScreenSelector.INPUT_PANELS,null,true).visibleRows(rows).restore(te.getSurfaceTexture(0)).custom(value->chooseArtwork(screenList)).redstone(te.getPos(),0);
        for(com.vandorlabs.ui.ScreenSelectorDialog.Control control:com.vandorlabs.ui.ScreenSelectorDialog.controls(layout,sharedDialogState(),profile(),key->I18n.format(key))){
            com.vandorlabs.ui.ProgrammableDialogLayout.Button b=control.bounds;GuiButton button=new GuiButton(b.id,b.x,b.y,b.width,b.height,b.id>=90?fontRenderer.trimStringToWidth(b.label,b.width-4):b.label);button.enabled=control.enabled;buttonList.add(button);
        }
        com.vandorlabs.ui.ProgrammableDialogLayout.Button channels=com.vandorlabs.ui.ScreenSelectorDialog.channelBounds(layout,profile());
        channelField=new GuiTextField(channels.id,fontRenderer,channels.x,channels.y,channels.width,channels.height);
        ChannelFields.configure(channelField);

        channelField.setText(te.getRedstoneChannels().toString());
        housingList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,housingTexture)
                .visibleRows(rows).custom(value->{housingTexture=value;te.setHousingTexture(value);sendUpdate();});
        refreshButtons();refreshTabs();
    }
    private void refreshTabs(){for(GuiButton b:buttonList)if(b.id>=90 && b.id<=91)b.enabled=b.id-90!=textureTab;}

    private String sidesLabel(){return "Wall texture: "+com.vandorlabs.render.TextureLayoutMode.label(te.getSurfaceTextureLayout());}
    private com.vandorlabs.ui.ScreenSelectorDialog.Profile profile(){return te.getBlockType() instanceof com.vandorlabs.blocks.BlockDiagonalHalfConsole?com.vandorlabs.ui.ScreenSelectorDialog.Profile.DIAGONAL_HALF:com.vandorlabs.ui.ScreenSelectorDialog.Profile.INPUT;}
    private com.vandorlabs.ui.ScreenSelectorDialog.State sharedDialogState(){return new com.vandorlabs.ui.ScreenSelectorDialog.State(false,textureTab,displayMode,speedIndex,!redstoneEnabled?0:redstoneInverted?2:1,te.getSurfaceTextureLayout(),screenList.framed(),textureTab==0&&screenList.hasPair(),smallInput);}
    private void refreshButtons(){for(com.vandorlabs.ui.ScreenSelectorDialog.Control control:com.vandorlabs.ui.ScreenSelectorDialog.controls(layout,sharedDialogState(),profile(),key->I18n.format(key)))for(GuiButton button:buttonList)if(button.id==control.bounds.id){button.enabled=control.enabled;if(button.id<90)button.displayString=control.bounds.label;}}

    private int maxScroll() {
        return Math.max(0, TileEntityAnimatedScreenSelector.INPUT_PANELS.length - ROWS);
    }

    private int scrollbarThumbHeight() {
        return Math.max(8, ROW_H * ROWS * ROWS
                / TileEntityAnimatedScreenSelector.INPUT_PANELS.length);
    }

    private int scrollbarThumbY() {
        return listY + (ROW_H * ROWS - scrollbarThumbHeight()) * scroll / maxScroll();
    }

    private void revealSelection() {
        for (int i = 0; i < TileEntityAnimatedScreenSelector.INPUT_PANELS.length; i++) {
            if (selected.equals(TileEntityAnimatedScreenSelector.INPUT_PANELS[i])) {
                scroll = Math.max(0, Math.min(i, maxScroll()));
                return;
            }
        }
    }

    private void sendUpdate() {
        if(channel()<0)return;
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(te.getPos(),
                te.getSelectedScreen(), redstoneEnabled, displayMode,
                te.isFramed(), speedIndex, selected, selected, smallInput, channel(),
                housingTexture).withRedstoneInverted(redstoneInverted).withChannels(ChannelFields.parse(channelField)));
    }

    private void chooseArtwork(ScreenTextureList picker) {
        refreshButtons();
        String nativeId=picker.selected();int choice=nativeId==null?picker.choice():-1;
        if(nativeId!=null)selected=nativeId;
        te.setSurfaceTexture(0,choice);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(te.getPos(),0,choice));
        if(nativeId!=null)sendUpdate();
    }

    private int channel() {return ChannelFields.first(channelField);}

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,mouseX,mouseY,button,this::actionPerformed,0,4,32))return;
        if (textureTab==1 && housingList.click(mouseX, mouseY, button)) {
            if (housingTexture != housingList.selected()) {
                housingTexture = housingList.selected();
                sendUpdate();
            }
            return;
        }
        if(textureTab==0 && screenList.click(mouseX,mouseY,button)){if(screenList.picked())chooseArtwork(screenList);return;}
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        screenList.release();
        draggingScrollbar = false;
        if (housingList != null) housingList.release();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
        if (housingList != null && housingList.drag(mouseY)) return;
        if(screenList.drag(mouseY))return;
        super.mouseClickMove(mouseX,mouseY,clickedMouseButton,timeSinceLastClick);
    }

    private void dragScrollbarTo(int mouseY) {
        scroll = scrollForDrag(mouseY, listY, ROW_H * ROWS,
                scrollbarThumbHeight(), maxScroll(), scrollbarDragOffset);
    }

    static int scrollForDrag(int mouseY, int trackTop, int trackHeight,
            int thumbHeight, int maximum, int dragOffset) {
        return com.vandorlabs.ui.DialogInput.scrollForDrag(mouseY,trackTop,trackHeight,thumbHeight,maximum,dragOffset);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        com.vandorlabs.ui.ScreenSelectorDialog.State next=sharedDialogState().action(button.id,GuiOptionCycle.direction());
        if(button.id>=90 && button.id<=91){textureTab=button.id-90;refreshTabs();refreshButtons();return;}
        if (button.id == 20) { if(channel()<0)return;sendUpdate();mc.player.closeScreen();return; }
        if(button.id==4){if(!screenList.hasPair())return;screenList.setFramed(next.framed);chooseArtwork(screenList);return;}
        if(button.id==32){te.setSurfaceTextureLayout(next.sides);button.displayString=sidesLabel();PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageProgrammableSlabSides(te.getPos(),te.getSurfaceTextureLayout()));return;}
        if (button.id == 0) {redstoneEnabled=next.redstoneEnabled();redstoneInverted=next.redstoneInverted();}
        else if (button.id >= 1 && button.id <= 3) displayMode = next.mode;
        else if (button.id >= 10 && button.id <= 12) speedIndex = next.speed;
        else if (button.id == 30) smallInput = next.smallInput;
        else if (button.id == 31) smallInput = next.smallInput;
        else return;
        refreshButtons();
        sendUpdate();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (textureTab==1 && housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel)) return;
        if(textureTab==0)screenList.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,wheel);
    }

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.input.title"),guiLeft+12,guiTop+8,0xFFFFFF);
        if(textureTab==0)screenList.draw(fontRenderer,mouseX,mouseY);
        else housingList.draw(fontRenderer,mouseX,mouseY);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.display"),layout.controlsX,guiTop+26,0xDAE8F0);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.speed"),layout.controlsX,guiTop+58,0xDAE8F0);
        fontRenderer.drawString("Channels (0 = none)",layout.controlsX,guiTop+174,0xDAE8F0);
        super.drawScreen(mouseX,mouseY,partialTicks);channelField.drawTextBox();
    }

    @Override protected void keyTyped(char c,int key) throws IOException {
        if (channelField.textboxKeyTyped(c,key)) { if (channel()>=0) sendUpdate(); }
        else super.keyTyped(c,key);
    }
    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
