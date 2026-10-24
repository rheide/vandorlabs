package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.WirelessDialogLayout;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native widgets preserve the existing endpoint dialog order and reverse cycling. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WirelessScreen extends AbstractContainerScreen<WirelessMenu> {
    // Opt-in client checks compare real label pixels against a displaced-thumb
    // reference and an intentionally wrong paint-order negative control.
    static int glyphCaptureMode;
    private EditBox channels;
    private WirelessDialogLayout layout;
    private Button signalButton, faceButton;
    private boolean analog, ready;
    private int on, off, face;
    public WirelessScreen(WirelessMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        analog = menu.tile.analog(); on = menu.tile.onLevel(); off = menu.tile.offLevel(); face = menu.tile.port().get3DDataValue();
        imageWidth = WirelessDialogLayout.MAX_WIDTH; imageHeight = menu.tile.transmitter() ? WirelessDialogLayout.TRANSMITTER_HEIGHT : WirelessDialogLayout.RECEIVER_HEIGHT;
    }
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) { event.register(WirelessContent.MENU.get(), WirelessScreen::new); }
    @Override protected void init() {
        String text = channels == null ? menu.tile.getRedstoneChannels().toString() : channels.getValue();
        layout = new WirelessDialogLayout(width, height, menu.tile.transmitter()); imageWidth = layout.width; imageHeight = layout.height;
        super.init(); clearWidgets(); ready = false;
        ProgrammableDialogLayout.Button field = layout.channels();
        channels = new EditBox(font, field.x, field.y, field.width, field.height, Component.literal("Channels"));
        channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH); channels.setFilter(WirelessDialogLayout::validChannelCharacters); channels.setValue(text);
        channels.setResponder(ignored -> { if (ready) send(); }); addRenderableWidget(channels);
        for (WirelessDialogLayout.Control control : layout.controls(analog, on, off, face)) {
            ProgrammableDialogLayout.Button bounds = control.bounds;
            if (control.slider) {
                AbstractSliderButton slider = slider(bounds, bounds.id == WirelessDialogLayout.ON); slider.active = control.enabled; addRenderableWidget(slider);
            } else {
                Button button = Button.builder(Component.literal(bounds.label), ignored -> {
                    if (bounds.id == WirelessDialogLayout.SIGNAL) { analog = !analog; send(); rebuild(); }
                    else if (bounds.id == WirelessDialogLayout.PORT) cycleFace(1);
                    else if (bounds.id == WirelessDialogLayout.DONE) { send(); onClose(); }
                }).bounds(bounds.x, bounds.y, bounds.width, bounds.height).build();
                button.active = control.enabled; addRenderableWidget(button);
                if (bounds.id == WirelessDialogLayout.SIGNAL) signalButton = button;
                if (bounds.id == WirelessDialogLayout.PORT) faceButton = button;
            }
        }
        ready = true;
    }
    private AbstractSliderButton slider(ProgrammableDialogLayout.Button bounds, boolean onSlider) {
        return new AbstractSliderButton(bounds.x, bounds.y, bounds.width, bounds.height,
                Component.literal(bounds.label), (onSlider ? on : off) / 15.0) {
            @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                double actual = value;
                if (onSlider && glyphCaptureMode == 1) value = 0;
                try { super.renderWidget(graphics, mouseX, mouseY, partialTick); }
                finally { value = actual; }
                if (onSlider && glyphCaptureMode == 2) {
                    graphics.flush(); graphics.pose().pushPose(); graphics.pose().translate(0, 0, 2);
                    graphics.blitSprite(getHandleSprite(), getX() + (int) (value * (width - 8)), getY(), 8, getHeight());
                    graphics.flush(); graphics.pose().popPose();
                }
            }
            @Override protected void renderScrollingString(GuiGraphics graphics, net.minecraft.client.gui.Font font, int padding, int color) {
                if (glyphCaptureMode == 3) { super.renderScrollingString(graphics, font, padding, color); return; }
                graphics.pose().pushPose(); graphics.pose().translate(0, 0, 1);
                super.renderScrollingString(graphics, font, padding, color);
                graphics.pose().popPose();
            }
            @Override protected void updateMessage() { setMessage(Component.literal(WirelessDialogLayout.levelLabel(onSlider, (int) Math.round(value * 15)))); }
            @Override protected void applyValue() {
                int next = (int) Math.round(value * 15);
                if (onSlider) on = next; else off = next;
                if (ready) send();
            }
        };
    }
    private void rebuild() { init(); }
    private void cycleFace(int direction) { face = WirelessDialogLayout.nextFace(face, direction); send(); rebuild(); }
    private void send() {
        ChannelList parsed = ChannelList.parse(channels.getValue());
        boolean valid = parsed != null;
        channels.setTextColor(valid ? 0xe0e0e0 : 0xff7777);
        if (valid) PacketDistributor.sendToServer(new WirelessPayload(menu.containerId, parsed.toString(), analog, on, off, face));
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1 && signalButton.isMouseOver(x, y)) { analog = !analog; send(); rebuild(); return true; }
        if (button == 1 && faceButton.isMouseOver(x, y)) { cycleFace(-1); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public void onClose() { if (ready) send(); super.onClose(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, WirelessDialogLayout.PANEL_COLOR);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + WirelessDialogLayout.HEADER_HEIGHT, WirelessDialogLayout.HEADER_COLOR);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, layout.title(), WirelessDialogLayout.LABEL_X, WirelessDialogLayout.TITLE_Y, 0xffffff, false);
        graphics.drawString(font, WirelessDialogLayout.CHANNEL_LABEL, WirelessDialogLayout.LABEL_X, WirelessDialogLayout.CHANNEL_LABEL_Y, 0xffffff, false);
        if (!menu.tile.transmitter() && analog) graphics.drawString(font, WirelessDialogLayout.ANALOG_HELP, WirelessDialogLayout.LABEL_X, WirelessDialogLayout.HELP_Y, 0xc9d9e2, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
