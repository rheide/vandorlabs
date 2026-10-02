package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageProgrammableLight;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Texture picker with a live preview and a 0 to 15 light level slider. */
public final class GuiProgrammableLight extends GuiContainer {
    private final TileEntityProgrammableLight tile;
    private int selected;
    private int level;
    private boolean join;
    private int housing;
    private int trigger;
    private boolean small,tileSides;
    private HousingTextureList housingList,faceList;
    private GuiTextField channelField;
    private boolean draggingLevel;
    private static final int SLIDER_W = 286;

    public GuiProgrammableLight(InventoryPlayer inventory, TileEntityProgrammableLight tile) {
        super(new ContainerAnimatedScreenSelector(inventory, tile));
        this.tile = tile;
        small=tile.isSmallInput();tileSides=tile.isSlabTileSides();
        selected = tile.getFaceTexture();
        level = tile.getLightLevel();
        join = tile.isJoin();
        housing = tile.getHousingTexture();
        trigger = tile.getTrigger();
        xSize = 420;
        ySize = 240;
    }

    @Override public void initGui() {
        int rows=Math.max(4,Math.min(8,(height-168)/HousingTextureList.ROW_HEIGHT));
        ySize=152+rows*HousingTextureList.ROW_HEIGHT;
        int extra=ySize-240;
        int listBudget=(rows*HousingTextureList.ROW_HEIGHT+11)/12;
        super.initGui();
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        channelField = new GuiTextField(0, fontRenderer, guiLeft + 150,
                guiTop + ySize - 52, 164, 18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(tile.getRedstoneChannel()));
        faceList=new HousingTextureList(guiLeft+12,guiTop+40,190,selected,listBudget,null,choice->!ScreenHousingTextures.isLightOff(choice)).custom(value->{selected=value;tile.setFaceTexture(value);send();});
        housingList = new HousingTextureList(guiLeft + 218, guiTop + 40, 182, housing,listBudget,null,choice->!ScreenHousingTextures.isLightOff(choice)).custom(value->{housing=value;tile.setHousingTexture(value);send();});
        if(tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableLightFrame) buttonList.add(new GuiButton(103,guiLeft+12,guiTop+134+extra,190,20,sizeLabel()));
        buttonList.add(new GuiButton(104,guiLeft+218,guiTop+134+extra,190,20,sidesLabel()));
        buttonList.add(new GuiButton(101, guiLeft + 12, guiTop + 214 + extra,
                96, 20, joinLabel()));
        buttonList.add(new GuiButton(102, guiLeft + 112, guiTop + 214 + extra,
                184, 20, triggerLabel()));
        buttonList.add(new GuiButton(100, guiLeft + 300, guiTop + 214 + extra,
                108, 20, I18n.format("gui.done")));
    }

    private String sizeLabel(){return "Size: "+(small?"Small":"Full");}
    private String sidesLabel(){return "Side layout: "+(tileSides?"Tile":"Fit");}

    private String joinLabel() {
        return "Join: "
                + (join ? I18n.format("options.on") : I18n.format("options.off"));
    }

    private String triggerLabel() {
        return trigger == 1 ? "Trigger: Redstone ON"
                : trigger == 2 ? "Trigger: Redstone OFF" : "Trigger: Disabled";
    }

    private int channel() {
        try {
            long value = Long.parseLong(channelField.getText());
            return value <= Integer.MAX_VALUE ? (int) value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void send() {
        int channel = channel();
        if (channel < 0) return;
        tile.setSmallInput(small);tile.setSlabTileSides(tileSides);
        tile.setFaceTexture(selected);tile.configure(tile.getTexture(), level, join, channel, housing, trigger);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableLight(
                tile.getPos(), selected, level, join, channel, housing, trigger,small,tileSides));
    }

    private void setLevelFromMouse(int mouseX) {
        int next = Math.max(0, Math.min(15,
                Math.round(15F * (mouseX - guiLeft - 16) / SLIDER_W)));
        if (next != level) {
            level = next;
            send();
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        int before=faceList.selected();
        if(faceList.click(mouseX,mouseY,button)){if(before!=faceList.selected()){selected=faceList.selected();send();}return;}
        if (housingList.click(mouseX, mouseY, button)) {
            if (housing != housingList.selected()) {
                housing = housingList.selected();
                send();
            }
            return;
        }
        if (button == 0) {
            if (mouseX >= guiLeft + 12 && mouseX <= guiLeft + 318
                    && mouseY >= guiTop + ySize - 72 && mouseY <= guiTop + ySize - 58) {
                draggingLevel = true;
                setLevelFromMouse(mouseX);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY,
            int button, long elapsed) {
        if (faceList.drag(mouseY) || housingList.drag(mouseY)) return;
        if (draggingLevel && button == 0) setLevelFromMouse(mouseX);
        else super.mouseClickMove(mouseX, mouseY, button, elapsed);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        draggingLevel = false;
        faceList.release();housingList.release();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        faceList.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());
        housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1,
                Mouse.getEventDWheel());
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 101) {
            join = !join;
            button.displayString = joinLabel();
            send();
        }
        if(button.id==103){small=!small;button.displayString=sizeLabel();send();}
        if(button.id==104){tileSides=!tileSides;button.displayString=sidesLabel();send();}
        if (button.id == 102) {
            trigger = (trigger + 1) % 3;
            button.displayString = triggerLabel();
            send();
        }
        if (button.id == 100) {
            send();
            mc.player.closeScreen();
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            send();
            mc.player.closeScreen();
            return;
        }
        if (channelField.textboxKeyTyped(typedChar, keyCode)) send();
        else super.keyTyped(typedChar, keyCode);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        channelField.updateCursorCounter();
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,
            int mouseX, int mouseY) { }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 18, 0xFF202028);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.title"),
                guiLeft + 8, guiTop + 5, 0xFFFFFFFF);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.housing"),
                guiLeft + 218, guiTop + 27, 0xFFD8D8D8);
        housingList.draw(fontRenderer, mouseX, mouseY);
        fontRenderer.drawString("Light texture",guiLeft+12,guiTop+27,0xFFD8D8D8);
        faceList.draw(fontRenderer,mouseX,mouseY);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.level") + ": " + level,
                guiLeft + 13, guiTop + ySize - 84, 0xFFD8D8D8);
        drawRect(guiLeft + 16, guiTop + ySize - 67, guiLeft + 16 + SLIDER_W,
                guiTop + ySize - 61, 0xFF555560);
        int thumb = guiLeft + 16 + Math.round(SLIDER_W * level / 15F);
        drawRect(thumb - 3, guiTop + ySize - 72, thumb + 3, guiTop + ySize - 58, 0xFFB8D7E8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.channel"),
                guiLeft + 13, guiTop + ySize - 47, 0xFFD8D8D8);
        super.drawScreen(mouseX, mouseY, partialTicks);
        channelField.drawTextBox();
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
