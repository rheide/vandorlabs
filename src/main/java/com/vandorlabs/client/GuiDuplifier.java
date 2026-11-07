package com.vandorlabs.client;

import com.vandorlabs.container.ContainerDuplifier;
import com.vandorlabs.items.DuplifierApplyOptions;
import com.vandorlabs.ui.CopifierDialog;
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
        xSize = CopifierDialog.WIDTH;
        ySize = CopifierDialog.height();
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
                + (DuplifierApplyOptions.enabled(DuplifierApplyOptions.maskWords(tool()), index)
                ? "On" : "Off");
    }

    private String connectedLabel() {
        return "Connected Matching Blocks: " + (DuplifierApplyOptions.connected(tool()) ? "On" : "Off");
    }

    private String directionLabel(){return "Ignore directionality: "+(DuplifierApplyOptions.ignoreDirectionality(tool())?"On":"Off");}
    private void sendOptions(){PacketHandler.INSTANCE.sendToServer(new MessageDuplifierOptions(DuplifierApplyOptions.maskWords(tool()),DuplifierApplyOptions.connected(tool()),DuplifierApplyOptions.ignoreDirectionality(tool())));}

    private void showPage() {
        buttonList.clear();
        for(CopifierDialog.Control control:CopifierDialog.controls(guiLeft,guiTop,page,
                DuplifierApplyOptions.maskWords(tool()),DuplifierApplyOptions.connected(tool()),
                DuplifierApplyOptions.ignoreDirectionality(tool()),"Done")) {
            GuiButton button=control.tab?new DialogTabButton(control.id,control.x,control.y,
                    control.width,control.height,control.label):new GuiButton(control.id,control.x,
                    control.y,control.width,control.height,control.label);
            button.enabled=control.enabled;buttonList.add(button);
        }
    }

    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==97){PacketHandler.INSTANCE.sendToServer(MessageDuplifierOptions.undo());
        } else if (button.id == 99) {
            mc.player.closeScreen();
        } else if (button.id == 96) {
            DuplifierApplyOptions.setIgnoreDirectionality(tool(),!DuplifierApplyOptions.ignoreDirectionality(tool()));
            sendOptions();button.displayString=directionLabel();
        } else if (button.id == 98) {
            DuplifierApplyOptions.setConnected(tool(), !DuplifierApplyOptions.connected(tool()));
            sendOptions();
            button.displayString = connectedLabel();
        } else if (button.id >= 0 && button.id < DuplifierApplyOptions.PAGES.length) {
            page = button.id;
            showPage();
        } else if (button.id >= OPTION_START
                && button.id < OPTION_START + DuplifierApplyOptions.OPTIONS.length) {
            int index = button.id - OPTION_START;
            long[] mask = DuplifierApplyOptions.maskWords(tool());mask[index/64]^=1L<<(index%64);
            DuplifierApplyOptions.setMask(tool(), mask);
            sendOptions();
            button.displayString = optionLabel(index);
        }
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial,
            int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF15171C);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 22, 0xFF2A3039);
        fontRenderer.drawString("Duplifier: Apply Settings", guiLeft + 10,
                guiTop + 7, 0xFFFFFFFF);
        DialogHeader.logo(guiLeft,guiTop,xSize);
        fontRenderer.drawString("Choose which copied properties to apply", guiLeft + 14,
                guiTop + 70, 0xFFB8C7D2);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,java.util.stream.IntStream.concat(java.util.stream.IntStream.of(96,98),
                java.util.stream.IntStream.range(OPTION_START,OPTION_START+DuplifierApplyOptions.OPTIONS.length)).toArray()))return;
        super.mouseClicked(x,y,button);
    }
}
