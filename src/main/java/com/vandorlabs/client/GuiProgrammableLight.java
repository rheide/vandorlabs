package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageProgrammableLight;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Texture picker with a live preview and a 0 to 15 light level slider. */
public final class GuiProgrammableLight extends GuiContainer {
    private final TileEntityProgrammableLight tile;
    private int selected;
    private int level,offset;
    private boolean signalBrightness;
    private boolean join;
    private int housing;
    private int trigger;
    private boolean small,tileSides;
    private HousingTextureList housingList,faceList;
    private GuiTextField channelField;
    private boolean draggingLevel;
    private ProgrammableDialogLayout layout;
    private int textureTab;
    private static final int SLIDER_W = 138;

    public GuiProgrammableLight(InventoryPlayer inventory, TileEntityProgrammableLight tile) {
        super(new ContainerAnimatedScreenSelector(inventory, tile));
        this.tile = tile;
        small=tile.isSmallInput();tileSides=tile.isSlabTileSides();
        selected = tile.getFaceTexture();
        level = tile.getConfiguredLightLevel();offset=tile.getLightOffset();signalBrightness=tile.isSignalBrightness();
        join = tile.isJoin();
        housing = tile.getHousingTexture();
        trigger = tile.getTrigger();
        xSize = 420;
        ySize = 240;
    }

    @Override public void initGui() {
        layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;
        super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        channelField=new GuiTextField(0,fontRenderer,layout.controlsX,guiTop+177,154,18);
        ChannelFields.configure(channelField);
        channelField.setText(tile.getRedstoneChannels().toString());
        java.util.function.IntPredicate include=choice->!ScreenHousingTextures.isLightOff(choice) && (HousingTextureList.generalTexture(choice) || "Lights".equals(ScreenHousingTextures.category(choice)));
        faceList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,selected,8,null,include).visibleRows(layout.rows(true))
                .custom(value->{selected=value;tile.setFaceTexture(value);send();});
        housingList=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,housing,8,null,include).visibleRows(layout.rows(true))
                .custom(value->{housing=value;tile.setHousingTexture(value);send();});
        buttonList.add(layout.tab(90,0,2,"Light texture"));buttonList.add(layout.tab(91,1,2,"Housing"));
        buttonList.add(layout.control(101,30,joinLabel()));buttonList.add(layout.control(102,52,triggerLabel()));
        if(tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableLightFrame)buttonList.add(layout.control(103,74,sizeLabel()));
        buttonList.add(layout.control(104,96,sidesLabel()));buttonList.add(layout.done(100));refreshTabs();
    }
    private String brightnessLabel(){return signalBrightness?"Brightness: Signal + offset":"Brightness: Slider";}
    private void refreshTabs(){for(GuiButton b:buttonList)if(b.id==90 || b.id==91)b.enabled=b.id-90!=textureTab;}

    private String sizeLabel(){return "Size: "+(small?"Small":"Full");}
    private String sidesLabel(){return "Side layout: "+(tileSides?"Tile":"Fit");}

    private String joinLabel() {
        return "Join: "
                + (join ? I18n.format("options.on") : I18n.format("options.off"));
    }

    private String triggerLabel() {
        if(signalBrightness)return brightnessLabel();
        return trigger == 1 ? "Trigger: Redstone ON"
                : trigger == 2 ? "Trigger: Redstone OFF" : "Trigger: Disabled";
    }

    private int channel() {return ChannelFields.first(channelField);}

    private void send() {
        int channel = channel();
        if (channel < 0) return;
        tile.setSmallInput(small);tile.setSlabTileSides(tileSides);
        tile.configureSignalBrightness(signalBrightness,offset);tile.setFaceTexture(selected);tile.configure(tile.getTexture(), level, join, ChannelFields.parse(channelField), housing, trigger);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableLight(
                tile.getPos(), selected, level, join, channel, housing, trigger,small,tileSides).withChannels(ChannelFields.parse(channelField)).withSignalBrightness(signalBrightness,offset));
    }

    private void setLevelFromMouse(int mouseX) {
        int next = Math.max(0, Math.min(15,
                Math.round(15F * (mouseX - layout.controlsX - 8) / SLIDER_W)));
        if(signalBrightness){offset=Math.max(-15,Math.min(15,Math.round(30F*(mouseX-layout.controlsX-8)/SLIDER_W)-15));send();return;}
        if (next != level) {
            level = next;
            send();
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        int before=faceList.selected();
        if(textureTab==0 && faceList.click(mouseX,mouseY,button)){if(before!=faceList.selected()){selected=faceList.selected();send();}return;}
        if (textureTab==1 && housingList.click(mouseX, mouseY, button)) {
            if (housing != housingList.selected()) {
                housing = housingList.selected();
                send();
            }
            return;
        }
        if (button == 0) {
            if (mouseX >= layout.controlsX && mouseX <= layout.controlsX+154
                    && mouseY >= guiTop+138 && mouseY <= guiTop+152) {
                draggingLevel = true;
                setLevelFromMouse(mouseX);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY,
            int button, long elapsed) {
        if (faceList.drag(mouseY) || housingList.drag(mouseY)) return;
        if (draggingLevel && button == 0) setLevelFromMouse(mouseX);
        else super.mouseClickMove(mouseX, mouseY, button, elapsed);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        draggingLevel = false;
        faceList.release();housingList.release();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if(textureTab==0)faceList.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());
        if(textureTab==1)housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1,
                Mouse.getEventDWheel());
    }

    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==90 || button.id==91){textureTab=button.id-90;refreshTabs();return;}
        if (button.id == 101) {
            join = !join;
            button.displayString = joinLabel();
            send();
        }
        if(button.id==103){small=!small;button.displayString=sizeLabel();send();}
        if(button.id==104){tileSides=!tileSides;button.displayString=sidesLabel();send();}
        if (button.id == 102) {
            if(signalBrightness){signalBrightness=false;trigger=0;}else if(trigger==2)signalBrightness=true;else trigger++;
            button.displayString = triggerLabel();
            send();
        }
        if (button.id == 100) {
            if(channel()<0)return;
            send();
            mc.player.closeScreen();
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if(channel()<0)return;
            send();
            mc.player.closeScreen();
            return;
        }
        if (channelField.textboxKeyTyped(typedChar, keyCode)) send();
        else super.keyTyped(typedChar, keyCode);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        channelField.updateCursorCounter();
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,
            int mouseX, int mouseY) { }

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.title"),guiLeft+12,guiTop+8,0xFFFFFF);
        if(textureTab==0)faceList.draw(fontRenderer,mouseX,mouseY);else housingList.draw(fontRenderer,mouseX,mouseY);
        fontRenderer.drawString((signalBrightness?"Signal offset":I18n.format("gui.vandorlabs.light.level"))+": "+(signalBrightness?offset:level),layout.controlsX,guiTop+124,0xDAE8F0);
        int sliderX=layout.controlsX+8;
        drawRect(sliderX,guiTop+142,sliderX+SLIDER_W,guiTop+148,0xFF555560);
        int thumb=sliderX+Math.round(SLIDER_W*(signalBrightness?(offset+15)/2F:level)/15F);
        drawRect(thumb-3,guiTop+138,thumb+3,guiTop+152,0xFFB8D7E8);
        fontRenderer.drawString("Channels (0 = none)",layout.controlsX,guiTop+166,0xDAE8F0);
        super.drawScreen(mouseX,mouseY,partialTicks);channelField.drawTextBox();
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
