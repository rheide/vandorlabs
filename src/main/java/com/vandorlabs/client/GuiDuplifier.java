package com.vandorlabs.client;

import com.vandorlabs.container.ContainerDuplifier;
import com.vandorlabs.items.DuplifierApplyOptions;
import com.vandorlabs.network.MessageDuplifierOptions;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/** A compact grid of per-property apply switches; no texture previews. */
public final class GuiDuplifier extends GuiContainer {
    private static final int OPTION_START = 100;
    private int page;

    public GuiDuplifier(InventoryPlayer inventory) {
        super(new ContainerDuplifier(inventory));
        xSize = 420;
        ySize = 234;
    }

    @Override public void initGui() {
        super.initGui();
        showPage();
    }

    private ItemStack tool() { return mc.player.getHeldItemMainhand(); }

    private String optionLabel(int index) {
        return DuplifierApplyOptions.OPTIONS[index].label + ": "
                + (DuplifierApplyOptions.enabled(DuplifierApplyOptions.mask(tool()), index)
                ? "On" : "Off");
    }

    private String connectedLabel() {
        return "Connected Matching Blocks: " + (DuplifierApplyOptions.connected(tool()) ? "On" : "Off");
    }

    private void showPage() {
        buttonList.clear();
        buttonList.add(new GuiButton(98, guiLeft + 14, guiTop + 26,
                392, 18, connectedLabel()));
        for (int i = 0; i < DuplifierApplyOptions.PAGES.length; i++) {
            GuiButton tab = new GuiButton(i, guiLeft + 10 + i * 80, guiTop + 48,
                    76, 18, DuplifierApplyOptions.PAGES[i]);
            tab.enabled = i != page;
            buttonList.add(tab);
        }
        int indexOnPage = 0;
        for (int i = 0; i < DuplifierApplyOptions.OPTIONS.length; i++) {
            if (DuplifierApplyOptions.OPTIONS[i].page != page) continue;
            int column = indexOnPage % 2;
            int row = indexOnPage / 2;
            buttonList.add(new GuiButton(OPTION_START + i,
                    guiLeft + 14 + column * 200, guiTop + 82 + row * 21,
                    190, 18, optionLabel(i)));
            indexOnPage++;
        }
        buttonList.add(new GuiButton(99, guiLeft + 162, guiTop + 211,
                96, 18, "Done"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 99) {
            mc.player.closeScreen();
        } else if (button.id == 98) {
            DuplifierApplyOptions.setConnected(tool(), !DuplifierApplyOptions.connected(tool()));
            PacketHandler.INSTANCE.sendToServer(new MessageDuplifierOptions(
                    DuplifierApplyOptions.mask(tool()), DuplifierApplyOptions.connected(tool())));
            button.displayString = connectedLabel();
        } else if (button.id >= 0 && button.id < DuplifierApplyOptions.PAGES.length) {
            page = button.id;
            showPage();
        } else if (button.id >= OPTION_START
                && button.id < OPTION_START + DuplifierApplyOptions.OPTIONS.length) {
            int index = button.id - OPTION_START;
            long mask = DuplifierApplyOptions.mask(tool()) ^ (1L << index);
            DuplifierApplyOptions.setMask(tool(), mask);
            PacketHandler.INSTANCE.sendToServer(new MessageDuplifierOptions(mask,
                    DuplifierApplyOptions.connected(tool())));
            button.displayString = optionLabel(index);
        }
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial,
            int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF15171C);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 22, 0xFF2A3039);
        fontRenderer.drawString("Duplifier: Apply Settings", guiLeft + 10,
                guiTop + 7, 0xFFFFFFFF);
        fontRenderer.drawString("Choose which copied properties to apply", guiLeft + 14,
                guiTop + 70, 0xFFB8C7D2);
    }
}
