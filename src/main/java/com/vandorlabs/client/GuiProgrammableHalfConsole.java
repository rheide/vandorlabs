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

/** Two peer input lists for the half-console incline and flat deck. */
public class GuiProgrammableHalfConsole extends GuiContainer {

    private static final int ROW_H = 12;
    private static final int ROWS = 8;
    private ProgrammableDialogLayout layout;
    private int textureTab;
    private final TileEntityAnimatedScreenSelector te;
    private String topPanel;
    private String bottomPanel;
    private int topScroll;
    private int bottomScroll;
    private int draggingList;
    private int scrollbarDragOffset;
    private int left;
    private int right;
    private int top;
    private int displayMode;
    private int speedIndex;
    private boolean redstoneEnabled;
    private boolean redstoneInverted;
    private int housingTexture;
    private GuiTextField channelField;
    private HousingTextureList housingList;
    private ScreenTextureList topList,bottomList;

    public GuiProgrammableHalfConsole(InventoryPlayer inventory,
            TileEntityAnimatedScreenSelector te) {
        super(new ContainerAnimatedScreenSelector(inventory, te));
        this.te = te;
        topPanel = te.getSecondaryInputPanel();
        bottomPanel = te.getInputPanel();
        displayMode = te.getDisplayMode();
        speedIndex = te.getAnimationSpeedIndex();
        redstoneEnabled = te.isRedstoneEnabled();
        redstoneInverted=te.isRedstoneInverted();
        housingTexture = te.getHousingTexture();
        xSize = 420;
        ySize = 240;
    }

