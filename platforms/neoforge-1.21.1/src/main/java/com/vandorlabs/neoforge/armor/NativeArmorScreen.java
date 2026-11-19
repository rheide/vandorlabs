package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

final class NativeArmorScreen extends AbstractContainerScreen<NativeArmorMenu> {
    private static final int CHOICE_BASE = 0x10000000;
    private static final int ROWS_BASE = -1000;
    private NativeMaterialPicker picker;

    NativeArmorScreen(NativeArmorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
    }

    @Override
    protected void init() {
        int rows = Math.max(3, Math.min(8, (height - 76) / 22));
        imageHeight = rows * 22 + 66;
        super.init();
        if (!(menu.editorStack().getItem() instanceof NativeArmorItem armor)) return;
        picker = new NativeMaterialPicker(-1, NativeArmorItem::listedMaterial, this::choose).sampling(false);
        for (var design : NativeArmorCatalog.designs()) {
            if (design.slot() == armor.getEquipmentSlot()) {
                picker.specialMaterial(row(design.choice()), design.label(), "Armor",
                    design.icon().replace(":items/", ":item/"));
            }
        }
        int selection = NativeArmorItem.choice(menu.editorStack());
        picker.select(NativeArmorItem.listedMaterial(selection) ? selection : row(NativeArmorItem.design(menu.editorStack()).choice()), false);
        picker.layout(leftPos + 12, topPos + 30, imageWidth - 32, rows, true);
        addRenderableWidget(picker.searchField());
        addRenderableWidget(NativeDialogButton.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(leftPos + 12, topPos + imageHeight - 28, imageWidth - 24, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (picker == null && menu.synchronizedStack && menu.editorStack().getItem() instanceof NativeArmorItem) rebuildWidgets();
    }

    private static int row(int choice) { return ROWS_BASE - (choice - CHOICE_BASE); }

    private void choose(int row) {
        PacketDistributor.sendToServer(new NativeArmorBindings.Edit(menu.containerId, row >= 0 ? row : CHOICE_BASE + ROWS_BASE - row));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return picker != null && picker.click(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return picker != null && picker.drag(x, y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (picker != null) picker.release();
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return picker != null && picker.wheel(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return picker != null && picker.keyPressed(key, scan, modifiers) || super.keyPressed(key, scan, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        NativeDialogVisuals.panel(graphics, font, title, leftPos, topPos, imageWidth, imageHeight, DialogStyle.PROGRAMMABLE);
        if (picker != null) picker.render(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}
}
