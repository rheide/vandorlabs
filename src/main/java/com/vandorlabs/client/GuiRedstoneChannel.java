package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.MessageRedstoneChannel;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class GuiRedstoneChannel extends GuiContainer {
    private final RedstoneChannelMember member;
    private GuiTextField channelField;
    private final boolean thruster;
    private boolean particles;
    private boolean connected;
    private boolean join;
    private GuiButton joinButton;
    private final boolean programmableThruster;
    private int shape;
    private int sideTexture;
    private HousingTextureList housingList;
    private GuiButton particleButton;

    public GuiRedstoneChannel(RedstoneChannelMember member) {
        super(new ContainerRedstoneChannel(member));
        this.member = member;
        this.thruster = member instanceof TileEntityRedstoneLight
                && member.channelTile().getWorld() != null
                && member.channelTile().getWorld().getBlockState(member.channelTile().getPos())
                        .getBlock() instanceof BlockPropulsionLight;
        this.particles = thruster
                && ((TileEntityRedstoneLight) member).isParticleStreamSelected();
        BlockPropulsionLight block = thruster ? (BlockPropulsionLight) member.channelTile()
                .getWorld().getBlockState(member.channelTile().getPos()).getBlock() : null;
        this.programmableThruster = block != null && !block.familyId().isEmpty();
        this.shape = programmableThruster ? block.shape() : 0;
        this.connected = block != null && block.hasJoinMode();
        this.join = thruster && ((TileEntityRedstoneLight) member).isJoin();
        this.sideTexture = thruster ? ((TileEntityRedstoneLight) member).getSideTexture() : 0;
        xSize = thruster ? 410 : 240;
        ySize = thruster ? 190 : 104;
    }

    @Override public void initGui() {
        super.initGui();
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        channelField = new GuiTextField(0, fontRenderer, guiLeft + 116, guiTop + 38, 106, 18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(member.getRedstoneChannel()));
        channelField.setFocused(true);
        if (thruster) housingList = new HousingTextureList(guiLeft + 250,
                guiTop + 48, 144, sideTexture);
        if (thruster) {
            particleButton = new GuiButton(2, guiLeft + 116, guiTop + 68, 106, 20,
                    particleLabel());
            buttonList.add(particleButton);
        }
        if (programmableThruster) buttonList.add(new GuiButton(4, guiLeft + 116,
                guiTop + 96, 106, 20, shapeLabel()));
        refreshJoinButton();
        buttonList.add(new GuiButton(1, guiLeft + (xSize - 200) / 2,
                guiTop + (thruster ? 158 : 72),
                200, 20, "Done"));
    }

    private void refreshJoinButton() {
        if (joinButton != null) buttonList.remove(joinButton);
        joinButton = null;
        if (connected) {
            joinButton = new GuiButton(3, guiLeft + 116,
                    guiTop + (programmableThruster ? 124 : 96),
                    106, 20, joinLabel());
            buttonList.add(joinButton);
        }
    }

    protected int channel() {
        try {
            long value = Long.parseLong(channelField.getText());
            return value > Integer.MAX_VALUE ? -1 : (int) value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    protected void submit() {
        int value = channel();
        if (value >= 0) PacketHandler.INSTANCE.sendToServer(
                new MessageRedstoneChannel(member.channelTile().getPos(), value,
                        thruster, particles, connected, join, thruster, sideTexture,
                        programmableThruster, shape));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            submit();
            mc.player.closeScreen();
        }
        if (button.id == 2 && thruster) {
            particles = !particles;
            particleButton.displayString = particleLabel();
        }
        if (button.id == 3 && connected) {
            join = !join;
            button.displayString = joinLabel();
        }
        if (button.id == 4 && programmableThruster) {
            shape = (shape + 1) % 3;
            button.displayString = shapeLabel();
            connected = shape == 0;
            refreshJoinButton();
        }
    }

    private String particleLabel() { return particles ? "Particles: On" : "Particles: Off"; }
    private String joinLabel() { return join ? "Join: On" : "Join: Off"; }
    private String shapeLabel() {
        return "Shape: " + (shape == 1 ? "Hexagon" : shape == 2 ? "Wedge" : "Block");
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit();
            mc.player.closeScreen();
            return;
        }
        if (!channelField.textboxKeyTyped(typedChar, keyCode)) super.keyTyped(typedChar, keyCode);
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (housingList != null && housingList.click(x, y, button)) {
            sideTexture = housingList.selected();
            return;
        }
        super.mouseClicked(x, y, button);
        channelField.mouseClicked(x, y, button);
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (housingList != null && housingList.drag(y)) return;
        super.mouseClickMove(x, y, button, elapsed);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (housingList != null) housingList.release();
        super.mouseReleased(x, y, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (housingList != null) housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1,
                Mouse.getEventDWheel());
    }

    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 28, 0xFF202028);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        fontRenderer.drawString("Redstone Channel", 14, 10, 0xFFFFFFFF);
        fontRenderer.drawString("Channel (0 = none)", 14, 43, 0xFFD8D8D8);
        if (thruster) fontRenderer.drawString("Active mode", 14, 74, 0xFFD8D8D8);
        if (programmableThruster) fontRenderer.drawString("Shape", 14, 102, 0xFFD8D8D8);
        if (connected) fontRenderer.drawString("Adjacent", 14,
                programmableThruster ? 130 : 102, 0xFFD8D8D8);
        if (thruster) fontRenderer.drawString("Side Texture", 250, 34, 0xFFD8D8D8);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        GlStateManager.disableLighting();
        if (housingList != null) housingList.draw(fontRenderer, mouseX, mouseY);
        channelField.drawTextBox();
    }
}