    @Override public void initGui() {
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int cx=layout.controlsX, rows=layout.rows(true);
        left=right=layout.listX;top=guiTop+54;
        topList=new ScreenTextureList(left,top,layout.listWidth,8,topPanel,TileEntityAnimatedScreenSelector.INPUT_PANELS,null,true).visibleRows(rows).restore(te.getSurfaceTexture(1)).custom(value->chooseArtwork(topList,1)).redstone(te.getPos(),1);
        bottomList=new ScreenTextureList(left,top,layout.listWidth,8,bottomPanel,TileEntityAnimatedScreenSelector.INPUT_PANELS,null,true).visibleRows(rows).restore(te.getSurfaceTexture(0)).custom(value->chooseArtwork(bottomList,0)).redstone(te.getPos(),0);
        for(com.vandorlabs.ui.ScreenSelectorDialog.Control control:com.vandorlabs.ui.ScreenSelectorDialog.controls(layout,sharedDialogState(),com.vandorlabs.ui.ScreenSelectorDialog.Profile.HALF_CONSOLE,key->I18n.format(key))){
            com.vandorlabs.ui.ProgrammableDialogLayout.Button b=control.bounds;GuiButton button=new GuiButton(b.id,b.x,b.y,b.width,b.height,b.id>=90?fontRenderer.trimStringToWidth(b.label,b.width-4):b.label);button.enabled=control.enabled;buttonList.add(button);
        }
        com.vandorlabs.ui.ProgrammableDialogLayout.Button channels=com.vandorlabs.ui.ScreenSelectorDialog.channelBounds(layout,com.vandorlabs.ui.ScreenSelectorDialog.Profile.HALF_CONSOLE);
        channelField=new GuiTextField(channels.id,fontRenderer,channels.x,channels.y,channels.width,channels.height);
        ChannelFields.configure(channelField);

        channelField.setText(te.getRedstoneChannels().toString());
        housingList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,housingTexture)
                .visibleRows(rows).custom(value->{housingTexture=value;te.setHousingTexture(value);sendUpdate();});
        refreshButtons();refreshTabs();
    }
    private void refreshTabs(){for(GuiButton b:buttonList)if(b.id>=90 && b.id<=92)b.enabled=b.id-90!=textureTab;}

    private String sidesLabel(){return "Sides: "+com.vandorlabs.render.TextureLayoutMode.label(te.getSurfaceTextureLayout());}
    private com.vandorlabs.ui.ScreenSelectorDialog.State sharedDialogState(){ScreenTextureList picker=textureTab==1?topList:bottomList;return new com.vandorlabs.ui.ScreenSelectorDialog.State(true,textureTab,displayMode,speedIndex,!redstoneEnabled?0:redstoneInverted?2:1,te.getSurfaceTextureLayout(),picker.framed(),textureTab<2&&picker.hasPair());}
    private void refreshButtons(){for(com.vandorlabs.ui.ScreenSelectorDialog.Control control:com.vandorlabs.ui.ScreenSelectorDialog.controls(layout,sharedDialogState(),com.vandorlabs.ui.ScreenSelectorDialog.Profile.HALF_CONSOLE,key->I18n.format(key)))for(GuiButton button:buttonList)if(button.id==control.bounds.id){button.enabled=control.enabled;if(button.id<90)button.displayString=control.bounds.label;}}

    private int maxScroll() {
        return Math.max(0, TileEntityAnimatedScreenSelector.INPUT_PANELS.length - ROWS);
    }

    private int reveal(String id) {
        for (int i = 0; i < TileEntityAnimatedScreenSelector.INPUT_PANELS.length; i++) {
            if (id.equals(TileEntityAnimatedScreenSelector.INPUT_PANELS[i])) {
                return Math.max(0, Math.min(i, maxScroll()));
            }
        }
        return 0;
    }

    private void sendUpdate() {
        if(channel()<0)return;
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(te.getPos(),
                te.getSelectedScreen(), redstoneEnabled, displayMode,
                te.isFramed(), speedIndex, bottomPanel, topPanel, false, channel(),
                housingTexture).withRedstoneInverted(redstoneInverted).withChannels(ChannelFields.parse(channelField)));
    }

    private void chooseArtwork(ScreenTextureList picker,int slot) {
        refreshButtons();
        String nativeId=picker.selected();int choice=nativeId==null?picker.choice():-1;
        if(nativeId!=null){if(slot==0)bottomPanel=nativeId;else topPanel=nativeId;}
        te.setSurfaceTexture(slot,choice);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(te.getPos(),slot,choice));
        if(nativeId!=null)sendUpdate();
    }

    private int channel() {return ChannelFields.first(channelField);}

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,mouseX,mouseY,button,this::actionPerformed,0,4,32))return;
        if (textureTab==2 && housingList.click(mouseX, mouseY, button)) {
            if (housingTexture != housingList.selected()) {
                housingTexture = housingList.selected();
                sendUpdate();
            }
            return;
        }
        boolean first=textureTab==0 && bottomList.click(mouseX,mouseY,button),second=textureTab==1 && topList.click(mouseX,mouseY,button);
        if(first || second){ScreenTextureList picker=first?bottomList:topList;if(picker.picked())chooseArtwork(picker,first?0:1);return;}
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX,mouseY,button);
    }

    @Override protected void mouseReleased(int mouseX,int mouseY,int state) {
        topList.release();bottomList.release();
        draggingList = 0;
        if (housingList != null) housingList.release();
        super.mouseReleased(mouseX,mouseY,state);
    }

    @Override protected void mouseClickMove(int mouseX,int mouseY,int button,long elapsed) {
        if (housingList != null && housingList.drag(mouseY)) return;
        if(topList.drag(mouseY) || bottomList.drag(mouseY))return;
        super.mouseClickMove(mouseX,mouseY,button,elapsed);
    }

    private void dragScrollbarTo(int mouseY) {
        int scroll = GuiProgrammableInput.scrollForDrag(mouseY, top, ROW_H * ROWS,
                scrollbarThumbHeight(), maxScroll(), scrollbarDragOffset);
        if (draggingList == 1) bottomScroll = scroll;
        else if (draggingList == 2) topScroll = scroll;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        com.vandorlabs.ui.ScreenSelectorDialog.State next=sharedDialogState().action(button.id,GuiOptionCycle.direction());
        if(button.id>=90 && button.id<=92){textureTab=button.id-90;refreshTabs();refreshButtons();return;}
        if (button.id == 20) { if(channel()<0)return;sendUpdate();mc.player.closeScreen();return; }
        if(button.id==4){ScreenTextureList picker=textureTab==1?topList:bottomList;if(!picker.hasPair())return;picker.setFramed(next.framed);chooseArtwork(picker,textureTab==1?1:0);return;}
        if(button.id==32){te.setSurfaceTextureLayout(next.sides);button.displayString=sidesLabel();PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageProgrammableSlabSides(te.getPos(),te.getSurfaceTextureLayout()));return;}
        if (button.id == 0) {redstoneEnabled=next.redstoneEnabled();redstoneInverted=next.redstoneInverted();}
        else if (button.id >= 1 && button.id <= 3) displayMode = next.mode;
        else if (button.id >= 10 && button.id <= 12) speedIndex = next.speed;
        else return;
        refreshButtons();
        sendUpdate();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (textureTab==2 && housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel)) return;
        int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(textureTab==0)bottomList.wheel(mx,my,wheel);else if(textureTab==1)topList.wheel(mx,my,wheel);
    }

    private int clamp(int value) { return Math.max(0, Math.min(maxScroll(), value)); }

    private int scrollbarThumbHeight() {
        return Math.max(8, ROW_H * ROWS * ROWS
                / TileEntityAnimatedScreenSelector.INPUT_PANELS.length);
    }

    private int scrollbarThumbY(int scroll) {
        return top + (ROW_H * ROWS - scrollbarThumbHeight()) * scroll
                / Math.max(1,maxScroll());
    }

    private void drawList(int x, int scroll, String selected) {
        drawRect(x - 1, top - 1, x + 121, top + ROW_H * ROWS + 1, 0xFF000000);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= TileEntityAnimatedScreenSelector.INPUT_PANELS.length) break;
            String id = TileEntityAnimatedScreenSelector.INPUT_PANELS[index];
            int yy = top + row * ROW_H;
            if (id.equals(selected)) drawRect(x, yy, x + 112, yy + ROW_H, 0xFF2A4A6A);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(
                    I18n.format("gui.vandorlabs.console.input." + id), 106),
                    x + 3, yy + 2, id.equals(selected) ? 0xFFFFE08A : 0xFFD8D8D8);
        }
        int thumbH = scrollbarThumbHeight();
        int thumbY = scrollbarThumbY(scroll);
        drawRect(x + 114, top, x + 120, top + ROW_H * ROWS, 0xFF303038);
        drawRect(x + 114, thumbY, x + 120, thumbY + thumbH, 0xFF808090);
    }

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.half_console.title"),guiLeft+12,guiTop+8,0xFFFFFF);
        if(textureTab==0)bottomList.draw(fontRenderer,mouseX,mouseY);
        else if(textureTab==1)topList.draw(fontRenderer,mouseX,mouseY);
        else housingList.draw(fontRenderer,mouseX,mouseY);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.display"),layout.controlsX,guiTop+30,0xDAE8F0);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.speed"),layout.controlsX,guiTop+64,0xDAE8F0);
        fontRenderer.drawString("Channels (0 = none)",layout.controlsX,guiTop+166,0xDAE8F0);
        super.drawScreen(mouseX,mouseY,partialTicks);channelField.drawTextBox();
    }

    @Override protected void keyTyped(char c,int key) throws IOException {
        if (channelField.textboxKeyTyped(c,key)) { if (channel()>=0) sendUpdate(); }
        else super.keyTyped(c,key);
    }
    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override protected void drawGuiContainerBackgroundLayer(float p, int x, int y) {}
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {}
    @Override public boolean doesGuiPauseGame() { return false; }
}
