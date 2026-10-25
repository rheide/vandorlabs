package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageProgrammableTrigger;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Two housing finish lists, selected by the trigger's redstone state. */
public final class GuiProgrammableTrigger extends GuiContainer {
    private final TileEntityProgrammableTrigger tile;
    private HousingTextureList offList;
    private HousingTextureList onList;
    private GuiTextField channelField;
    private ProgrammableDialogLayout layout;
    private int textureTab;
    private int off;
    private int on,low,medium,exact;private boolean states;
    private HousingTextureList lowList,mediumList;
    private HousingTextureList activeList(){return textureTab==0?offList:textureTab==1?lowList:textureTab==2?mediumList:onList;}

    public GuiProgrammableTrigger(InventoryPlayer inventory,
            TileEntityProgrammableTrigger tile) {
        super(new ContainerAnimatedScreenSelector(inventory, tile));
        this.tile = tile;
        off = tile.getHousingTexture();
        on = tile.getOnTexture();low=tile.getLowTexture();medium=tile.getMediumTexture();exact=tile.getExactLevel();states=tile.isLevelStates();
        xSize = 420;
        ySize = 196;
    }

    @Override public void initGui() {
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        offList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,off).visibleRows(layout.rows(true)).custom(value->{off=value;send();});
        onList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,on).visibleRows(layout.rows(true)).custom(value->{on=value;send();});
        lowList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,low).visibleRows(layout.rows(true)).custom(value->{low=value;send();});
        mediumList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,medium).visibleRows(layout.rows(true)).custom(value->{medium=value;send();});
        for(com.vandorlabs.ui.TriggerDialog.Control control:com.vandorlabs.ui.TriggerDialog.controls(layout,new com.vandorlabs.ui.TriggerDialog.State(textureTab,states,exact))){com.vandorlabs.ui.ProgrammableDialogLayout.Button b=control.bounds;buttonList.add(new GuiButton(b.id,b.x,b.y,b.width,b.height,b.label));}
        channelField=new GuiTextField(0,fontRenderer,layout.controlsX,guiTop+42,154,18);
        ChannelFields.configure(channelField);
        channelField.setText(tile.getRedstoneChannels().toString());refreshTabs();
    }
    private void refreshTabs(){
        for(com.vandorlabs.ui.TriggerDialog.Control control:com.vandorlabs.ui.TriggerDialog.controls(layout,new com.vandorlabs.ui.TriggerDialog.State(textureTab,states,exact)))for(GuiButton button:buttonList)if(button.id==control.bounds.id){com.vandorlabs.ui.ProgrammableDialogLayout.Button bounds=control.bounds;button.x=bounds.x;button.y=bounds.y;button.width=bounds.width;button.height=bounds.height;button.visible=control.visible;button.enabled=control.enabled;button.displayString=bounds.id>=90&&bounds.id<=93?fontRenderer.trimStringToWidth(bounds.label,bounds.width-4):bounds.label;}
    }

    private String statesLabel(){return com.vandorlabs.ui.TriggerDialog.bandsLabel(states);}
    private String exactLabel(){return com.vandorlabs.ui.TriggerDialog.exactLabel(exact);}
    private int channel() {return ChannelFields.first(channelField);}

    private void send() {
        int value = channel();
        if (value < 0) return;
        tile.configureLevels(states,exact,low,medium);tile.configure(off,on,ChannelFields.parse(channelField));
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrigger(
                tile.getPos(), off, on, value).withChannels(ChannelFields.parse(channelField)).withLevels(states,exact,low,medium));
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,101,102))return;
        if(activeList().click(x,y,button)){
            int selected=activeList().selected();if(textureTab==0)off=selected;else if(textureTab==1)low=selected;else if(textureTab==2)medium=selected;else on=selected;send();return;
        }
        super.mouseClicked(x, y, button);
        channelField.mouseClicked(x, y, button);
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (activeList().drag(y)) return;
        super.mouseClickMove(x, y, button, elapsed);
    }

    @Override protected void mouseReleased(int x, int y, int button) {
        offList.release();
        onList.release();lowList.release();mediumList.release();
        super.mouseReleased(x, y, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        activeList().wheel(x,y,wheel);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if(button.id>=90 && button.id<=93){textureTab=new com.vandorlabs.ui.TriggerDialog.State(textureTab,states,exact).action(button.id,GuiOptionCycle.direction()).tab;refreshTabs();return;}
        if(button.id==101){com.vandorlabs.ui.TriggerDialog.State next=new com.vandorlabs.ui.TriggerDialog.State(textureTab,states,exact).action(101,GuiOptionCycle.direction());states=next.bands;textureTab=next.tab;refreshTabs();button.displayString=statesLabel();send();}
        if(button.id==102){exact=new com.vandorlabs.ui.TriggerDialog.State(textureTab,states,exact).action(102,GuiOptionCycle.direction()).exact;button.displayString=exactLabel();send();}
        if (button.id == 100 && channel()>=0) { send(); mc.player.closeScreen(); }
    }

    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if(channel()<0)return;
            send(); mc.player.closeScreen(); return;
        }
        if (channelField.textboxKeyTyped(typed, key)) send();
        else super.keyTyped(typed, key);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        channelField.updateCursorCounter();
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX,
            int mouseY) { }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 24, 0xFF202028);
        fontRenderer.drawString("Programmable Trigger Block", guiLeft + 12,
                guiTop + 8, 0xFFFFFFFF);
        activeList().draw(fontRenderer,mouseX,mouseY);
        fontRenderer.drawString("Channels (0 = none)",layout.controlsX,guiTop+30,0xDAE8F0);
        super.drawScreen(mouseX, mouseY, partial);
        channelField.drawTextBox();
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    private void drawPreview(int x, int texture) {
        int y = guiTop + 65;
        drawRect(x - 2, y - 2, x + 50, y + 50, 0xFF505058);
        drawRect(x, y, x + 48, y + 48, 0xFF202028);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        drawTexturedModalRect(x, y, mc.getTextureMapBlocks().getAtlasSprite(
                ScreenHousingTextures.texture(texture)), 48, 48);
        GlStateManager.disableBlend();
    }
}
