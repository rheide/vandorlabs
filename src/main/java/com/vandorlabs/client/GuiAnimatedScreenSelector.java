package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.BlockProgrammableConsole;
import com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
import com.vandorlabs.blocks.BlockProgrammableFullInput;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageSyncScreenSelector;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Selector GUI: a scrollable list of every screen family (bare/framed
 * variants collapsed to one entry), a static image preview, a redstone
 * enable checkbox, an Off/Static/Animated mode control, a Bare/Framed
 * variant checkbox, and the slow/normal/fast speed control. Every change is
 * pushed to the server immediately so the block previews live behind the GUI.
 */
@SideOnly(Side.CLIENT)
public class GuiAnimatedScreenSelector extends GuiContainer {

    private static class Entry {
        final ModBlocks.ScreenOption option;
        final String name;

        Entry(ModBlocks.ScreenOption option, String name) {
            this.option = option;
            this.name = name;
        }
    }

    private static final int ROW_H = 12;
    private static final int LIST_ROWS = 8;
    private static final int LIST_H = ROW_H * LIST_ROWS;

    private SurfaceTexturePicker surfacePicker;
    private final TileEntityAnimatedScreenSelector te;
    private final BlockPos pos;
    private final List<Entry> entries = new ArrayList<>();
    private final boolean console;
    private final boolean diagonalScreen;
    private final boolean fullInput;

    private ModBlocks.ScreenOption selectedOption;
    private boolean framed;
    private boolean redstoneEnabled;
    private int displayMode;
    private int speedIndex;
    private ResourceLocation previewTexture;
    private String inputPanel;
    private int housingTexture;
    private HousingTextureList housingList;
    private ScreenTextureList screenList,inputList;

    private int scrollIndex;
    private boolean draggingScrollbar;
    private int inputScrollIndex;
    private boolean draggingInputScrollbar;

    private GuiButton redstoneButton;
    private GuiButton modeOffButton;
    private GuiButton modeStaticButton;
    private GuiButton modeAnimatedButton;
    private GuiButton frameButton;
    private GuiButton slowButton;
    private GuiButton normalButton;
    private GuiButton fastButton;
    private GuiTextField channelField;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private int previewX;
    private int previewY;
    private int previewSize;
    private int inputListLeft;
    private int inputListTop;
    private int inputListRight;
    private int inputListBottom;

