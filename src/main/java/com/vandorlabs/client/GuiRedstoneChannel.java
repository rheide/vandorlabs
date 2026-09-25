package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.MessageRedstoneChannel;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockConnectedPropulsionLight;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiRedstoneChannel extends GuiContainer {
    private final RedstoneChannelMember member;
    private GuiTextField channelField;
    private final boolean thruster;
    private boolean particles;
    private final boolean connected;
    private boolean join;
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
        this.connected = thruster && member.channelTile().getWorld()
                .getBlockState(member.channelTile().getPos()).getBlock()
                instanceof BlockConnectedPropulsionLight;
        this.join = connected && ((TileEntityRedstoneLight) member).isJoin();
        xSize = 240;
        ySize = connected ? 160 : thruster ? 132 : 104;
    }

    @Override public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        channelField = new GuiTextField(0, fontRenderer, guiLeft + 116, guiTop + 38, 106, 18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(member.getRedstoneChannel()));
        channelField.setFocused(true);
        if (thruster) {
            particleButton = new GuiButton(2, guiLeft + 116, guiTop + 68, 106, 20,
                    particleLabel());
            buttonList.add(particleButton);
        }
        if (connected) buttonList.add(new GuiButton(3, guiLeft + 116,
                guiTop + 96, 106, 20, joinLabel()));
        buttonList.add(new GuiButton(1, guiLeft + 14,
                guiTop + (connected ? 128 : thruster ? 100 : 72),
                212, 20, "Done"));
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
                        thruster, particles, connected, join));
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
    }

    private String particleLabel() { return particles ? "Particles: On" : "Particles: Off"; }
    private String joinLabel() { return join ? "Join: On" : "Join: Off"; }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit();
            mc.player.closeScreen();
            return;
        }
        if (!channelField.textboxKeyTyped(typedChar, keyCode)) super.keyTyped(typedChar, keyCode);
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);
        channelField.mouseClicked(x, y, button);
    }

    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF19232C);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 28, 0xFF304858);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString("Redstone Channel", 14, 10, 0xFFFFFF);
        fontRenderer.drawString("Channel (0 = none)", 14, 43, 0xDAE8F0);
        if (thruster) fontRenderer.drawString("Active mode", 14, 74, 0xDAE8F0);
        if (connected) fontRenderer.drawString("Adjacent", 14, 102, 0xDAE8F0);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        channelField.drawTextBox();
    }
}
