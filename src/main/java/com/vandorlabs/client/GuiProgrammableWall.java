package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageSyncScreenSelector;
import com.vandorlabs.network.MessageProgrammableWallShade;
import com.vandorlabs.network.MessageProgrammableSlabSides;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.blocks.BlockProgrammableBlock;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Scrollable finish picker shared by programmable walls and the full block. */
public class GuiProgrammableWall extends GuiContainer {
    private static final int ROW_H = 16;
    private static final int ROWS = 8;
    private static final int LIST_W = 190;
    private static final String[] SHADES = {"Clear", "Cyan", "Dark Grey"};
    private final TileEntityAnimatedScreenSelector tile;
    private final boolean porthole;
    private final boolean fullBlock;
    private final boolean slab;
    private int selected;
    private int shade;
    private boolean join;
    private boolean tileSides;
    private int scroll;
    private int listX;
    private int listY;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;

    public GuiProgrammableWall(InventoryPlayer inventory,
            TileEntityAnimatedScreenSelector tile) {
        super(new ContainerAnimatedScreenSelector(inventory, tile));
        this.tile = tile;
        porthole = tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockProgrammableWall
                && ((BlockProgrammableWall) tile.getWorld().getBlockState(tile.getPos())
                .getBlock()).getShape() == BlockProgrammableWall.Shape.PORTHOLE;
        slab = tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockProgrammableSlab;
        fullBlock = tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockProgrammableBlock
                || slab;
        selected = tile.getHousingTexture();
        shade = tile.getGlassShade();
        join = tile.isJoinPortholes();
        tileSides = tile.isSlabTileSides();
        xSize = 340;
        ySize = porthole ? 246 : slab ? 216 : 190;
    }

    @Override public void initGui() {
        super.initGui();
        buttonList.clear();
        listX = guiLeft + 11;
        listY = guiTop + (porthole ? 80 : 27);
        scroll = Math.min(Math.max(0, selected - ROWS / 2), maxScroll());
        if (porthole) buttonList.add(new GuiButton(101, guiLeft + 14,
                guiTop + 25, 312, 20, shadeLabel()));
        if (porthole) buttonList.add(new GuiButton(102, guiLeft + 14,
                guiTop + 51, 312, 20, joinLabel()));
        if (slab) buttonList.add(new GuiButton(103, guiLeft + 14,
                guiTop + 163, 312, 20, slabSidesLabel()));
        buttonList.add(new GuiButton(100, guiLeft + 14, guiTop + ySize - 25, 312, 20,
                I18n.format("gui.done")));
    }

    private String shadeLabel() { return "Glass: " + SHADES[shade]; }
    private String joinLabel() { return "Join glass: " + (join ? "On" : "Off"); }
    private String slabSidesLabel() { return "Side texture: " + (tileSides ? "Tile" : "Fit"); }

