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
    private int previousGuiScale=-1;

    public GuiDuplifier(InventoryPlayer inventory) {
        super(new ContainerDuplifier(inventory));
        xSize = 420;
        int rows=0;
        for(int page=0;page<DuplifierApplyOptions.PAGES.length;page++) {
            int options=0;for(DuplifierApplyOptions.Option option:DuplifierApplyOptions.OPTIONS)if(option.page==page)options++;
            rows=Math.max(rows,(options+1)/2);
        }
        ySize = Math.max(234,82+rows*21+40);
    }

    @Override public void initGui() {
        if(width<xSize+12 || height<ySize+12) {
            if(previousGuiScale<0)previousGuiScale=mc.gameSettings.guiScale;
            int scale=new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
            net.minecraft.client.gui.ScaledResolution resolution;
            do {
                mc.gameSettings.guiScale=Math.max(1,--scale);
                resolution=new net.minecraft.client.gui.ScaledResolution(mc);
            } while((resolution.getScaledWidth()<xSize+12 || resolution.getScaledHeight()<ySize+12) && scale>1);
            width=resolution.getScaledWidth();height=resolution.getScaledHeight();
        }
        super.initGui();
        showPage();
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        if(previousGuiScale>=0){mc.gameSettings.guiScale=previousGuiScale;previousGuiScale=-1;}
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
        buttonList.add(new GuiButton(99, guiLeft + 162, guiTop + ySize - 28,
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
    @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,java.util.stream.IntStream.concat(java.util.stream.IntStream.of(98),
                java.util.stream.IntStream.range(OPTION_START,OPTION_START+DuplifierApplyOptions.OPTIONS.length)).toArray()))return;
        super.mouseClicked(x,y,button);
    }
}
