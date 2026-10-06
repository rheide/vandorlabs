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
    private int rows = 8;
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
        return block == com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_STORAGE
                || block == com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK || slab;
    }
    private int selectedTexture() {
        return faceTarget == -2 ? (tile.getSideTexture()>=0?tile.getSideTexture():porthole?26:tile.getHousingTexture())
                : faceTarget < 0 ? tile.getHousingTexture()
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
    private int listX;
    private int listY;

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

    @Override public void initGui() { initWallLayout(); }

    private void initWallLayout() {
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);
        xSize=layout.width;ySize=layout.height;
        super.initGui();
        buttonList.clear();
        int controlsWidth=ProgrammableDialogLayout.CONTROLS_WIDTH, controlsX=layout.controlsX;
        boolean faceTabs=supportsFaces() && tile.getFaceTextures().enabled;
        listX=guiLeft+12; listY=guiTop+(faceTabs || slab || porthole?54:30);
        rows=Math.max(2,(ySize-(faceTabs || slab || porthole?60:64))/HousingTextureList.ROW_HEIGHT);
        textureList=new HousingTextureList(listX,listY,controlsX-listX-19,selected)
                .visibleRows(rows).custom(this::choose);
        if(faceTabs || slab || porthole) {
            int count=faceTabs?(slab?8:7):2;
            int span=faceTabs?xSize-24:controlsX-listX-19;
            int tabWidth=span/count;
            for(int i=0;i<count;i++) {
                boolean side=(slab || porthole) && i==1;
                int face=i-((slab || porthole)?2:1);
                int id=i==0?120:side?127:121+face;
                String label=i==0?"Main":side?"Side":FACE_NAMES[face];
                GuiButton tab=new GuiButton(id,listX+i*tabWidth,guiTop+30,
                        i==count-1?span-i*tabWidth-2:tabWidth-2,18,label);
                tab.enabled=faceTarget!=(i==0?-1:side?-2:face);buttonList.add(tab);
            }
        }
        int y=guiTop+(faceTabs?54:30);
        if (supportsFaces()) {
            buttonList.add(new GuiButton(106,controlsX,y,controlsWidth,20,
                    "Face overrides: "+(tile.getFaceTextures().enabled?"On":"Off")));y+=24;
            if (tile.getFaceTextures().enabled) {
                GuiButton inherit=new GuiButton(108,controlsX,y,controlsWidth,20,
                        faceTarget<0?"Select a face":tile.getFaceTextures().choice(faceTarget)<0?"Using main texture":"Use main texture");
                inherit.enabled=faceTarget>=0 && tile.getFaceTextures().choice(faceTarget)>=0;
                buttonList.add(inherit);y+=24;
            }
        }
        if(faceTarget==-2) {
            GuiButton reset=new GuiButton(128,controlsX,y,controlsWidth,20,"Use default side texture");
            reset.enabled=tile.getSideTexture()>=0;buttonList.add(reset);y+=24;
        }
        if (slab) { buttonList.add(new GuiButton(103,controlsX,y,controlsWidth,20,slabSidesLabel())); y+=24; }
        if (porthole) {
            buttonList.add(new GuiButton(101,controlsX,y,controlsWidth,20,shadeLabel())); y+=24;
            buttonList.add(new GuiButton(102,controlsX,y,controlsWidth,20,joinLabel())); y+=24;
            buttonList.add(new GuiButton(104,controlsX,y,controlsWidth,20,shapeLabel())); y+=24;
        }
        if (diagonal) {
            buttonList.add(new GuiButton(105,controlsX,y,controlsWidth,20,diagonalWidthLabel())); y+=24;
            buttonList.add(new GuiButton(111,controlsX,y,controlsWidth,20,slopeLabel())); y+=24;
            if (!porthole) {
                buttonList.add(new GuiButton(109,controlsX,y,controlsWidth,20,"Fill inside: "+((tile.getDiagonalFill()&1)!=0?"On":"Off"))); y+=24;
                buttonList.add(new GuiButton(110,controlsX,y,controlsWidth,20,"Fill outside: "+((tile.getDiagonalFill()&2)!=0?"On":"Off")));
            }
        }
        buttonList.add(new GuiButton(100,controlsX,guiTop+ySize-26,controlsWidth,20,I18n.format("gui.done")));
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
        return tile.isDiagonalHalfHeight() ? "Size: Wide / half height"
                : "Size: " + (tile.isDiagonalFullWidth() ? "Full width / height" : "Tall / half width");
    }

    private void sendPortholeSettings() {
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableWallShade(
                tile.getPos(), shade, join, shape));
    }

    static int scrollForDrag(int mouseY, int trackTop, int trackHeight,
            int thumbHeight, int maximum, int dragOffset) {
        int travel = trackHeight - thumbHeight;
        if (maximum <= 0 || travel <= 0) return 0;
        int thumbTop = Math.max(0, Math.min(travel,
                mouseY - trackTop - dragOffset));
        return Math.round((float) thumbTop * maximum / travel);
    }

    private void choose(int choice) {
        selected = choice;
        if(faceTarget==-2) {
            tile.setSideTexture(choice);
            PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSideTexture(tile.getPos(),choice));
            initGui();return;
        }
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
                tile.getRedstoneChannel(), choice).withChannels(tile.getRedstoneChannels()));
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,mouseX,mouseY,button,this::actionPerformed,101,102,103,104,105,106,109,110,111))return;
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
        if (button.id == 120 || (button.id >= 121 && button.id <= 126 && supportsFaces() && tile.getFaceTextures().enabled)) {
            faceTarget = button.id - 121;
            selected = selectedTexture();
            initGui();
        }
        if(button.id==127 || button.id==128) {
            faceTarget=-2;
            if(button.id==128){tile.setSideTexture(-1);PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSideTexture(tile.getPos(),-1));}
            selected=selectedTexture();initGui();
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
            shade = GuiOptionCycle.next(shade,SHADES.length);
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
            shape = GuiOptionCycle.next(shape,SHAPES.length);
            tile.setPortholeShape(shape);
            button.displayString = shapeLabel();
            sendPortholeSettings();
        }
        if (button.id == 105 || button.id == 109 || button.id == 110) {
            int mode=tile.isDiagonalHalfHeight()?2:tile.isDiagonalFullWidth()?1:0;
            int fill=tile.getDiagonalFill();
            if (button.id==105) mode=GuiOptionCycle.next(mode,3);
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
                        : tile instanceof com.vandorlabs.tiles.TileEntityProgrammableStorage ? "tile.vandorlabs.programmable_storage.name"
                        : fullBlock ? "gui.vandorlabs.block.title"
                        : "gui.vandorlabs.wall.title"),
                guiLeft + 8, guiTop + 5, 0xFFFFFFFF);
        textureList.draw(fontRenderer,mouseX,mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