    private void sendPortholeSettings() {
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableWallShade(
                tile.getPos(), shade, join));
    }

    private int maxScroll() {
        return Math.max(0, ScreenHousingTextures.IDS.length - ROWS);
    }

    private int thumbHeight() {
        return Math.max(8, ROW_H * ROWS * ROWS / ScreenHousingTextures.IDS.length);
    }

    private int thumbY() {
        return listY + (ROW_H * ROWS - thumbHeight()) * scroll / maxScroll();
    }

    static int scrollForDrag(int mouseY, int trackTop, int trackHeight,
            int thumbHeight, int maximum, int dragOffset) {
        int travel = trackHeight - thumbHeight;
        if (maximum <= 0 || travel <= 0) return 0;
        int thumbTop = Math.max(0, Math.min(travel,
                mouseY - trackTop - dragOffset));
        return Math.round((float) thumbTop * maximum / travel);
    }

    private void dragTo(int mouseY) {
        scroll = scrollForDrag(mouseY, listY, ROW_H * ROWS,
                thumbHeight(), maxScroll(), scrollbarDragOffset);
    }

    private void choose(int choice) {
        selected = choice;
        tile.setHousingTexture(choice);
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(tile.getPos(),
                tile.getSelectedScreen(), tile.isRedstoneEnabled(), tile.getDisplayMode(),
                tile.isFramed(), tile.getAnimationSpeedIndex(), tile.getInputPanel(),
                tile.getSecondaryInputPanel(), tile.isSmallInput(),
                tile.getRedstoneChannel(), choice));
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        if (button == 0 && mouseY >= listY && mouseY < listY + ROW_H * ROWS) {
            if (mouseX >= listX + LIST_W && mouseX < listX + LIST_W + 7
                    && maxScroll() > 0) {
                int top = thumbY();
                scrollbarDragOffset = mouseY >= top && mouseY < top + thumbHeight()
                        ? mouseY - top : thumbHeight() / 2;
                draggingScrollbar = true;
                dragTo(mouseY);
                return;
            }
            if (mouseX >= listX && mouseX < listX + LIST_W) {
                int index = scroll + (mouseY - listY) / ROW_H;
                if (index < ScreenHousingTextures.IDS.length) choose(index);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        draggingScrollbar = false;
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY,
            int clickedButton, long timeSinceLastClick) {
        if (draggingScrollbar) dragTo(mouseY);
        else super.mouseClickMove(mouseX, mouseY, clickedButton, timeSinceLastClick);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel > 0 ? -1 : 1)));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 100) mc.player.closeScreen();
        if (button.id == 101) {
            shade = (shade + 1) % SHADES.length;
            tile.setGlassShade(shade);
            button.displayString = shadeLabel();
            sendPortholeSettings();
        }
        if (button.id == 102) {
            join = !join;
            tile.setJoinPortholes(join);
            button.displayString = joinLabel();
            sendPortholeSettings();
        }
        if (button.id == 103) {
            tileSides = !tileSides;
            tile.setSlabTileSides(tileSides);
            button.displayString = slabSidesLabel();
            PacketHandler.INSTANCE.sendToServer(new MessageProgrammableSlabSides(
                    tile.getPos(), tileSides));
        }
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,
            int mouseX, int mouseY) { }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 18, 0xFF202028);
        fontRenderer.drawString(I18n.format(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                        instanceof BlockProgrammableSlab ? "gui.vandorlabs.slab.title"
                        : fullBlock ? "gui.vandorlabs.block.title"
                        : "gui.vandorlabs.wall.title"),
                guiLeft + 8, guiTop + 5, 0xFFFFFFFF);
        drawRect(listX - 1, listY - 1, listX + LIST_W + 8,
                listY + ROW_H * ROWS + 1, 0xFF000000);
        drawRect(listX, listY, listX + LIST_W, listY + ROW_H * ROWS, 0xFF0A0A0C);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= ScreenHousingTextures.IDS.length) break;
            int yy = listY + row * ROW_H;
            boolean hovered = mouseX >= listX && mouseX < listX + LIST_W
                    && mouseY >= yy && mouseY < yy + ROW_H;
            if (index == selected || hovered)
                drawRect(listX, yy, listX + LIST_W, yy + ROW_H,
                        index == selected ? 0xFF2A4A6A : 0xFF1A1A20);
            String label = I18n.format("tile.vandorlabs."
                    + ScreenHousingTextures.IDS[index] + ".name");
            fontRenderer.drawStringWithShadow(
                    fontRenderer.trimStringToWidth(label, LIST_W - 8),
                    listX + 4, yy + 4, index == selected ? 0xFFFFE08A : 0xFFD8D8D8);
        }
        if (maxScroll() > 0) {
            drawRect(listX + LIST_W, listY, listX + LIST_W + 7,
                    listY + ROW_H * ROWS, 0xFF303038);
            drawRect(listX + LIST_W, thumbY(), listX + LIST_W + 7,
                    thumbY() + thumbHeight(), 0xFF808090);
        }
        int previewX = listX + LIST_W + 18;
        int previewY = listY + 16;
        fontRenderer.drawString("Preview", previewX, listY + 2, 0xFFD8D8D8);
        drawRect(previewX - 2, previewY - 2, previewX + 98,
                previewY + 98, 0xFF505058);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(
                ScreenHousingTextures.texture(selected));
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawTexturedModalRect(previewX, previewY, sprite, 96, 96);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
