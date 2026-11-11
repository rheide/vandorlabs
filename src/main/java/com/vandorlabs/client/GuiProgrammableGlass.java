package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.ui.GlassDialog;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.gui.GuiButton;
import java.io.IOException;

/** Glass finishes use the same scrollable thumbnail picker as programmable walls. */
public final class GuiProgrammableGlass extends GuiMaterialPicker {
    private final TileEntityProgrammableGlass tile;
    private GlassDialog.State state;
    private HousingTextureList textures;private FrameAppearancePane framePane;
    public GuiProgrammableGlass(TileEntityProgrammableGlass tile) {
        super(new ContainerProgrammableGlass(tile));this.tile=tile;
        state=new GlassDialog.State(tile.getShade(),tile.getTint(),tile.getMaterial(),tile.getTextureLayout(),tile.isJoin(),tile.isFramed(),tile.isStretch());
    }
    @Override protected HousingTextureList materialPicker(){return state.framePage()&&framePane!=null?framePane.picker():textures;}
    @Override public void initGui() {
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);
        xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();com.vandorlabs.ui.ProgrammableDialogLayout shared=layout.shared();
        for(GlassDialog.Control control:GlassDialog.tabs(shared,state))addControl(control);
        textures=HousingTextureList.forGlassMaterials(state.size,shared.listX,GlassDialog.listY(shared),GlassDialog.listWidth(shared),state.shade,state.material)
                .visibleRows(GlassDialog.listRows(shared)).keepView(textures);
        if(!state.framePage()){
            for(GlassDialog.Control control:GlassDialog.body(shared,state))addControl(control);
            if(GlassPlaybackPane.available(state.material))GlassPlaybackPane.init(layout,tile.glassPlayback,buttonList,130);
        }else{if(framePane==null)framePane=new FrameAppearancePane(this,tile,()->{state=state.withFramed(!tile.isFramed());send();},()->tile.isFramed());framePane.initGlassFace(state.page-1,54,buttonList);}
        addControl(GlassDialog.done(shared,"Done"));
    }
    private void addControl(GlassDialog.Control control){GuiButton button=control.tab?new DialogTabButton(control.id,control.x,control.y,control.width,control.height,control.label):new GuiButton(control.id,control.x,control.y,control.width,control.height,control.label);button.enabled=control.enabled;button.visible=control.visible;buttonList.add(button);}
    void choose(int choice){if(textures!=null&&textures.selected()!=choice)textures.setSelected(choice);state=state.choose(choice,HousingTextureList.CANOPY_GLASS_BASE);send();initGui();}
    private void send(){tile.configure(state.size,state.shade,state.material,state.layout,state.join,state.tint,state.framed,-1,1);PacketHandler.INSTANCE.sendToServer(new MessageProgrammableGlass(tile.getPos(),state.size,state.shade,state.join).withAppearance(state.tint,state.framed).withMaterial(state.material).withTextureLayout(state.layout).withPlayback(tile.glassPlayback.mode(),tile.glassPlayback.speed()));}
    @Override protected void actionPerformed(GuiButton button) {
        if(GlassPlaybackPane.action(button,tile.glassPlayback)){send();initGui();return;}
        if(state.framePage()&&framePane.action(button))return;
        if(GlassDialog.isPage(button.id)){state=state.action(button.id,GuiOptionCycle.direction());initGui();return;}

        if(button.id==2){mc.player.closeScreen();return;}
        state=state.action(button.id,GuiOptionCycle.direction());
        send();initGui();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        if(state.framePage()){if(!framePane.click(x,y,button))super.mouseClicked(x,y,button);return;}
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,GlassDialog.rightClickIds()))return;
        if(textures.click(x,y,button)){if(textures.picked())choose(textures.selected());return;}
        super.mouseClicked(x,y,button);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){}
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF101012);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF202028);
        fontRenderer.drawString(GlassDialog.TITLE,guiLeft+8,guiTop+8,0xFFFFFFFF);
        if(state.framePage())framePane.draw(x,y);else textures.draw(fontRenderer,x,y);DialogHeader.logo(guiLeft,guiTop,xSize);super.drawScreen(x,y,partial);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
