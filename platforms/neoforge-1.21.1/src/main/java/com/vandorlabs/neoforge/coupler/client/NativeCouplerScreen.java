package com.vandorlabs.neoforge.coupler.client;

import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.neoforge.coupler.NativeCouplerBindings;
import com.vandorlabs.neoforge.coupler.NativeCouplerMenu;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeCouplerScreen extends AbstractContainerScreen<NativeCouplerMenu> {
    private EditBox channels;
    private Button polarity, latch;
    private boolean inverted;

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(NativeCouplerBindings.MENU, NativeCouplerScreen::new);
    }

    public NativeCouplerScreen(NativeCouplerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 171;
        inverted = menu.owner.inverted();
    }

    @Override protected void init() {
        super.init();
        int x = leftPos + 12, w = imageWidth - 24;
        channels = addRenderableWidget(new EditBox(font, x, topPos + 45, w, 18, Component.literal("Receive channels")));
        channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);
        channels.setValue(menu.owner.getRedstoneChannels().toString());
        polarity = addRenderableWidget(NativeDialogButton.builder(polarity(), ignored -> changePolarity())
                .bounds(x, topPos + 69, w, 20).build());
        latch = addRenderableWidget(NativeDialogButton.builder(latch(), ignored -> {
            if (send() && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(x, topPos + 94, w, 20).build());
        addRenderableWidget(NativeDialogButton.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(x, topPos + 138, w, 20).build());
    }

    private Component polarity() { return Component.literal(inverted ? "Lock when signal is off" : "Lock when signal is on"); }
    private Component latch() { return Component.literal(menu.owner.engaged() ? "Release Coupler" : "Engage Coupler"); }
    private void changePolarity() {
        inverted = !inverted;
        if (!send()) inverted = !inverted;
        polarity.setMessage(polarity());
    }
    private boolean send() {
        var bank = ChannelList.parse(channels.getValue());
        channels.setTextColor(bank == null ? 0xff7777 : 0xffffff);
        if (bank == null) return false;
        PacketDistributor.sendToServer(new NativeCouplerBindings.Edit(menu.owner.getBlockPos(), menu.containerId, bank, inverted));
        return true;
    }

    @Override protected void containerTick() {
        super.containerTick();
        latch.setMessage(latch());
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1 && polarity.isMouseOver(x, y)) {
            changePolarity();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void onClose() { if (send()) super.onClose(); }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int x, int y) {
        NativeDialogVisuals.panel(graphics, font, title, leftPos, topPos, imageWidth, imageHeight, DialogStyle.LANDING);
        graphics.drawString(font, "Receive channels", leftPos + 12, topPos + 32, 0xb7cadb, false);
        String state = menu.owner.paired() ? "Connected" : menu.owner.engaged() ? "Waiting for an opposing head" : "Released";
        graphics.drawString(font, state, leftPos + 12, topPos + 123, 0xabcfe8, false);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {}
    @Override public boolean isPauseScreen() { return false; }
}
