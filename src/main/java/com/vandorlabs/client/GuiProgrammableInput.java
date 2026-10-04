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
        this.smallInput = te.isSmallInput();
        this.housingTexture = te.getHousingTexture();
        this.xSize = 420;
        this.ySize = 240;
    }

    @Override public void initGui() {
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int cx=layout.controlsX, rows=layout.rows(true);
        buttonList.add(layout.tab(90,0,2,"Controls"));
        buttonList.add(layout.tab(91,1,2,"Housing"));
        listX=layout.listX;listY=guiTop+54;
        screenList=new ScreenTextureList(listX,listY,layout.listWidth,8,selected,TileEntityAnimatedScreenSelector.INPUT_PANELS,null,true).visibleRows(rows).restore(te.getSurfaceTexture(0)).custom(value->chooseArtwork(screenList)).redstone(te.getPos(),0);
        for(int i=0;i<3;i++) {
            buttonList.add(layout.choice(1+i,i,3,42,I18n.format(new String[]{"gui.vandorlabs.selector.off","gui.vandorlabs.selector.static","gui.vandorlabs.selector.animated"}[i])));
            buttonList.add(layout.choice(10+i,i,3,76,I18n.format(new String[]{"gui.vandorlabs.selector.slow","gui.vandorlabs.selector.normal","gui.vandorlabs.selector.fast"}[i])));
        }
        if(!(te.getBlockType() instanceof com.vandorlabs.blocks.BlockDiagonalHalfConsole)) {
            buttonList.add(layout.choice(30,0,2,98,I18n.format("gui.vandorlabs.input.small")));
            buttonList.add(layout.choice(31,1,2,98,I18n.format("gui.vandorlabs.selector.normal")));
        }
        buttonList.add(layout.control(0,120,""));
        buttonList.add(layout.control(32,142,sidesLabel()));
        channelField=new GuiTextField(40,fontRenderer,cx,guiTop+177,154,18);
        ChannelFields.configure(channelField);

        channelField.setText(te.getRedstoneChannels().toString());
        housingList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,housingTexture)
                .visibleRows(rows).custom(value->{housingTexture=value;te.setHousingTexture(value);sendUpdate();});
        buttonList.add(layout.done(20));refreshButtons();refreshTabs();
    }
    private void refreshTabs(){for(GuiButton b:buttonList)if(b.id>=90 && b.id<=91)b.enabled=b.id-90!=textureTab;}

    private String sidesLabel(){return "Wall texture: "+(te.isSurfaceTileSides()?"Tile":"Fit");}
    private void refreshButtons() {
        for (GuiButton button : buttonList) {
            if (button.id == 0) {
                button.displayString = I18n.format("gui.vandorlabs.selector.redstone") + ": "
                        + I18n.format(redstoneEnabled
                                ? "gui.vandorlabs.selector.on"
                                : "gui.vandorlabs.selector.off_state");
            }
            if (button.id >= 1 && button.id <= 3) button.enabled = displayMode != button.id - 1;
            if (button.id >= 10 && button.id <= 12) button.enabled = speedIndex != button.id - 10;
            if (button.id == 30) button.enabled = !smallInput;
            if (button.id == 31) button.enabled = smallInput;
        }
    }

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
                housingTexture).withChannels(ChannelFields.parse(channelField)));
    }

    private void chooseArtwork(ScreenTextureList picker) {
        String nativeId=picker.selected();int choice=nativeId==null?picker.choice():-1;
        if(nativeId!=null)selected=nativeId;
        te.setSurfaceTexture(0,choice);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(te.getPos(),0,choice));
        if(nativeId!=null)sendUpdate();
    }

    private int channel() {return ChannelFields.first(channelField);}

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
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
        int travel = trackHeight - thumbHeight;
        if (maximum <= 0 || travel <= 0) {
            return 0;
        }
        int thumbTop = Math.max(0, Math.min(travel,
                mouseY - trackTop - dragOffset));
        return Math.round((float) thumbTop * maximum / travel);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if(button.id>=90 && button.id<=91){textureTab=button.id-90;refreshTabs();return;}
        if (button.id == 20) { if(channel()<0)return;sendUpdate();mc.player.closeScreen();return; }
        if(button.id==32){te.setSurfaceTileSides(!te.isSurfaceTileSides());button.displayString=sidesLabel();PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageProgrammableSlabSides(te.getPos(),te.isSurfaceTileSides()));return;}
        if (button.id == 0) redstoneEnabled = !redstoneEnabled;
        else if (button.id >= 1 && button.id <= 3) displayMode = button.id - 1;
        else if (button.id >= 10 && button.id <= 12) speedIndex = button.id - 10;
        else if (button.id == 30) smallInput = true;
        else if (button.id == 31) smallInput = false;
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

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
