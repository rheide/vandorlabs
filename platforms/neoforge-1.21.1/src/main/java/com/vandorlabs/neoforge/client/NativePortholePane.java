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
    private final boolean diagonal,diagonalGlass;
    private int diagonalFacing=2;private boolean diagonalInverted;
    private final Map<Integer,NativeMaterialPicker> pickers=new HashMap<>();
    public NativePortholePane(ProgrammableMenu menu,Runnable rebuild){
        if(!(menu.tile instanceof NativeWallEntity wall)||!wall.isPorthole()&&!wall.isGlass())throw new IllegalArgumentException("Porthole editor requires porthole host");this.menu=menu;this.rebuild=rebuild;diagonalGlass=wall.isGlass();var snapshot=wall.wallAppearance();base=snapshot.base();walls=snapshot.walls();frames=snapshot.frames();glass=snapshot.glass();playback=snapshot.playback();diagonal=menu.tile.getBlockState().getBlock() instanceof NativeDiagonalWallBlock;if(diagonal){diagonalFacing=menu.tile.getBlockState().getValue(NativeDiagonalWallBlock.FACING).get3DDataValue();diagonalInverted=menu.tile.getBlockState().getValue(NativeDiagonalWallBlock.INVERTED);}
    }
    private boolean framePage(int id){return diagonalGlass?DiagonalGlassDialog.framePage(id):PortholeDialog.framePage(id);}
    private boolean glassPage(int id){return id==(diagonalGlass?DiagonalGlassDialog.GLASS:PortholeDialog.GLASS);}
    private boolean page(int id){return diagonalGlass?DiagonalGlassDialog.page(id):PortholeDialog.page(id);}
    public int page(){return page;}
    public int selectedTexture(){return glassPage(page)?glass.material<0?PortholeDialog.shadeChoice(glass.shade):glass.material:page==DiagonalGlassDialog.FRONT?draft.front:page==PortholeDialog.INTERIOR?draft.interior:page==PortholeDialog.EXTERIOR?draft.exterior:base.housing().mainTexture;}
    public NativeMaterialPicker picker(){
        return pickers.computeIfAbsent(page,id->{
            if(framePage(id))return new NativeMaterialPicker(selectedTexture(),id==DiagonalGlassDialog.FRONT?choice->NativeWallEntity.validFrame(choice)&&NativeWallDialog.surfaceOption(choice):NativeWallDialog::surfaceOption,this::choose).originalArtwork("vandorlabs:frame-default/metal_side").sampling(false);
            if(glassPage(id)){var picker=new NativeMaterialPicker(selectedTexture(),choice->{var entry=ModernFinishCatalog.option(choice);return entry.general()||entry.displaySelectable();},this::choose).sampling(false);for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++)picker.specialMaterial(PortholeDialog.shadeChoice(shade),com.vandorlabs.tiles.GlassTextures.LABELS[shade],"Glass","vandorlabs:glass-palette/"+com.vandorlabs.tiles.GlassTextures.IDS[shade]);return picker;}
            return new NativeMaterialPicker(selectedTexture(),choice->ModernFinishCatalog.option(choice).general(),this::choose);
        });
    }
    private static final class Control extends DialogControl{Control(int id,int x,int y,int width,int height,String label,boolean enabled){super(id,x,y,width,height,label,false,enabled);}}
    public List<DialogControl> controls(ProgrammableDialogLayout panel){
        var controls=new ArrayList<>(diagonalGlass?DiagonalGlassDialog.tabs(panel,page):PortholeDialog.tabs(panel,page));
        if(diagonalGlass){if(framePage(page))controls.add(new Control(DiagonalGlassDialog.FRAME,panel.controlsX,panel.top+54,154,20,glass.framed?"Framed":"Bare",true));else for(var control:DiagonalGlassDialog.body(panel,new DiagonalWallDialog.Geometry(walls,diagonalFacing,diagonalInverted),glass))if(DiagonalGlassDialog.visible(control.id))controls.add(control);}
        else controls.addAll(PortholeDialog.body(panel,page,base.housing().layout,walls,glass,diagonal,diagonalFacing,diagonalInverted));
        if(glassPage(page)&&glass.material>=0&&ModernFinishCatalog.option(glass.material).playback()!=null){int width=(ProgrammableDialogLayout.CONTROLS_WIDTH+2)/3,y=panel.top+PortholeDialog.playbackY(glass,diagonal);String[] modes={"off","static","animated"},speeds={"slow","normal","fast"};for(int i=0;i<3;i++){controls.add(new Control(700+i,panel.controlsX+i*width,y,width-2,18,Component.translatable("gui.vandorlabs.selector."+modes[i]).getString(),playback.mode()!=i));controls.add(new Control(710+i,panel.controlsX+i*width,y+24,width-2,18,Component.translatable("gui.vandorlabs.selector."+speeds[i]).getString(),playback.speed()!=i));}}
        controls.add(new Control(ProgrammableBlockDialog.DONE,panel.controlsX,panel.doneY,154,20,Component.translatable("gui.done").getString(),true));return List.copyOf(controls);
    }
    public int listWidth(ProgrammableDialogLayout panel){return framePage(page)&&!diagonalGlass?panel.width-24:panel.listWidth;}
    public int listRows(ProgrammableDialogLayout panel){return Math.max(2,(panel.height-(framePage(page)?diagonalGlass?88:110:84))/ProgrammableDialogLayout.ROW_HEIGHT);}
    public boolean reverseOption(int id){return diagonalGlass&&(id==109||id==110||id==112||id==116||id==DiagonalGlassDialog.FRAME)||id==103||id==102||id==104||id==113||id==115||diagonal&&(id==105||id==111);}
    public void action(int id,int direction){
        picker().closeInventory();if(page(id)){if(framePage(id)&&draft==null)draft=frames.editor(!diagonalGlass,false,NativeWallDialog.defaultScreen());page=id;rebuild.run();return;}
        if(diagonal&&(id==105||id==111||diagonalGlass&&(id==109||id==110||id==112))){var next=new DiagonalWallDialog.Geometry(walls,diagonalFacing,diagonalInverted).action(id,direction);walls=next.walls;diagonalFacing=next.facing;diagonalInverted=next.inverted;PacketDistributor.sendToServer(new DiagonalGeometryPayload(menu.tile.getBlockPos(),menu.containerId,walls.halfHeight?2:walls.fullWidth?1:0,walls.fill,walls.join,net.minecraft.core.Direction.from3DDataValue(diagonalFacing),diagonalInverted));}
        else if(id==103){var housing=base.housing().action(id,direction);base=new ProgrammableShapeEntity.Appearance(housing,base.sideTexture(),base.stairs());sendWall();}
        else if(id==102||id==104){walls=new WallSettings(walls.fullWidth,walls.halfHeight,walls.fill,walls.join,id==102?!walls.joinPortholes:walls.joinPortholes,id==104?WallAppearanceDialog.nextShape(walls.portholeShape,direction):walls.portholeShape);sendGlass();}
        else if(id==113||id==115||diagonalGlass&&(id==116||id==DiagonalGlassDialog.FRAME)){glass=diagonalGlass?DiagonalGlassDialog.action(glass,id,direction):PortholeDialog.action(glass,id,direction);sendGlass();}
        else if(id>=700&&id<=702){playback=new PanelPlayback(id-700,playback.speed());sendGlass();}
        else if(id>=710&&id<=712){playback=new PanelPlayback(playback.mode(),id-710);sendGlass();}
        rebuild.run();
    }
    public void choose(int choice){
        if(glassPage(page)){glass=PortholeDialog.choose(glass,choice);sendGlass();}
        else if(framePage(page)){draft=new FrameSettings(page==DiagonalGlassDialog.FRONT?choice:draft.front,page==PortholeDialog.INTERIOR?choice:draft.interior,page==PortholeDialog.EXTERIOR?choice:draft.exterior,draft.artworkVersion,draft.splitTop,draft.splitBottom,draft.tile,draft.displayFit,draft.split,draft.bare);frames=draft;sendWall();}
        else{base=new ProgrammableShapeEntity.Appearance(base.housing().choose(choice),base.sideTexture(),base.stairs());sendWall();}rebuild.run();
    }
    private void sendGlass(){int layout=glass.textureLayout();glass=new PanelGlassSettings(glass.shade,glass.material,layout,layout!=1,glass.tint,glass.framed,glass.textureJoin);PacketDistributor.sendToServer(new PortholeGlassPayload(menu.tile.getBlockPos(),menu.containerId,glass,playback,walls.joinPortholes,walls.portholeShape));}
    private void sendWall(){PacketDistributor.sendToServer(new WallAppearancePayload(menu.tile.getBlockPos(),menu.containerId,base,walls,frames));}
}
