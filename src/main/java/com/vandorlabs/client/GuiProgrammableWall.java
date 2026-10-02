package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageSyncScreenSelector;
import com.vandorlabs.network.MessageProgrammableWallShade;
import com.vandorlabs.network.MessageProgrammableSlabSides;
import com.vandorlabs.network.MessageProgrammableDiagonalWidth;
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
    private static final int ROW_H = 12;
    private int rows = 8;
    private static final int LIST_W = 310;
    private HousingTextureList textureList;
    private static final String[] SHADES = {"Clear", "Cyan", "Dark Grey"};
    private static final String[] SHAPES = {"Hexagon", "Octagon", "Square", "Round"};
    private final TileEntityAnimatedScreenSelector tile;
    private final boolean porthole;
    private final boolean fullBlock;
    private final boolean slab;
    private final boolean diagonal;
    private int selected;
    private int faceTarget = -1;
    private static final String[] FACE_NAMES = {"Bottom", "Top", "Front", "Back", "Left", "Right"};
    private boolean supportsFaces() {
        net.minecraft.block.Block block = tile.getBlockType();
        return block == com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK || slab;
    }
    private int selectedTexture() {
        return faceTarget < 0 ? tile.getHousingTexture()
                : tile.getFaceTextures().texture(faceTarget, tile.getHousingTexture());
    }
    private void sendFaces(com.vandorlabs.tiles.FaceTextures faces) {
        tile.setFaceTextures(faces);
        PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageFaceTextures(tile.getPos(), faces));
    }
    private int shade;
    private int shape;
    private boolean join;
    private boolean tileSides;
    private boolean fullWidth;
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
                .getBlock()).isPortholeShape();
        slab = tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockProgrammableSlab || tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableStairs;
        diagonal = tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockProgrammableWall
                && ((BlockProgrammableWall) tile.getWorld().getBlockState(tile.getPos())
                .getBlock()).isDiagonalShape();
        fullBlock = tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockProgrammableBlock
                || slab;
        selected = tile.getHousingTexture();
        shade = tile.getGlassShade();
        shape = tile.getPortholeShape();
        join = tile.isJoinPortholes();
        tileSides = tile.isSlabTileSides();
        fullWidth = tile.isDiagonalFullWidth();
        xSize = 340;
        ySize = porthole ? 272 : slab || diagonal ? 216 : 190;
    }

    @Override public void initGui() {
        rows=8;
        ySize = porthole ? (diagonal ? 298 : 272) : diagonal ? 242 : slab ? 242 : supportsFaces() ? 216 : 190;
        if (supportsFaces() && tile.getFaceTextures().enabled) ySize+=52;
        if (diagonal) ySize += 26;
        while (ySize > height-8 && rows > (diagonal && porthole ? 2 : 3)) { rows--; ySize-=ROW_H; }
        super.initGui();
        buttonList.clear();
        listX = guiLeft + 11;
        listY = guiTop + (porthole ? 106 : supportsFaces() && tile.getFaceTextures().enabled ? 79 : 27);
        textureList=new HousingTextureList(listX,listY,LIST_W,selected,rows);
        if (supportsFaces()) {
            buttonList.add(new GuiButton(106, guiLeft + 14, guiTop + ySize - 51, 312, 20,
                    "Face overrides: " + (tile.getFaceTextures().enabled ? "On" : "Off")));
            if (tile.getFaceTextures().enabled) {
                buttonList.add(new GuiButton(107, guiLeft + 14, guiTop + 25, 312, 20,
                        "Texture for: " + (faceTarget < 0 ? "Main" : FACE_NAMES[faceTarget])));
                GuiButton inherit = new GuiButton(108, guiLeft + 14, guiTop + 51, 312, 20,
                        faceTarget < 0 ? "Select a face to override" : tile.getFaceTextures().choice(faceTarget) < 0
                                ? "Using main texture" : "Use main texture");
                inherit.enabled = faceTarget >= 0 && tile.getFaceTextures().choice(faceTarget) >= 0;
                buttonList.add(inherit);
            }
        }
        if (porthole) buttonList.add(new GuiButton(101, guiLeft + 14,
                guiTop + 25, 312, 20, shadeLabel()));
        if (porthole) buttonList.add(new GuiButton(102, guiLeft + 14,
                guiTop + 51, 312, 20, joinLabel()));
        if (porthole) buttonList.add(new GuiButton(104, guiLeft + 14,
                guiTop + 77, 312, 20, shapeLabel()));
        if (slab) buttonList.add(new GuiButton(103, guiLeft + 14,
                guiTop + ySize - 77, 312, 20, slabSidesLabel()));
        if (diagonal) {
            buttonList.add(new GuiButton(105,guiLeft+14,guiTop+ySize-(porthole?77:103),312,20,diagonalWidthLabel()));
            buttonList.add(new GuiButton(111, guiLeft+14,
                    guiTop+ySize-(porthole?51:77), 312, 20, slopeLabel()));
            if (!porthole) {
                buttonList.add(new GuiButton(109,guiLeft+14,guiTop+ySize-51,153,20,"Fill inside: "+((tile.getDiagonalFill()&1)!=0?"On":"Off")));
                buttonList.add(new GuiButton(110,guiLeft+173,guiTop+ySize-51,153,20,"Fill outside: "+((tile.getDiagonalFill()&2)!=0?"On":"Off")));
            }
        }
        buttonList.add(new GuiButton(100, guiLeft + 14, guiTop + ySize - 25, 312, 20,
                I18n.format("gui.done")));
    }

    private String shadeLabel() { return "Glass: " + SHADES[shade]; }
    private String joinLabel() { return "Join: " + (join ? "On" : "Off"); }
    private String shapeLabel() { return "Shape: " + SHAPES[shape]; }
    private String slabSidesLabel() { return "Side layout: " + (tileSides ? "Tile" : "Fit"); }
    private String slopeLabel() {
        net.minecraft.block.state.IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        net.minecraft.util.EnumFacing direction = state.getValue(BlockProgrammableWall.FACING);
        if (!state.getValue(BlockProgrammableWall.INVERTED)) direction = direction.getOpposite();
        String name = direction.getName();
        return (tile.isDiagonalHalfHeight() ? "Slope rises: " : "Slope leans: ")
                + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String diagonalWidthLabel() {
        return tile.isDiagonalHalfHeight() ? "Shape: Half height, full width"
                : "Shape: " + (tile.isDiagonalFullWidth() ? "Full height, full width" : "Full height, half width");
    }

    private void sendPortholeSettings() {
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableWallShade(
                tile.getPos(), shade, join, shape));
    }

    private int maxScroll() {
        return Math.max(0, ScreenHousingTextures.IDS.length - rows);
    }

    private int thumbHeight() {
        return Math.max(8, ROW_H * rows * rows / ScreenHousingTextures.IDS.length);
    }

    private int thumbY() {
        return listY + (ROW_H * rows - thumbHeight()) * scroll / maxScroll();
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
        scroll = scrollForDrag(mouseY, listY, ROW_H * rows,
                thumbHeight(), maxScroll(), scrollbarDragOffset);
    }

    private void choose(int choice) {
        selected = choice;
        if (supportsFaces() && tile.getFaceTextures().enabled && faceTarget >= 0) {
            int[] choices = tile.getFaceTextures().choices();
            choices[faceTarget] = choice;
            sendFaces(new com.vandorlabs.tiles.FaceTextures(true, choices));
            initGui();
            return;
        }
        tile.setHousingTexture(choice);
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(tile.getPos(),
                tile.getSelectedScreen(), tile.isRedstoneEnabled(), tile.getDisplayMode(),
                tile.isFramed(), tile.getAnimationSpeedIndex(), tile.getInputPanel(),
                tile.getSecondaryInputPanel(), tile.isSmallInput(),
                tile.getRedstoneChannel(), choice));
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        int before=textureList.selected();
        if(textureList.click(mouseX,mouseY,button)){if(before!=textureList.selected())choose(textureList.selected());return;}
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        textureList.release();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY,
            int clickedButton, long timeSinceLastClick) {
        if (textureList.drag(mouseY)) return;
        else super.mouseClickMove(mouseX, mouseY, clickedButton, timeSinceLastClick);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        textureList.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 111 && diagonal) {
            net.minecraft.block.state.IBlockState next = com.vandorlabs.blocks.DiagonalPanelGeometry.reverseSlope(
                    tile.getWorld().getBlockState(tile.getPos()), tile.isDiagonalHalfHeight());
            tile.getWorld().setBlockState(tile.getPos(), next, 3);
            int mode = tile.isDiagonalHalfHeight() ? 2 : tile.isDiagonalFullWidth() ? 1 : 0;
            tile.setDiagonalGeometry(mode, tile.getDiagonalFill());
            PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageDiagonalGeometry(
                    tile.getPos(), mode, tile.getDiagonalFill(), next.getBlock().getMetaFromState(next)));
            initGui();
        }
        if (button.id == 106) {
            sendFaces(new com.vandorlabs.tiles.FaceTextures(!tile.getFaceTextures().enabled,
                    tile.getFaceTextures().choices()));
            faceTarget = -1;
            selected = selectedTexture();
            initGui();
        }
        if (button.id == 107) {
            faceTarget = (faceTarget + 2) % 7 - 1;
            selected = selectedTexture();
            initGui();
        }
        if (button.id == 108 && faceTarget >= 0) {
            int[] choices = tile.getFaceTextures().choices();
            choices[faceTarget] = -1;
            sendFaces(new com.vandorlabs.tiles.FaceTextures(true, choices));
            selected = selectedTexture();
            initGui();
        }
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
        if (button.id == 104) {
            shape = (shape + 1) % SHAPES.length;
            tile.setPortholeShape(shape);
            button.displayString = shapeLabel();
            sendPortholeSettings();
        }
        if (button.id == 105 || button.id == 109 || button.id == 110) {
            int mode=tile.isDiagonalHalfHeight()?2:tile.isDiagonalFullWidth()?1:0;
            int fill=tile.getDiagonalFill();
            if (button.id==105) mode=(mode+1)%3;
            if (button.id==109) fill^=1;
            if (button.id==110) fill^=2;
            tile.setDiagonalGeometry(mode,fill);
            PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageDiagonalGeometry(tile.getPos(),mode,fill));
            initGui();
        }
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,
            int mouseX, int mouseY) { }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 18, 0xFF202028);
        fontRenderer.drawString(I18n.format(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                        instanceof com.vandorlabs.blocks.BlockProgrammableStairs ? "gui.vandorlabs.stairs.title"
                : tile.getBlockType() instanceof BlockProgrammableSlab ? "gui.vandorlabs.slab.title"
                        : fullBlock ? "gui.vandorlabs.block.title"
                        : "gui.vandorlabs.wall.title"),
                guiLeft + 8, guiTop + 5, 0xFFFFFFFF);
        textureList.draw(fontRenderer,mouseX,mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
