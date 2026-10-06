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
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
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
 * variants collapsed to one entry) with texture thumbnails, a redstone
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

    private ProgrammableDialogLayout layout;
    private int textureTab;
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
    }

    /** Block id currently addressed: the selected family's active variant. */
    private String activeId() {
        if (selectedOption == null) {
            return "engineering_screen";
        }
        return framed ? selectedOption.framedId : selectedOption.bareId;
    }

    @Override public void initGui() {
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int tabs=console?3:2,rows=layout.rows(true);
        buttonList.add(layout.tab(90,0,tabs,"Screen"));
        if(console)buttonList.add(layout.tab(91,1,tabs,"Controls"));
        buttonList.add(layout.tab(console?92:91,tabs-1,tabs,"Housing"));
        listLeft=layout.listX;listTop=guiTop+54;listRight=listLeft+layout.listWidth;listBottom=listTop+rows*HousingTextureList.ROW_HEIGHT;
        String[] ids=new String[entries.size()],labels=new String[entries.size()];
        for(int i=0;i<ids.length;i++){ids[i]=entries.get(i).option.bareId;labels[i]=entries.get(i).name;}
        screenList=new ScreenTextureList(listLeft,listTop,layout.listWidth,8,activeId(),ids,labels,false).visibleRows(rows).restore(te.getSurfaceTexture(0)).custom(value->chooseArtwork(screenList,0)).redstone(pos,0);
        if(console)inputList=new ScreenTextureList(listLeft,listTop,layout.listWidth,8,inputPanel,TileEntityAnimatedScreenSelector.INPUT_PANELS,null,true).visibleRows(rows).restore(te.getSurfaceTexture(1)).custom(value->chooseArtwork(inputList,1)).redstone(pos,1);
        housingList=new HousingTextureList(listLeft,listTop,layout.listWidth,housingTexture)
                .visibleRows(rows).custom(value->{housingTexture=value;te.setHousingTexture(value);sendUpdate();});
        modeOffButton=layout.choice(1,0,3,42,I18n.format("gui.vandorlabs.selector.off"));
        modeStaticButton=layout.choice(2,1,3,42,I18n.format("gui.vandorlabs.selector.static"));
        modeAnimatedButton=layout.choice(3,2,3,42,I18n.format("gui.vandorlabs.selector.animated"));
        slowButton=layout.choice(10,0,3,76,I18n.format("gui.vandorlabs.selector.slow"));
        normalButton=layout.choice(11,1,3,76,I18n.format("gui.vandorlabs.selector.normal"));
        fastButton=layout.choice(12,2,3,76,I18n.format("gui.vandorlabs.selector.fast"));
        frameButton=layout.control(4,98,"");redstoneButton=layout.control(0,120,"");
        java.util.Collections.addAll(buttonList,modeOffButton,modeStaticButton,modeAnimatedButton,slowButton,normalButton,fastButton,frameButton,redstoneButton);
        buttonList.add(layout.control(32,142,sidesLabel()));
        channelField=new GuiTextField(40,fontRenderer,layout.controlsX,guiTop+177,154,18);
        ChannelFields.configure(channelField);
        channelField.setText(te.getRedstoneChannels().toString());
        buttonList.add(layout.done(20));refreshButtons();refreshTabs();
    }
    private void refreshTabs(){for(GuiButton b:buttonList)if(b.id>=90 && b.id<=(console?92:91))b.enabled=b.id-90!=textureTab;}

    private String sidesLabel(){return "Sides: "+(te.isSurfaceTileSides()?"Tile":"Fit");}
    private void refreshButtons() {
        redstoneButton.displayString = I18n.format("gui.vandorlabs.selector.redstone") + ": "
                + I18n.format(redstoneEnabled
                        ? "gui.vandorlabs.selector.on" : "gui.vandorlabs.selector.off_state");
        modeOffButton.enabled = displayMode != TileEntityAnimatedScreenSelector.MODE_OFF;
        modeStaticButton.enabled = displayMode != TileEntityAnimatedScreenSelector.MODE_STATIC;
        modeAnimatedButton.enabled = displayMode != TileEntityAnimatedScreenSelector.MODE_ANIMATED;
        frameButton.displayString = ((console && textureTab==1?inputList.framed():framed) ? "[x] " : "[ ] ")
                + I18n.format("gui.vandorlabs.selector.framed");
        frameButton.enabled = console && textureTab==1?inputList.hasPair():textureTab==0 && selectedOption != null && selectedOption.hasPair();
        slowButton.enabled = speedIndex != 0;
        normalButton.enabled = speedIndex != 1;
        fastButton.enabled = speedIndex != 2;
    }

    private void sendUpdate() {
        if(channel()<0)return;
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(pos,
                activeId(), redstoneEnabled, displayMode, framed, speedIndex,
                inputPanel, inputPanel, false, channel(), housingTexture).withChannels(ChannelFields.parse(channelField)));
    }

    private void chooseArtwork(ScreenTextureList picker,int slot) {
        String nativeId=picker.selected();
        if(nativeId==null){te.setSurfaceTexture(slot,picker.choice());PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(pos,slot,picker.choice()));return;}
        if(slot==1)inputPanel=nativeId;
        else for(Entry entry:entries)if(entry.option.bareId.equals(nativeId)){selectedOption=entry.option;framed=picker.framed();break;}
        te.setSurfaceTexture(slot,-1);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(pos,slot,-1));refreshButtons();sendUpdate();
    }

    private int channel() {return ChannelFields.first(channelField);}

    @Override
    protected void actionPerformed(GuiButton button) {
        if(button.id>=90 && button.id<=(console?92:91)){textureTab=button.id-90;refreshTabs();refreshButtons();return;}
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
                if(console && textureTab==1){
                    if(!inputList.hasPair())return;
                    inputList.setFramed(!inputList.framed());inputPanel=inputList.selected();
                    break;
                }
                if (selectedOption == null || !selectedOption.hasPair()) {
                    return;
                }
                framed = !framed;
                screenList.setFramed(framed);
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
                if(channel()<0)return;
                sendUpdate();
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
        if(GuiOptionCycle.rightClick(mc,buttonList,mouseX,mouseY,mouseButton,this::actionPerformed,0,4,32))return;
        if (textureTab==(console?2:1) && housingList.click(mouseX, mouseY, mouseButton)) {
            if (housingTexture != housingList.selected()) {
                housingTexture = housingList.selected();
                sendUpdate();
            }
            return;
        }
        if(console && textureTab==1 && inputList.click(mouseX,mouseY,mouseButton)){if(inputList.picked())chooseArtwork(inputList,1);return;}
        if(textureTab==0 && screenList.click(mouseX,mouseY,mouseButton)){if(screenList.picked())chooseArtwork(screenList,0);return;}
        super.mouseClicked(mouseX, mouseY, mouseButton);
        channelField.mouseClicked(mouseX,mouseY,mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        screenList.release();if(inputList!=null)inputList.release();
        draggingScrollbar = false;
        if (housingList != null) housingList.release();
        draggingInputScrollbar = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
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
        int wheel = Mouse.getEventDWheel();
        if (textureTab==(console?2:1) && housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel)) return;
        int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(textureTab==0)screenList.wheel(mx,my,wheel);else if(console && textureTab==1)inputList.wheel(mx,my,wheel);
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

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);
        fontRenderer.drawString(I18n.format(console?"gui.vandorlabs.console.title":fullInput?"gui.vandorlabs.full_input.title":diagonalScreen?"gui.vandorlabs.diagonal_screen.title":"gui.vandorlabs.selector.title"),guiLeft+12,guiTop+8,0xFFFFFF);
        if(textureTab==0)screenList.draw(fontRenderer,mouseX,mouseY);
        else if(console && textureTab==1)inputList.draw(fontRenderer,mouseX,mouseY);
        else housingList.draw(fontRenderer,mouseX,mouseY);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.display"),layout.controlsX,guiTop+30,0xDAE8F0);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.speed"),layout.controlsX,guiTop+64,0xDAE8F0);
        fontRenderer.drawString("Channels (0 = none)",layout.controlsX,guiTop+166,0xDAE8F0);
        super.drawScreen(mouseX,mouseY,partialTicks);channelField.drawTextBox();
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
