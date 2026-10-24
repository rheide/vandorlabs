package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
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
    private EditBox channels;
    private Button signalButton, faceButton;
    private boolean analog, ready;
    private int on, off, face;
    public WirelessScreen(WirelessMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        analog = menu.tile.analog(); on = menu.tile.onLevel(); off = menu.tile.offLevel(); face = menu.tile.port().get3DDataValue();
        imageWidth = 280; imageHeight = menu.tile.transmitter() ? 160 : 224;
    }
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) { event.register(WirelessContent.MENU.get(), WirelessScreen::new); }
    @Override protected void init() {
        String text = channels == null ? menu.tile.getRedstoneChannels().toString() : channels.getValue();
        imageWidth = Math.min(280, width - 12); super.init(); clearWidgets(); ready = false;
        channels = new EditBox(font, leftPos + 12, topPos + 48, imageWidth - 24, 18, Component.literal("Channels"));
        channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH); channels.setFilter(textValue -> textValue.matches("[0-9, ]*")); channels.setValue(text);
        channels.setResponder(ignored -> { if (ready) send(); }); addRenderableWidget(channels);
        signalButton = addRenderableWidget(Button.builder(Component.literal("Signal: " + (analog ? "Analog" : "Digital")), ignored -> {
            analog = !analog; send(); rebuild();
        }).bounds(leftPos + 12, topPos + 76, imageWidth - 24, 20).build());
        faceButton = addRenderableWidget(Button.builder(Component.literal((menu.tile.transmitter() ? "Input" : "Output") + " port: " + Direction.from3DDataValue(face).getName()), ignored -> cycleFace(1))
                .bounds(leftPos + 12, topPos + 102, imageWidth - 24, 20).build());
        if (!menu.tile.transmitter()) {
            AbstractSliderButton onSlider = slider(topPos + 128, true); onSlider.active = !analog; addRenderableWidget(onSlider);
            addRenderableWidget(slider(topPos + 154, false));
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> { send(); onClose(); })
                .bounds(leftPos + 12, topPos + imageHeight - 30, imageWidth - 24, 20).build());
        ready = true;
    }
    private AbstractSliderButton slider(int y, boolean onSlider) {
        return new AbstractSliderButton(leftPos + 12, y, imageWidth - 24, 20,
                Component.literal((onSlider ? "On level: " : "Off level: ") + (onSlider ? on : off)), (onSlider ? on : off) / 15.0) {
            @Override protected void updateMessage() { setMessage(Component.literal((onSlider ? "On level: " : "Off level: ") + Math.round(value * 15))); }
            @Override protected void applyValue() {
                int next = (int) Math.round(value * 15);
                if (onSlider) on = next; else off = next;
                if (ready) send();
            }
        };
    }
    private void rebuild() { init(); }
    private void cycleFace(int direction) { face = DialogInput.cycle(face, 6, direction); send(); rebuild(); }
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
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff19232c);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 28, 0xff304858);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, menu.tile.transmitter() ? "Redstone Transmitter" : "Redstone Receiver", 12, 10, 0xffffff, false);
        graphics.drawString(font, "Channels (0 = none)", 12, 34, 0xffffff, false);
        if (!menu.tile.transmitter() && analog) graphics.drawString(font, "On level follows received signal", 12, 180, 0xc9d9e2, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
