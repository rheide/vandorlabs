package com.vandorlabs.client;

import com.vandorlabs.network.MessageSpaceDoor;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.client.gui.GuiButton;

/** Uses the existing channel field and keyboard/save behavior. */
public class GuiSpaceDoor extends GuiRedstoneChannel {
    private final TileEntitySpaceDoor tile;
    private int design,detail;
    private int slideDirection;
    private final boolean sliding;
    private boolean framed;
    private boolean middle;
    private static final String[] LABELS={"Observation","Airlock","Standard","Security","Reactor Service",
            "Viewport","Laboratory","Cargo","Ventilation","Cargo Lift","Blast Shield","Glazed Hangar",
            "Quarantine Seal","Reactor Barrier","Modular Shutter"};
    private static final String[] SIZES={"Small (128 x 256)","Medium (256 x 512)","Large (512 x 1024)"};
    private static final String[] DIRECTIONS={"Sideways","Up","Down"};
    public GuiSpaceDoor(TileEntitySpaceDoor tile) {
        super(tile); this.tile=tile;
        design=tile.getDesign(); detail=tile.getDetail(); framed=tile.isFramed();
        sliding=((com.vandorlabs.blocks.BlockDetailedDoor)tile.getWorld().getBlockState(tile.getPos()).getBlock()).isSlidingModel();
        slideDirection=tile.getSlideDirection();
        middle=tile.isMiddle();
        ySize=224;
    }
    @Override public void initGui() {
        super.initGui();
        for (GuiButton button:buttonList) if (button.id==1) button.y=guiTop+194;
        buttonList.add(new GuiButton(10,guiLeft+14,guiTop+66,212,20,"Design: "+LABELS[design]));
        buttonList.add(new GuiButton(11,guiLeft+14,guiTop+94,212,20,"Texture: "+SIZES[detail]));
        buttonList.add(new GuiButton(12,guiLeft+14,guiTop+122,212,20,framed?"Frame: Framed":"Frame: Bare"));
        if (sliding) buttonList.add(new GuiButton(13,guiLeft+14,guiTop+150,212,20,"Slide direction: "+DIRECTIONS[slideDirection]));
        else buttonList.add(new GuiButton(14,guiLeft+14,guiTop+150,212,20,middle?"Position: Middle":"Position: Edge"));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id==10) { design=(design+1)%LABELS.length; button.displayString="Design: "+LABELS[design]; }
        else if (button.id==11) { detail=(detail+1)%SIZES.length; button.displayString="Texture: "+SIZES[detail]; }
        else if (button.id==12) { framed=!framed; button.displayString=framed?"Frame: Framed":"Frame: Bare"; }
        else if (button.id==13) { slideDirection=(slideDirection+1)%3; button.displayString="Slide direction: "+DIRECTIONS[slideDirection]; }
        else if (button.id==14) { middle=!middle; button.displayString=middle?"Position: Middle":"Position: Edge"; }
        else super.actionPerformed(button);
    }
    @Override protected void submit() {
        if (channel()>=0) PacketHandler.INSTANCE.sendToServer(
                new MessageSpaceDoor(tile.getPos(),design,detail,framed,channel(),sliding?slideDirection:0,!sliding && middle));
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString("Space Door",14,10,0xFFFFFF);
        fontRenderer.drawString("Channel (0 = none)",14,43,0xDAE8F0);
        fontRenderer.drawString("Done applies to both paired leaves",14,179,0xDAE8F0);
    }
}