    public GuiAnimatedScreenSelector(InventoryPlayer playerInventory,
            TileEntityAnimatedScreenSelector te) {
        super(new ContainerAnimatedScreenSelector(playerInventory, te));
        this.te = te;
        this.pos = te.getPos();
        this.console = te.getWorld() != null
                && te.getWorld().getBlockState(pos).getBlock() instanceof BlockProgrammableConsole;
        this.diagonalScreen = te.getWorld() != null && te.getWorld().getBlockState(pos)
                .getBlock() instanceof BlockProgrammableDiagonalScreen;
        this.fullInput = te.getWorld() != null && te.getWorld().getBlockState(pos)
                .getBlock() instanceof BlockProgrammableFullInput;
        this.xSize = 420;
        this.ySize = 240;
        this.redstoneEnabled = te.isRedstoneEnabled();
        this.displayMode = te.getDisplayMode();
        this.speedIndex = te.getAnimationSpeedIndex();
        this.inputPanel = te.getInputPanel();
        this.housingTexture = te.getHousingTexture();
        for (ModBlocks.ScreenOption option : ModBlocks.SCREEN_OPTIONS) {
            String raw = I18n.format("tile.vandorlabs." + option.bareId + ".name");
            String name = raw.replaceFirst("\\b(Bare|Framed) ", "")
                    .replace("Control Panel", "Panel")
                    .replace("Communications", "Comms")
                    .replace("Engineering", "Eng.")
                    .replaceAll(" Left$", " L")
                    .replaceAll(" Right$", " R");
            entries.add(new Entry(option, name));
        }
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
            }
        });
        // Restore the tile's selection; fall back to the engineering screen.
        String teId = te.getSelectedScreen();
        Entry current = null;
        for (Entry e : entries) {
            if (teId.equals(e.option.bareId) || teId.equals(e.option.framedId)) {
                current = e;
                break;
            }
        }
        if (current == null) {
            for (Entry e : entries) {
                if ("engineering_screen".equals(e.option.key)) {
                    current = e;
                    break;
                }
            }
        }
        if (current == null && !entries.isEmpty()) {
            current = entries.get(0);
        }
        selectedOption = current != null ? current.option : null;
        if (selectedOption != null) {
            if (teId.equals(selectedOption.bareId) || teId.equals(selectedOption.framedId)) {
                if (selectedOption.hasPair()) {
                    framed = teId.equals(selectedOption.framedId);
                } else {
                    framed = ModBlocks.DISPLAY_FRAMED_IDS.contains(teId);
                }
            } else {
                framed = te.isFramed();
            }
        }
        refreshPreview();
    }

    /** Block id currently addressed: the selected family's active variant. */
    private String activeId() {
        if (selectedOption == null) {
            return "engineering_screen";
        }
        return framed ? selectedOption.framedId : selectedOption.bareId;
    }

    private void refreshPreview() {
        previewTexture = new ResourceLocation("vandorlabs",
                "textures/blocks/" + activeId() + "_static.png");
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        surfacePicker=new SurfaceTexturePicker(te,te.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableConsole);
        surfacePicker.init(guiLeft,guiTop,xSize,ySize);
        Keyboard.enableRepeatEvents(true);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        listLeft = x + 8;
        listTop = y + 22;
        listRight = x + (console ? 126 : 184);
        listBottom = listTop + LIST_H;
        previewX = x + (console ? 340 : 194);
        previewY = y + (console ? 130 : 32);
        previewSize = console ? 52 : 64;
        inputListLeft = x + 136;
        inputListTop = y + 22;
        inputListRight = x + 236;
        inputListBottom = inputListTop + LIST_H;
        int controlsX = x + (console ? 164 : 196);
        int controlsW = console ? 148 : 96;
        int leftControlsW = console ? 150 : 176;

        redstoneButton = new GuiButton(0, x + 8, y + 124, leftControlsW, 20, "");
        int speedW = console ? 46 : 56;
        int speedGap = console ? 6 : 4;
        slowButton = new GuiButton(10, x + 8, y + 158, speedW, 20,
                I18n.format("gui.vandorlabs.selector.slow"));
        normalButton = new GuiButton(11, x + 8 + speedW + speedGap, y + 158, speedW, 20,
                I18n.format("gui.vandorlabs.selector.normal"));
        fastButton = new GuiButton(12, x + 8 + (speedW + speedGap) * 2,
                y + 158, speedW, 20,
                I18n.format("gui.vandorlabs.selector.fast"));
        modeOffButton = new GuiButton(1, controlsX, y + 126, controlsW, 18,
                I18n.format("gui.vandorlabs.selector.off"));
        modeStaticButton = new GuiButton(2, controlsX, y + 146, controlsW, 18,
                I18n.format("gui.vandorlabs.selector.static"));
        modeAnimatedButton = new GuiButton(3, controlsX, y + 166, controlsW, 18,
                I18n.format("gui.vandorlabs.selector.animated"));
        frameButton = new GuiButton(4, controlsX, y + 186, controlsW, 18, "");
        buttonList.add(redstoneButton);
        buttonList.add(slowButton);
        buttonList.add(normalButton);
        buttonList.add(fastButton);
        buttonList.add(modeOffButton);
        buttonList.add(modeStaticButton);
        buttonList.add(modeAnimatedButton);
        buttonList.add(frameButton);
        housingList = new HousingTextureList(x + (console ? 244 : 266), y + 22,
                console ? 160 : 140,
                housingTexture).custom(value->{housingTexture=value;te.setHousingTexture(value);sendUpdate();});
        channelField = new GuiTextField(40,fontRenderer,x+64,y+186,84,18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(te.getRedstoneChannel()));
        buttonList.add(new GuiButton(20, x + (console ? 164 : 154), y + 212,
                console ? 148 : 138, 20,
                I18n.format("gui.done")));
        refreshButtons();
        String[] ids=new String[entries.size()],labels=new String[entries.size()];for(int i=0;i<ids.length;i++){ids[i]=entries.get(i).option.bareId;labels[i]=entries.get(i).name;}
        screenList=new ScreenTextureList(listLeft,listTop,listRight-listLeft,8,selectedOption.bareId,ids,labels,false);
        if(console)inputList=new ScreenTextureList(inputListLeft,inputListTop,inputListRight-inputListLeft,8,inputPanel,TileEntityAnimatedScreenSelector.INPUT_PANELS,null,true);
        buttonList.add(new GuiButton(32,x+8,y+212,138,20,sidesLabel()));
    }

    private String sidesLabel(){return "Sides: "+(te.isSurfaceTileSides()?"Tile":"Fit");}
    private void refreshButtons() {
        redstoneButton.displayString = I18n.format("gui.vandorlabs.selector.redstone") + ": "
                + I18n.format(redstoneEnabled
                        ? "gui.vandorlabs.selector.on" : "gui.vandorlabs.selector.off_state");
        modeOffButton.enabled = displayMode != TileEntityAnimatedScreenSelector.MODE_OFF;
        modeStaticButton.enabled = displayMode != TileEntityAnimatedScreenSelector.MODE_STATIC;
        modeAnimatedButton.enabled = displayMode != TileEntityAnimatedScreenSelector.MODE_ANIMATED;
        frameButton.displayString = (framed ? "[x] " : "[ ] ")
                + I18n.format("gui.vandorlabs.selector.framed");
        frameButton.enabled = selectedOption != null && selectedOption.hasPair();
        slowButton.enabled = speedIndex != 0;
        normalButton.enabled = speedIndex != 1;
        fastButton.enabled = speedIndex != 2;
    }

    private void sendUpdate() {
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(pos,
                activeId(), redstoneEnabled, displayMode, framed, speedIndex,
                inputPanel, inputPanel, false, channel(), housingTexture));
    }

    private int channel() {
        try { long value=Long.parseLong(channelField.getText()); return value<=Integer.MAX_VALUE?(int)value:-1; }
        catch (NumberFormatException e) { return -1; }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if(button.id==32){te.setSurfaceTileSides(!te.isSurfaceTileSides());button.displayString=sidesLabel();PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageProgrammableSlabSides(pos,te.isSurfaceTileSides()));return;}
        switch (button.id) {
            case 0:
                redstoneEnabled = !redstoneEnabled;
                break;
            case 1:
                displayMode = TileEntityAnimatedScreenSelector.MODE_OFF;
                break;
            case 2:
                displayMode = TileEntityAnimatedScreenSelector.MODE_STATIC;
                break;
            case 3:
                displayMode = TileEntityAnimatedScreenSelector.MODE_ANIMATED;
                break;
            case 4:
                if (selectedOption == null || !selectedOption.hasPair()) {
                    return;
                }
                framed = !framed;
                refreshPreview();
                break;
            case 10:
                speedIndex = 0;
                break;
            case 11:
                speedIndex = 1;
                break;
            case 12:
                speedIndex = 2;
                break;
            case 20:
                if (channel() >= 0) sendUpdate();
                mc.player.closeScreen();
                return;
            default:
                return;
        }
        refreshButtons();
        sendUpdate();
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - LIST_ROWS);
    }

    private void clampScroll() {
        scrollIndex = Math.max(0, Math.min(maxScroll(), scrollIndex));
    }

    private int maxInputScroll() {
        return Math.max(0, TileEntityAnimatedScreenSelector.INPUT_PANELS.length - LIST_ROWS);
    }

    private void clampInputScroll() {
        inputScrollIndex = Math.max(0, Math.min(maxInputScroll(), inputScrollIndex));
    }

    private int rowAt(int mouseY) {
        int row = (mouseY - listTop) / ROW_H + scrollIndex;
        if (row < 0 || row >= entries.size()) {
            return -1;
        }
        return row;
    }

    private int inputRowAt(int mouseY) {
        int row = (mouseY - inputListTop) / ROW_H + inputScrollIndex;
        return row >= 0 && row < TileEntityAnimatedScreenSelector.INPUT_PANELS.length
                ? row : -1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if(surfacePicker.click(mouseX,mouseY,mouseButton))return;
        if (housingList.click(mouseX, mouseY, mouseButton)) {
            if (housingTexture != housingList.selected()) {
                housingTexture = housingList.selected();
                sendUpdate();
            }
            return;
        }
        if(console && inputList.click(mouseX,mouseY,mouseButton)) {
            if(inputList.picked() && (!inputPanel.equals(inputList.selected()) || te.getSurfaceTexture(1)>=0)){inputPanel=inputList.selected();te.setSurfaceTexture(1,-1);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(pos,1,-1));sendUpdate();}return;
        }
        if(screenList.click(mouseX,mouseY,mouseButton)) {
            for(Entry entry:entries)if(screenList.picked() && entry.option.bareId.equals(screenList.selected()) && (!entry.option.key.equals(selectedOption.key) || te.getSurfaceTexture(0)>=0)) {
                selectedOption=entry.option;framed=selectedOption.hasPair() || ModBlocks.DISPLAY_FRAMED_IDS.contains(selectedOption.framedId);
                te.setSurfaceTexture(0,-1);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(pos,0,-1));refreshPreview();refreshButtons();sendUpdate();break;
            }return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        channelField.mouseClicked(mouseX,mouseY,mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        screenList.release();if(inputList!=null)inputList.release();
        surfacePicker.release();
        draggingScrollbar = false;
        if (housingList != null) housingList.release();
        draggingInputScrollbar = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
        if(surfacePicker.drag(mouseY))return;
        if (housingList != null && housingList.drag(mouseY)) return;
        if(screenList.drag(mouseY) || inputList!=null && inputList.drag(mouseY))return;
        super.mouseClickMove(mouseX,mouseY,clickedMouseButton,timeSinceLastClick);
    }

    private void dragScrollbarTo(int mouseY) {
        float frac = (float) (mouseY - listTop) / (float) LIST_H;
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        scrollIndex = Math.round(frac * maxScroll());
        clampScroll();
    }

    private void dragInputScrollbarTo(int mouseY) {
        float frac = (float) (mouseY - inputListTop) / (float) LIST_H;
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        inputScrollIndex = Math.round(frac * maxInputScroll());
        clampInputScroll();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if(surfacePicker.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel()))return;
        int wheel = Mouse.getEventDWheel();
        if (housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel)) return;
        int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
        screenList.wheel(mx,my,wheel);if(inputList!=null)inputList.wheel(mx,my,wheel);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (channelField.textboxKeyTyped(typedChar,keyCode)) {
            if (channel() >= 0) sendUpdate();
            return;
        }
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.player.closeScreen();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        // Panel.
        drawRect(x, y, x + xSize, y + ySize, 0xFF101012);
        drawRect(x, y, x + xSize, y + 16, 0xFF202028);
        fontRenderer.drawString(I18n.format(console ? "gui.vandorlabs.console.title"
                        : (fullInput ? "gui.vandorlabs.full_input.title"
                        : (diagonalScreen ? "gui.vandorlabs.diagonal_screen.title"
                                : "gui.vandorlabs.selector.title"))),
                x + 8, y + 5, 0xFFFFFFFF);
        screenList.draw(fontRenderer,mouseX,mouseY);
        if(inputList!=null)inputList.draw(fontRenderer,mouseX,mouseY);
        // Right column: preview + section labels.
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.preview"),
                previewX, console ? y + 118 : listTop, 0xFFA0A0A8);
        int previewHeight = previewSize + (console ? previewSize / 2 : 0);
        drawRect(previewX - 1, previewY - 1, previewX + previewSize + 1,
                previewY + previewHeight + 1, 0xFF000000);
        if (previewTexture != null) {
            mc.getTextureManager().bindTexture(previewTexture);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawScaledCustomSizeModalRect(previewX, previewY, 0.0F, 0.0F,
                    256, 256, previewSize, previewSize, 256.0F, 256.0F);
        }
        if (console) {
            mc.getTextureManager().bindTexture(new ResourceLocation("vandorlabs",
                    "textures/blocks/console_inputs/" + inputPanel
                            + (displayMode == TileEntityAnimatedScreenSelector.MODE_OFF
                                    ? "_off.png" : "_static.png")));
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawScaledCustomSizeModalRect(previewX, previewY + previewSize,
                    0.0F, 0.0F, 512, 256, previewSize, previewSize / 2,
                    512.0F, 256.0F);
        }
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.display"),
                x + (console ? 164 : 196), y + 116, 0xFFA0A0A8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.speed"),
                x + 8, y + 148, 0xFFA0A0A8);
        fontRenderer.drawString("Channel",x+8,y+192,0xFFA0A0A8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.housing"),
                x + (console ? 244 : 266), y + 5, 0xFFA0A0A8);
        housingList.draw(fontRenderer, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        channelField.drawTextBox();
        surfacePicker.draw(fontRenderer,mouseX,mouseY);
        // Hovered-row tooltip: family name plus which variant it addresses.
        // The selected row shows its live variant; other pairs show both.
        if (mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY < listBottom) {
            int row = rowAt(mouseY);
            if (row >= 0 && selectedOption != null) {
                ModBlocks.ScreenOption option = entries.get(row).option;
                String tip;
                if (option.key.equals(selectedOption.key)) {
                    tip = entries.get(row).name + " — "
                            + I18n.format(framed ? "gui.vandorlabs.selector.framed"
                                    : "gui.vandorlabs.selector.bare");
                } else if (option.hasPair()) {
                    tip = entries.get(row).name + " — "
                            + I18n.format("gui.vandorlabs.selector.bare") + " / "
                            + I18n.format("gui.vandorlabs.selector.framed");
                } else {
                    tip = entries.get(row).name;
                }
                drawHoveringText(tip, mouseX, mouseY);
            }
        }
    }

    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Background is drawn custom in drawScreen; the container has no slots.
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
