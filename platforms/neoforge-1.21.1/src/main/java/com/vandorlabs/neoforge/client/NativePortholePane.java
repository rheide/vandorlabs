package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.ui.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pane component for the existing programmable screen; wired only with functional insert rendering. */
public final class NativePortholePane {
    private final ProgrammableMenu menu;
    private final Runnable rebuild;
    private ProgrammableShapeEntity.Appearance base;
    private WallSettings walls;
    private FrameSettings frames,draft;
    private PanelGlassSettings glass;
    private PanelPlayback playback;
    private int page=PortholeDialog.MAIN;
    private final Map<Integer,NativeMaterialPicker> pickers=new HashMap<>();
    public NativePortholePane(ProgrammableMenu menu,Runnable rebuild){
        if(!(menu.tile instanceof NativeWallEntity wall)||!wall.isPorthole())throw new IllegalArgumentException("Porthole editor requires porthole host");this.menu=menu;this.rebuild=rebuild;var snapshot=wall.wallAppearance();base=snapshot.base();walls=snapshot.walls();frames=snapshot.frames();glass=snapshot.glass();playback=snapshot.playback();
    }
    public int page(){return page;}
    public int selectedTexture(){return page==PortholeDialog.GLASS?glass.material<0?PortholeDialog.shadeChoice(glass.shade):glass.material:page==PortholeDialog.INTERIOR?draft.interior:page==PortholeDialog.EXTERIOR?draft.exterior:base.housing().mainTexture;}
    public NativeMaterialPicker picker(){
        return pickers.computeIfAbsent(page,id->{
            if(PortholeDialog.framePage(id))return new NativeMaterialPicker(selectedTexture(),NativeWallDialog::surfaceOption,this::choose).originalArtwork("vandorlabs:frame-default/metal_side").sampling(false);
            if(id==PortholeDialog.GLASS){var picker=new NativeMaterialPicker(selectedTexture(),choice->{var entry=ModernFinishCatalog.option(choice);return entry.general()||entry.displaySelectable();},this::choose).sampling(false);for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++)picker.specialMaterial(PortholeDialog.shadeChoice(shade),com.vandorlabs.tiles.GlassTextures.LABELS[shade],"Glass","vandorlabs:glass-palette/"+com.vandorlabs.tiles.GlassTextures.IDS[shade]);return picker;}
            return new NativeMaterialPicker(selectedTexture(),choice->ModernFinishCatalog.option(choice).general(),this::choose);
        });
    }
    private static final class Control extends DialogControl{Control(int id,int x,int y,int width,int height,String label,boolean enabled){super(id,x,y,width,height,label,false,enabled);}}
    public List<DialogControl> controls(ProgrammableDialogLayout panel){
        var controls=new ArrayList<>(PortholeDialog.tabs(panel,page));controls.addAll(PortholeDialog.body(panel,page,base.housing().layout,walls,glass));
        if(page==PortholeDialog.GLASS&&glass.material>=0&&ModernFinishCatalog.option(glass.material).playback()!=null){int width=(ProgrammableDialogLayout.CONTROLS_WIDTH+2)/3,y=panel.top+PortholeDialog.playbackY(glass);String[] modes={"off","static","animated"},speeds={"slow","normal","fast"};for(int i=0;i<3;i++){controls.add(new Control(700+i,panel.controlsX+i*width,y,width-2,18,Component.translatable("gui.vandorlabs.selector."+modes[i]).getString(),playback.mode()!=i));controls.add(new Control(710+i,panel.controlsX+i*width,y+24,width-2,18,Component.translatable("gui.vandorlabs.selector."+speeds[i]).getString(),playback.speed()!=i));}}
        controls.add(new Control(ProgrammableBlockDialog.DONE,panel.controlsX,panel.doneY,154,20,Component.translatable("gui.done").getString(),true));return List.copyOf(controls);
    }
    public int listWidth(ProgrammableDialogLayout panel){return PortholeDialog.framePage(page)?panel.width-24:panel.listWidth;}
    public int listRows(ProgrammableDialogLayout panel){return Math.max(2,(panel.height-(PortholeDialog.framePage(page)?110:84))/ProgrammableDialogLayout.ROW_HEIGHT);}
    public boolean reverseOption(int id){return id==103||id==102||id==104||id==113||id==115;}
    public void action(int id,int direction){
        picker().closeInventory();if(PortholeDialog.page(id)){if(PortholeDialog.framePage(id)&&draft==null)draft=frames.editor(true,false,NativeWallDialog.defaultScreen());page=id;rebuild.run();return;}
        if(id==103){var housing=base.housing().action(id,direction);base=new ProgrammableShapeEntity.Appearance(housing,base.sideTexture(),base.stairs());sendWall();}
        else if(id==102||id==104){walls=new WallSettings(walls.fullWidth,walls.halfHeight,walls.fill,walls.join,id==102?!walls.joinPortholes:walls.joinPortholes,id==104?WallAppearanceDialog.nextShape(walls.portholeShape,direction):walls.portholeShape);sendGlass();}
        else if(id==113||id==115){glass=PortholeDialog.action(glass,id,direction);sendGlass();}
        else if(id>=700&&id<=702){playback=new PanelPlayback(id-700,playback.speed());sendGlass();}
        else if(id>=710&&id<=712){playback=new PanelPlayback(playback.mode(),id-710);sendGlass();}
        rebuild.run();
    }
    public void choose(int choice){
        if(page==PortholeDialog.GLASS){glass=PortholeDialog.choose(glass,choice);sendGlass();}
        else if(PortholeDialog.framePage(page)){draft=new FrameSettings(draft.front,page==PortholeDialog.INTERIOR?choice:draft.interior,page==PortholeDialog.EXTERIOR?choice:draft.exterior,draft.artworkVersion,draft.splitTop,draft.splitBottom,draft.tile,draft.displayFit,draft.split,draft.bare);frames=draft;sendWall();}
        else{base=new ProgrammableShapeEntity.Appearance(base.housing().choose(choice),base.sideTexture(),base.stairs());sendWall();}rebuild.run();
    }
    private void sendGlass(){int layout=glass.textureLayout();glass=new PanelGlassSettings(glass.shade,glass.material,layout,layout!=1,glass.tint,glass.framed,glass.textureJoin);PacketDistributor.sendToServer(new PortholeGlassPayload(menu.tile.getBlockPos(),menu.containerId,glass,playback,walls.joinPortholes,walls.portholeShape));}
    private void sendWall(){PacketDistributor.sendToServer(new WallAppearancePayload(menu.tile.getBlockPos(),menu.containerId,base,walls,frames));}
}
